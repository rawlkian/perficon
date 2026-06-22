package com.kian.perficon.util

import com.kian.perficon.model.IconMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicIconDefaultsTest {
    @Test
    fun expandsProjectCalendarIntoCandyBarDefaultComponents() {
        val mapping = IconMapping(
            projectId = 1,
            iconName = "动态日历",
            targetPackageName = DynamicIconDefaults.DEFAULT_CALENDAR_MAPPING_PACKAGE,
            targetActivityName = "",
            iconPath = "/unused/calendar.png",
            mappingType = 1
        )

        val components = DynamicIconDefaults.expandCalendarMapping(mapping)

        assertTrue(components.size > 20)
        assertTrue(components.any { it.targetPackageName == "com.google.android.calendar" })
        assertTrue(components.any { it.targetPackageName == "com.samsung.android.calendar" })
        assertEquals(1, components.map { it.targetPackageName }.count { it == "com.oppo.calendar" })
    }

    @Test
    fun expandsLegacyCalendarMappingsIntoTheSameDefaultComponents() {
        val legacyMapping = IconMapping(
            projectId = 1,
            targetPackageName = "com.example.oldcalendar",
            targetActivityName = "com.example.oldcalendar.MainActivity",
            iconPath = "/unused/calendar.png",
            mappingType = 1
        )

        val components = DynamicIconDefaults.expandCalendarMapping(legacyMapping)

        assertTrue(components.none { it.targetPackageName == "com.example.oldcalendar" })
        assertTrue(components.any { it.targetPackageName == "com.android.calendar" })
    }

    @Test
    fun expandsClockIntoCandyBarDefaultComponents() {
        val mapping = IconMapping(
            projectId = 1,
            targetPackageName = DynamicIconDefaults.DEFAULT_CLOCK_MAPPING_PACKAGE,
            targetActivityName = "",
            iconPath = "/unused/clock.png",
            mappingType = 2
        )

        val components = DynamicIconDefaults.expandClockMapping(mapping)

        assertTrue(components.size >= 30)
        assertTrue(components.any { it.targetPackageName == "com.android.deskclock" })
        assertTrue(components.any { it.targetPackageName == "com.sec.android.app.clockpackage" })
    }
}
