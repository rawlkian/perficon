package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds CandyBar's drawable.xml catalogue with one clean user-icon category. */
object BinaryDrawableXmlWriter {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104
    private const val UTF8_FLAG = 0x00000100
    private const val NO_INDEX = -1
    private const val TYPE_STRING = 0x03

    fun build(mappings: List<IconMapping>, slotIndices: List<Int>): ByteArray {
        require(mappings.size == slotIndices.size) { "Mappings and template slots must match." }

        val strings = linkedSetOf(
            "resources", "version", "category", "item", "title", "drawable", "name", "1", "全部图标"
        )
        mappings.zip(slotIndices).forEach { (mapping, slotIndex) ->
            strings += "icon_$slotIndex"
            strings += mapping.iconName.ifBlank { mapping.targetPackageName }
        }
        val allStrings = strings.toList()
        val indexes = allStrings.withIndex().associate { it.value to it.index }

        val body = ByteArrayOutputStream().apply {
            writeStart(indexes.getValue("resources"), emptyList())
            writeStart(indexes.getValue("version"), emptyList())
            writeCData(indexes.getValue("1"))
            writeEnd(indexes.getValue("version"))
            writeStart(indexes.getValue("category"), listOf(indexes.getValue("title") to indexes.getValue("全部图标")))
            mappings.zip(slotIndices).forEach { (mapping, slotIndex) ->
                writeStart(
                    indexes.getValue("item"),
                    listOf(
                        indexes.getValue("drawable") to indexes.getValue("icon_$slotIndex"),
                        indexes.getValue("name") to indexes.getValue(mapping.iconName.ifBlank { mapping.targetPackageName })
                    )
                )
                writeEnd(indexes.getValue("item"))
            }
            writeEnd(indexes.getValue("category"))
            writeEnd(indexes.getValue("resources"))
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

    private fun buildStringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { index, value ->
            offsets[index] = data.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeLength(data, value.length)
            writeLength(data, bytes.size)
            data.write(bytes)
            data.write(0)
        }
        while (data.size() % 4 != 0) data.write(0)

        val headerSize = 28
        val stringsStart = headerSize + offsets.size * Int.SIZE_BYTES
        return ByteBuffer.allocate(stringsStart + data.size()).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(RES_STRING_POOL_TYPE.toShort())
            putShort(headerSize.toShort())
            putInt(capacity())
            putInt(offsets.size)
            putInt(0)
            putInt(UTF8_FLAG)
            putInt(stringsStart)
            putInt(0)
            offsets.forEach(::putInt)
            put(data.toByteArray())
        }.array()
    }

    private fun ByteArrayOutputStream.writeStart(name: Int, attributes: List<Pair<Int, Int>>) {
        writeHeader(RES_XML_START_ELEMENT_TYPE, 16, 36 + attributes.size * 20)
        writeInt(0); writeInt(NO_INDEX); writeInt(NO_INDEX); writeInt(name)
        writeShort(20); writeShort(20); writeShort(attributes.size); writeShort(0); writeShort(0); writeShort(0)
        attributes.forEach { (attributeName, value) ->
            writeInt(NO_INDEX); writeInt(attributeName); writeInt(value)
            writeShort(8); write(0); write(TYPE_STRING); writeInt(value)
        }
    }

    private fun ByteArrayOutputStream.writeEnd(name: Int) {
        writeHeader(RES_XML_END_ELEMENT_TYPE, 16, 24)
        writeInt(0); writeInt(NO_INDEX); writeInt(NO_INDEX); writeInt(name)
    }

    private fun ByteArrayOutputStream.writeCData(value: Int) {
        writeHeader(RES_XML_CDATA_TYPE, 16, 28)
        writeInt(0); writeInt(NO_INDEX); writeInt(value)
        writeShort(8); write(0); write(TYPE_STRING); writeInt(value)
    }

    private fun ByteArrayOutputStream.writeHeader(type: Int, headerSize: Int, size: Int) {
        writeShort(type); writeShort(headerSize); writeInt(size)
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write(value and 0xff); write(value ushr 8 and 0xff)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value and 0xff); write(value ushr 8 and 0xff); write(value ushr 16 and 0xff); write(value ushr 24 and 0xff)
    }

    private fun writeLength(output: ByteArrayOutputStream, value: Int) {
        require(value < 0x8000) { "XML string is too long." }
        if (value < 0x80) output.write(value) else {
            output.write((value ushr 8) or 0x80)
            output.write(value and 0xff)
        }
    }
}
