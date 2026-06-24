package com.kian.perficon.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscPackageNamePatcherTest {
    @Test
    fun patchesPackageNameInArscBytes() {
        val packageName = "com.kian.perficontemplate"
        val newPackageName = "com.nature.iconpackImported3"

        val nameBytes = packageName.toByteArray(Charsets.UTF_16LE)
        val nameBuffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        nameBuffer.put(nameBytes)

        val packageChunkSize = 12 + 256 + 100
        val totalSize = 12 + packageChunkSize

        val arscBytes = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0002.toShort())
            putShort(12.toShort())
            putInt(totalSize)
            putInt(1)

            putShort(0x0200.toShort())
            putShort(288.toShort())
            putInt(packageChunkSize)
            putInt(127)
            put(nameBuffer.array())
            put(ByteArray(100))
        }.array()

        val patched = ArscPackageNamePatcher.patch(arscBytes, newPackageName)

        val buffer = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12 + 12)
        val patchedNameBytes = ByteArray(256)
        buffer.get(patchedNameBytes)

        val expectedNameBytes = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(newPackageName.toByteArray(Charsets.UTF_16LE))
        }.array()

        assertArrayEquals(expectedNameBytes, patchedNameBytes)
    }
}
