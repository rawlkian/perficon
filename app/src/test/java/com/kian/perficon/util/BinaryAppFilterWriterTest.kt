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
    fun testInspectDbFile() {
        val oneFile = File("d:/AndroidProject/Perficon/One.apk")
        val threeFile = File("d:/AndroidProject/Perficon/Three.apk")
        if (!oneFile.exists() || !threeFile.exists()) {
            println("APK files missing!")
            return
        }

        java.util.zip.ZipFile(oneFile).use { one ->
            java.util.zip.ZipFile(threeFile).use { three ->
                val oneEntries = one.entries().asSequence().map { it.name }.toSet()
                val threeEntries = three.entries().asSequence().map { it.name }.toSet()

                println("=== ENTRY COUNT ===")
                println("One.apk entries: ${oneEntries.size}")
                println("Three.apk entries: ${threeEntries.size}")

                println("\n=== non-icon Entries in One but not in Three ===")
                (oneEntries - threeEntries).filter { !it.contains("icon_") && !it.startsWith("META-INF") }.forEach {
                    println("  $it")
                }

                println("\n=== non-icon Entries in Three but not in One ===")
                (threeEntries - oneEntries).filter { !it.contains("icon_") && !it.startsWith("META-INF") }.forEach {
                    println("  $it")
                }

                println("user.dir: ${System.getProperty("user.dir")}")
                val baseFile = listOf(
                    File("app/src/main/assets/base.apk"),
                    File("src/main/assets/base.apk"),
                    File("../app/src/main/assets/base.apk")
                ).firstOrNull { it.exists() }
                if (baseFile != null) {
                    java.util.zip.ZipFile(baseFile).use { base ->
                        val entry = base.getEntry("assets/appfilter.xml") ?: base.getEntry("res/xml/appfilter.xml")
                        if (entry != null) {
                            val baseText = base.getInputStream(entry).readBytes().decodeToString()
                            println("base.apk appfilter (${entry.name}) lines for mt.plus:")
                            if (entry.name.endsWith(".xml") && !entry.name.startsWith("assets")) {
                                // Binary XML contains the string
                                if (baseText.contains("bin.mt.plus")) {
                                    println("  [Found 'bin.mt.plus' in binary XML string pool]")
                                } else {
                                    println("  [NOT Found 'bin.mt.plus' in binary XML string pool]")
                                }
                            } else {
                                baseText.lineSequence().filter { it.contains("bin.mt.plus") }.forEach { println("  $it") }
                            }
                        } else {
                            println("base.apk appfilter entry NOT FOUND")
                        }
                    }
                } else {
                    println("base.apk NOT FOUND")
                }

                val oneText = one.getInputStream(one.getEntry("assets/appfilter.xml")).readBytes().decodeToString()
                val threeText = three.getInputStream(three.getEntry("assets/appfilter.xml")).readBytes().decodeToString()

                println("One.apk appfilter lines for mt.plus:")
                oneText.lineSequence().filter { it.contains("bin.mt.plus") }.forEach { println("  $it") }
                println("Three.apk appfilter lines for mt.plus:")
                threeText.lineSequence().filter { it.contains("bin.mt.plus") }.forEach { println("  $it") }

                // Inspect drawable.xml mapping as well
                println("\n=== DRAWABLE.XML MAPPING FOR MT MANAGER ===")
                val oneDrawableXml = one.getInputStream(one.getEntry("assets/drawable.xml") ?: one.getEntry("res/xml/drawable.xml")).readBytes().decodeToString()
                val threeDrawableXml = three.getInputStream(three.getEntry("assets/drawable.xml") ?: three.getEntry("res/xml/drawable.xml")).readBytes().decodeToString()
                
                println("One.apk drawable.xml lines for icon_2682:")
                oneDrawableXml.lineSequence().filter { it.contains("icon_2682") }.forEach { println("  $it") }
                println("Three.apk drawable.xml lines for icon_2287:")
                threeDrawableXml.lineSequence().filter { it.contains("icon_2287") }.forEach { println("  $it") }
            }
        }
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









