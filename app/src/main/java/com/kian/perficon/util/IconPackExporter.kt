package com.kian.perficon.util

import android.content.Context
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IconPackExporter(private val context: Context) {

    fun exportToZip(project: IconPackProject, mappings: List<IconMapping>): File? {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        val zipFile = File(exportDir, "${project.name.replace(" ", "_")}_iconpack.zip")
        
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 1. Generate appfilter.xml
                val appFilterContent = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
                mappings.forEach { mapping ->
                    val drawableName = "icon_${mapping.id}"
                    appFilterContent.append("    <item component=\"ComponentInfo{${mapping.targetPackageName}/${mapping.targetActivityName}}\" drawable=\"$drawableName\" />\n")
                    
                    // 2. Add icon to zip
                    val iconFile = File(mapping.iconPath)
                    if (iconFile.exists()) {
                        val entry = ZipEntry("res/drawable-xxxhdpi/$drawableName.png")
                        zos.putNextEntry(entry)
                        iconFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                appFilterContent.append("</resources>")
                
                val appFilterEntry = ZipEntry("res/xml/appfilter.xml")
                zos.putNextEntry(appFilterEntry)
                zos.write(appFilterContent.toString().toByteArray())
                zos.closeEntry()
            }
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
