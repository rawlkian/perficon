package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds the compiled XML resource read by Lawnchair and other resource-based launchers. */
object BinaryAppFilterWriter {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val UTF8_FLAG = 0x00000100
    private const val NO_INDEX = -1
    private const val TYPE_STRING = 0x03

    fun build(mappings: List<IconMapping>, slotIndices: List<Int>): ByteArray {
        require(mappings.size == slotIndices.size) { "Mappings and template slots must match." }

        val strings = linkedSetOf("resources", "item", "component", "drawable")
        mappings.zip(slotIndices).forEach { (mapping, slotIndex) ->
            strings += componentName(mapping)
            strings += "icon_$slotIndex"
        }
        val allStrings = strings.toList()
        val indexes = allStrings.withIndex().associate { it.value to it.index }

        val body = ByteArrayOutputStream().apply {
            writeStartElement(indexes.getValue("resources"), emptyList())
            mappings.zip(slotIndices).forEach { (mapping, slotIndex) ->
                writeStartElement(
                    indexes.getValue("item"),
                    listOf(
                        indexes.getValue("component") to indexes.getValue(componentName(mapping)),
                        indexes.getValue("drawable") to indexes.getValue("icon_$slotIndex")
                    )
                )
                writeEndElement(indexes.getValue("item"))
            }
            writeEndElement(indexes.getValue("resources"))
        }.toByteArray()
        val stringPool = buildStringPool(allStrings)

        return ByteBuffer.allocate(8 + stringPool.size + 8 + body.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(RES_XML_TYPE.toShort())
                putShort(8)
                putInt(capacity())
                put(stringPool)
                putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
                putShort(8)
                putInt(8)
                put(body)
            }
            .array()
    }

    private fun componentName(mapping: IconMapping): String {
        val activity = mapping.targetActivityName.ifBlank {
            "${mapping.targetPackageName}.MainActivity"
        }
        val qualifiedActivity = if (activity.startsWith(".")) {
            mapping.targetPackageName + activity
        } else {
            activity
        }
        return "ComponentInfo{${mapping.targetPackageName}/$qualifiedActivity}"
    }

    private fun buildStringPool(strings: List<String>): ByteArray {
        val content = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { index, value ->
            offsets[index] = content.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeLength(content, value.length)
            writeLength(content, bytes.size)
            content.write(bytes)
            content.write(0)
        }
        while (content.size() % 4 != 0) content.write(0)

        val headerSize = 28
        val stringsStart = headerSize + offsets.size * Int.SIZE_BYTES
        return ByteBuffer.allocate(stringsStart + content.size())
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(RES_STRING_POOL_TYPE.toShort())
                putShort(headerSize.toShort())
                putInt(capacity())
                putInt(offsets.size)
                putInt(0)
                putInt(UTF8_FLAG)
                putInt(stringsStart)
                putInt(0)
                offsets.forEach(::putInt)
                put(content.toByteArray())
            }
            .array()
    }

    private fun ByteArrayOutputStream.writeStartElement(name: Int, attributes: List<Pair<Int, Int>>) {
        val size = 36 + attributes.size * 20
        writeChunkHeader(RES_XML_START_ELEMENT_TYPE, 16, size)
        writeInt(0)
        writeInt(NO_INDEX)
        writeInt(NO_INDEX)
        writeInt(name)
        writeShort(20)
        writeShort(20)
        writeShort(attributes.size)
        writeShort(0)
        writeShort(0)
        writeShort(0)
        attributes.forEach { (attributeName, value) ->
            writeInt(NO_INDEX)
            writeInt(attributeName)
            writeInt(value)
            writeShort(8)
            write(0)
            write(TYPE_STRING)
            writeInt(value)
        }
    }

    private fun ByteArrayOutputStream.writeEndElement(name: Int) {
        writeChunkHeader(RES_XML_END_ELEMENT_TYPE, 16, 24)
        writeInt(0)
        writeInt(NO_INDEX)
        writeInt(NO_INDEX)
        writeInt(name)
    }

    private fun ByteArrayOutputStream.writeChunkHeader(type: Int, headerSize: Int, size: Int) {
        writeShort(type)
        writeShort(headerSize)
        writeInt(size)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
        write(value ushr 16 and 0xff)
        write(value ushr 24 and 0xff)
    }

    private fun writeLength(output: ByteArrayOutputStream, value: Int) {
        require(value < 0x8000) { "XML string is too long." }
        if (value < 0x80) {
            output.write(value)
        } else {
            output.write((value ushr 8) or 0x80)
            output.write(value and 0xff)
        }
    }
}
