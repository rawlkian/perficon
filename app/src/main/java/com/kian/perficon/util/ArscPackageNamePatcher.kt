package com.kian.perficon.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Patches the package name inside the compiled resources.arsc file in-place.
 *
 * The package name field in resources.arsc is a fixed-size buffer of 128 UTF-16 characters (256 bytes).
 * We can patch it in-place by converting the new package name to UTF-16LE, padding it to 256 bytes with zeros,
 * and writing it over the old package name.
 */
object ArscPackageNamePatcher {
    /**
     * Patches the package name in resources.arsc bytes.
     */
    fun patch(arscBytes: ByteArray, newPackageName: String): ByteArray {
        val buffer = ByteBuffer.wrap(arscBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (arscBytes.size < 12) return arscBytes
        val tableType = buffer.getShort(0).toInt() and 0xffff
        if (tableType != 0x0002) return arscBytes // Not a table chunk
        val tableHeaderSize = buffer.getShort(2).toInt() and 0xffff
        
        var offset = tableHeaderSize
        while (offset + 8 <= arscBytes.size) {
            val chunkType = buffer.getShort(offset).toInt() and 0xffff
            val chunkHeaderSize = buffer.getShort(offset + 2).toInt() and 0xffff
            val chunkSize = buffer.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > arscBytes.size) break
            
            if (chunkType == 0x0200) { // RES_TABLE_PACKAGE_TYPE
                val nameOffset = offset + 12
                if (nameOffset + 256 <= arscBytes.size) {
                    val nameBytes = newPackageName.toByteArray(Charsets.UTF_16LE)
                    val paddedBytes = ByteArray(256)
                    val copyLength = minOf(nameBytes.size, 256)
                    System.arraycopy(nameBytes, 0, paddedBytes, 0, copyLength)
                    System.arraycopy(paddedBytes, 0, arscBytes, nameOffset, 256)
                }
            }
            offset += chunkSize
        }
        return arscBytes
    }
}

