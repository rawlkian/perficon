package com.kian.perficontemplate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kian.perficontemplate.model.DynamicEntry
import com.kian.perficontemplate.model.DynamicType
import com.kian.perficontemplate.model.IconEntry

@Composable
private fun dynamicStringResource(name: String, default: String): String {
    val context = LocalContext.current
    return remember(name) {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else default
    }
}

data class IconMappingInfo(
    val label: String,
    val packageName: String
)

data class UnifiedIcon(
    val id: String,
    val drawableName: String,
    val iconName: String,
    val isDynamic: Boolean,
    val dynamicType: String? = null, // "calendar" or "clock"
    val resId: Int,
    val mappings: List<IconMappingInfo>
)

@Composable
fun IconsScreen(icons: List<IconEntry>, dynamics: List<DynamicEntry>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Cache bitmaps by resId to save memory and avoid re-decoding
    val bitmapCache = remember { mutableStateMapOf<Int, ImageBitmap?>() }
    
    var selectedIconForDetail by remember { mutableStateOf<UnifiedIcon?>(null) }

    val unifiedList = remember(icons, dynamics) {
        val list = mutableListOf<UnifiedIcon>()
        
        // Group static icons by drawableResId to deduplicate
        val staticGroups = icons.groupBy { it.drawableResId }
        staticGroups.forEach { (resId, entries) ->
            val firstEntry = entries.first()
            list += UnifiedIcon(
                id = "static_${resId}",
                drawableName = firstEntry.drawableName,
                iconName = firstEntry.iconName,
                isDynamic = false,
                resId = resId,
                mappings = entries.map { IconMappingInfo(it.iconName, it.packageName) }
            )
        }
        
        // Group calendar dynamics by prefix
        val calendarGroups = dynamics.filter { it.type == DynamicType.CALENDAR }.groupBy { it.drawablePrefix }
        calendarGroups.forEach { (prefix, entries) ->
            val firstEntry = entries.first()
            val previewName = "${prefix}1"
            val resId = context.resources.getIdentifier(previewName, "drawable", context.packageName)
            if (resId != 0) {
                list += UnifiedIcon(
                    id = "calendar_${prefix}",
                    drawableName = prefix,
                    iconName = firstEntry.iconName,
                    isDynamic = true,
                    dynamicType = "calendar",
                    resId = resId,
                    mappings = entries.map { IconMappingInfo(it.iconName, it.packageName) }
                )
            }
        }
        
        // Group clock dynamics by drawableName
        val clockGroups = dynamics.filter { it.type == DynamicType.CLOCK }.groupBy { it.drawableName }
        clockGroups.forEach { (drawableName, entries) ->
            val firstEntry = entries.first()
            val slotNum = drawableName.removePrefix("clock_dynamic_")
            val bgResName = "clock_${slotNum}_bg"
            val resId = context.resources.getIdentifier(bgResName, "drawable", context.packageName)
            if (resId != 0) {
                list += UnifiedIcon(
                    id = "clock_${drawableName}",
                    drawableName = drawableName,
                    iconName = firstEntry.iconName,
                    isDynamic = true,
                    dynamicType = "clock",
                    resId = resId,
                    mappings = entries.map { IconMappingInfo(it.iconName, it.packageName) }
                )
            }
        }
        
        list.sortedBy { it.iconName }
    }

    val filtered = remember(unifiedList, query) {
        if (query.isBlank()) unifiedList
        else unifiedList.filter { icon ->
            icon.iconName.contains(query, ignoreCase = true) ||
            icon.mappings.any { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Search bar ───────────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(dynamicStringResource("search_hint", "搜索图标或应用包名..."), style = MaterialTheme.typography.bodyMedium) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; focusManager.clearFocus() }) {
                            Text("✕", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
            )
        }

        // Icon count
        val countFormat = dynamicStringResource("icons_count_summary", "%1\$d / %2\$d 个图标")
        val countText = remember(filtered.size, unifiedList.size, countFormat) {
            runCatching { String.format(countFormat, filtered.size, unifiedList.size) }
                .getOrDefault("${filtered.size} / ${unifiedList.size} 个图标")
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ── Icon grid ────────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", style = MaterialTheme.typography.headlineLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(dynamicStringResource("no_results", "未找到结果"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { entry ->
                    IconCell(
                        entry = entry,
                        bitmap = bitmapCache.getOrPut(entry.resId) {
                            runCatching {
                                context.resources.getDrawable(entry.resId, null)
                                    .toBitmap(96, 96).asImageBitmap()
                            }.getOrNull()
                        },
                        onClick = { selectedIconForDetail = entry }
                    )
                }
            }
        }
    }

    // Detail Dialog
    if (selectedIconForDetail != null) {
        val icon = selectedIconForDetail!!
        AlertDialog(
            onDismissRequest = { selectedIconForDetail = null },
            confirmButton = {
                TextButton(onClick = { selectedIconForDetail = null }) {
                    Text(dynamicStringResource("dialog_ok", "确定"))
                }
            },
            title = {
                Text(
                    text = icon.iconName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = dynamicStringResource("dialog_mappings_title", "关联应用包名："),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    icon.mappings.forEach { mapping ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = mapping.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mapping.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun IconCell(entry: UnifiedIcon, bitmap: ImageBitmap?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = entry.iconName,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entry.isDynamic) {
                val badge = if (entry.dynamicType == "calendar") "📅" else "⏰"
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.iconName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
