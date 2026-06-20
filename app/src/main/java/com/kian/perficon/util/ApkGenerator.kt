package com.kian.perficon.util

import android.content.Context
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * ApkGenerator optimized for templates like CandyBar.
 * It injects appfilter.xml, drawable.xml, and icons into the specified folders.
 */
class ApkGenerator(private val context: Context) {

    fun generateApk(project: IconPackProject, mappings: List<IconMapping>): File? {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val outputApk = File(exportDir, "${project.packageName}.apk")
        val workspace = File(context.cacheDir, "build_${project.id}")
        
        if (workspace.exists()) workspace.deleteRecursively()
        workspace.mkdirs()
        
        try {
            val unsignedApk = File(workspace, "unsigned.apk")
            val baseApkStream = try { context.assets.open("base.apk") } catch (e: Exception) { null }

            ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
                if (baseApkStream != null) {
                    val tempBase = File(workspace, "temp_base.apk")
                    baseApkStream.use { it.copyTo(FileOutputStream(tempBase)) }
                    ZipFile(tempBase).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            // Skip files we are going to replace or that conflict with our injection
                            val name = entry.name
                            val isMetadata = name == "res/xml/appfilter.xml" || 
                                           name == "res/xml/drawable.xml" ||
                                           name == "assets/appfilter.xml"
                            val isIcon = name.startsWith("res/drawable") && 
                                       (name.contains("icon_") || name.contains("iconmask") || name.contains("iconback") || name.contains("iconupon"))
                            
                            if (!isMetadata && !isIcon) {
                                zos.putNextEntry(ZipEntry(name))
                                zip.getInputStream(entry).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                // 1. Inject appfilter.xml (Standard & CandyBar compatibility)
                val appFilterContent = generateAppFilter(project, mappings)
                injectStringAsset(zos, "res/xml/appfilter.xml", appFilterContent)
                injectStringAsset(zos, "assets/appfilter.xml", appFilterContent)

                // 2. Inject drawable.xml (Required for CandyBar Dashboard)
                val drawableContent = generateDrawableXml(mappings)
                injectStringAsset(zos, "res/xml/drawable.xml", drawableContent)

                // 3. Inject Mapping Icons
                mappings.forEach { mapping ->
                    injectIcon(zos, mapping.iconPath, "icon_${mapping.id}")
                }

                // 4. Inject Global Styles (Mask, Back, Upon)
                injectIcon(zos, project.iconMaskPath, "icon_mask")
                injectIcon(zos, project.iconUponPath, "icon_upon")
                project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.forEachIndexed { index, path ->
                    injectIcon(zos, path, "icon_back${index + 1}")
                }
            }
            
            // 5. Sign the resulting APK
            ApkSignerUtil.sign(unsignedApk, outputApk)
            return outputApk
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun injectStringAsset(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun injectIcon(zos: ZipOutputStream, path: String?, name: String) {
        if (path.isNullOrEmpty()) return
        val iconFile = File(path)
        if (iconFile.exists()) {
            // We inject into multiple densities for better launcher support
            listOf("res/drawable-nodpi-v4", "res/drawable-xxxhdpi-v4").forEach { folder ->
                zos.putNextEntry(ZipEntry("$folder/$name.png"))
                iconFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun generateAppFilter(project: IconPackProject, mappings: List<IconMapping>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        sb.append("    <scale factor=\"${project.scaleFactor}\" />\n")
        
        if (!project.iconMaskPath.isNullOrEmpty()) sb.append("    <iconmask img1=\"icon_mask\" />\n")
        if (!project.iconUponPath.isNullOrEmpty()) sb.append("    <iconupon img1=\"icon_upon\" />\n")
        
        val backs = project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        if (backs.isNotEmpty()) {
            sb.append("    <iconback ")
            backs.forEachIndexed { index, _ -> sb.append("img${index + 1}=\"icon_back${index + 1}\" ") }
            sb.append("/>\n")
        }

        mappings.forEach { mapping ->
            sb.append("    <item component=\"ComponentInfo{${mapping.targetPackageName}/${mapping.targetActivityName}}\" drawable=\"icon_${mapping.id}\" />\n")
        }
        sb.append("</resources>")
        return sb.toString()
    }

    /**
     * Generates drawable.xml which tells templates like CandyBar which icons to show in the gallery.
     */
    private fun generateDrawableXml(mappings: List<IconMapping>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        sb.append("    <category title=\"Icons\" />\n")
        mappings.forEach { mapping ->
            sb.append("    <item drawable=\"icon_${mapping.id}\" />\n")
        }
        sb.append("</resources>")
        return sb.toString()
    }
}
