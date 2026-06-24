package com.kian.perficon.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Updates the two Manifest values that Android reads before loading the APK's resources.
 * Resource IDs remain unchanged, so the CandyBar template's compiled resources stay valid.
 */
object ApkManifestEditor {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val UTF8_FLAG = 0x00000100
    private const val NO_INDEX = -1
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11

    fun patch(manifest: ByteArray, packageName: String, versionCode: Int, appName: String): ByteArray {
        require(isValidPackageName(packageName)) { "Invalid Android package name: $packageName" }
        require(versionCode > 0) { "Version code must be positive." }

        val buffer = ByteBuffer.wrap(manifest).order(ByteOrder.LITTLE_ENDIAN)
        require(readU16(buffer, 0) == RES_XML_TYPE) { "The template manifest is not binary Android XML." }

        val poolStart = readU16(buffer, 2)
        require(readU16(buffer, poolStart) == RES_STRING_POOL_TYPE) { "The template manifest has no string pool." }
        val pool = StringPool.read(manifest, poolStart)
        val strings = pool.strings.toMutableList()

        val templatePackageName = readPackageName(manifest) ?: "com.kian.perficontemplate"
        val appNameIndex = strings.size.also { strings += appName }

        var packageNameUpdated = false
        var versionCodeUpdated = false
        var offset = poolStart + pool.chunkSize
        while (offset < manifest.size) {
            val type = readU16(buffer, offset)
            val headerSize = readU16(buffer, offset + 2)
            val chunkSize = readI32(buffer, offset + 4)
            require(chunkSize >= headerSize && offset + chunkSize <= manifest.size) {
                "Malformed binary XML chunk in template manifest."
            }

            if (type == RES_XML_START_ELEMENT_TYPE) {
                val attributeExtension = offset + headerSize
                val elementName = strings.getOrNull(readI32(buffer, attributeExtension + 4))
                val attributeStart = readU16(buffer, attributeExtension + 8)
                val attributeSize = readU16(buffer, attributeExtension + 10)
                val attributeCount = readU16(buffer, attributeExtension + 12)
                val attributeOffset = attributeExtension + attributeStart

                repeat(attributeCount) { index ->
                    val currentAttribute = attributeOffset + index * attributeSize
                    val nameIndex = readI32(buffer, currentAttribute + 4)
                    val rawValueIndex = readI32(buffer, currentAttribute + 8)
                    val dataType = manifest[currentAttribute + 15].toInt() and 0xff
                    val name = strings.getOrNull(nameIndex)

                    when (name) {
                        "package" -> {
                            require(rawValueIndex != NO_INDEX) { "Template package attribute has no raw value." }
                            strings[rawValueIndex] = packageName
                            packageNameUpdated = true
                        }

                        "versionCode" -> {
                            require(dataType == TYPE_INT_DEC || dataType == TYPE_INT_HEX) {
                                "Template versionCode is not an integer."
                            }
                            writeI32(manifest, currentAttribute + 16, versionCode)
                            versionCodeUpdated = true
                        }

                        "label" -> if (elementName == "application") {
                            writeI32(manifest, currentAttribute + 8, appNameIndex)
                            manifest[currentAttribute + 15] = 0x03
                            writeI32(manifest, currentAttribute + 16, appNameIndex)
                        }

                        "authorities" -> if (rawValueIndex != NO_INDEX) {
                            strings[rawValueIndex] = strings[rawValueIndex].replace(templatePackageName, packageName)
                        }

                        "name" -> if (rawValueIndex != NO_INDEX && strings[rawValueIndex].contains(templatePackageName)) {
                            if (elementName == "permission" || elementName == "uses-permission" || elementName == "uses-permission-sdk-23") {
                                strings[rawValueIndex] = strings[rawValueIndex].replace(templatePackageName, packageName)
                            }
                        }
                    }
                }
            }
            offset += chunkSize
        }

        require(packageNameUpdated) { "Template manifest has no package attribute." }
        require(versionCodeUpdated) { "Template manifest has no versionCode attribute." }

        val rebuiltPool = pool.rebuild(strings)
        val delta = rebuiltPool.size - pool.chunkSize
        return ByteArrayOutputStream(manifest.size + delta).use { output ->
            output.write(manifest, 0, poolStart)
            output.write(rebuiltPool)
            output.write(manifest, poolStart + pool.chunkSize, manifest.size - poolStart - pool.chunkSize)
            output.toByteArray().also { patched ->
                writeI32(patched, 4, readI32(buffer, 4) + delta)
            }
        }
    }

    internal fun readPackageName(manifest: ByteArray): String? = readManifestAttribute(manifest, "package")

    internal fun readVersionCode(manifest: ByteArray): Int? {
        val buffer = ByteBuffer.wrap(manifest).order(ByteOrder.LITTLE_ENDIAN)
        val poolStart = readU16(buffer, 2)
        val pool = StringPool.read(manifest, poolStart)
        var offset = poolStart + pool.chunkSize
        while (offset < manifest.size) {
            val type = readU16(buffer, offset)
            val headerSize = readU16(buffer, offset + 2)
            val chunkSize = readI32(buffer, offset + 4)
            if (type == RES_XML_START_ELEMENT_TYPE) {
                val extension = offset + headerSize
                val attributeStart = readU16(buffer, extension + 8)
                val attributeSize = readU16(buffer, extension + 10)
                val attributeCount = readU16(buffer, extension + 12)
                val attributeOffset = extension + attributeStart
                repeat(attributeCount) { index ->
                    val attribute = attributeOffset + index * attributeSize
                    if (pool.strings.getOrNull(readI32(buffer, attribute + 4)) == "versionCode") {
                        return readI32(buffer, attribute + 16)
                    }
                }
            }
            offset += chunkSize
        }
        return null
    }

    private fun readManifestAttribute(manifest: ByteArray, attributeName: String): String? {
        val buffer = ByteBuffer.wrap(manifest).order(ByteOrder.LITTLE_ENDIAN)
        val poolStart = readU16(buffer, 2)
        val pool = StringPool.read(manifest, poolStart)
        var offset = poolStart + pool.chunkSize
        while (offset < manifest.size) {
            val type = readU16(buffer, offset)
            val headerSize = readU16(buffer, offset + 2)
            val chunkSize = readI32(buffer, offset + 4)
            if (type == RES_XML_START_ELEMENT_TYPE) {
                val extension = offset + headerSize
                val attributeStart = readU16(buffer, extension + 8)
                val attributeSize = readU16(buffer, extension + 10)
                val attributeCount = readU16(buffer, extension + 12)
                val attributeOffset = extension + attributeStart
                repeat(attributeCount) { index ->
                    val attribute = attributeOffset + index * attributeSize
                    if (pool.strings.getOrNull(readI32(buffer, attribute + 4)) == attributeName) {
                        val rawValue = readI32(buffer, attribute + 8)
                        return pool.strings.getOrNull(rawValue)
                    }
                }
            }
            offset += chunkSize
        }
        return null
    }

    private fun isValidPackageName(value: String): Boolean =
        value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))

    private fun readU16(buffer: ByteBuffer, offset: Int): Int = buffer.getShort(offset).toInt() and 0xffff

    private fun readI32(buffer: ByteBuffer, offset: Int): Int = buffer.getInt(offset)

    private fun writeI32(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }

    internal class StringPool(
        val headerSize: Int,
        val chunkSize: Int,
        val flags: Int,
        val strings: List<String>
    ) {
        val isUtf8: Boolean = flags and UTF8_FLAG != 0

        fun rebuild(updatedStrings: List<String>): ByteArray {
            val stringsData = ByteArrayOutputStream()
            val offsets = IntArray(updatedStrings.size)
            updatedStrings.forEachIndexed { index, value ->
                offsets[index] = stringsData.size()
                stringsData.write(encodeString(value, isUtf8))
            }
            while (stringsData.size() % 4 != 0) stringsData.write(0)

            val stringsStart = headerSize + offsets.size * Int.SIZE_BYTES
            val newChunkSize = stringsStart + stringsData.size()
            return ByteBuffer.allocate(newChunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(RES_STRING_POOL_TYPE.toShort())
                putShort(headerSize.toShort())
                putInt(newChunkSize)
                putInt(offsets.size)
                putInt(0)
                putInt(flags)
                putInt(stringsStart)
                putInt(0)
                offsets.forEach(::putInt)
                put(stringsData.toByteArray())
            }.array()
        }

        companion object {
            fun read(bytes: ByteArray, start: Int): StringPool {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val headerSize = readU16(buffer, start + 2)
                val chunkSize = readI32(buffer, start + 4)
                val stringCount = readI32(buffer, start + 8)
                val styleCount = readI32(buffer, start + 12)
                val flags = readI32(buffer, start + 16)
                val stringsStart = readI32(buffer, start + 20)
                require(styleCount == 0) { "Styled string pools are not supported in the template manifest." }
                require(chunkSize > 0 && start + chunkSize <= bytes.size) { "Malformed string pool in template manifest." }

                val offsetsStart = start + headerSize
                val strings = List(stringCount) { index ->
                    decodeString(bytes, start + stringsStart + readI32(buffer, offsetsStart + index * Int.SIZE_BYTES), flags)
                }
                return StringPool(headerSize, chunkSize, flags, strings)
            }

            private fun decodeString(bytes: ByteArray, start: Int, flags: Int): String {
                var offset = start
                return if (flags and UTF8_FLAG != 0) {
                    offset += encodedLengthSize(bytes, offset)
                    val byteLength = readEncodedLength(bytes, offset)
                    offset += encodedLengthSize(bytes, offset)
                    String(bytes, offset, byteLength, Charsets.UTF_8)
                } else {
                    val length = readUtf16Length(bytes, offset)
                    offset += utf16LengthSize(bytes, offset)
                    String(bytes, offset, length * 2, Charsets.UTF_16LE)
                }
            }

            private fun encodeString(value: String, utf8: Boolean): ByteArray {
                val output = ByteArrayOutputStream()
                if (utf8) {
                    val encoded = value.toByteArray(Charsets.UTF_8)
                    writeEncodedLength(output, value.length)
                    writeEncodedLength(output, encoded.size)
                    output.write(encoded)
                    output.write(0)
                } else {
                    writeUtf16Length(output, value.length)
                    output.write(value.toByteArray(Charsets.UTF_16LE))
                    output.write(byteArrayOf(0, 0))
                }
                return output.toByteArray()
            }

            private fun readEncodedLength(bytes: ByteArray, offset: Int): Int {
                val first = bytes[offset].toInt() and 0xff
                return if (first and 0x80 == 0) first else (first and 0x7f shl 8) or (bytes[offset + 1].toInt() and 0xff)
            }

            private fun encodedLengthSize(bytes: ByteArray, offset: Int): Int =
                if (bytes[offset].toInt() and 0x80 == 0) 1 else 2

            private fun writeEncodedLength(output: ByteArrayOutputStream, value: Int) {
                require(value < 0x8000) { "Manifest string is too long." }
                if (value < 0x80) {
                    output.write(value)
                } else {
                    output.write((value shr 8) or 0x80)
                    output.write(value and 0xff)
                }
            }

            private fun readUtf16Length(bytes: ByteArray, offset: Int): Int {
                val first = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
                return if (first and 0x8000 == 0) first else (first and 0x7fff shl 16) or
                    ((bytes[offset + 2].toInt() and 0xff) or ((bytes[offset + 3].toInt() and 0xff) shl 8))
            }

            private fun utf16LengthSize(bytes: ByteArray, offset: Int): Int {
                val first = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
                return if (first and 0x8000 == 0) 2 else 4
            }

            private fun writeUtf16Length(output: ByteArrayOutputStream, value: Int) {
                require(value < 0x80000000) { "Manifest string is too long." }
                if (value < 0x8000) {
                    output.write(value and 0xff)
                    output.write(value shr 8)
                } else {
                    output.write((value shr 16 and 0x7f) or 0x80)
                    output.write(value shr 24)
                    output.write(value and 0xff)
                    output.write(value shr 8)
                }
            }
        }
    }
}
