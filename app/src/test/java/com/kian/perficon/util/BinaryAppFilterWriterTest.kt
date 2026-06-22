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

    @Test
    fun writesCalendarPrefixesAndClockLayerMetadata() {
        val calendar = IconMapping(
            projectId = 1,
            targetPackageName = "com.example.calendar",
            targetActivityName = "com.example.calendar.MainActivity",
            iconPath = "/unused/calendar.png",
            mappingType = 1
        )
        val clock = IconMapping(
            projectId = 1,
            targetPackageName = "com.example.clock",
            targetActivityName = "com.example.clock.MainActivity",
            iconPath = "/unused/clock.png",
            mappingType = 2
        )

        val content = BinaryAppFilterWriter.build(
            staticMappings = emptyList(),
            staticSlotIndices = emptyList(),
            calendarMappings = listOf(calendar),
            calendarSlotIndices = listOf(1),
            clockMappings = listOf(clock),
            clockSlotIndices = listOf(2),
            scaleFactor = 1f
        ).decodeToString()

        assertTrue(content.contains("calendar_1_"))
        assertTrue(content.contains("clock_dynamic_2"))
        assertTrue(content.contains("hourLayerIndex"))
    }
}
