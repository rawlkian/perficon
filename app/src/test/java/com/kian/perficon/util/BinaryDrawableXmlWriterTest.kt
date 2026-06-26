package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BinaryDrawableXmlWriterTest {
    @Test
    fun writesPackageTailDisplayNamesForCandyBar() {
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
        assertTrue(content.contains("music"))
        assertTrue(content.contains("camera"))
        File("build/test-output").apply { mkdirs() }
            .resolve("drawable.xml")
            .writeBytes(xml)
    }

    @Test
    fun separatesDynamicIconsIntoDashboardCategories() {
        val calendar = IconMapping(
            projectId = 1,
            iconName = "我的日历",
            targetPackageName = "com.example.calendar",
            targetActivityName = "com.example.calendar.MainActivity",
            iconPath = "/unused/calendar.png",
            mappingType = 1
        )
        val clock = IconMapping(
            projectId = 1,
            iconName = "我的时钟",
            targetPackageName = "com.example.clock",
            targetActivityName = "com.example.clock.MainActivity",
            iconPath = "/unused/clock.png",
            mappingType = 2
        )

        val content = BinaryDrawableXmlWriter.build(
            staticMappings = emptyList(),
            staticSlotIndices = emptyList(),
            calendarMappings = listOf(calendar),
            calendarSlotIndices = listOf(1),
            clockMappings = listOf(clock),
            clockSlotIndices = listOf(2)
        ).decodeToString()

        assertTrue(content.contains("动态日历"))
        assertTrue(content.contains("calendar"))
        assertTrue(content.contains("动态时钟"))
        assertTrue(content.contains("clock"))
    }
}
