package com.kian.perficon.util

import android.content.Context
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkGenerator(private val context: Context) {

    private companion object {
        // CandyBar's compiled drawable.xml in the bundled template lists these slots.
        const val DASHBOARD_SLOT_COUNT = 100
        const val DYNAMIC_CALENDAR_SLOT_COUNT = 8
        const val DYNAMIC_CLOCK_SLOT_COUNT = 8
    }

    class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Progress(val step: Int, val message: String)

    private data class TemplateSlot(
        val index: Int,
        val entryName: String
    )

    @Throws(GenerationException::class)
    fun generateApk(
        project: IconPackProject,
        mappings: List<IconMapping>,
        onProgress: (Progress) -> Unit = {}
    ): File {
        onProgress(Progress(1, "正在准备导出"))
        val outputDir = StorageHelper.outputsDir
        val safeName = project.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val outputApk = File(outputDir, "$safeName.apk")

        val projectRoot = StorageHelper.getProjectDir(project.id)
        val workspace = File(projectRoot, ".build")

        if (workspace.exists()) workspace.deleteRecursively()
        check(workspace.mkdirs()) { "Unable to create the APK build workspace." }

        try {
            val unsignedApk = File(workspace, "unsigned.apk")
            val baseApk = File(workspace, "template.apk")
            try {
                onProgress(Progress(2, "正在加载图标包模板"))
                context.assets.open("base.apk").use { input ->
                    FileOutputStream(baseApk).use(input::copyTo)
                }
            } catch (e: Exception) {
                throw GenerationException("The CandyBar APK template is missing or unreadable.", e)
            }

            val mappedEntries = mappings.filter { it.targetPackageName.isNotBlank() }
            val calendarMappings = mappedEntries.filter { it.mappingType == 1 && project.useDynamicCalendar }
            // A calendar/clock entry can share a package with its static fallback. When the
            // project enables the dynamic mode, the dynamic component wins; otherwise the
            // explicit static mapping wins and the dynamic artwork is exported as a fallback.
            val clockMappings = mappedEntries
                .filter { it.mappingType == 2 && project.useDynamicClock }
                .filterNot { clock -> calendarMappings.any { it.targetPackageName == clock.targetPackageName } }
            val dynamicPackages = (calendarMappings + clockMappings).mapTo(mutableSetOf()) { it.targetPackageName }
            val staticMappings = mappedEntries
                .filter { mapping ->
                    mapping.targetPackageName !in dynamicPackages &&
                        (mapping.mappingType == 0 ||
                            (mapping.mappingType == 1 && !project.useDynamicCalendar) ||
                            (mapping.mappingType == 2 && !project.useDynamicClock))
                }
                .groupBy { it.targetPackageName }
                .values
                .map { alternatives -> alternatives.firstOrNull { it.mappingType == 0 } ?: alternatives.first() }
            if (calendarMappings.size > DYNAMIC_CALENDAR_SLOT_COUNT) {
                throw GenerationException("当前模板最多支持 $DYNAMIC_CALENDAR_SLOT_COUNT 个动态日历图标。")
            }
            if (clockMappings.size > DYNAMIC_CLOCK_SLOT_COUNT) {
                throw GenerationException("当前模板最多支持 $DYNAMIC_CLOCK_SLOT_COUNT 个动态时钟图标。")
            }
            // Older projects only stored a package name. Resolve its real launcher Activity
            // at export time so component-based launchers such as Lawnchair can match it.
            val resolvedMappings = staticMappings.map(::resolveLaunchActivity)
            val resolvedCalendars = calendarMappings.map(::resolveLaunchActivity)
            val resolvedClocks = clockMappings.map(::resolveLaunchActivity)

            ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
                ZipFile(baseApk).use { zip ->
                    onProgress(Progress(3, "正在写入图标与映射"))
                    val slots = findTemplateSlots(zip)
                    val calendarSlots = findCalendarTemplateSlots(zip)
                    val clockSlots = findClockTemplateSlots(zip)
                    if (resolvedMappings.size > slots.size) {
                        throw GenerationException(
                            "This CandyBar template has ${slots.size} icon slots, but the project has ${resolvedMappings.size} icons."
                        )
                    }
                    if (resolvedCalendars.size > calendarSlots.size) {
                        throw GenerationException("模板只包含 ${calendarSlots.size} 组完整的动态日历资源。")
                    }
                    if (resolvedClocks.size > clockSlots.size) {
                        throw GenerationException("模板只包含 ${clockSlots.size} 组完整的动态时钟资源。")
                    }

                    val replacements = slots.zip(resolvedMappings).associate { (slot, mapping) ->
                        slot.entryName to File(mapping.iconPath)
                    }
                    val calendarSlotIndices = calendarSlots.take(resolvedCalendars.size)
                    val clockSlotIndices = clockSlots.take(resolvedClocks.size)
                    val expandedCalendarMappings = mutableListOf<IconMapping>()
                    val expandedCalendarSlotIndices = mutableListOf<Int>()
                    resolvedCalendars.zip(calendarSlotIndices).forEach { (mapping, slotIndex) ->
                        DynamicIconDefaults.expandCalendarMapping(mapping).forEach { expanded ->
                            expandedCalendarMappings += expanded
                            expandedCalendarSlotIndices += slotIndex
                        }
                    }
                    val expandedClockMappings = mutableListOf<IconMapping>()
                    val expandedClockSlotIndices = mutableListOf<Int>()
                    resolvedClocks.zip(clockSlotIndices).forEach { (mapping, slotIndex) ->
                        DynamicIconDefaults.expandClockMapping(mapping).forEach { expanded ->
                            expandedClockMappings += expanded
                            expandedClockSlotIndices += slotIndex
                        }
                    }
                    val dynamicReplacements = buildDynamicReplacements(
                        resolvedCalendars,
                        calendarSlotIndices,
                        resolvedClocks,
                        clockSlotIndices
                    )
                    val missingIcons = (replacements.values + dynamicReplacements.values).filterNot(File::isFile)
                    if (missingIcons.isNotEmpty()) {
                        throw GenerationException("One or more project icon files no longer exist.")
                    }

                    val resourceAppFilter = BinaryAppFilterWriter.build(
                        staticMappings = resolvedMappings,
                        staticSlotIndices = slots.take(resolvedMappings.size).map(TemplateSlot::index),
                        calendarMappings = expandedCalendarMappings,
                        calendarSlotIndices = expandedCalendarSlotIndices,
                        clockMappings = expandedClockMappings,
                        clockSlotIndices = expandedClockSlotIndices,
                        scaleFactor = project.scaleFactor
                    )
                    val resourceDrawables = BinaryDrawableXmlWriter.build(
                        staticMappings = resolvedMappings,
                        staticSlotIndices = slots.take(resolvedMappings.size).map(TemplateSlot::index),
                        calendarMappings = resolvedCalendars,
                        calendarSlotIndices = calendarSlotIndices,
                        clockMappings = resolvedClocks,
                        clockSlotIndices = clockSlotIndices
                    )
                    val resourceReplacements = mapOf(
                        "AndroidManifest.xml" to ApkManifestEditor.patch(
                            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readBytes(),
                            project.packageName,
                            ((System.currentTimeMillis() / 1000L) % Int.MAX_VALUE).toInt().coerceAtLeast(1),
                            project.name
                        ),
                        "res/xml/appfilter.xml" to resourceAppFilter,
                        "res/xml/drawable.xml" to resourceDrawables,
                        "res/xml-v26/drawable.xml" to resourceDrawables
                    )
                    val projectIcon = project.projectIconPath
                        ?.let(::File)
                        ?.takeIf(File::isFile)
                    zip.entries().asSequence().forEach { entry ->
                        if (
                            shouldDiscardSignature(entry.name) ||
                            entry.name == "assets/appfilter.xml"
                        ) {
                            return@forEach
                        }
                        val replacement = replacements[entry.name]
                            ?: dynamicReplacements[entry.name.substringAfterLast('/')]
                            ?: projectIcon?.takeIf {
                            entry.name.matches(Regex("^res/mipmap[^/]*/ic_launcher\\.png$"))
                        }
                        val resourceReplacement = resourceReplacements[entry.name]
                        val outputEntry = ZipEntry(entry.name).apply {
                            if (replacement == null && resourceReplacement == null && entry.method == ZipEntry.STORED) {
                                method = ZipEntry.STORED
                                size = entry.size
                                compressedSize = entry.size
                                crc = entry.crc
                            }
                        }
                        zos.putNextEntry(outputEntry)
                        when {
                            resourceReplacement != null -> zos.write(resourceReplacement)
                            replacement != null -> replacement.inputStream().use { it.copyTo(zos) }
                            else -> zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }

                    injectStringAsset(
                        zos,
                        "assets/appfilter.xml",
                        generateAppFilter(
                            project,
                            resolvedMappings,
                            slots.take(resolvedMappings.size).map(TemplateSlot::index),
                            expandedCalendarMappings,
                            expandedCalendarSlotIndices,
                            expandedClockMappings,
                            expandedClockSlotIndices
                        )
                    )
                }
            }

            onProgress(Progress(4, "正在签名 APK"))
            ApkSignerUtil.sign(context, unsignedApk, outputApk)
            onProgress(Progress(5, "正在完成导出"))
            return outputApk
        } catch (e: Exception) {
            outputApk.delete()
            throw if (e is GenerationException) e else GenerationException("Unable to build the icon pack APK.", e)
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun injectStringAsset(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun findTemplateSlots(zip: ZipFile): List<TemplateSlot> {
        val slotPattern = Regex("^res/drawable[^/]*/icon_(\\d+)\\.png$")
        return zip.entries().asSequence()
            .mapNotNull { entry ->
                slotPattern.matchEntire(entry.name)?.let { match ->
                    TemplateSlot(match.groupValues[1].toInt(), entry.name)
                }
            }
            .sortedBy(TemplateSlot::index)
            .toList()
    }

    private fun findCalendarTemplateSlots(zip: ZipFile): List<Int> {
        val resourcePattern = Regex("^calendar_(\\d+)_(\\d+)\\.png$")
        return zip.entries().asSequence()
            .map { it.name.substringAfterLast('/') }
            .mapNotNull { name -> resourcePattern.matchEntire(name)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() } }
            .groupBy({ it.first }, { it.second })
            .filterValues { days -> days.containsAll((1..DynamicIconAssets.CALENDAR_DAY_COUNT).toSet()) }
            .keys
            .sorted()
    }

    private fun findClockTemplateSlots(zip: ZipFile): List<Int> {
        val resourcePattern = Regex("^clock_(\\d+)_(bg|hour|minute|second)\\.png$")
        return zip.entries().asSequence()
            .map { it.name.substringAfterLast('/') }
            .mapNotNull { name -> resourcePattern.matchEntire(name)?.let { it.groupValues[1].toInt() to it.groupValues[2] } }
            .groupBy({ it.first }, { it.second })
            .filterValues { layers -> layers.containsAll(setOf("bg", "hour", "minute", "second")) }
            .keys
            .sorted()
    }

    private fun buildDynamicReplacements(
        calendars: List<IconMapping>,
        calendarSlotIndices: List<Int>,
        clocks: List<IconMapping>,
        clockSlotIndices: List<Int>
    ): Map<String, File> {
        val replacements = linkedMapOf<String, File>()
        calendars.zip(calendarSlotIndices).forEach { (mapping, slotIndex) ->
            DynamicIconAssets.calendarFrames(mapping).forEachIndexed { index, path ->
                replacements["calendar_${slotIndex}_${index + 1}.png"] = File(path)
            }
        }
        clocks.zip(clockSlotIndices).forEach { (mapping, slotIndex) ->
            val layers = DynamicIconAssets.clockLayers(mapping)
            replacements["clock_${slotIndex}_bg.png"] = File(layers.backgroundPath)
            replacements["clock_${slotIndex}_hour.png"] = File(layers.hourPath)
            replacements["clock_${slotIndex}_minute.png"] = File(layers.minutePath)
            replacements["clock_${slotIndex}_second.png"] = File(layers.secondPath)
        }
        return replacements
    }

    private fun resolveLaunchActivity(mapping: IconMapping): IconMapping {
        if (mapping.targetActivityName.isNotBlank()) return mapping
        val activityName = context.packageManager
            .getLaunchIntentForPackage(mapping.targetPackageName)
            ?.component
            ?.className
            ?: return mapping
        return mapping.copy(targetActivityName = activityName)
    }

    private fun shouldDiscardSignature(entryName: String): Boolean {
        if (!entryName.startsWith("META-INF/", ignoreCase = true)) return false
        val upperName = entryName.uppercase()
        return upperName == "META-INF/MANIFEST.MF" ||
            upperName.endsWith(".SF") ||
            upperName.endsWith(".RSA") ||
            upperName.endsWith(".DSA") ||
            upperName.endsWith(".EC")
    }

    private fun generateAppFilter(
        project: IconPackProject,
        mappings: List<IconMapping>,
        slotIndices: List<Int>,
        calendars: List<IconMapping>,
        calendarSlotIndices: List<Int>,
        clocks: List<IconMapping>,
        clockSlotIndices: List<Int>
    ): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        sb.append("    <scale factor=\"${project.scaleFactor}\" />\n")

        mappings.zip(slotIndices).forEach { (mapping, slotIndex) ->
            sb.append(
                "    <item component=\"${componentName(mapping)}\" drawable=\"icon_$slotIndex\" />\n"
            )
        }
        calendars.zip(calendarSlotIndices).forEach { (mapping, slotIndex) ->
            val component = componentName(mapping)
            sb.append("    <item component=\"$component\" drawable=\"calendar_${slotIndex}_1\" />\n")
            sb.append("    <calendar component=\"$component\" prefix=\"calendar_${slotIndex}_\" />\n")
        }
        val declaredClockDrawables = mutableSetOf<String>()
        clocks.zip(clockSlotIndices).forEach { (mapping, slotIndex) ->
            val component = componentName(mapping)
            val drawable = "clock_dynamic_$slotIndex"
            sb.append("    <item component=\"$component\" drawable=\"$drawable\" />\n")
            if (declaredClockDrawables.add(drawable)) {
                sb.append(
                    "    <dynamic-clock drawable=\"$drawable\" defaultHour=\"10\" defaultMinute=\"10\" " +
                        "defaultSecond=\"30\" hourLayerIndex=\"1\" minuteLayerIndex=\"2\" secondLayerIndex=\"3\" />\n"
                )
            }
        }
        sb.append("</resources>")
        return sb.toString()
    }

    private fun componentName(mapping: IconMapping): String {
        val activity = mapping.targetActivityName.ifBlank {
            "${mapping.targetPackageName}.MainActivity"
        }.let { if (it.startsWith(".")) mapping.targetPackageName + it else it }
        return "ComponentInfo{${mapping.targetPackageName}/$activity}"
    }
}
