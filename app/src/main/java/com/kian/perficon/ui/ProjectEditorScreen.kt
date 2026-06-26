package com.kian.perficon.ui

import android.content.Intent
import android.app.DownloadManager
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.ui.components.*
import com.kian.perficon.util.AppInfo
import com.kian.perficon.util.ApkGenerator
import com.kian.perficon.util.DynamicIconAssets
import com.kian.perficon.util.DynamicIconDefaults
import com.kian.perficon.util.IconPackExporter
import com.kian.perficon.util.saveIconToInternalStorage
import com.kian.perficon.util.exportFileToDownloads
import com.kian.perficon.viewmodel.IconPackViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class NewIconInput(
    val iconName: String,
    val targetPackageName: String,
    val targetActivityName: String
)

private data class CalendarFrameTarget(
    val mapping: IconMapping,
    val day: Int
)

private data class ClockLayerTarget(
    val mapping: IconMapping,
    val layer: DynamicIconAssets.ClockLayer
)

private enum class CalendarFrameAction {
    Gallery,
    File,
    Reset
}

private enum class ClockLayerAction {
    Gallery,
    File,
    Reset
}

private fun defaultActivityName(packageName: String): String =
    packageName.trim().takeIf(String::isNotEmpty)?.let { "$it.MainActivity" }.orEmpty()

private fun packageTail(packageName: String): String =
    packageName.substringAfterLast(".").ifBlank { packageName }

private fun hasPackageConflict(
    packageName: String,
    mappingType: Int,
    mappings: List<IconMapping>,
    excludedMappingId: Long? = null
): Boolean {
    if (packageName.isBlank()) return false
    val isDynamic = mappingType != 0
    return mappings.any { existing ->
        existing.id != excludedMappingId &&
            existing.targetPackageName == packageName &&
            (if (isDynamic) existing.mappingType != 0 else existing.mappingType == 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    projectId: Long,
    viewModel: IconPackViewModel,
    onBack: () -> Unit,
    onNavigateToGenerator: (String, String, String, Boolean, Long) -> Unit
) {
    val mappings by remember(projectId) { viewModel.getMappingsForProject(projectId) }.collectAsState(initial = emptyList())
    val project by remember(projectId) { viewModel.getProjectById(projectId) }.collectAsState(initial = null)
    
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val currentLanguage = LocalAppLanguage.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(0) } 
    var isSearchOverlayVisible by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showUncoveredOnly by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var initialAddApp by remember { mutableStateOf<AppInfo?>(null) }
    var showProjectEditDialog by remember { mutableStateOf(false) }
    var showExportConfirmation by remember { mutableStateOf(false) }
    var showDynamicTypePicker by remember { mutableStateOf(false) }
    var showDefaultCalendarConfirmation by remember { mutableStateOf(false) }
    var isCreatingDefaultCalendar by remember { mutableStateOf(false) }
    var calendarToDelete by remember { mutableStateOf<IconMapping?>(null) }
    var calendarFrameTarget by remember { mutableStateOf<CalendarFrameTarget?>(null) }
    var showDefaultClockConfirmation by remember { mutableStateOf(false) }
    var isCreatingDefaultClock by remember { mutableStateOf(false) }
    var clockToDelete by remember { mutableStateOf<IconMapping?>(null) }
    var clockLayerTarget by remember { mutableStateOf<ClockLayerTarget?>(null) }
    var showCalendarEditor by remember { mutableStateOf(false) }
    var showClockEditor by remember { mutableStateOf(false) }
    var calendarMappingToEdit by remember { mutableStateOf<IconMapping?>(null) }
    var clockMappingToEdit by remember { mutableStateOf<IconMapping?>(null) }
    var exportProgress by remember { mutableStateOf<ApkGenerator.Progress?>(null) }
    var completedApk by remember { mutableStateOf<File?>(null) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var buildErrorDetails by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val dynamicCalendarEnabled = project?.useDynamicCalendar == true
    val dynamicClockEnabled = project?.useDynamicClock == true
    val hasDynamicCalendar = mappings.any { it.mappingType == 1 }
    val hasDynamicClock = mappings.any { it.mappingType == 2 }
    val canAddDynamicCalendar = dynamicCalendarEnabled && !hasDynamicCalendar && !isCreatingDefaultCalendar
    val canAddDynamicClock = dynamicClockEnabled && !hasDynamicClock && !isCreatingDefaultClock
    val visibleTabs = buildList {
        add(0)
        if (dynamicCalendarEnabled || dynamicClockEnabled) add(1)
        add(2)
    }

    LaunchedEffect(dynamicCalendarEnabled, dynamicClockEnabled) {
        if (selectedTab == 1 && 1 !in visibleTabs) selectedTab = 0
    }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            com.kian.perficon.util.getInstalledApps(context)
        }
    }

    val displayMappings = remember(mappings, selectedTab, isSearchActive, searchQuery, searchMode, dynamicCalendarEnabled, dynamicClockEnabled) {
        val type = when (selectedTab) {
            1 -> buildList {
                if (dynamicCalendarEnabled) add(1)
                if (dynamicClockEnabled) add(2)
            }
            else -> buildList {
                add(0)
                if (!dynamicCalendarEnabled) add(1)
                if (!dynamicClockEnabled) add(2)
            }
        }
        val baseList = mappings.filter { it.mappingType in type }
        
        if (!isSearchActive || searchQuery.isEmpty()) baseList
        else {
            baseList.filter { 
                val target = if (searchMode == 0) it.targetPackageName else it.displayName()
                target.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val uncoveredApps = remember(installedApps, mappings) {
        val covered = mappings.map { it.targetPackageName }.toSet()
        installedApps.filter { it.packageName !in covered }
    }

    var mappingToEditPkg by remember { mutableStateOf<IconMapping?>(null) }
    var mappingToChangeIcon by remember { mutableStateOf<IconMapping?>(null) }
    var mappingToDuplicate by remember { mutableStateOf<IconMapping?>(null) }
    var pendingNewIcon by remember { mutableStateOf<NewIconInput?>(null) }

    val addIconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val input = pendingNewIcon
        if (uri != null && input != null) {
            saveIconToInternalStorage(context, uri, "custom_${System.currentTimeMillis()}.png", projectId)?.let { path ->
                viewModel.insertMapping(projectId, input.iconName, input.targetPackageName, input.targetActivityName, path)
            }
        }
        pendingNewIcon = null
    }

    val changeIconLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mapping = mappingToChangeIcon ?: return@rememberLauncherForActivityResult
            saveIconToInternalStorage(context, it, "custom_${System.currentTimeMillis()}.png", projectId)?.let { path ->
                viewModel.updateMapping(mapping.copy(iconPath = path))
            }
        }
        mappingToChangeIcon = null
    }

    val calendarFrameLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = calendarFrameTarget
        if (uri != null && target != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    DynamicIconAssets.saveAsset(context, uri, projectId, "calendar_custom_${target.day}")
                }
                if (path == null) {
                    Toast.makeText(context, localize("无法保存日期图标", currentLanguage), Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateMapping(DynamicIconAssets.withCalendarFrame(target.mapping, target.day, path))
                }
            }
        }
        calendarFrameTarget = null
    }

    val clockLayerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = clockLayerTarget
        if (uri != null && target != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    DynamicIconAssets.saveAsset(context, uri, projectId, "clock_custom_${target.layer.resourceName}")
                }
                if (path == null) {
                    Toast.makeText(context, localize("无法保存时钟图层", currentLanguage), Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateMapping(DynamicIconAssets.withClockLayer(target.mapping, target.layer, path))
                }
            }
        }
        clockLayerTarget = null
    }

    fun buildAndShareApk() {
        scope.launch {
            val p = project ?: return@launch
            exportProgress = ApkGenerator.Progress(0, "正在启动构建")
            try {
                val safeFileName = p.name.replace("[^\\p{L}\\p{N}]".toRegex(), "_") + ".apk"
                val apkFile = withContext(Dispatchers.IO) {
                    val generated = ApkGenerator(context).generateApk(p, mappings) { progress ->
                        mainHandler.post { exportProgress = progress }
                    }
                    val exportedPath = exportFileToDownloads(context, generated, safeFileName)
                        ?: throw IllegalStateException("无法将 APK 保存到下载目录。")
                    generated
                }
                completedApk = apkFile
            } catch (e: Throwable) {
                Log.e("ProjectEditor", "Failed to build APK", e)
                val fullTrace = Log.getStackTraceString(e)
                val appLanguage = AppSettings(context).language.value
                val message = localize(e.message ?: e.toString(), appLanguage)
                mainHandler.post {
                    buildError = message
                    buildErrorDetails = fullTrace
                }
            } finally {
                exportProgress = null
            }
        }
    }

    Scaffold(
        topBar = {
            if ((isSearchActive && !isSearchOverlayVisible) || showUncoveredOnly) {
                CenterAlignedTopAppBar(
                    title = { Text(if (showUncoveredOnly) "未适配应用" else "搜索结果") },
                    navigationIcon = {
                        RetroIconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                                showUncoveredOnly = false
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Icon(Icons.Default.ArrowBack, null) }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(project?.name ?: "编辑器") },
                    navigationIcon = { RetroIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        RetroIconButton(onClick = { showProjectEditDialog = true }) { Icon(Icons.Default.Edit, localize("编辑项目", LocalAppLanguage.current)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroIconButton(onClick = { showExportConfirmation = true }) { Icon(Icons.Default.Build, localize("导出", LocalAppLanguage.current)) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                )
            }
        },
        floatingActionButton = {
            Box {
                if (showUncoveredOnly) {
                    RetroFAB(
                        onClick = { showUncoveredOnly = false },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                    ) { Icon(Icons.Default.Close, null) }
                } else if (isSearchActive) {
                    RetroFAB(
                        onClick = { isSearchActive = false; searchQuery = "" },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                    ) { Icon(Icons.Default.Close, null) }
                } else if (selectedTab == 0) {
                    FabMenu(
                        onAdd = {
                            initialAddApp = null
                            showAddDialog = true
                        },
                        onSearch = { isSearchOverlayVisible = true },
                        onStats = { showStatsDialog = true },
                        onUncovered = { showUncoveredOnly = true }
                    )
                } else if (selectedTab == 1) {
                    FabMenu(
                        onAdd = { showDynamicTypePicker = true },
                        onSearch = { isSearchOverlayVisible = true },
                        onStats = { showStatsDialog = true },
                        showAdd = canAddDynamicCalendar || canAddDynamicClock
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)) {
                visibleTabs.forEach { tab ->
                    val title = when (tab) {
                        0 -> "图标映射"
                        1 -> "动态"
                        else -> "样式"
                    }
                    Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }) { Text(title, Modifier.padding(12.dp)) }
                }
            }

            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f), modifier = Modifier.fillMaxWidth()) {
                    Text("正在搜索 \"$searchQuery\"", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            when (selectedTab) {
                0 -> {
                    if (showUncoveredOnly) {
                        UncoveredAppsGrid(uncoveredApps) { app ->
                            initialAddApp = app
                            showAddDialog = true
                        }
                    } else {
                        MappingGridWithScrollbar(displayMappings, { mappingToEditPkg = it }, { mappingToChangeIcon = it }, { mappingToDuplicate = it }, { viewModel.deleteMapping(it) }, { mappingToChangeIcon = it }, false) { mapping ->
                            project?.let { viewModel.updateProject(it.copy(projectIconPath = mapping.iconPath)) }
                        }
                    }
                }
                1 -> DynamicTabContent(
                    mappings = displayMappings,
                    showCalendar = dynamicCalendarEnabled,
                    showClock = dynamicClockEnabled,
                    onEdit = { mappingToEditPkg = it },
                    onChangeIcon = { mappingToChangeIcon = it },
                    onDuplicate = { mappingToDuplicate = it },
                    onDelete = { viewModel.deleteMapping(it) },
                    onEditDynamic = {},
                    onCalendarFrameAction = { mapping, day, action ->
                        when (action) {
                            CalendarFrameAction.Gallery -> {
                                calendarFrameTarget = CalendarFrameTarget(mapping, day)
                                calendarFrameLauncher.launch("image/*")
                            }
                            CalendarFrameAction.File -> {
                                calendarFrameTarget = CalendarFrameTarget(mapping, day)
                                calendarFrameLauncher.launch("*/*")
                            }
                            CalendarFrameAction.Reset -> {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) {
                                        DynamicIconAssets.restoreDefaultCalendarFrame(context, projectId, day)
                                    }
                                    if (path == null) {
                                        Toast.makeText(context, localize("无法恢复缺省日期图标", currentLanguage), Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.updateMapping(DynamicIconAssets.withCalendarFrame(mapping, day, path))
                                    }
                                }
                            }
                        }
                    },
                    onDeleteCalendar = { calendarToDelete = it },
                    onClockLayerAction = { mapping, layer, action ->
                        when (action) {
                            ClockLayerAction.Gallery -> {
                                clockLayerTarget = ClockLayerTarget(mapping, layer)
                                clockLayerLauncher.launch("image/*")
                            }
                            ClockLayerAction.File -> {
                                clockLayerTarget = ClockLayerTarget(mapping, layer)
                                clockLayerLauncher.launch("*/*")
                            }
                            ClockLayerAction.Reset -> {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) {
                                        DynamicIconAssets.restoreDefaultClockLayer(context, projectId, layer)
                                    }
                                    if (path == null) {
                                        Toast.makeText(context, localize("无法恢复缺省时钟图层", currentLanguage), Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.updateMapping(DynamicIconAssets.withClockLayer(mapping, layer, path))
                                    }
                                }
                            }
                        }
                    },
                    onDeleteClock = { clockToDelete = it },
                    onSetProjectIcon = { mapping ->
                        project?.let { viewModel.updateProject(it.copy(projectIconPath = mapping.iconPath)) }
                    }
                )
                2 -> GlobalSettings(project, { viewModel.updateProject(it) })
            }
        }

        if (isSearchOverlayVisible) SearchOverlay(searchQuery, searchMode, { searchQuery = it }, { searchMode = it }, { isSearchActive = true; isSearchOverlayVisible = false }, { isSearchOverlayVisible = false })

        if (showProjectEditDialog && project != null) {
            val currentProject = project!!
                AddProjectDialog(
                    title = "编辑项目",
                    initialName = currentProject.name,
                    initialPkg = currentProject.packageName,
                    initialIconPath = currentProject.projectIconPath,
                    initialDescription = currentProject.description ?: "",
                    showDynamicOptions = true,
                    initialUseDynamicCalendar = currentProject.useDynamicCalendar,
                    initialUseDynamicClock = currentProject.useDynamicClock,
                    confirmLabel = "保存",
                    onDismiss = { showProjectEditDialog = false },
                    onConfirm = { name, packageName, iconPath, description, useCalendar, useClock ->
                        viewModel.updateProject(
                            currentProject.copy(
                                name = name,
                                packageName = packageName,
                                projectIconPath = iconPath,
                                description = description,
                                useDynamicCalendar = useCalendar,
                                useDynamicClock = useClock
                            )
                        )
                        showProjectEditDialog = false
                    }
                )
        }

        if (showExportConfirmation) {
            RetroDialog(
                onDismissRequest = { showExportConfirmation = false }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("确认导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("将根据当前项目设置生成并签名图标包 APK。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { showExportConfirmation = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(onClick = {
                            showExportConfirmation = false
                            buildAndShareApk()
                        }) { Text("开始导出") }
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddIconDialog(
                existingMappings = mappings,
                initialApp = initialAddApp,
                onDismiss = { showAddDialog = false },
                onAddFromGallery = { input ->
                    pendingNewIcon = input
                    addIconLauncher.launch("image/*")
                    showAddDialog = false
                    initialAddApp = null
                },
                onAddFromFile = { input ->
                    pendingNewIcon = input
                    addIconLauncher.launch("*/*")
                    showAddDialog = false
                    initialAddApp = null
                },
                onAddViaGenerator = { input ->
                    onNavigateToGenerator(
                        input.iconName,
                        input.targetPackageName,
                        input.targetActivityName,
                        false,
                        0L
                    )
                    showAddDialog = false
                    initialAddApp = null
                }
            )
        }
        
        if (mappingToChangeIcon != null) {
            ChangeIconDialog(
                onDismiss = { mappingToChangeIcon = null },
                onFromFile = { changeIconLauncher.launch("*/*") },
                onFromGallery = { changeIconLauncher.launch("image/*") },
                onViaGenerator = {
                    val mapping = mappingToChangeIcon!!
                    onNavigateToGenerator(
                        mapping.displayName(),
                        mapping.targetPackageName,
                        mapping.targetActivityName,
                        true,
                        mapping.id
                    )
                    mappingToChangeIcon = null
                }
            )
        }

        if (mappingToEditPkg != null) {
            EditMappingDialog(mapping = mappingToEditPkg!!, existingMappings = mappings, onDismiss = { mappingToEditPkg = null }, onConfirm = { viewModel.updateMapping(it); mappingToEditPkg = null })
        }
        
        if (mappingToDuplicate != null) {
            DuplicateMappingDialog(
                mapping = mappingToDuplicate!!,
                existingMappings = mappings,
                onDismiss = { mappingToDuplicate = null },
                onConfirm = { newPkg, activity ->
                    val source = mappingToDuplicate!!
                    viewModel.insertMapping(
                        projectId,
                        source.iconName,
                        newPkg,
                        activity,
                        source.iconPath
                    )
                    mappingToDuplicate = null
                }
            )
        }
        
        if (showStatsDialog) StatsDialog(mappings, { showStatsDialog = false })

        exportProgress?.let { progress ->
            ExportProgressDialog(progress)
        }

        if (showDynamicTypePicker) {
            DynamicTypePickerDialog(
                showCalendar = canAddDynamicCalendar,
                showClock = canAddDynamicClock,
                onDismiss = { showDynamicTypePicker = false },
                onCalendar = { showDefaultCalendarConfirmation = true; showDynamicTypePicker = false },
                onClock = { showDefaultClockConfirmation = true; showDynamicTypePicker = false }
            )
        }

        if (showDefaultCalendarConfirmation) {
            RetroDialog(
                onDismissRequest = { showDefaultCalendarConfirmation = false }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("添加动态日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("将创建 1 至 31 日的缺省图标，并按 CandyBar 默认日历应用规则导出。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { showDefaultCalendarConfirmation = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(onClick = {
                            showDefaultCalendarConfirmation = false
                            isCreatingDefaultCalendar = true
                            scope.launch {
                                val frames = withContext(Dispatchers.IO) {
                                    DynamicIconAssets.createDefaultCalendarFrames(context, projectId)
                                }
                                isCreatingDefaultCalendar = false
                                if (frames == null) {
                                    Toast.makeText(context, localize("无法创建缺省动态日历图标", currentLanguage), Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.insertMapping(
                                        IconMapping(
                                            projectId = projectId,
                                            iconName = "动态日历",
                                            targetPackageName = DynamicIconDefaults.DEFAULT_CALENDAR_MAPPING_PACKAGE,
                                            targetActivityName = "",
                                            iconPath = frames.first(),
                                            mappingType = 1,
                                            extraInfo = DynamicIconAssets.calendarExtraInfo(frames)
                                        )
                                    )
                                }
                            }
                        }) { Text("确认添加") }
                    }
                }
            }
        }

        if (isCreatingDefaultCalendar) {
            RetroDialog(onDismissRequest = {}) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("正在创建动态日历", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        calendarToDelete?.let { calendar ->
            RetroDialog(
                onDismissRequest = { calendarToDelete = null }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("删除动态日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("将删除这组 1 至 31 日图标及其动态日历映射。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { calendarToDelete = null }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(
                            onClick = {
                                viewModel.deleteMapping(calendar)
                                calendarToDelete = null
                            },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) { Text("删除") }
                    }
                }
            }
        }

        if (showDefaultClockConfirmation) {
            RetroDialog(
                onDismissRequest = { showDefaultClockConfirmation = false }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("添加动态时钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("将创建缺省表盘与指针图层，并按图标包模板默认时钟应用规则导出。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { showDefaultClockConfirmation = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(onClick = {
                            showDefaultClockConfirmation = false
                            isCreatingDefaultClock = true
                            scope.launch {
                                val layers = withContext(Dispatchers.IO) {
                                    DynamicIconAssets.createDefaultClockLayers(context, projectId)
                                }
                                isCreatingDefaultClock = false
                                if (layers == null) {
                                    Toast.makeText(context, localize("无法创建缺省动态时钟图标", currentLanguage), Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.insertMapping(
                                        IconMapping(
                                            projectId = projectId,
                                            iconName = "动态时钟",
                                            targetPackageName = DynamicIconDefaults.DEFAULT_CLOCK_MAPPING_PACKAGE,
                                            targetActivityName = "",
                                            iconPath = layers.backgroundPath,
                                            mappingType = 2,
                                            extraInfo = DynamicIconAssets.clockExtraInfo(layers)
                                        )
                                    )
                                }
                            }
                        }) { Text("确认添加") }
                    }
                }
            }
        }

        if (isCreatingDefaultClock) {
            RetroDialog(onDismissRequest = {}) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("正在创建动态时钟", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        clockToDelete?.let { clock ->
            RetroDialog(
                onDismissRequest = { clockToDelete = null }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("删除动态时钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("将删除表盘、指针图层及其动态时钟映射。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { clockToDelete = null }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(
                            onClick = {
                                viewModel.deleteMapping(clock)
                                clockToDelete = null
                            },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) { Text("删除") }
                    }
                }
            }
        }

        if (showCalendarEditor) {
            CalendarDynamicEditorDialog(
                projectId = projectId,
                mapping = calendarMappingToEdit,
                existingMappings = mappings,
                onDismiss = { calendarMappingToEdit = null; showCalendarEditor = false },
                onConfirm = { mapping ->
                    if (mapping.id == 0L) viewModel.insertMapping(mapping) else viewModel.updateMapping(mapping)
                    calendarMappingToEdit = null
                    showCalendarEditor = false
                }
            )
        }

        if (showClockEditor) {
            ClockDynamicEditorDialog(
                projectId = projectId,
                mapping = clockMappingToEdit,
                existingMappings = mappings,
                onDismiss = { clockMappingToEdit = null; showClockEditor = false },
                onConfirm = { mapping ->
                    if (mapping.id == 0L) viewModel.insertMapping(mapping) else viewModel.updateMapping(mapping)
                    clockMappingToEdit = null
                    showClockEditor = false
                }
            )
        }

        completedApk?.let { apkFile ->
            ExportCompleteDialog(
                fileName = apkFile.name,
                onInstall = {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            Toast.makeText(context, localize("请授予 Perficon 安装应用的权限", currentLanguage), Toast.LENGTH_LONG).show()
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } else {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                apkFile
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, localize("无法打开系统安装器", currentLanguage), Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenLocation = {
                    try {
                        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (e: Exception) {
                        Toast.makeText(context, localize("无法打开下载目录", currentLanguage), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { completedApk = null }
            )
        }

        buildError?.let { errorMsg ->
            var showDetails by remember { mutableStateOf(false) }
            RetroDialog(
                onDismissRequest = {
                    buildError = null
                    buildErrorDetails = null
                }
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text("构建失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMsg, style = MaterialTheme.typography.bodyMedium)

                    buildErrorDetails?.let { details ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (showDetails) "隐藏详情" else "显示详情",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showDetails = !showDetails }
                        )
                        if (showDetails) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(max = 250.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroButton(onClick = {
                            buildError = null
                            buildErrorDetails = null
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportProgressDialog(progress: ApkGenerator.Progress) {
    RetroDialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("正在构建 APK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(progress.message, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { (progress.step.coerceIn(0, 5)) / 5f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("步骤 ${progress.step.coerceAtLeast(1)} / 5", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExportCompleteDialog(
    fileName: String,
    onInstall: () -> Unit,
    onOpenLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("导出完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("$fileName 已保存到下载目录")
            Spacer(modifier = Modifier.height(24.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RetroOutlinedButton(onClick = onOpenLocation) { Text("打开位置") }
                RetroOutlinedButton(onClick = onDismiss) { Text("关闭") }
                RetroButton(onClick = onInstall) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("立即安装")
                }
            }
        }
    }
}

@Composable
fun ChangeIconDialog(onDismiss: () -> Unit, onFromFile: () -> Unit, onFromGallery: () -> Unit, onViaGenerator: () -> Unit) {
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("更换图标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onFromGallery,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    ListItem(headlineContent = { Text("从相册选择") }, leadingContent = { Icon(Icons.Default.Photo, null) })
                }
                Surface(
                    onClick = onFromFile,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    ListItem(headlineContent = { Text("从文件选择") }, leadingContent = { Icon(Icons.Default.FileOpen, null) })
                }
                Surface(
                    onClick = onViaGenerator,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    ListItem(headlineContent = { Text("快速生成") }, leadingContent = { Icon(Icons.Default.AutoFixHigh, null) })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}

@Composable
private fun AddIconDialog(
    existingMappings: List<IconMapping>,
    initialApp: AppInfo?,
    onDismiss: () -> Unit,
    onAddFromGallery: (NewIconInput) -> Unit,
    onAddFromFile: (NewIconInput) -> Unit,
    onAddViaGenerator: (NewIconInput) -> Unit
) {
    var packageName by remember(initialApp) { mutableStateOf(initialApp?.packageName.orEmpty()) }
    var activityName by remember(initialApp) { mutableStateOf(initialApp?.activityName.orEmpty()) }
    var showAppPicker by remember { mutableStateOf(false) }
    val packageDuplicate = remember(packageName, existingMappings) {
        hasPackageConflict(packageName, 0, existingMappings)
    }
    val input = NewIconInput(
        "",
        packageName.trim(),
        activityName.trim().ifBlank { defaultActivityName(packageName) }
    )
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("添加图标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    packageName,
                    { packageName = it },
                    label = { Text("目标包名") },
                    supportingText = { if (packageDuplicate) Text("该包名已经有图标映射") },
                    isError = packageDuplicate || packageName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    activityName,
                    { activityName = it },
                    label = { Text("目标 Activity") },
                    placeholder = { Text(defaultActivityName(packageName)) },
                    modifier = Modifier.fillMaxWidth()
                )
                RetroOutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("从已安装应用填入包名")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroOutlinedButton({ onAddFromGallery(input) }, Modifier.weight(1f), enabled = packageName.isNotBlank() && !packageDuplicate) { Text("图库") }
                    RetroOutlinedButton({ onAddFromFile(input) }, Modifier.weight(1f), enabled = packageName.isNotBlank() && !packageDuplicate) { Text("文件") }
                }
                RetroButton({ onAddViaGenerator(input) }, Modifier.fillMaxWidth(), enabled = packageName.isNotBlank() && !packageDuplicate) { Text("快速生成") }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onDismiss) { Text("取消") }
            }
        }
    }

    if (showAppPicker) {
        AppPicker(
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                packageName = app.packageName
                activityName = app.activityName
                showAppPicker = false
            }
        )
    }
}

@Composable
fun DuplicateMappingDialog(mapping: IconMapping, existingMappings: List<IconMapping>, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputPkg by remember { mutableStateOf("") }
    var inputActivity by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var uncoveredApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var isManual by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val apps = com.kian.perficon.util.getInstalledApps(context)
        val covered = existingMappings.map { it.targetPackageName }.toSet()
        uncoveredApps = apps.filter { it.packageName !in covered }
    }
    val isDup = remember(inputPkg, selectedApp, isManual) {
        val t = if (isManual) inputPkg else selectedApp?.packageName ?: ""
        hasPackageConflict(t, 0, existingMappings)
    }
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("复制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(isManual, { isManual = true }); Text("手动填写", Modifier.clickable { isManual = true })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(!isManual, { isManual = false }); Text("未覆盖应用", Modifier.clickable { isManual = false })
                }
                Spacer(Modifier.height(16.dp))
                if (isManual) {
                    OutlinedTextField(inputPkg, { inputPkg = it }, label = { Text("目标包名") }, isError = isDup, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        inputActivity,
                        { inputActivity = it },
                        label = { Text("目标 Activity") },
                        placeholder = { Text(defaultActivityName(inputPkg)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(uncoveredApps) { app ->
                            Surface(
                                onClick = { selectedApp = app },
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                ListItem(
                                    headlineContent = { Text(app.name) },
                                    supportingContent = { Text(app.packageName) },
                                    leadingContent = { Image(app.icon.toBitmap().asImageBitmap(), null, Modifier.size(32.dp)) },
                                    trailingContent = { RadioButton(selectedApp == app, { selectedApp = app }) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                RetroButton(
                    onClick = {
                        onConfirm(
                            if (isManual) inputPkg else selectedApp!!.packageName,
                            if (isManual) inputActivity.ifBlank { defaultActivityName(inputPkg) } else selectedApp!!.activityName
                        )
                    },
                    enabled = !isDup && (if (isManual) inputPkg.isNotBlank() else selectedApp != null)
                ) { Text("确认") }
            }
        }
    }
}

@Composable
fun EditMappingDialog(title: String = "编辑", mapping: IconMapping, existingMappings: List<IconMapping>, onDismiss: () -> Unit, onConfirm: (IconMapping) -> Unit) {
    var pkg by remember { mutableStateOf(mapping.targetPackageName) }
    var activity by remember { mutableStateOf(mapping.targetActivityName) }
    val isDup = remember(pkg, existingMappings) {
        pkg != mapping.targetPackageName && hasPackageConflict(pkg, mapping.mappingType, existingMappings, mapping.id)
    }
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(pkg, { pkg = it }, label = { Text("包名") }, isError = isDup, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    activity,
                    { activity = it },
                    label = { Text("Activity") },
                    placeholder = { Text(defaultActivityName(pkg)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                RetroButton(
                    onClick = { onConfirm(mapping.copy(iconName = "", targetPackageName = pkg.trim(), targetActivityName = activity.trim().ifBlank { defaultActivityName(pkg) })) },
                    enabled = pkg.isNotBlank() && !isDup
                ) { Text("确认") }
            }
        }
    }
}

@Composable
private fun DynamicTypePickerDialog(
    showCalendar: Boolean,
    showClock: Boolean,
    onDismiss: () -> Unit,
    onCalendar: () -> Unit,
    onClock: () -> Unit
) {
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("添加动态图标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showCalendar) {
                    Surface(
                        onClick = onCalendar,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        ListItem(
                            headlineContent = { Text("动态日历") },
                            leadingContent = { Icon(Icons.Default.CalendarMonth, null) }
                        )
                    }
                }
                if (showClock) {
                    Surface(
                        onClick = onClock,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        ListItem(
                            headlineContent = { Text("动态时钟") },
                            leadingContent = { Icon(Icons.Default.AccessTime, null) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}

@Composable
private fun CalendarDynamicEditorDialog(
    projectId: Long,
    mapping: IconMapping?,
    existingMappings: List<IconMapping>,
    onDismiss: () -> Unit,
    onConfirm: (IconMapping) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var iconName by remember(mapping?.id) { mutableStateOf(mapping?.iconName.orEmpty()) }
    var packageName by remember(mapping?.id) { mutableStateOf(mapping?.targetPackageName.orEmpty()) }
    var activityName by remember(mapping?.id) { mutableStateOf(mapping?.targetActivityName.orEmpty()) }
    var frames by remember(mapping?.id) { mutableStateOf(mapping?.let(DynamicIconAssets::calendarFrames).orEmpty()) }
    var isGeneratingFrames by remember { mutableStateOf(false) }
    var frameGenerationError by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isGeneratingFrames = true
            frameGenerationError = null
            scope.launch {
                val generated = withContext(Dispatchers.IO) {
                    DynamicIconAssets.createCalendarFrames(context, uri, projectId)
                }
                isGeneratingFrames = false
                if (generated == null) {
                    frameGenerationError = "无法读取图片或生成日期图标，请换一张图片后重试。"
                } else {
                    frames = generated
                }
            }
        }
    }
    val packageDuplicate = remember(packageName, existingMappings, mapping?.id) {
        hasPackageConflict(packageName, 1, existingMappings, mapping?.id)
    }
    val canConfirm = iconName.isNotBlank() && packageName.isNotBlank() && !packageDuplicate && !isGeneratingFrames && frames.size == DynamicIconAssets.CALENDAR_DAY_COUNT

    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(if (mapping == null) "添加动态日历" else "编辑动态日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(iconName, { iconName = it }, label = { Text("图标名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    packageName,
                    { packageName = it },
                    label = { Text("目标包名") },
                    isError = packageDuplicate,
                    supportingText = { if (packageDuplicate) Text("该包名已经有其他动态图标映射") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    activityName,
                    { activityName = it },
                    label = { Text("目标 Activity") },
                    placeholder = { Text(defaultActivityName(packageName)) },
                    modifier = Modifier.fillMaxWidth()
                )
                RetroOutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Apps, null)
                    Spacer(Modifier.width(8.dp))
                    Text("从已安装应用填入包名")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    RetroOutlinedButton(onClick = { imageLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("图库") }
                    RetroOutlinedButton(onClick = { imageLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("文件") }
                }
                when {
                    isGeneratingFrames -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在生成 31 个日期图标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    frameGenerationError != null -> {
                        Text(frameGenerationError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    frames.size == DynamicIconAssets.CALENDAR_DAY_COUNT -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = File(frames.first()), contentDescription = null, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Text("已生成 ${frames.size} 个日期图标", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {
                        Text("请选择图片以生成日期图标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                RetroButton(
                    onClick = {
                        val base = mapping ?: IconMapping(projectId = projectId, targetPackageName = packageName.trim(), targetActivityName = "", iconPath = frames.first())
                        onConfirm(
                            base.copy(
                                iconName = iconName.trim(),
                                targetPackageName = packageName.trim(),
                                targetActivityName = activityName.trim().ifBlank { defaultActivityName(packageName) },
                                iconPath = frames.first(),
                                mappingType = 1,
                                extraInfo = DynamicIconAssets.calendarExtraInfo(frames)
                            )
                        )
                    },
                    enabled = canConfirm
                ) { Text("确认") }
            }
        }
    }

    if (showAppPicker) {
        AppPicker(
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                packageName = app.packageName
                activityName = app.activityName
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun ClockDynamicEditorDialog(
    projectId: Long,
    mapping: IconMapping?,
    existingMappings: List<IconMapping>,
    onDismiss: () -> Unit,
    onConfirm: (IconMapping) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storedLayers = remember(mapping?.id) { mapping?.let(DynamicIconAssets::clockLayers) }
    var iconName by remember(mapping?.id) { mutableStateOf(mapping?.iconName.orEmpty()) }
    var packageName by remember(mapping?.id) { mutableStateOf(mapping?.targetPackageName.orEmpty()) }
    var activityName by remember(mapping?.id) { mutableStateOf(mapping?.targetActivityName.orEmpty()) }
    var backgroundPath by remember(mapping?.id) { mutableStateOf(storedLayers?.backgroundPath.orEmpty()) }
    var hourPath by remember(mapping?.id) { mutableStateOf(storedLayers?.hourPath.orEmpty()) }
    var minutePath by remember(mapping?.id) { mutableStateOf(storedLayers?.minutePath.orEmpty()) }
    var secondPath by remember(mapping?.id) { mutableStateOf(storedLayers?.secondPath.orEmpty()) }
    var requestedLayer by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val layer = requestedLayer
        if (uri != null && layer != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    DynamicIconAssets.saveAsset(context, uri, projectId, "clock_$layer")
                }
                when (layer) {
                    "background" -> backgroundPath = path.orEmpty()
                    "hour" -> hourPath = path.orEmpty()
                    "minute" -> minutePath = path.orEmpty()
                    "second" -> secondPath = path.orEmpty()
                }
            }
        }
        requestedLayer = null
    }
    val packageDuplicate = remember(packageName, existingMappings, mapping?.id) {
        hasPackageConflict(packageName, 2, existingMappings, mapping?.id)
    }
    val canConfirm = iconName.isNotBlank() && packageName.isNotBlank() && !packageDuplicate && backgroundPath.isNotBlank() && hourPath.isNotBlank() && minutePath.isNotBlank()

    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(if (mapping == null) "添加动态时钟" else "编辑动态时钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(iconName, { iconName = it }, label = { Text("图标名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    packageName,
                    { packageName = it },
                    label = { Text("目标包名") },
                    isError = packageDuplicate,
                    supportingText = { if (packageDuplicate) Text("该包名已经有图标映射") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    activityName,
                    { activityName = it },
                    label = { Text("目标 Activity") },
                    placeholder = { Text(defaultActivityName(packageName)) },
                    modifier = Modifier.fillMaxWidth()
                )
                RetroOutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Apps, null)
                    Spacer(Modifier.width(8.dp))
                    Text("从已安装应用填入包名")
                }
                ClockLayerSelector("表盘", backgroundPath, { requestedLayer = "background"; imageLauncher.launch("image/*") }, { requestedLayer = "background"; imageLauncher.launch("*/*") })
                ClockLayerSelector("时针", hourPath, { requestedLayer = "hour"; imageLauncher.launch("image/*") }, { requestedLayer = "hour"; imageLauncher.launch("*/*") })
                ClockLayerSelector("分针", minutePath, { requestedLayer = "minute"; imageLauncher.launch("image/*") }, { requestedLayer = "minute"; imageLauncher.launch("*/*") })
                ClockLayerSelector("秒针", secondPath, { requestedLayer = "second"; imageLauncher.launch("image/*") }, { requestedLayer = "second"; imageLauncher.launch("*/*") })
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                RetroButton(
                    onClick = {
                        val layers = DynamicIconAssets.ClockLayers(backgroundPath, hourPath, minutePath, secondPath.ifBlank { minutePath })
                        val base = mapping ?: IconMapping(projectId = projectId, targetPackageName = packageName.trim(), targetActivityName = "", iconPath = backgroundPath)
                        onConfirm(
                            base.copy(
                                iconName = iconName.trim(),
                                targetPackageName = packageName.trim(),
                                targetActivityName = activityName.trim().ifBlank { defaultActivityName(packageName) },
                                iconPath = backgroundPath,
                                mappingType = 2,
                                extraInfo = DynamicIconAssets.clockExtraInfo(layers)
                            )
                        )
                    },
                    enabled = canConfirm
                ) { Text("确认") }
            }
        }
    }

    if (showAppPicker) {
        AppPicker(
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                packageName = app.packageName
                activityName = app.activityName
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun ClockLayerSelector(label: String, path: String, onGallery: () -> Unit, onFile: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (path.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(40.dp)
                ) {
                    AsyncImage(model = File(path), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            } else {
                Icon(Icons.Default.Image, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text(label, modifier = Modifier.weight(1f))
            IconButton(onClick = onGallery) { Icon(Icons.Default.Photo, localize("从图库选择", LocalAppLanguage.current)) }
            IconButton(onClick = onFile) { Icon(Icons.Default.FileOpen, localize("从文件选择", LocalAppLanguage.current)) }
        }
    }
}

@Composable
private fun DynamicTabContent(
    mappings: List<IconMapping>,
    showCalendar: Boolean,
    showClock: Boolean,
    onEdit: (IconMapping) -> Unit,
    onChangeIcon: (IconMapping) -> Unit,
    onDuplicate: (IconMapping) -> Unit,
    onDelete: (IconMapping) -> Unit,
    onEditDynamic: (IconMapping) -> Unit,
    onCalendarFrameAction: (IconMapping, Int, CalendarFrameAction) -> Unit,
    onDeleteCalendar: (IconMapping) -> Unit,
    onClockLayerAction: (IconMapping, DynamicIconAssets.ClockLayer, ClockLayerAction) -> Unit,
    onDeleteClock: (IconMapping) -> Unit,
    onSetProjectIcon: (IconMapping) -> Unit
) {
    val calendars = mappings.filter { it.mappingType == 1 }
    val clocks = mappings.filter { it.mappingType == 2 }
    if (calendars.isEmpty() && clocks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("还没有动态图标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("点击右下角添加动态日历或动态时钟。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showCalendar) {
            calendars.firstOrNull()?.let { calendar ->
                CalendarDynamicCard(
                    mapping = calendar,
                    onFrameAction = onCalendarFrameAction,
                    onDelete = onDeleteCalendar
                )
            } ?: DynamicTypeCard(
                title = "动态日历",
                subtitle = "按日期切换图标的应用",
                icon = Icons.Default.CalendarMonth,
                mappings = emptyList(),
                onEdit = onEdit,
                onChangeIcon = onChangeIcon,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onEditDynamic = onEditDynamic,
                onSetProjectIcon = onSetProjectIcon
            )
        }
        if (showClock) {
            clocks.firstOrNull()?.let { clock ->
                ClockDynamicCard(
                    mapping = clock,
                    onLayerAction = onClockLayerAction,
                    onDelete = onDeleteClock
                )
            } ?: DynamicTypeCard(
                title = "动态时钟",
                subtitle = "包含表盘与指针图层的图标",
                icon = Icons.Default.AccessTime,
                mappings = emptyList(),
                onEdit = onEdit,
                onChangeIcon = onChangeIcon,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onEditDynamic = onEditDynamic,
                onSetProjectIcon = onSetProjectIcon
            )
        }
    }
}

@Composable
private fun CalendarDynamicCard(
    mapping: IconMapping,
    onFrameAction: (IconMapping, Int, CalendarFrameAction) -> Unit,
    onDelete: (IconMapping) -> Unit
) {
    val frames = remember(mapping.iconPath, mapping.extraInfo) { DynamicIconAssets.calendarFrames(mapping) }
    val rowCount = (DynamicIconAssets.CALENDAR_DAY_COUNT + 3) / 4
    val calendarGridHeight = 98.dp * rowCount + 12.dp * (rowCount - 1) + 8.dp
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("动态日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("按日期切换图标的应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(mapping) }) {
                    Icon(Icons.Default.Delete, contentDescription = localize("删除动态日历", LocalAppLanguage.current), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
                modifier = Modifier.height(calendarGridHeight).fillMaxWidth()
            ) {
                items((1..DynamicIconAssets.CALENDAR_DAY_COUNT).toList(), key = { it }) { day ->
                    CalendarFrameItem(
                        day = day,
                        path = frames[day - 1],
                        onAction = { action -> onFrameAction(mapping, day, action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarFrameItem(
    day: Int,
    path: String,
    onAction: (CalendarFrameAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.height(98.dp).clickable { showMenu = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = localize("$day 日图标", LocalAppLanguage.current),
            modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
        Text("$day 日", style = MaterialTheme.typography.labelSmall)
        RetroDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("从图库更换") }, onClick = { showMenu = false; onAction(CalendarFrameAction.Gallery) })
            DropdownMenuItem(text = { Text("从文件更换") }, onClick = { showMenu = false; onAction(CalendarFrameAction.File) })
            DropdownMenuItem(text = { Text("恢复缺省图标") }, onClick = { showMenu = false; onAction(CalendarFrameAction.Reset) })
        }
    }
}

@Composable
private fun ClockDynamicCard(
    mapping: IconMapping,
    onLayerAction: (IconMapping, DynamicIconAssets.ClockLayer, ClockLayerAction) -> Unit,
    onDelete: (IconMapping) -> Unit
) {
    val layers = remember(mapping.iconPath, mapping.extraInfo) { DynamicIconAssets.clockLayers(mapping) }
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("动态时钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("按当前时间旋转指针", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(mapping) }) {
                    Icon(Icons.Default.Delete, contentDescription = localize("删除动态时钟", LocalAppLanguage.current), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
                modifier = Modifier.height(112.dp).fillMaxWidth()
            ) {
                items(DynamicIconAssets.ClockLayer.values().toList(), key = { it.name }) { layer ->
                    val path = when (layer) {
                        DynamicIconAssets.ClockLayer.Background -> layers.backgroundPath
                        DynamicIconAssets.ClockLayer.Hour -> layers.hourPath
                        DynamicIconAssets.ClockLayer.Minute -> layers.minutePath
                        DynamicIconAssets.ClockLayer.Second -> layers.secondPath
                    }
                    ClockLayerItem(
                        layer = layer,
                        path = path,
                        onAction = { action -> onLayerAction(mapping, layer, action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockLayerItem(
    layer: DynamicIconAssets.ClockLayer,
    path: String,
    onAction: (ClockLayerAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.height(104.dp).clickable { showMenu = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = layer.displayName,
            modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(4.dp))
        Text(layer.displayName, style = MaterialTheme.typography.labelSmall)
        RetroDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("从图库更换") }, onClick = { showMenu = false; onAction(ClockLayerAction.Gallery) })
            DropdownMenuItem(text = { Text("从文件更换") }, onClick = { showMenu = false; onAction(ClockLayerAction.File) })
            DropdownMenuItem(text = { Text("恢复缺省图层") }, onClick = { showMenu = false; onAction(ClockLayerAction.Reset) })
        }
    }
}

@Composable
private fun DynamicTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    mappings: List<IconMapping>,
    onEdit: (IconMapping) -> Unit,
    onChangeIcon: (IconMapping) -> Unit,
    onDuplicate: (IconMapping) -> Unit,
    onDelete: (IconMapping) -> Unit,
    onEditDynamic: (IconMapping) -> Unit,
    onSetProjectIcon: (IconMapping) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(mappings.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            if (mappings.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(28.dp), MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(10.dp))
                    Text("暂无此类图标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MappingGridFixed(mappings, onEdit, onChangeIcon, onDuplicate, onDelete, onEditDynamic, onSetProjectIcon)
            }
        }
    }
}

@Composable
fun MappingGridFixed(mappings: List<IconMapping>, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onEditDynamic: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit, enableDynamicActions: Boolean = true) {
    val rows = (mappings.size + 4) / 5
    Box(Modifier.height((rows * 110).dp).fillMaxWidth()) {
        LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
            items(mappings, key = { "${it.id}_${it.targetPackageName}" }) { MappingItemView(it, onEdit, onChangeIcon, onDuplicate, onDelete, onEditDynamic, onSetProjectIcon, enableDynamicActions) }
        }
    }
}

@Composable
fun MappingGridWithScrollbar(mappings: List<IconMapping>, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onEditDynamic: (IconMapping) -> Unit, enableDynamicActions: Boolean = true, onSetProjectIcon: (IconMapping) -> Unit) {
    if (mappings.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(84.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("还没有图标映射", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "点击右下角菜单添加图标或快速生成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(state = state, columns = GridCells.Fixed(5), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(mappings, key = { "${it.id}_${it.targetPackageName}" }) { MappingItemView(it, onEdit, onChangeIcon, onDuplicate, onDelete, onEditDynamic, onSetProjectIcon, enableDynamicActions) }
        }
        VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(2.dp))
    }
}

private fun saveImageToPictures(context: android.content.Context, path: String): Boolean {
    val source = File(path).takeIf(File::isFile) ?: return false
    val resolver = context.contentResolver
    val displayName = source.nameWithoutExtension.ifBlank { "perficon_asset" } + "_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Perficon")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

@Composable
fun UncoveredAppsGrid(apps: List<AppInfo>, onPick: (AppInfo) -> Unit) {
    if (apps.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("已适配全部已安装应用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        return
    }

    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier
                        .aspectRatio(0.7f)
                        .clickable { onPick(app) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(app.icon.toBitmap().asImageBitmap(), null, Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
                    Text(
                        packageTail(app.packageName),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(2.dp))
    }
}

@Composable
fun MappingItemView(mapping: IconMapping, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onEditDynamic: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit, enableDynamicActions: Boolean = true) {
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.aspectRatio(0.7f).clickable { showMenu = true }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AsyncImage(model = File(mapping.iconPath), null, Modifier.size(44.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
        Text(mapping.displayName(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        RetroDropdownMenu(showMenu, { showMenu = false }) {
            DropdownMenuItem(text = { Text("编辑信息") }, onClick = { showMenu = false; onEdit(mapping) })
            if (mapping.mappingType == 0 || !enableDynamicActions) {
                DropdownMenuItem(text = { Text("更换图标") }, onClick = { showMenu = false; onChangeIcon(mapping) })
            } else {
                DropdownMenuItem(text = { Text("编辑动态素材") }, onClick = { showMenu = false; onEditDynamic(mapping) })
            }
            DropdownMenuItem(text = { Text("设为项目图标") }, onClick = { showMenu = false; onSetProjectIcon(mapping) })
            if (mapping.mappingType == 0 || !enableDynamicActions) {
                DropdownMenuItem(text = { Text("复制") }, onClick = { showMenu = false; onDuplicate(mapping) })
            }
            DropdownMenuItem(text = { Text("删除", color = Color.Red) }, onClick = { showMenu = false; onDelete(mapping) })
        }
    }
}

private fun IconMapping.displayName(): String =
    iconName.ifBlank { packageTail(targetPackageName) }

@Composable
fun SearchOverlay(q: String, m: Int, onQ: (String) -> Unit, onM: (Int) -> Unit, onS: () -> Unit, onD: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Dialog(onD) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(24.dp), Arrangement.spacedBy(16.dp)) {
                Text("搜索图标", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(q, onQ, Modifier.fillMaxWidth().focusRequester(fr), placeholder = { Text("例如：音乐") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.large)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(m == 0, { onM(0) }); Text("包名", Modifier.clickable { onM(0) })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(m == 1, { onM(1) }); Text("图标名称", Modifier.clickable { onM(1) })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onD) { Text("取消") }
                    Button(onS, enabled = q.isNotBlank()) { Text("搜索") }
                }
            }
        }
    }
}

@Composable
fun FabMenu(onAdd: () -> Unit, onSearch: () -> Unit, onStats: () -> Unit, showAdd: Boolean = true, onUncovered: (() -> Unit)? = null) {
    var ex by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        if (ex) {
            RetroSmallFAB({ ex = false; onStats() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.PieChart, null) }
            RetroSmallFAB({ ex = false; onSearch() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Search, null) }
            if (onUncovered != null) {
                RetroSmallFAB({ ex = false; onUncovered() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Apps, null) }
            }
            if (showAdd) {
                RetroSmallFAB({ ex = false; onAdd() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Add, null) }
            }
        }
        RetroFAB({ ex = !ex }) { Icon(if (ex) Icons.Default.Close else Icons.Default.Menu, null) }
    }
}

@Composable
fun StatsDialog(mappings: List<IconMapping>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var installedCount by remember { mutableIntStateOf(0) }
    var coveredCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(mappings) {
        val apps = com.kian.perficon.util.getInstalledApps(context)
        installedCount = apps.size
        val pkgs = mappings.map { it.targetPackageName }.toSet()
        coveredCount = apps.count { it.packageName in pkgs }
    }
    RetroDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("统计信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("图标总数", mappings.size.toString())
                StatRow("已安装应用", installedCount.toString())
                StatRow("已覆盖", coveredCount.toString())
                StatRow("未覆盖", (installedCount - coveredCount).toString())
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (installedCount > 0) coveredCount.toFloat() / installedCount else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                RetroButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
fun StatRow(l: String, v: String) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(l); Text(v, fontWeight = FontWeight.Bold) } }

@Composable
fun GlobalSettings(project: IconPackProject?, onUpdate: (IconPackProject) -> Unit) {
    if (project == null) return
    val context = LocalContext.current
    var showBackMenu by remember { mutableStateOf(false) }
    var currentPickingType by remember { mutableStateOf<String?>(null) }
    val stylePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            saveIconToInternalStorage(context, it, "global_${System.currentTimeMillis()}.png", project.id)?.let { path ->
                when (currentPickingType) {
                    "mask" -> onUpdate(project.copy(iconMaskPath = path))
                    "upon" -> onUpdate(project.copy(iconUponPath = path))
                    "back" -> {
                        val current = project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
                        current.add(path)
                        onUpdate(project.copy(iconBackPaths = current.joinToString(",")))
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(24.dp)) {
        Text("样式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { Column(Modifier.padding(16.dp)) {
            Text("缩放：${(project.scaleFactor * 100).toInt()}%")
            Slider(value = project.scaleFactor, onValueChange = { onUpdate(project.copy(scaleFactor = it)) }, valueRange = 0.5f..1.5f)
        } }
        
        IconSettingItem(
            title = "蒙版",
            desc = "应用于所有图标的裁切形状",
            path = project.iconMaskPath,
            onPickFromGallery = { currentPickingType = "mask"; stylePickerLauncher.launch("image/*") },
            onPickFromFile = { currentPickingType = "mask"; stylePickerLauncher.launch("*/*") },
            onRemove = { onUpdate(project.copy(iconMaskPath = null)) },
            onDownload = { path ->
                val ok = saveImageToPictures(context, path)
                Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
            }
        )
        IconSettingItem(
            title = "叠层",
            desc = "显示在图标最上方的覆盖层",
            path = project.iconUponPath,
            onPickFromGallery = { currentPickingType = "upon"; stylePickerLauncher.launch("image/*") },
            onPickFromFile = { currentPickingType = "upon"; stylePickerLauncher.launch("*/*") },
            onRemove = { onUpdate(project.copy(iconUponPath = null)) },
            onDownload = { path ->
                val ok = saveImageToPictures(context, path)
                Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
            }
        )
        
        Text("背景", style = MaterialTheme.typography.titleMedium)
        project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.forEach { path ->
            IconSettingItem(
                title = "背景图片",
                desc = "图标背景",
                path = path,
                onPickFromGallery = {},
                onPickFromFile = {},
                onRemove = { 
                    val remaining = project.iconBackPaths.split(",").filter { it != path && it.isNotEmpty() }
                    onUpdate(project.copy(iconBackPaths = if(remaining.isEmpty()) null else remaining.joinToString(",")))
                },
                onDownload = { assetPath ->
                    val ok = saveImageToPictures(context, assetPath)
                    Toast.makeText(context, if (ok) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            RetroOutlinedButton(onClick = { showBackMenu = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加背景")
            }
            RetroDropdownMenu(expanded = showBackMenu, onDismissRequest = { showBackMenu = false }) {
                DropdownMenuItem(text = { Text("从图库选择") }, onClick = { showBackMenu = false; currentPickingType = "back"; stylePickerLauncher.launch("image/*") })
                DropdownMenuItem(text = { Text("从文件选择") }, onClick = { showBackMenu = false; currentPickingType = "back"; stylePickerLauncher.launch("*/*") })
            }
        }
    }
}

@Composable
fun IconSettingItem(
    title: String,
    desc: String,
    path: String?,
    onPickFromGallery: () -> Unit,
    onPickFromFile: () -> Unit,
    onRemove: () -> Unit,
    onDownload: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            if (path != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(model = File(path), null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                    RetroIconButton(onClick = { onDownload(path) }) { Icon(Icons.Default.Download, "下载") }
                    RetroIconButton(onClick = onRemove) { Icon(Icons.Default.Close, "移除", tint = MaterialTheme.colorScheme.error) }
                }
            } else {
                Box {
                    TextButton(onClick = { showMenu = true }) { Text("选择") }
                    RetroDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("从图库选择") }, onClick = { showMenu = false; onPickFromGallery() })
                        DropdownMenuItem(text = { Text("从文件选择") }, onClick = { showMenu = false; onPickFromFile() })
                    }
                }
            }
        }
    }
}
