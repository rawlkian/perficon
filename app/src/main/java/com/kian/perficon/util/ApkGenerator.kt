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
            val staticMappings = mappedEntries.filter { it.mappingType == 0 }
            if (staticMappings.size != mappedEntries.size) {
                throw GenerationException(
                    "项目包含动态日历或动态时钟。当前导出模板尚未包含 CandyBar 所需的日期序列或时钟图层资源，无法安全导出。"
                )
            }
            // Older projects only stored a package name. Resolve its real launcher Activity
            // at export time so component-based launchers such as Lawnchair can match it.
            val resolvedMappings = staticMappings.map(::resolveLaunchActivity)

            ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
                ZipFile(baseApk).use { zip ->
                    onProgress(Progress(3, "正在写入图标与映射"))
                    val slots = findTemplateSlots(zip)
                    if (resolvedMappings.size > slots.size) {
                        throw GenerationException(
                            "This CandyBar template has ${slots.size} icon slots, but the project has ${resolvedMappings.size} icons."
                        )
                    }

                    val replacements = slots.zip(resolvedMappings).associate { (slot, mapping) ->
                        slot.entryName to File(mapping.iconPath)
                    }
                    val missingIcons = replacements.values.filterNot(File::isFile)
                    if (missingIcons.isNotEmpty()) {
                        throw GenerationException("One or more project icon files no longer exist.")
                    }

                    val resourceAppFilter = BinaryAppFilterWriter.build(
                        mappings = resolvedMappings,
                        slotIndices = slots.take(resolvedMappings.size).map(TemplateSlot::index)
                    )
                    val resourceDrawables = BinaryDrawableXmlWriter.build(
                        mappings = resolvedMappings,
                        slotIndices = slots.take(resolvedMappings.size).map(TemplateSlot::index)
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
                        val replacement = replacements[entry.name] ?: projectIcon?.takeIf {
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

                    injectStringAsset(zos, "assets/appfilter.xml", generateAppFilter(project, resolvedMappings, slots))
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
        slots: List<TemplateSlot>
    ): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        sb.append("    <scale factor=\"${project.scaleFactor}\" />\n")

        mappings.zip(slots).forEach { (mapping, slot) ->
            val activityName = mapping.targetActivityName.ifBlank {
                "${mapping.targetPackageName}.MainActivity"
            }.let { activity ->
                if (activity.startsWith(".")) mapping.targetPackageName + activity else activity
            }
            sb.append(
                "    <item component=\"ComponentInfo{${mapping.targetPackageName}/$activityName}\" " +
                    "drawable=\"icon_${slot.index}\" />\n"
            )
        }
        sb.append("</resources>")
        return sb.toString()
    }
}
