package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BinaryAppFilterWriterTest {
    @Test
    fun writesComponentMappingsAsBinaryAndroidXml() {
        val xml = BinaryAppFilterWriter.build(
            mappings = listOf(
                IconMapping(
                    projectId = 1,
                    targetPackageName = "app.lawnchair",
                    targetActivityName = "app.lawnchair.LawnchairLauncher",
                    iconPath = "/unused/icon.png"
                )
            ),
            slotIndices = listOf(1)
        )

        assertEquals(0x03, xml[0].toInt() and 0xff)
        assertEquals(0x00, xml[1].toInt() and 0xff)
        assertTrue(xml.decodeToString().contains("ComponentInfo{app.lawnchair/app.lawnchair.LawnchairLauncher}"))
        File("build/test-output").apply { mkdirs() }
            .resolve("appfilter.xml")
            .writeBytes(xml)
    }
}
