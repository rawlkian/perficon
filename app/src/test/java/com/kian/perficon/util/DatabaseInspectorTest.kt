package com.kian.perficon.util

import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DatabaseInspectorTest {
    @Test
    fun inspectBaseApk() {
        val baseFile = File("src/main/assets/base.apk")
        if (!baseFile.exists()) {
            println("[BASE_INSPECT] base.apk does not exist at ${baseFile.absolutePath}")
            return
        }
        println("[BASE_INSPECT] base.apk size: ${baseFile.length()}")
        try {
            ZipFile(baseFile).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                val staticSlots = entries.filter { it.matches(Regex("^res/drawable[^/]*/icon_(\\d+)\\.png$")) }
                val calendarSlots = entries.filter { it.matches(Regex("^res/drawable[^/]*/calendar_(\\d+)_1\\.png$")) }
                val clockSlots = entries.filter { it.matches(Regex("^res/drawable[^/]*/clock_dynamic_(\\d+)\\.xml$")) }
                
                println("[BASE_INSPECT] Static icon slots count: ${staticSlots.size}")
                println("[BASE_INSPECT] Calendar slots count: ${calendarSlots.size}")
                println("[BASE_INSPECT] Clock slots count: ${clockSlots.size}")
                if (staticSlots.isNotEmpty()) {
                    println("[BASE_INSPECT] Sample static slots: ${staticSlots.take(5)}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


