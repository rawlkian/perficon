package com.kian.perficon.util

import android.content.Context
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkGenerator(private val context: Context) {

    private companion object {
        // Bundled template slot capacities — derived from the actual template APK resources.
        // These are only used for pre-build validation; actual capacity is always
        // detected at build time via findTemplateSlots / findCalendarTemplateSlots / findClockTemplateSlots.
        const val DYNAMIC_CALENDAR_SLOT_COUNT = 8
        const val DYNAMIC_CLOCK_SLOT_COUNT = 8
    }

    class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Progress(val step: Int, val message: String)

    private data class TemplateSlot(
        val index: Int,
        val entryName: String
    )

    /**
     * Fingerprint key that identifies a unique set of calendar day artwork.
     * Two calendar mappings that reference the exact same 31 image files
     * will produce the same key and therefore share a single template slot.
     */
    private fun calendarArtworkKey(mapping: IconMapping): List<String> =
        DynamicIconAssets.calendarFrames(mapping)

    /**
     * Fingerprint key that identifies a unique set of clock layer artwork.
     */
    private fun clockArtworkKey(mapping: IconMapping): DynamicIconAssets.ClockLayers =
        DynamicIconAssets.clockLayers(mapping)

    @Throws(GenerationException::class)
    fun generateApk(
        project: IconPackProject,
        mappings: List<IconMapping>,
        onProgress: (Progress) -> Unit = {}
    ): File {
        val outputDir = StorageHelper.outputsDir
        val safeName = project.name.replace("[^\\p{L}\\p{N}]".toRegex(), "_")
        val outputApk = File(outputDir, "$safeName.apk")

        val projectRoot = StorageHelper.getProjectDir(project.id)
        val workspace = File(projectRoot, ".build")

        try {
            onProgress(Progress(1, "正在准备导出"))
            if (workspace.exists()) workspace.deleteRecursively()
            check(workspace.mkdirs()) { "Unable to create the APK build workspace." }
            val unsignedApk = File(workspace, "unsigned.apk")
            val baseApk = File(workspace, "template.apk")
            try {
                onProgress(Progress(2, "正在加载图标包模板"))
                context.assets.open("base.apk").use { input ->
                    FileOutputStream(baseApk).use(input::copyTo)
                }
            } catch (e: Exception) {
                throw GenerationException("图标包模板（base.apk）缺失或无法读取。", e)
            }

            val mappedEntries = mappings.filter { it.targetPackageName.isNotBlank() }
            val calendarMappings = mappedEntries.filter { it.mappingType == 1 && project.useDynamicCalendar }
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

            // ── Resource deduplication ──────────────────────────────────────
            // Group calendar mappings by their artwork fingerprint so that
            // multiple apps sharing the same 31-day images use one template slot.
            val calendarGroups = calendarMappings.groupBy { calendarArtworkKey(it) }
            val uniqueCalendarCount = calendarGroups.size
            if (uniqueCalendarCount > DYNAMIC_CALENDAR_SLOT_COUNT) {
                throw GenerationException("当前模板最多支持 $DYNAMIC_CALENDAR_SLOT_COUNT 个动态日历图标。")
            }

            // Group clock mappings by their artwork fingerprint.
            val clockGroups = clockMappings.groupBy { clockArtworkKey(it) }
            val uniqueClockCount = clockGroups.size
            if (uniqueClockCount > DYNAMIC_CLOCK_SLOT_COUNT) {
                throw GenerationException("当前模板最多支持 $DYNAMIC_CLOCK_SLOT_COUNT 个动态时钟图标。")
            }

            // Older projects only stored a package name. Resolve its real launcher Activity
            // at export time so component-based launchers such as Lawnchair can match it.
            val resolvedMappings = staticMappings.map(::resolveLaunchActivity)

            // Build per-mapping slot index assignments with deduplication.
            // Each unique artwork group gets one slot; all mappings in the group share it.
            val resolvedCalendars = mutableListOf<IconMapping>()
            val calendarSlotAssignments = mutableListOf<Int>()
            var calendarSlotCounter = 0
            for ((_, group) in calendarGroups) {
                calendarSlotCounter++
                for (mapping in group) {
                    resolvedCalendars += resolveLaunchActivity(mapping)
                    calendarSlotAssignments += calendarSlotCounter
                }
            }

            val resolvedClocks = mutableListOf<IconMapping>()
            val clockSlotAssignments = mutableListOf<Int>()
            var clockSlotCounter = 0
            for ((_, group) in clockGroups) {
                clockSlotCounter++
                for (mapping in group) {
                    resolvedClocks += resolveLaunchActivity(mapping)
                    clockSlotAssignments += clockSlotCounter
                }
            }

            val countingOut = CountingOutputStream(FileOutputStream(unsignedApk))
            ZipOutputStream(countingOut).use { zos ->
                ZipFile(baseApk).use { zip ->
                    onProgress(Progress(3, "正在写入图标与映射"))
                    val slotsResult = findAllTemplateSlots(zip)
                    val slots = slotsResult.staticSlots
                    val calendarSlots = slotsResult.calendarSlots
                    val clockSlots = slotsResult.clockSlots
                    if (resolvedMappings.size > slots.size) {
                        throw GenerationException(
                            "图标包模板最多支持 ${slots.size} 个静态图标，当前项目有 ${resolvedMappings.size} 个静态图标映射，请减少图标数量或使用支持更多槽位的模板。"
                        )
                    }
                    if (uniqueCalendarCount > calendarSlots.size) {
                        throw GenerationException("模板只包含 ${calendarSlots.size} 组完整的动态日历资源。")
                    }
                    if (uniqueClockCount > clockSlots.size) {
                        throw GenerationException("模板只包含 ${clockSlots.size} 组完整的动态时钟资源。")
                    }

                    // Map our sequential slot counter (1-based) to actual template slot indices
                    val calendarSlotMap = calendarSlots.take(uniqueCalendarCount)
                        .withIndex().associate { (i, v) -> i + 1 to v }
                    val clockSlotMap = clockSlots.take(uniqueClockCount)
                        .withIndex().associate { (i, v) -> i + 1 to v }

                    val resolvedCalendarSlotIndices = calendarSlotAssignments.map { calendarSlotMap.getValue(it) }
                    val resolvedClockSlotIndices = clockSlotAssignments.map { clockSlotMap.getValue(it) }

                    val replacements = slots.zip(resolvedMappings).associate { (slot, mapping) ->
                        slot.entryName to File(mapping.iconPath)
                    }

                    val expandedCalendarMappings = mutableListOf<IconMapping>()
                    val expandedCalendarSlotIndices = mutableListOf<Int>()
                    resolvedCalendars.zip(resolvedCalendarSlotIndices).forEach { (mapping, slotIndex) ->
                        DynamicIconDefaults.expandCalendarMapping(mapping).forEach { expanded ->
                            expandedCalendarMappings += expanded
                            expandedCalendarSlotIndices += slotIndex
                        }
                    }
                    val expandedClockMappings = mutableListOf<IconMapping>()
                    val expandedClockSlotIndices = mutableListOf<Int>()
                    resolvedClocks.zip(resolvedClockSlotIndices).forEach { (mapping, slotIndex) ->
                        DynamicIconDefaults.expandClockMapping(mapping).forEach { expanded ->
                            expandedClockMappings += expanded
                            expandedClockSlotIndices += slotIndex
                        }
                    }

                    // For dynamic replacements, only build one replacement per unique slot
                    // (the first mapping in each dedup group provides the artwork)
                    val dedupCalendars = mutableListOf<IconMapping>()
                    val dedupCalendarSlots = mutableListOf<Int>()
                    val seenCalendarSlots = mutableSetOf<Int>()
                    resolvedCalendars.zip(resolvedCalendarSlotIndices).forEach { (mapping, slotIndex) ->
                        if (seenCalendarSlots.add(slotIndex)) {
                            dedupCalendars += mapping
                            dedupCalendarSlots += slotIndex
                        }
                    }
                    val dedupClocks = mutableListOf<IconMapping>()
                    val dedupClockSlots = mutableListOf<Int>()
                    val seenClockSlots = mutableSetOf<Int>()
                    resolvedClocks.zip(resolvedClockSlotIndices).forEach { (mapping, slotIndex) ->
                        if (seenClockSlots.add(slotIndex)) {
                            dedupClocks += mapping
                            dedupClockSlots += slotIndex
                        }
                    }
                    val dynamicReplacements = buildDynamicReplacements(
                        dedupCalendars,
                        dedupCalendarSlots,
                        dedupClocks,
                        dedupClockSlots,
                        slotsResult.calendarDir,
                        slotsResult.clockDir
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
                    val currentLang = com.kian.perficon.ui.AppSettings(context).language.value
                    val resourceDrawables = BinaryDrawableXmlWriter.build(
                        staticMappings = resolvedMappings,
                        staticSlotIndices = slots.take(resolvedMappings.size).map(TemplateSlot::index),
                        calendarMappings = dedupCalendars,
                        calendarSlotIndices = dedupCalendarSlots,
                        clockMappings = dedupClocks,
                        clockSlotIndices = dedupClockSlots,
                        language = currentLang
                    )
                    val arscEntry = zip.getEntry("resources.arsc")
                    val patchedArsc = if (arscEntry != null) {
                        ArscPackageNamePatcher.patch(
                            zip.getInputStream(arscEntry).readBytes(),
                            project.packageName
                        )
                    } else null

                    val resourceReplacements = mutableMapOf(
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
                    if (patchedArsc != null) {
                        resourceReplacements["resources.arsc"] = patchedArsc
                    }

                    val projectIcon = project.projectIconPath
                        ?.let(::File)
                        ?.takeIf(File::isFile)
                    zip.entries().asSequence().forEach { entry ->
                        if (
                            shouldDiscardSignature(entry.name) ||
                            entry.name == "assets/appfilter.xml" ||
                            entry.name == "assets/pack_meta.xml"
                        ) {
                            return@forEach
                        }
                        val replacement = replacements[entry.name]
                            ?: dynamicReplacements[entry.name]
                            ?: projectIcon?.takeIf {
                            entry.name.matches(Regex("^res/mipmap[^/]*/ic_launcher(_round)?\\.(png|webp)$"))
                        }
                        val resourceReplacement = resourceReplacements[entry.name]
                        val outputEntry = ZipEntry(entry.name)
                        val methodToUse = entry.method
                        outputEntry.method = methodToUse
                        
                        if (methodToUse == ZipEntry.STORED) {
                            val bytes = when {
                                resourceReplacement != null -> resourceReplacement
                                replacement != null -> replacement.readBytes()
                                else -> null
                            }
                            if (bytes != null) {
                                outputEntry.size = bytes.size.toLong()
                                outputEntry.compressedSize = bytes.size.toLong()
                                outputEntry.crc = CRC32().apply { update(bytes) }.value
                            } else {
                                outputEntry.size = entry.size
                                outputEntry.compressedSize = entry.size
                                outputEntry.crc = entry.crc
                            }
                            
                            val entryNameBytes = entry.name.toByteArray(Charsets.UTF_8)
                            val baseOffset = countingOut.bytesWritten + 34 + entryNameBytes.size
                            val k = ((4 - (baseOffset % 4)) % 4).toInt()
                            val extraSize = 4 + k
                            val extra = ByteArray(extraSize)
                            extra[0] = 0xd1.toByte()
                            extra[1] = 0xd9.toByte()
                            extra[2] = k.toByte()
                            extra[3] = 0x00.toByte()
                            outputEntry.extra = extra
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

                    val packMeta = "<pack><name>${project.name}</name><description>${project.description}</description></pack>"
                    injectStringAsset(
                        zos,
                        "assets/pack_meta.xml",
                        packMeta
                    )
                }
            }

            onProgress(Progress(4, "正在签名 APK"))
            ApkSignerUtil.sign(context, unsignedApk, outputApk)
            onProgress(Progress(5, "正在完成导出"))
            return outputApk
        } catch (e: Throwable) {
            runCatching { outputApk.delete() }
            val errorType = e::class.java.name
            val detail = e.message ?: e::class.java.simpleName
            val causeChain = generateSequence(e.cause) { it.cause }
                .map { " → ${it::class.java.simpleName}: ${it.message ?: it::class.java.name}" }
                .joinToString("")
            throw if (e is GenerationException) e else GenerationException(
                "构建 APK 失败：[$errorType] $detail$causeChain", e
            )
        } finally {
            runCatching { workspace.deleteRecursively() }
        }
    }

    private fun injectStringAsset(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private data class TemplateSlotsResult(
        val staticSlots: List<TemplateSlot>,
        val calendarSlots: List<Int>,
        val clockSlots: List<Int>,
        val calendarDir: String?,
        val clockDir: String?
    )

    private fun findAllTemplateSlots(zip: ZipFile): TemplateSlotsResult {
        val staticSlotPattern = Regex("^res/drawable[^/]*/icon_(\\d+)\\.png$")
        val calendarPattern = Regex("^(.*/)calendar_(\\d+)_(\\d+)\\.png$")
        val clockPattern = Regex("^(.*/)clock_(\\d+)_(bg|hour|minute|second)\\.png$")

        val staticSlots = mutableListOf<TemplateSlot>()
        val calendarDayMap = mutableMapOf<Int, MutableSet<Int>>()
        val clockLayerMap = mutableMapOf<Int, MutableSet<String>>()
        var calendarDir: String? = null
        var clockDir: String? = null

        zip.entries().asSequence().forEach { entry ->
            val name = entry.name
            staticSlotPattern.matchEntire(name)?.let { match ->
                staticSlots += TemplateSlot(match.groupValues[1].toInt(), name)
                return@forEach
            }
            calendarPattern.matchEntire(name)?.let { match ->
                val dir = match.groupValues[1]
                val slot = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                if (day in 1..DynamicIconAssets.CALENDAR_DAY_COUNT) {
                    calendarDayMap.getOrPut(slot) { mutableSetOf() }.add(day)
                    if (calendarDir == null) calendarDir = dir
                }
                return@forEach
            }
            clockPattern.matchEntire(name)?.let { match ->
                val dir = match.groupValues[1]
                val slot = match.groupValues[2].toInt()
                val layer = match.groupValues[3]
                clockLayerMap.getOrPut(slot) { mutableSetOf() }.add(layer)
                if (clockDir == null) clockDir = dir
                return@forEach
            }
        }

        val calendarSlots = calendarDayMap
            .filterValues { it.size == DynamicIconAssets.CALENDAR_DAY_COUNT }
            .keys
            .sorted()
        val clockSlots = clockLayerMap
            .filterValues { it.containsAll(setOf("bg", "hour", "minute", "second")) }
            .keys
            .sorted()

        return TemplateSlotsResult(
            staticSlots = staticSlots.sortedBy(TemplateSlot::index),
            calendarSlots = calendarSlots,
            clockSlots = clockSlots,
            calendarDir = calendarDir,
            clockDir = clockDir
        )
    }

    private fun buildDynamicReplacements(
        calendars: List<IconMapping>,
        calendarSlotIndices: List<Int>,
        clocks: List<IconMapping>,
        clockSlotIndices: List<Int>,
        calendarDir: String?,
        clockDir: String?
    ): Map<String, File> {
        val replacements = linkedMapOf<String, File>()
        val calPrefix = calendarDir ?: ""
        val clkPrefix = clockDir ?: ""
        calendars.zip(calendarSlotIndices).forEach { (mapping, slotIndex) ->
            DynamicIconAssets.calendarFrames(mapping).forEachIndexed { index, path ->
                replacements["${calPrefix}calendar_${slotIndex}_${index + 1}.png"] = File(path)
            }
        }
        clocks.zip(clockSlotIndices).forEach { (mapping, slotIndex) ->
            val layers = DynamicIconAssets.clockLayers(mapping)
            replacements["${clkPrefix}clock_${slotIndex}_bg.png"] = File(layers.backgroundPath)
            replacements["${clkPrefix}clock_${slotIndex}_hour.png"] = File(layers.hourPath)
            replacements["${clkPrefix}clock_${slotIndex}_minute.png"] = File(layers.minutePath)
            replacements["${clkPrefix}clock_${slotIndex}_second.png"] = File(layers.secondPath)
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

private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var bytesWritten: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray) {
        out.write(b)
        bytesWritten += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        bytesWritten += len
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        out.close()
    }
}
