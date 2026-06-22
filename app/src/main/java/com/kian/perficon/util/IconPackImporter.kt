package com.kian.perficon.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.json.JSONObject

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

    private class GlobalOverlayConfig {
        var maskImg: String? = null
        var uponImg: String? = null
        val backImgs = mutableSetOf<String>()
        var scaleFactor: Float? = null
    }

    fun getInstalledIconPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableSetOf<String>()
        val intentFilters = listOf("com.novalauncher.THEME", "org.adw.launcher.THEMES", "com.dlto.atom.launcher.THEME", "com.fede.launcher.THEME_ICONPACK")
        intentFilters.forEach { action ->
            val intent = Intent(action)
            pm.queryIntentActivities(intent, PackageManager.GET_META_DATA).forEach { iconPacks.add(it.activityInfo.packageName) }
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
        projectIconPath: String? = null,
        progressFlow: MutableStateFlow<ImportProgress>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteContext = context.createPackageContext(sourcePackageName, Context.CONTEXT_IGNORE_SECURITY)
            val remoteRes = remoteContext.resources
            val mappingFiles = listOf("appfilter.xml", "drawable.xml", "theme_resources.xml", "appmap.xml")

            val allIconsMap = mutableMapOf<String, Triple<String?, String, String>>()
            val clockMappings = mutableListOf<Triple<String, String, JSONObject>>()
            val dynamicClockDrawables = mutableSetOf<String>()
            val globalConfig = GlobalOverlayConfig()

            for (fileName in mappingFiles) {
                val resName = fileName.substringBeforeLast(".")
                
                var assetsStream: InputStream? = null
                try {
                    assetsStream = remoteContext.assets.open(fileName)
                    val parser = Xml.newPullParser().apply { setInput(assetsStream, "UTF-8") }
                    parseXmlToMap(parser, allIconsMap, clockMappings, dynamicClockDrawables, globalConfig)
                } catch (e: Exception) {} finally { assetsStream?.close() }

                try {
                    val resId = remoteRes.getIdentifier(resName, "xml", sourcePackageName)
                    if (resId != 0) {
                        val parser = remoteRes.getXml(resId)
                        parseXmlToMap(parser, allIconsMap, clockMappings, dynamicClockDrawables, globalConfig)
                    }
                } catch (e: Exception) {}
            }

            if (allIconsMap.isEmpty() && clockMappings.isEmpty() && dynamicClockDrawables.isEmpty()) {
                progressFlow.value = progressFlow.value.copy(error = "No valid mapping found", isFinished = true)
                return@withContext false
            }

            // Create project first to get ID
            val projectId = repository.insertProject(IconPackProject(name = newProjectName, packageName = newPackageName))
            var finalIconPath: String? = projectIconPath
            if (projectIconPath != null) {
                val tempFile = File(projectIconPath)
                if (tempFile.exists()) {
                    val projectIconsDir = StorageHelper.getProjectIconsDir(projectId)
                    if (!projectIconsDir.exists()) projectIconsDir.mkdirs()
                    val destFile = File(projectIconsDir, tempFile.name)
                    try {
                        tempFile.copyTo(destFile, overwrite = true)
                        finalIconPath = destFile.absolutePath
                        tempFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (finalIconPath != null) {
                repository.updateProject(repository.getProjectById(projectId)!!.copy(projectIconPath = finalIconPath))
            }
            val projectIconsDir = StorageHelper.getProjectIconsDir(projectId)
            var currentProject = repository.getProjectById(projectId) ?: return@withContext false

            progressFlow.value = ImportProgress(
                totalItems = allIconsMap.size + clockMappings.size,
                hasMask = globalConfig.maskImg != null,
                hasUpon = globalConfig.uponImg != null,
                backCount = globalConfig.backImgs.size
            )

            var currentProcessed = 0
            val calendarComponents = allIconsMap.values
                .filter { it.third.equals("calendar", true) || it.third.equals("calender", true) }
                .mapNotNull { it.first }
                .toSet()

            // Items & Calendars
            for ((_, triple) in allIconsMap) {
                val component = triple.first
                val drawableName = triple.second
                val tagName = triple.third

                val isCalendar = tagName.equals("calendar", true) || tagName.equals("calender", true)
                if (!isCalendar && component != null && component in calendarComponents) {
                    continue
                }

                val (targetPkg, targetActivity) = parseComponentInfo(component)
                if (isCalendar) {
                    val frames = (1..DynamicIconAssets.CALENDAR_DAY_COUNT).mapNotNull { day ->
                        extractDrawableToPrivateStorage(
                            remoteContext,
                            sourcePackageName,
                            "${drawableName}$day",
                            "calendar_${System.currentTimeMillis()}_$day",
                            projectIconsDir
                        )
                    }
                    if (frames.size == DynamicIconAssets.CALENDAR_DAY_COUNT) {
                        repository.insertMapping(IconMapping(
                            projectId = projectId,
                            iconName = targetPkg.substringAfterLast("."),
                            targetPackageName = targetPkg,
                            targetActivityName = targetActivity,
                            iconPath = frames.first(),
                            mappingType = 1,
                            extraInfo = DynamicIconAssets.calendarExtraInfo(frames)
                        ))
                    }
                } else if (drawableName in dynamicClockDrawables) {
                    val layers = extractClockLayers(remoteContext, sourcePackageName, drawableName, projectIconsDir)
                    if (layers != null) {
                        repository.insertMapping(IconMapping(
                            projectId = projectId,
                            iconName = targetPkg.substringAfterLast("."),
                            targetPackageName = targetPkg,
                            targetActivityName = targetActivity,
                            iconPath = layers.backgroundPath,
                            mappingType = 2,
                            extraInfo = DynamicIconAssets.clockExtraInfo(layers)
                        ))
                    }
                } else {
                    val iconPath = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, drawableName, "${drawableName}_${System.currentTimeMillis()}", projectIconsDir)
                    if (iconPath == null) {
                        currentProcessed++
                        progressFlow.value = progressFlow.value.copy(currentItem = currentProcessed)
                        continue
                    }
                    repository.insertMapping(IconMapping(
                        projectId = projectId,
                        iconName = targetPkg.substringAfterLast("."),
                        targetPackageName = targetPkg,
                        targetActivityName = targetActivity,
                        iconPath = iconPath
                    ))
                }
                currentProcessed++
                progressFlow.value = progressFlow.value.copy(currentItem = currentProcessed)
            }

            // Clocks
            for (triple in clockMappings) {
                val (pkg, face, hands) = triple
                extractDrawableToPrivateStorage(remoteContext, sourcePackageName, face, "clock_face_${System.currentTimeMillis()}", projectIconsDir)?.let { facePath ->
                    repository.insertMapping(IconMapping(
                        projectId = projectId,
                        targetPackageName = pkg,
                        targetActivityName = "",
                        iconPath = facePath,
                        mappingType = 2,
                        extraInfo = hands.toString()
                    ))
                }
                currentProcessed++
                progressFlow.value = progressFlow.value.copy(currentItem = currentProcessed)
            }

            // Global Styles
            globalConfig.maskImg?.let { name ->
                extractDrawableToPrivateStorage(remoteContext, sourcePackageName, name, "mask", projectIconsDir)?.let {
                    currentProject = currentProject.copy(iconMaskPath = it)
                }
            }
            globalConfig.uponImg?.let { name ->
                extractDrawableToPrivateStorage(remoteContext, sourcePackageName, name, "upon", projectIconsDir)?.let {
                    currentProject = currentProject.copy(iconUponPath = it)
                }
            }
            if (globalConfig.backImgs.isNotEmpty()) {
                val paths = globalConfig.backImgs.mapNotNull { extractDrawableToPrivateStorage(remoteContext, sourcePackageName, it, "back_${System.currentTimeMillis()}", projectIconsDir) }
                if (paths.isNotEmpty()) currentProject = currentProject.copy(iconBackPaths = paths.joinToString(","))
            }
            globalConfig.scaleFactor?.let { currentProject = currentProject.copy(scaleFactor = it) }
            
            repository.updateProject(currentProject)
            progressFlow.value = progressFlow.value.copy(isFinished = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failure", e)
            progressFlow.value = progressFlow.value.copy(error = e.message, isFinished = true)
            false
        }
    }

    private fun parseXmlToMap(
        parser: XmlPullParser,
        map: MutableMap<String, Triple<String?, String, String>>,
        clocks: MutableList<Triple<String, String, JSONObject>>,
        dynamicClockDrawables: MutableSet<String>,
        config: GlobalOverlayConfig
    ) {
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item", "calendar", "calender" -> {
                            val comp = getSafeAttribute(parser, "component")
                            val draw = getSafeAttribute(parser, "drawable") ?: getSafeAttribute(parser, "prefix")
                            if (draw != null) map["${comp ?: ""}_$draw"] = Triple(comp, draw, parser.name)
                        }
                        "dynamic-clock" -> {
                            val pkg = getSafeAttribute(parser, "package")
                            val draw = getSafeAttribute(parser, "drawable")
                            if (pkg != null && draw != null) {
                                val hands = JSONObject()
                                var clockEvent = parser.next()
                                while (!(clockEvent == XmlPullParser.END_TAG && parser.name == "dynamic-clock")) {
                                    if (clockEvent == XmlPullParser.START_TAG) {
                                        getSafeAttribute(parser, "drawable")?.let { hands.put(parser.name, it) }
                                    }
                                    clockEvent = parser.next()
                                }
                                clocks.add(Triple(pkg, draw, hands))
                            } else if (draw != null) {
                                dynamicClockDrawables += draw
                            }
                        }
                        "iconmask" -> getSafeAttribute(parser, "img1")?.let { config.maskImg = it }
                        "iconupon" -> getSafeAttribute(parser, "img1")?.let { config.uponImg = it }
                        "iconback" -> for (i in 1..10) getSafeAttribute(parser, "img$i")?.let { config.backImgs.add(it) }
                        "scale" -> getSafeAttribute(parser, "factor")?.toFloatOrNull()?.let { config.scaleFactor = it }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {}
    }

    private fun getSafeAttribute(parser: XmlPullParser, attrName: String): String? {
        val value = parser.getAttributeValue(null, attrName)
        if (value != null) return value
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).substringAfterLast(":").equals(attrName, true)) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun parseComponentInfo(component: String?): Pair<String, String> {
        if (component == null) return "" to ""
        val cleaned = component.replace(" ", "")
        val regex = "ComponentInfo\\{([^/]+)/([^}]+)\\}".toRegex()
        val match = regex.find(cleaned)
        return if (match != null) match.groupValues[1] to match.groupValues[2] else "" to ""
    }

    private fun extractClockLayers(
        remoteContext: Context,
        remotePkg: String,
        drawableName: String,
        outputDir: File
    ): DynamicIconAssets.ClockLayers? {
        val slot = Regex("clock_dynamic_(\\d+)").find(drawableName)?.groupValues?.getOrNull(1)
        val face = extractDrawableToPrivateStorage(remoteContext, remotePkg, drawableName, "clock_face_${System.currentTimeMillis()}", outputDir)
        val background = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_bg", "clock_bg_${System.currentTimeMillis()}", outputDir) } ?: face
        val hour = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_hour", "clock_hour_${System.currentTimeMillis()}", outputDir) } ?: background
        val minute = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_minute", "clock_minute_${System.currentTimeMillis()}", outputDir) } ?: hour
        val second = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_second", "clock_second_${System.currentTimeMillis()}", outputDir) } ?: minute
        return background?.let { DynamicIconAssets.ClockLayers(it, hour ?: it, minute ?: hour ?: it, second ?: minute ?: hour ?: it) }
    }

    private fun extractDrawableToPrivateStorage(remoteContext: Context, remotePkg: String, drawableName: String?, fileName: String, outputDir: File): String? {
        if (drawableName == null) return null
        val res = remoteContext.resources
        val name = drawableName.substringAfterLast("/")
        var resId = res.getIdentifier(name, "drawable", remotePkg)
        if (resId == 0) resId = res.getIdentifier(name, "mipmap", remotePkg)
        
        val targetFile = File(outputDir, "$fileName.png")
        if (resId != 0) {
            try {
                val bitmap = drawableToBitmap(res.getDrawable(resId, remoteContext.theme))
                FileOutputStream(targetFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                return targetFile.absolutePath
            } catch (e: Exception) {}
        }

        val paths = listOf("res/drawable-nodpi-v4/$name.png", "res/drawable/$name.png", "res/mipmap-xxxhdpi-v4/$name.png")
        for (path in paths) {
            try {
                remoteContext.assets.openNonAssetFd(path).createInputStream().use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        FileOutputStream(targetFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                        return targetFile.absolutePath
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val w = if (drawable.intrinsicWidth <= 10) 256 else drawable.intrinsicWidth
        val h = if (drawable.intrinsicHeight <= 10) 256 else drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
