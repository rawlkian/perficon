package com.kian.perficon.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.kian.perficon.model.IconMapping
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.min

/** Persists the per-frame assets that the fixed CandyBar dynamic-resource slots consume. */
object DynamicIconAssets {
    const val CALENDAR_DAY_COUNT = 31
    private const val MAX_DYNAMIC_ICON_EDGE = 1024

    data class ClockLayers(
        val backgroundPath: String,
        val hourPath: String,
        val minutePath: String,
        val secondPath: String
    )

    enum class ClockLayer(val resourceName: String, val displayName: String) {
        Background("bg", "表盘"),
        Hour("hour", "时针"),
        Minute("minute", "分针"),
        Second("second", "秒针")
    }

    fun calendarFrames(mapping: IconMapping): List<String> {
        val stored = runCatching {
            val values = JSONObject(mapping.extraInfo.orEmpty()).optJSONArray("calendarFrames") ?: JSONArray()
            List(values.length()) { values.optString(it) }
        }.getOrDefault(emptyList())
        return (0 until CALENDAR_DAY_COUNT).map { index ->
            stored.getOrNull(index)?.takeIf { it.isNotBlank() } ?: mapping.iconPath
        }
    }

    fun calendarExtraInfo(framePaths: List<String>): String {
        require(framePaths.size == CALENDAR_DAY_COUNT) { "A dynamic calendar needs 31 day images." }
        return JSONObject().put("calendarFrames", JSONArray(framePaths)).toString()
    }

    fun clockLayers(mapping: IconMapping): ClockLayers {
        val data = runCatching { JSONObject(mapping.extraInfo.orEmpty()) }.getOrNull()
        val background = data?.optString("backgroundPath").orEmpty().ifBlank {
            data?.optString("face").orEmpty().ifBlank { mapping.iconPath }
        }
        val hour = data?.optString("hourPath").orEmpty().ifBlank {
            data?.optString("hour").orEmpty().ifBlank { background }
        }
        val minute = data?.optString("minutePath").orEmpty().ifBlank {
            data?.optString("minute").orEmpty().ifBlank { hour }
        }
        val second = data?.optString("secondPath").orEmpty().ifBlank {
            data?.optString("second").orEmpty().ifBlank { minute }
        }
        return ClockLayers(background, hour, minute, second)
    }

    fun clockExtraInfo(layers: ClockLayers): String = JSONObject().apply {
        put("backgroundPath", layers.backgroundPath)
        put("hourPath", layers.hourPath)
        put("minutePath", layers.minutePath)
        put("secondPath", layers.secondPath)
    }.toString()

    fun saveAsset(context: Context, uri: Uri, projectId: Long, prefix: String): String? =
        saveIconToInternalStorage(context, uri, "${prefix}_${System.currentTimeMillis()}.png", projectId)

    fun createDefaultCalendarFrames(context: Context, projectId: Long): List<String>? {
        val outputDir = StorageHelper.getProjectIconsDir(projectId)
        val token = System.currentTimeMillis()
        val outputFiles = (1..CALENDAR_DAY_COUNT).associateWith { day ->
            File(outputDir, "calendar_default_${token}_$day.png")
        }
        // Find the first calendar slot that has all 31 days in the template.
        // Template uses non-sequential slots (e.g. 10-16); we must detect which
        // slot actually has complete resources rather than assuming slot 1.
        val slot = detectFirstCompleteCalendarSlot(context) ?: return null.also { outputFiles.values.forEach(File::delete) }
        return try {
            context.assets.open("base.apk").use { asset ->
                ZipInputStream(asset).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        CALENDAR_BY_SLOT_RESOURCE.matchEntire(entry.name.substringAfterLast('/'))
                            ?.let { match ->
                                val s = match.groupValues[1].toIntOrNull()
                                val day = match.groupValues[2].toIntOrNull()
                                if (s == slot && day != null && day in 1..CALENDAR_DAY_COUNT) {
                                    FileOutputStream(outputFiles.getValue(day)).use(zip::copyTo)
                                }
                            }
                        entry = zip.nextEntry
                    }
                }
            }
            val frames = (1..CALENDAR_DAY_COUNT).map { outputFiles.getValue(it) }
            if (frames.all(File::isFile)) frames.map(File::getAbsolutePath) else {
                frames.forEach(File::delete)
                null
            }
        } catch (_: Exception) {
            outputFiles.values.forEach(File::delete)
            null
        }
    }

    private fun detectFirstCompleteCalendarSlot(context: Context): Int? {
        return try {
            context.assets.open("base.apk").use { asset ->
                ZipInputStream(asset).use { zip ->
                    val slotDays = mutableMapOf<Int, MutableSet<Int>>()
                    var entry = zip.nextEntry
                    while (entry != null) {
                        DEFAULT_CALENDAR_RESOURCE.matchEntire(entry.name.substringAfterLast('/'))
                            ?.let { match ->
                                val slot = match.groupValues[1].toIntOrNull()
                                val day = match.groupValues[2].toIntOrNull()
                                if (slot != null && day != null && day in 1..CALENDAR_DAY_COUNT) {
                                    slotDays.getOrPut(slot) { mutableSetOf() }.add(day)
                                }
                            }
                        entry = zip.nextEntry
                    }
                    slotDays.entries.firstOrNull { it.value.size == CALENDAR_DAY_COUNT }?.key
                }
            }
        } catch (_: Exception) { null }
    }

    fun restoreDefaultCalendarFrame(context: Context, projectId: Long, day: Int): String? {
        require(day in 1..CALENDAR_DAY_COUNT)
        val output = File(StorageHelper.getProjectIconsDir(projectId), "calendar_default_${System.currentTimeMillis()}_$day.png")
        // Detect the first calendar slot with complete 31-day resources in the template.
        val slot = detectFirstCompleteCalendarSlot(context) ?: return null
        return try {
            context.assets.open("base.apk").use { asset ->
                ZipInputStream(asset).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.substringAfterLast('/') == "calendar_${slot}_${day}.png") {
                            FileOutputStream(output).use(zip::copyTo)
                            return output.absolutePath
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            null
        } catch (_: Exception) {
            output.delete()
            null
        }
    }

    fun createDefaultClockLayers(context: Context, projectId: Long): ClockLayers? {
        val layers = ClockLayer.values().associateWith { layer ->
            copyTemplateResource(context, projectId, "clock_1_${layer.resourceName}.png", "clock_default_${layer.resourceName}")
        }
        val background = layers[ClockLayer.Background] ?: return null
        val hour = layers[ClockLayer.Hour] ?: return null
        val minute = layers[ClockLayer.Minute] ?: return null
        val second = layers[ClockLayer.Second] ?: return null
        return ClockLayers(background, hour, minute, second)
    }

    fun restoreDefaultClockLayer(context: Context, projectId: Long, layer: ClockLayer): String? =
        copyTemplateResource(context, projectId, "clock_1_${layer.resourceName}.png", "clock_default_${layer.resourceName}")

    fun withClockLayer(mapping: IconMapping, layer: ClockLayer, path: String): IconMapping {
        val current = clockLayers(mapping)
        val updated = when (layer) {
            ClockLayer.Background -> current.copy(backgroundPath = path)
            ClockLayer.Hour -> current.copy(hourPath = path)
            ClockLayer.Minute -> current.copy(minutePath = path)
            ClockLayer.Second -> current.copy(secondPath = path)
        }
        return mapping.copy(iconPath = updated.backgroundPath, extraInfo = clockExtraInfo(updated))
    }

    fun withCalendarFrame(mapping: IconMapping, day: Int, path: String): IconMapping {
        require(day in 1..CALENDAR_DAY_COUNT)
        val frames = calendarFrames(mapping).toMutableList()
        frames[day - 1] = path
        return mapping.copy(iconPath = frames.first(), extraInfo = calendarExtraInfo(frames))
    }

    /**
     * Generates a complete date sequence from one chosen calendar artwork. The day badge makes
     * the sequence useful immediately, while users can still replace the source later.
     */
    fun createCalendarFrames(context: Context, uri: Uri, projectId: Long): List<String>? {
        val decoded = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
        val source = decoded.scaleForDynamicIcon()
        if (source !== decoded) decoded.recycle()
        val outputDir = StorageHelper.getProjectIconsDir(projectId)
        val token = System.currentTimeMillis()
        val created = mutableListOf<File>()
        return try {
            (1..CALENDAR_DAY_COUNT).map { day ->
                val bitmap = renderCalendarDay(source, day)
                val output = File(outputDir, "calendar_${token}_$day.png")
                FileOutputStream(output).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Unable to save calendar image." }
                }
                bitmap.recycle()
                created += output
                output.absolutePath
            }
        } catch (_: Exception) {
            created.forEach(File::delete)
            null
        } finally {
            source.recycle()
        }
    }

    private fun renderCalendarDay(source: Bitmap, day: Int): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val size = min(result.width, result.height).toFloat()
        val radius = size * 0.145f
        val margin = size * 0.07f
        val centerX = result.width - margin - radius
        val centerY = result.height - margin - radius
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 24, 31, 45) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = radius * if (day >= 10) 1.05f else 1.25f
        }
        canvas.drawCircle(centerX, centerY, radius, badge)
        val baseline = centerY - (text.ascent() + text.descent()) / 2f
        canvas.drawText(day.toString(), centerX, baseline, text)
        return result
    }

    private fun Bitmap.scaleForDynamicIcon(): Bitmap {
        val largestEdge = maxOf(width, height)
        if (largestEdge <= MAX_DYNAMIC_ICON_EDGE) return this
        val factor = MAX_DYNAMIC_ICON_EDGE.toFloat() / largestEdge
        return Bitmap.createScaledBitmap(this, (width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), true)
    }

    private fun copyTemplateResource(context: Context, projectId: Long, resourceName: String, filePrefix: String): String? {
        val output = File(StorageHelper.getProjectIconsDir(projectId), "${filePrefix}_${System.currentTimeMillis()}.png")
        return try {
            context.assets.open("base.apk").use { asset ->
                ZipInputStream(asset).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.substringAfterLast('/') == resourceName) {
                            FileOutputStream(output).use(zip::copyTo)
                            return output.absolutePath
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            null
        } catch (_: Exception) {
            output.delete()
            null
        }
    }

    private val DEFAULT_CALENDAR_RESOURCE = Regex("calendar_(\\d+)_(\\d+)\\.png")
    private val CALENDAR_BY_SLOT_RESOURCE = Regex("calendar_(\\d+)_(\\d+)\\.png")
}
