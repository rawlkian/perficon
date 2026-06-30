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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class IconPackImporter(
    private val context: Context,
    private val repository: IconPackRepository
) {

    private val TAG = "IconPackImporter"
    private val drawableLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private data class ParsedMapping(
        val component: String?,
        val drawable: String,
        val tagName: String,
        val displayName: String?
    )

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
        val uponImgs = mutableListOf<String>()
        val backImgs = mutableListOf<String>()
        var scaleFactor: Float? = null
    }

    private class ContentDeduper {
        val hashToPath = ConcurrentHashMap<String, String>()
        val lock = Mutex()
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
        description: String = "",
        progressFlow: MutableStateFlow<ImportProgress>
    ): Boolean = withContext(Dispatchers.IO) {
        var projectId: Long = -1L
        var importSuccessful = false
        try {
            val remoteContext = context.createPackageContext(sourcePackageName, Context.CONTEXT_IGNORE_SECURITY)
            val remoteRes = remoteContext.resources
            val mappingFiles = listOf("appfilter.xml", "drawable.xml", "theme_resources.xml", "appmap.xml")

            val allIconsMap = mutableMapOf<String, ParsedMapping>()
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
            val globalBackImgs = expandIconBackResourceNames(globalConfig.backImgs) { candidate ->
                val res = remoteContext.resources
                res.getIdentifier(candidate, "drawable", sourcePackageName) != 0 ||
                    res.getIdentifier(candidate, "mipmap", sourcePackageName) != 0
            }

            // Create project first to get ID
            projectId = repository.insertProject(IconPackProject(name = newProjectName, packageName = newPackageName, description = description))
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
            val contentDeduper = ContentDeduper()

            val totalItems = allIconsMap.size + clockMappings.size
            progressFlow.value = ImportProgress(
                totalItems = totalItems,
                hasMask = globalConfig.maskImg != null,
                hasUpon = globalConfig.uponImgs.isNotEmpty(),
                backCount = globalBackImgs.size
            )

            val calendarComponents = allIconsMap.values
                .filter { it.tagName.equals("calendar", true) || it.tagName.equals("calender", true) }
                .mapNotNull { it.component }
                .toSet()

            val mappingsToInsert = java.util.Collections.synchronizedList(mutableListOf<IconMapping>())
            val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(16)

            coroutineScope {
                allIconsMap.values.map { mappingItem ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            val component = mappingItem.component
                            val drawableName = mappingItem.drawable
                            val tagName = mappingItem.tagName
                            val displayName = mappingItem.displayName

                            val isCalendar = tagName.equals("calendar", true) || tagName.equals("calender", true)
                            if (!isCalendar && component != null && component in calendarComponents) {
                                val current = processedCount.incrementAndGet()
                                if (current % 50 == 0 || current == totalItems) {
                                    progressFlow.value = progressFlow.value.copy(currentItem = current)
                                }
                                return@async
                            }

                            val (targetPkg, targetActivity) = parseComponentInfo(component)
                            val finalIconName = displayName?.takeIf { it.isNotBlank() }
                                ?: targetPkg.substringAfterLast(".")

                            if (isCalendar) {
                                val frames = (1..DynamicIconAssets.CALENDAR_DAY_COUNT).mapNotNull { day ->
                                    extractDrawableToPrivateStorage(
                                        remoteContext,
                                        sourcePackageName,
                                        "${drawableName}$day",
                                        "calendar_${drawableName}_$day",
                                        projectIconsDir,
                                        contentDeduper
                                    )
                                }
                                if (frames.size == DynamicIconAssets.CALENDAR_DAY_COUNT) {
                                    mappingsToInsert.add(IconMapping(
                                        projectId = projectId,
                                        iconName = finalIconName,
                                        targetPackageName = targetPkg,
                                        targetActivityName = targetActivity,
                                        iconPath = frames.first(),
                                        mappingType = 1,
                                        extraInfo = DynamicIconAssets.calendarExtraInfo(frames)
                                    ))
                                }
                            } else if (drawableName in dynamicClockDrawables) {
                                val layers = extractClockLayers(remoteContext, sourcePackageName, drawableName, projectIconsDir, contentDeduper)
                                if (layers != null) {
                                    mappingsToInsert.add(IconMapping(
                                        projectId = projectId,
                                        iconName = finalIconName,
                                        targetPackageName = targetPkg,
                                        targetActivityName = targetActivity,
                                        iconPath = layers.backgroundPath,
                                        mappingType = 2,
                                        extraInfo = DynamicIconAssets.clockExtraInfo(layers)
                                    ))
                                }
                            } else {
                                val iconPath = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, drawableName, "icon_${drawableName}", projectIconsDir, contentDeduper)
                                if (iconPath != null) {
                                    mappingsToInsert.add(IconMapping(
                                        projectId = projectId,
                                        iconName = finalIconName,
                                        targetPackageName = targetPkg,
                                        targetActivityName = targetActivity,
                                        iconPath = iconPath
                                    ))
                                }
                            }

                            val current = processedCount.incrementAndGet()
                            if (current % 50 == 0 || current == totalItems) {
                                progressFlow.value = progressFlow.value.copy(currentItem = current)
                            }
                        }
                    }
                } + clockMappings.map { triple ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            val (pkg, face, hands) = triple
                            val facePath = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, face, "clock_face_${face}", projectIconsDir, contentDeduper)
                            if (facePath != null) {
                                val hourDrawable = hands.optString("hour")
                                val minuteDrawable = hands.optString("minute")
                                val secondDrawable = hands.optString("second")
                                
                                val hourPath = if (hourDrawable.isNotBlank()) {
                                    extractDrawableToPrivateStorage(remoteContext, sourcePackageName, hourDrawable, "clock_hand_${hourDrawable}", projectIconsDir, contentDeduper)
                                } else null
                                val minutePath = if (minuteDrawable.isNotBlank()) {
                                    extractDrawableToPrivateStorage(remoteContext, sourcePackageName, minuteDrawable, "clock_hand_${minuteDrawable}", projectIconsDir, contentDeduper)
                                } else null
                                val secondPath = if (secondDrawable.isNotBlank()) {
                                    extractDrawableToPrivateStorage(remoteContext, sourcePackageName, secondDrawable, "clock_hand_${secondDrawable}", projectIconsDir, contentDeduper)
                                } else null
                                
                                val layers = DynamicIconAssets.ClockLayers(
                                    backgroundPath = facePath,
                                    hourPath = hourPath ?: facePath,
                                    minutePath = minutePath ?: hourPath ?: facePath,
                                    secondPath = secondPath ?: minutePath ?: hourPath ?: facePath
                                )
                                
                                mappingsToInsert.add(IconMapping(
                                    projectId = projectId,
                                    targetPackageName = pkg,
                                    targetActivityName = "",
                                    iconPath = facePath,
                                    mappingType = 2,
                                    extraInfo = DynamicIconAssets.clockExtraInfo(layers)
                                ))
                            }
                            val current = processedCount.incrementAndGet()
                            if (current % 50 == 0 || current == totalItems) {
                                progressFlow.value = progressFlow.value.copy(currentItem = current)
                            }
                        }
                    }
                }
            }.awaitAll()

            if (mappingsToInsert.isNotEmpty()) {
                repository.insertMappings(mappingsToInsert)
            }

            // Global Styles
            var importedMaskPath: String? = currentProject.iconMaskPath
            var importedBackPaths: String? = currentProject.iconBackPaths
            var importedUponPath: String? = currentProject.iconUponPath
            var importedScaleFactor = currentProject.scaleFactor

            globalConfig.maskImg?.let { name ->
                extractDrawableToPrivateStorage(remoteContext, sourcePackageName, name, "mask", projectIconsDir, contentDeduper)?.let {
                    importedMaskPath = it
                }
            }
            if (globalConfig.uponImgs.isNotEmpty()) {
                val paths = globalConfig.uponImgs.mapIndexedNotNull { index, name ->
                    val outputName = sanitizeOutputFileName("upon_${index + 1}_${normalizeDrawableResourceName(name)}")
                    extractDrawableToPrivateStorage(remoteContext, sourcePackageName, name, outputName, projectIconsDir, contentDeduper)
                }
                if (paths.isNotEmpty()) importedUponPath = paths.joinToString(",")
            }
            if (globalBackImgs.isNotEmpty()) {
                val paths = globalBackImgs.mapNotNull { name ->
                    val outputName = sanitizeOutputFileName("back_${normalizeDrawableResourceName(name)}")
                    extractDrawableToPrivateStorage(remoteContext, sourcePackageName, name, outputName, projectIconsDir, contentDeduper)
                }
                if (paths.isNotEmpty()) importedBackPaths = paths.joinToString(",")
            }
            globalConfig.scaleFactor?.let { importedScaleFactor = it }

            repository.updateProjectStyle(
                projectId = projectId,
                maskPath = importedMaskPath,
                backPaths = importedBackPaths,
                uponPath = importedUponPath,
                scaleFactor = importedScaleFactor
            )
            importSuccessful = true
            progressFlow.value = progressFlow.value.copy(isFinished = true)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Import cancelled by user")
            } else {
                Log.e(TAG, "Import failure", e)
            }
            progressFlow.value = progressFlow.value.copy(
                error = if (e is kotlinx.coroutines.CancellationException) "导入被取消" else e.message, 
                isFinished = true
            )
            false
        } finally {
            if (!importSuccessful && projectId != -1L) {
                try {
                    val proj = repository.getProjectById(projectId)
                    if (proj != null) {
                        repository.deleteProject(proj)
                    }
                    val projectRoot = StorageHelper.getProjectDir(projectId)
                    projectRoot.deleteRecursively()
                } catch (ex: Exception) {
                    Log.e(TAG, "Error cleaning up after failed/cancelled import", ex)
                }
            }
        }
    }

    private fun parseXmlToMap(
        parser: XmlPullParser,
        map: MutableMap<String, ParsedMapping>,
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
                            val name = getSafeAttribute(parser, "name")
                            if (draw != null) {
                                map["${comp ?: ""}_$draw"] = ParsedMapping(comp, draw, parser.name, name)
                            }
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
                        "iconmask" -> {
                            if (config.maskImg == null) {
                                getSafeAttribute(parser, "img1")?.let { config.maskImg = it }
                            }
                        }
                        "iconupon" -> {
                            for (i in 1..10) {
                                getSafeAttribute(parser, "img$i")?.let { value ->
                                    config.uponImgs.addIfAbsent(value)
                                }
                            }
                        }
                        "iconback" -> {
                            for (i in 1..10) {
                                getSafeAttribute(parser, "img$i")?.let { value ->
                                    config.backImgs.addIfAbsent(value)
                                }
                            }
                        }
                        "scale" -> {
                            if (config.scaleFactor == null) {
                                getSafeAttribute(parser, "factor")?.toFloatOrNull()?.let { config.scaleFactor = it }
                            }
                        }
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

    private suspend fun extractClockLayers(
        remoteContext: Context,
        remotePkg: String,
        drawableName: String,
        outputDir: File,
        contentDeduper: ContentDeduper
    ): DynamicIconAssets.ClockLayers? {
        val slot = Regex("clock_dynamic_(\\d+)").find(drawableName)?.groupValues?.getOrNull(1)
        val face = extractDrawableToPrivateStorage(remoteContext, remotePkg, drawableName, "clock_face_${drawableName}", outputDir, contentDeduper)
        val background = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_bg", "clock_bg_${drawableName}", outputDir, contentDeduper) } ?: face
        val hour = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_hour", "clock_hour_${drawableName}", outputDir, contentDeduper) } ?: background
        val minute = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_minute", "clock_minute_${drawableName}", outputDir, contentDeduper) } ?: hour
        val second = slot?.let { extractDrawableToPrivateStorage(remoteContext, remotePkg, "clock_${it}_second", "clock_second_${drawableName}", outputDir, contentDeduper) } ?: minute
        return background?.let { DynamicIconAssets.ClockLayers(it, hour ?: it, minute ?: hour ?: it, second ?: minute ?: hour ?: it) }
    }

    private fun MutableList<String>.addIfAbsent(value: String) {
        if (none { it == value }) add(value)
    }

    private suspend fun extractDrawableToPrivateStorage(
        remoteContext: Context, 
        remotePkg: String, 
        drawableName: String?, 
        fileName: String, 
        outputDir: File,
        contentDeduper: ContentDeduper
    ): String? = withContext(Dispatchers.IO) {
        if (drawableName == null) return@withContext null
        
        val lockKey = "${remotePkg}_${drawableName}"
        val lock = drawableLocks.getOrPut(lockKey) { Mutex() }
        
        lock.withLock {
            val targetFile = File(outputDir, "${sanitizeOutputFileName(fileName)}.png")
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withLock targetFile.absolutePath
            }
            
            val res = remoteContext.resources
            val name = normalizeDrawableResourceName(drawableName)
            var resId = res.getIdentifier(name, "drawable", remotePkg)
            if (resId == 0) resId = res.getIdentifier(name, "mipmap", remotePkg)
            
            if (resId != 0) {
                try {
                    val bitmap = drawableToBitmap(res.getDrawable(resId, remoteContext.theme))
                    val savedPath = saveDeduplicatedBitmap(bitmap, targetFile, contentDeduper)
                    bitmap.recycle()
                    return@withLock savedPath
                } catch (e: Exception) {}
            }

            val paths = drawableAssetCandidates(drawableName)
            for (path in paths) {
                try {
                    remoteContext.assets.openNonAssetFd(path).createInputStream().use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        if (bitmap != null) {
                            val savedPath = saveDeduplicatedBitmap(bitmap, targetFile, contentDeduper)
                            bitmap.recycle()
                            return@withLock savedPath
                        }
                    }
                } catch (e: Exception) {}
            }
            null
        }
    }

    private suspend fun saveDeduplicatedBitmap(
        bitmap: Bitmap,
        targetFile: File,
        contentDeduper: ContentDeduper
    ): String {
        val pngBytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode drawable." }
            output.toByteArray()
        }
        val hash = sha256(pngBytes)
        return contentDeduper.lock.withLock {
            contentDeduper.hashToPath[hash]
                ?.takeIf { File(it).isFile }
                ?.let { return@withLock it }

            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { it.write(pngBytes) }
            contentDeduper.hashToPath[hash] = targetFile.absolutePath
            targetFile.absolutePath
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sanitizeOutputFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

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

internal fun normalizeDrawableResourceName(drawableName: String): String {
    val withoutPrefix = drawableName
        .substringAfterLast("/")
        .substringAfterLast(":")
        .removePrefix("@")
    return withoutPrefix.substringBeforeLast(".", withoutPrefix)
}

internal fun expandIconBackResourceNames(
    configuredNames: List<String>,
    resourceExists: (String) -> Boolean
): List<String> {
    val expanded = mutableListOf<String>()
    fun add(value: String) {
        if (expanded.none { it == value }) expanded += value
    }

    configuredNames.forEach { configuredName ->
        add(configuredName)

        val normalized = normalizeDrawableResourceName(configuredName)
        val shouldProbeSiblings =
            normalized.contains("icon_back", ignoreCase = true) ||
                normalized.endsWith("back", ignoreCase = true) ||
                normalized.endsWith("background", ignoreCase = true)

        if (shouldProbeSiblings) {
            for (index in 2..10) {
                val candidate = "${normalized}_$index"
                if (resourceExists(candidate)) add(candidate)
            }
        }
    }

    return expanded
}

internal fun drawableAssetCandidates(drawableName: String): List<String> {
    val rawPath = drawableName
        .removePrefix("@")
        .substringAfter(":")
        .replace('\\', '/')
    val normalized = normalizeDrawableResourceName(drawableName)
    val rawFileName = rawPath.substringAfterLast("/")
    val fileNames = buildList {
        if (rawFileName.contains(".")) add(rawFileName)
        add("$normalized.png")
        add("$normalized.webp")
    }.distinct()
    val folders = listOf(
        "res/drawable-nodpi-v4",
        "res/drawable-nodpi",
        "res/drawable-anydpi-v26",
        "res/drawable-anydpi",
        "res/drawable-xxxhdpi-v4",
        "res/drawable-xxxhdpi",
        "res/drawable-xxhdpi-v4",
        "res/drawable-xxhdpi",
        "res/drawable-xhdpi-v4",
        "res/drawable-xhdpi",
        "res/drawable-hdpi-v4",
        "res/drawable-hdpi",
        "res/drawable-mdpi-v4",
        "res/drawable-mdpi",
        "res/drawable-v24",
        "res/drawable",
        "res/mipmap-xxxhdpi-v4",
        "res/mipmap-xxxhdpi",
        "res/mipmap-xxhdpi-v4",
        "res/mipmap-xxhdpi",
        "res/mipmap-xhdpi-v4",
        "res/mipmap-xhdpi",
        "res/mipmap-hdpi-v4",
        "res/mipmap-hdpi",
        "res/mipmap-mdpi-v4",
        "res/mipmap-mdpi",
        "res/mipmap"
    )
    return buildList {
        if (rawPath.startsWith("res/") && rawPath.substringAfterLast("/").contains(".")) add(rawPath)
        folders.forEach { folder ->
            fileNames.forEach { fileName -> add("$folder/$fileName") }
        }
    }.distinct()
}
