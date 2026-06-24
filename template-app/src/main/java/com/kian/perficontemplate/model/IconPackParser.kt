package com.kian.perficontemplate.model

import android.content.Context
import android.util.Xml

const val RESOURCE_PACKAGE_NAME = "com.kian.perficontemplate"

/**
 * Parses the assets/appfilter.xml and assets/pack_meta.xml files that are
 * injected by Perficon's ApkGenerator at export time.
 *
 * appfilter.xml format (written by generateAppFilter + name attribute):
 *   <item component="ComponentInfo{pkg/activity}" drawable="icon_N" name="Icon Name" />
 *   <calendar component="..." prefix="calendar_N_" />
 *   <item component="..." drawable="clock_dynamic_N" name="..." />
 *   <dynamic-clock drawable="clock_dynamic_N" ... />
 *
 * pack_meta.xml format:
 *   <pack><name>Pack Name</name><description>Description text</description></pack>
 */
data class IconEntry(
    val drawableName: String,   // e.g. "icon_1"
    val iconName: String,       // display name
    val packageName: String,    // e.g. "com.android.chrome"
    val drawableResId: Int      // resolved via resources.getIdentifier
)

data class DynamicEntry(
    val type: DynamicType,
    val drawablePrefix: String, // "calendar_N_" or "" for clocks
    val drawableName: String,   // for clock: "clock_dynamic_N"
    val iconName: String,
    val packageName: String
)

enum class DynamicType { CALENDAR, CLOCK }

data class PackMeta(
    val name: String,
    val description: String
)

object IconPackParser {

    fun parseMeta(context: Context): PackMeta {
        val defaultAppName = try {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        } catch (_: Exception) {
            "Icon Pack"
        }
        return try {
            context.assets.open("pack_meta.xml").use { stream ->
                val parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                var name = defaultAppName
                var description = ""
                var inName = false; var inDesc = false
                var eventType = parser.eventType
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> {
                            inName = parser.name == "name"
                            inDesc = parser.name == "description"
                        }
                        org.xmlpull.v1.XmlPullParser.TEXT -> {
                            if (inName) name = parser.text ?: name
                            if (inDesc) description = parser.text ?: ""
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> { inName = false; inDesc = false }
                    }
                    eventType = parser.next()
                }
                PackMeta(name, description)
            }
        } catch (e: Exception) {
            android.util.Log.e("IconPackParser", "Error parsing meta", e)
            PackMeta(
                name = defaultAppName,
                description = ""
            )
        }
    }

    fun parseIcons(context: Context): Pair<List<IconEntry>, List<DynamicEntry>> {
        val icons = mutableListOf<IconEntry>()
        val dynamics = mutableListOf<DynamicEntry>()
        val calendarComponents = mutableSetOf<String>()
        val clockComponents = mutableSetOf<String>()

        try {
            context.assets.open("appfilter.xml").use { stream ->
                val parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
                var eventType = parser.eventType
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> {
                                val component = parser.getAttributeValue(null, "component") ?: ""
                                val drawable  = parser.getAttributeValue(null, "drawable") ?: continue
                                val name      = parser.getAttributeValue(null, "name") ?: drawable
                                val pkg       = extractPackage(component)
                                val resId     = context.resources.getIdentifier(drawable, "drawable", context.packageName)
                                if (resId != 0 && pkg !in calendarComponents && pkg !in clockComponents) {
                                    icons += IconEntry(drawable, name, pkg, resId)
                                }
                            }
                            "calendar" -> {
                                val component = parser.getAttributeValue(null, "component") ?: ""
                                val prefix    = parser.getAttributeValue(null, "prefix") ?: ""
                                val pkg       = extractPackage(component)
                                calendarComponents += pkg
                                // Remove the static item that was already added for this component
                                icons.removeAll { it.packageName == pkg }
                                val name = icons.firstOrNull { it.packageName == pkg }?.iconName ?: pkg.substringAfterLast(".")
                                dynamics += DynamicEntry(DynamicType.CALENDAR, prefix, "", name, pkg)
                            }
                            "dynamic-clock" -> {
                                val drawable = parser.getAttributeValue(null, "drawable") ?: ""
                                // Find the matching icon entry
                                val entry = icons.firstOrNull { it.drawableName == drawable }
                                if (entry != null) {
                                    clockComponents += entry.packageName
                                    icons.removeAll { it.drawableName == drawable }
                                    dynamics += DynamicEntry(DynamicType.CLOCK, "", drawable, entry.iconName, entry.packageName)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("IconPackParser", "Error parsing icons", e)
        }

        return icons to dynamics
    }

    private fun extractPackage(component: String): String {
        // ComponentInfo{pkg/activity} → pkg
        val inner = component.removePrefix("ComponentInfo{").removeSuffix("}")
        return inner.substringBefore("/")
    }
}
