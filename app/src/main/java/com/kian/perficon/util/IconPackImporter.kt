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
        val intentFilters = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.dlto.atom.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK"
        )

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

            // 优先尝试 Assets 读取
            for (file in mappingFiles) {
                try {
                    val stream = remoteContext.assets.open(file)
                    parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                    inputStream = stream
                    Log.d(TAG, "成功在 Assets 中找到映射文件: $file")
                    break
                } catch (e: Exception) {}
            }

            // 兜底尝试编译后的 res/xml 读取
            if (parser == null) {
                for (file in mappingFiles) {
                    val resName = file.substringBeforeLast(".")
                    val resId = remoteRes.getIdentifier(resName, "xml", sourcePackageName)
                    if (resId != 0) {
                        parser = remoteRes.getXml(resId)
                        Log.d(TAG, "成功在 res/xml 中找到编译后的映射文件: $file")
                        break
                    }
                }
            }

            if (parser == null) {
                progressFlow.value = progressFlow.value.copy(error = "未找到有效的 XML 映射配置文件", isFinished = true)
                return@withContext false
            }

            val projectId = repository.insertProject(IconPackProject(name = newProjectName, packageName = newPackageName))
            val projectDir = File(context.filesDir, "projects/$projectId/icons").apply { mkdirs() }
            var currentProject = repository.getProjectById(projectId) ?: return@withContext false

            // ======= 第一阶段：健壮扫描计数 =======
            val itemsToProcess = mutableListOf<Pair<String, String>>()
            var eventType = parser.eventType
            var hasMask = false
            var hasUpon = false
            var backsFound = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    if (tagName.equals("item", ignoreCase = true)) {
                        val comp = getSafeAttribute(parser, "component")
                        val draw = getSafeAttribute(parser, "drawable")
                        if (comp != null && draw != null) {
                            itemsToProcess.add(comp to draw)
                        }
                    } else if (tagName.equals("iconmask", ignoreCase = true)) {
                        hasMask = true
                    } else if (tagName.equals("iconupon", ignoreCase = true)) {
                        hasUpon = true
                    } else if (tagName.equals("iconback", ignoreCase = true)) {
                        for (i in 1..10) {
                            if (getSafeAttribute(parser, "img$i") != null) backsFound++
                        }
                    }
                }
                eventType = parser.next()
            }

            Log.d(TAG, "第一阶段扫描完毕。共发现图标项: ${itemsToProcess.size}, 遮罩: $hasMask, 叠加层: $hasUpon, 背景数: $backsFound")
            progressFlow.value = ImportProgress(totalItems = itemsToProcess.size)

            // ======= 第二阶段：重新获取 Parser 进行流提取 =======
            inputStream?.close() // 先关闭之前的流
            parser = null
            inputStream = null

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
                    when {
                        tagName.equals("item", ignoreCase = true) || tagName.equals("calendar", ignoreCase = true) -> {
                            val component = getSafeAttribute(parser, "component")
                            val drawableName = getSafeAttribute(parser, "drawable") ?: getSafeAttribute(parser, "prefix")

                            if (component != null && drawableName != null) {
                                val cleanedComponent = component.replace(" ", "")
                                val regex = "ComponentInfo\\{([^/]+)/([^}]+)\\}".toRegex()
                                val match = regex.find(cleanedComponent)
                                if (match != null) {
                                    val pkg = match.groupValues[1]
                                    val activity = match.groupValues[2]

                                    // 提取图片（用相对稳定的特征命名）
                                    val iconPath = extractDrawableToPrivateStorage(
                                        remoteContext, sourcePackageName, drawableName,
                                        "${pkg}_icon", projectDir
                                    )

                                    if (iconPath != null) {
                                        repository.insertMapping(
                                            IconMapping(
                                                projectId = projectId,
                                                targetPackageName = pkg,
                                                targetActivityName = activity,
                                                iconPath = iconPath,
                                                mappingType = if (tagName.equals("calendar", ignoreCase = true)) 1 else 0
                                            )
                                        )
                                    }
                                }
                                currentProcessed++
                                progressFlow.value = progressFlow.value.copy(currentItem = currentProcessed)
                            }
                        }
                        tagName.equals("iconmask", ignoreCase = true) -> {
                            val img = getSafeAttribute(parser, "img1")
                            val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "mask", projectDir)
                            if (path != null) {
                                currentProject = currentProject.copy(iconMaskPath = path)
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(hasMask = true)
                            }
                        }
                        tagName.equals("iconupon", ignoreCase = true) -> {
                            val img = getSafeAttribute(parser, "img1")
                            val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "upon", projectDir)
                            if (path != null) {
                                currentProject = currentProject.copy(iconUponPath = path)
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(hasUpon = true)
                            }
                        }
                        tagName.equals("iconback", ignoreCase = true) -> {
                            val backs = mutableListOf<String>()
                            for (i in 1..10) {
                                val img = getSafeAttribute(parser, "img$i") ?: break
                                val path = extractDrawableToPrivateStorage(remoteContext, sourcePackageName, img, "back_$i", projectDir)
                                if (path != null) backs.add(path)
                            }
                            if (backs.isNotEmpty()) {
                                currentProject = currentProject.copy(iconBackPaths = backs.joinToString(","))
                                repository.updateProject(currentProject)
                                progressFlow.value = progressFlow.value.copy(backCount = backs.size)
                            }
                        }
                        tagName.equals("scale", ignoreCase = true) -> {
                            val factor = getSafeAttribute(parser, "factor")?.toFloatOrNull()
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
            Log.d(TAG, "逆向导入任务完美成功！")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入崩溃: ", e)
            progressFlow.value = progressFlow.value.copy(error = e.message, isFinished = true)
            false
        }
    }

    /**
     * 防御性属性提取函数：攻克二进制 XML 属性读取不到的隐蔽 Bug
     */
    private fun getSafeAttribute(parser: XmlPullParser, attrName: String): String? {
        // 1. 尝试常规方式取
        val value = parser.getAttributeValue(null, attrName)
        if (value != null) return value

        // 2. 循环遍历属性域肉搏匹配（解决 AAPT2 二进制混淆问题）
        val count = parser.attributeCount
        if (count != -1) {
            for (i in 0 until count) {
                if (parser.getAttributeName(i).equals(attrName, ignoreCase = true)) {
                    return parser.getAttributeValue(i)
                }
            }
        }
        return null
    }

    private fun extractDrawableToPrivateStorage(
        remoteContext: Context,
        remotePkg: String,
        drawableName: String?,
        fileName: String,
        outputDir: File
    ): String? {
        if (drawableName == null) return null
        val res = remoteContext.resources
        val sanitizedName = drawableName.substringAfterLast("/")

        // 攻克 mipmap 资源域缺陷：先查 drawable，失败再查 mipmap
        var resId = res.getIdentifier(sanitizedName, "drawable", remotePkg)
        if (resId == 0) {
            resId = res.getIdentifier(sanitizedName, "mipmap", remotePkg)
        }

        if (resId == 0) {
            Log.w(TAG, "未能找到资源标识符: $sanitizedName")
            return null
        }

        return try {
            val drawable = res.getDrawable(resId, remoteContext.theme)
            val bitmap = drawableToBitmap(drawable)

            // 确保多级父目录绝对存在
            val file = File(outputDir, "$fileName.png")
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "提取图片资源失败: $sanitizedName", e)
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        // 自适应图标（AdaptiveIconDrawable）强转 BitmapDrawable 必崩，强制走离屏 Canvas 绘制
        val width = if (drawable.intrinsicWidth <= 0) 192 else drawable.intrinsicWidth
        val height = if (drawable.intrinsicHeight <= 0) 192 else drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}