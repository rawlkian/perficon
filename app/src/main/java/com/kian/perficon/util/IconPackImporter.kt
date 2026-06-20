package com.kian.perficon.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.repository.IconPackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class IconPackImporter(
    private val context: Context,
    private val repository: IconPackRepository
) {

    private val TAG = "IconPackImporter"

    data class ImportProgress(
        val totalItems: Int = 0,
        val currentItem: Int = 0,
        val hasMask: Boolean = false,
        val hasUpon: Boolean = false,
        val backCount: Int = 0,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    data class IconPackInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    fun getInstalledIconPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableSetOf<String>()
        val intentFilters = listOf("com.novalauncher.THEME", "org.adw.launcher.THEMES", "com.dlto.atom.launcher.THEME", "com.fede.launcher.THEME_ICONPACK")
        
        intentFilters.forEach { action ->
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            resolveInfos.forEach { iconPacks.add(it.activityInfo.packageName) }
        }
        
        return iconPacks.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                IconPackInfo(pm.getApplicationLabel(appInfo).toString(), pkg, pm.getApplicationIcon(appInfo))
            } catch (e: Exception) { null }
        }.sortedBy { it.name }
    }

    suspend fun importFromInstalledApp(
        sourcePackageName: String,
        newProjectName: String,
        newPackageName: String,
        progressFlow: MutableStateFlow<ImportProgress>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteContext = context.createPackageContext(sourcePackageName, Context.CONTEXT_IGNORE_SECURITY)
            val remoteRes = remoteContext.resources
            val mappingFiles = listOf("appfilter.xml", "theme_resources.xml", "appmap.xml", "drawable.xml")

            var parser: XmlPullParser? = null
            var inputStream: InputStream? = null

            for (file in mappingFiles) {
                try {
                    val stream = remoteContext.assets.open(file)
                    parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                    inputStream = stream
                    break
                } catch (e: Exception) {}
            }

            if (parser == null) {
                for (file in mappingFiles) {
                    val resName = file.substringBeforeLast(".")
                    val resId = remoteRes.getIdentifier(resName, "xml", sourcePackageName)
                    if (resId != 0) {
                        parser = remoteRes.getXml(resId)
                        break
                    }
                }
            }

            if (parser == null) {
                progressFlow.value = progressFlow.value.copy(error = "Mapping file not found", isFinished = true)
                return@withContext false
            }

            val projectId = repository.insertProject(IconPackProject(name = newProjectName, packageName = newPackageName))
            val projectDir = File(context.filesDir, "projects/$projectId/icons").apply { mkdirs() }
            var currentProject = repository.getProjectById(projectId) ?: return@withContext false

            // First pass to count items
            val itemsToProcess = mutableListOf<Pair<String, String>>()
            var eventType = parser.eventType
            var hasMask = false
            var hasUpon = false
            var backsFound = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val draw = parser.getAttributeValue(null, "drawable")
                            if (comp != null && draw != null) itemsToProcess.add(comp to draw)
                        }
                        "iconmask" -> hasMask = true
                        "iconupon" -> hasUpon = true
                        "iconback" -> {
                            for (i in 1..10) if (parser.getAttributeValue(null, "img$i") != null) backsFound++
                        }
                    }
                }
                eventType = parser.next()
            }
            
            progressFlow.value = ImportProgress(totalItems = itemsToProcess.size)

            // Reset parser for second pass (re-opening stream or getting XML)
            // Re-fetch parser
            parser = null 
            for (file in mappingFiles) {
                try {
                    val stream = remoteContext.assets.open(file)
                    parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                    break
                } catch (e: Exception) {}
            }
            if (parser == null) {
                for (file in mappingFiles) {
                    val resId = remoteRes.getIdentifier(file.substringBeforeLast("."), "xml", sourcePackageName)
                    if (resId != 0) { parser = remoteRes.getXml(resId); break }
                }
            }

            if (parser == null) return@withContext false

            eventType = parser.eventType
            var currentProcessed = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    when (tagName) {
                        "item", "calendar" -> {
                            val component = parser.getAttributeValue(null, "component")
                            val drawableName = parser.getAttributeValue(null, "drawable") 
                                ?: parser.getAttributeValue(null, "prefix")
                            
                            if (component != null && drawableName != null) {
                                val cleanedComponent = component.replace(" ", "")
                                val regex = "ComponentInfo\\{([^/]+)/([^}]+)\\}".toRegex()
                                val match = regex.find(cleanedComponent)
                                if (match != null) {
                                    val pkg = match.groupValues[1]
                                    val activity = match.groupValues[2]
                                    val iconPath = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, drawableName, "${pkg}_${System.currentTimeMillis()}", projectDir)
                                    if (iconPath != null) {
                                        repository.insertMapping(
                                            IconMapping(
                                                projectId = projectId,
                                                targetPackageName = pkg,
                                                targetActivityName = activity,
                                                iconPath = iconPath,
                                                mappingType = if (tagName == "calendar") 1 else 0
                                            )
                                        )
                                    }
                                }
                                currentProcessed++
                                progressFlow.value = progressFlow.value.copy(currentItem = currentProcessed)
                            }
                        }
                        "iconmask" -> {
                            val img = parser.getAttributeValue(null, "img1")
                            val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "mask_${System.currentTimeMillis()}", projectDir)
                            if (path != null) {
                                currentProject = currentProject.copy(iconMaskPath = path)
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(hasMask = true)
                            }
                        }
                        "iconupon" -> {
                            val img = parser.getAttributeValue(null, "img1")
                            val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "upon_${System.currentTimeMillis()}", projectDir)
                            if (path != null) {
                                currentProject = currentProject.copy(iconUponPath = path)
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(hasUpon = true)
                            }
                        }
                        "iconback" -> {
                            val backs = mutableListOf<String>()
                            for (i in 1..10) {
                                val img = parser.getAttributeValue(null, "img$i") ?: break
                                val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "back${i}_${System.currentTimeMillis()}", projectDir)
                                if (path != null) backs.add(path)
                            }
                            if (backs.isNotEmpty()) {
                                currentProject = currentProject.copy(iconBackPaths = backs.joinToString(","))
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(backCount = backs.size)
                            }
                        }
                        "scale" -> {
                            val factor = parser.getAttributeValue(null, "factor")?.toFloatOrNull()
                            if (factor != null) {
                                currentProject = currentProject.copy(scaleFactor = factor)
                                repository.updateProject(currentProject)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream?.close()
            progressFlow.value = progressFlow.value.copy(isFinished = true)
            true
        } catch (e: Exception) {
            progressFlow.value = progressFlow.value.copy(error = e.message, isFinished = true)
            false
        }
    }

    private fun extractDrawableToPrivateStorage(remoteContext: Context, remotePkg: String, drawableName: String?, fileName: String, outputDir: File): String? {
        if (drawableName == null) return null
        val res = remoteContext.resources
        val sanitizedName = drawableName.substringAfterLast("/")
        val resId = res.getIdentifier(sanitizedName, "drawable", remotePkg)
        if (resId == 0) return null
        return try {
            val drawable = res.getDrawable(resId, remoteContext.theme)
            val bitmap = drawableToBitmap(drawable)
            val file = File(outputDir, "$fileName.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file.absolutePath
        } catch (e: Exception) { null }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = if (drawable.intrinsicWidth <= 0) 192 else drawable.intrinsicWidth
        val height = if (drawable.intrinsicHeight <= 0) 192 else drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
