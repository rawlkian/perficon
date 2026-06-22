package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BinaryDrawableXmlWriterTest {
    @Test
    fun writesCustomDisplayNamesForCandyBar() {
        val xml = BinaryDrawableXmlWriter.build(
            mappings = listOf(
                IconMapping(
                    projectId = 1,
                    iconName = "音乐",
                    targetPackageName = "com.example.music",
                    targetActivityName = "com.example.music.MainActivity",
                    iconPath = "/unused/music.png"
                ),
                IconMapping(
                    projectId = 1,
                    targetPackageName = "com.example.camera",
                    targetActivityName = "com.example.camera.MainActivity",
                    iconPath = "/unused/camera.png"
                )
            ),
            slotIndices = listOf(1, 2)
        )

        val content = xml.decodeToString()
        assertTrue(content.contains("全部图标"))
        assertTrue(content.contains("音乐"))
        assertTrue(content.contains("com.example.camera"))
        File("build/test-output").apply { mkdirs() }
            .resolve("drawable.xml")
            .writeBytes(xml)
    }
}
