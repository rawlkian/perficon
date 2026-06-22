package com.kian.perficon.ui

import android.content.Intent
import android.app.DownloadManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
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
import com.kian.perficon.ui.components.VerticalScrollbar
import com.kian.perficon.util.AppInfo
import com.kian.perficon.util.ApkGenerator
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

private fun defaultActivityName(packageName: String): String =
    packageName.trim().takeIf(String::isNotEmpty)?.let { "$it.MainActivity" }.orEmpty()

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
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(0) } 
    var isSearchOverlayVisible by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<ApkGenerator.Progress?>(null) }
    var completedApk by remember { mutableStateOf<File?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val displayMappings = remember(mappings, selectedTab, isSearchActive, searchQuery, searchMode) {
        val type = when(selectedTab) {
            1 -> listOf(1, 2)
            else -> listOf(0)
        }
        val baseList = mappings.filter { it.mappingType in type }
        val distinctList = if (selectedTab == 0) baseList.distinctBy { it.targetPackageName } else baseList
        
        if (!isSearchActive || searchQuery.isEmpty()) distinctList
        else {
            distinctList.filter { 
                val target = if (searchMode == 0) it.targetPackageName else it.displayName()
                target.contains(searchQuery, ignoreCase = true)
            }
        }
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

    fun buildAndShareApk() {
        scope.launch {
            val p = project ?: return@launch
            exportProgress = ApkGenerator.Progress(0, "正在启动Build")
            try {
                val safeFileName = p.name.replace("[^a-zA-Z0-9]".toRegex(), "_") + ".apk"
                val apkFile = withContext(Dispatchers.IO) {
                    ApkGenerator(context).generateApk(p, mappings) { progress ->
                        mainHandler.post { exportProgress = progress }
                    }.also { apk ->
                        checkNotNull(exportFileToDownloads(context, apk, safeFileName))
                    }
                }
                completedApk = apkFile
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "APK Build失败。", Toast.LENGTH_LONG).show()
            } finally {
                exportProgress = null
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive && !isSearchOverlayVisible) {
                CenterAlignedTopAppBar(
                    title = { Text("Search结果") },
                    navigationIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, null) } }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(project?.name ?: "编辑器") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = { IconButton(onClick = { buildAndShareApk() }) { Icon(Icons.Default.Build, "导出") } }
                )
            }
        },
        floatingActionButton = {
            Box {
                if (isSearchActive) {
                    FloatingActionButton(
                        onClick = { isSearchActive = false; searchQuery = "" },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                    ) { Icon(Icons.Default.Close, null) }
                } else if (selectedTab != 2) {
                    FabMenu({ showAddDialog = true }, { isSearchOverlayVisible = true }, { showStatsDialog = true })
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("图标映射", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("动态", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("样式", Modifier.padding(12.dp)) }
            }

            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f), modifier = Modifier.fillMaxWidth()) {
                    Text("正在Search \"$searchQuery\"", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            when (selectedTab) {
                0 -> MappingGridWithScrollbar(displayMappings, { mappingToEditPkg = it }, { mappingToChangeIcon = it }, { mappingToDuplicate = it }, { viewModel.deleteMapping(it) }) { mapping ->
                    project?.let { viewModel.updateProject(it.copy(projectIconPath = mapping.iconPath)) }
                }
                1 -> DynamicTabContent(displayMappings, { mappingToEditPkg = it }, { mappingToChangeIcon = it }, { mappingToDuplicate = it }, { viewModel.deleteMapping(it) }) { mapping ->
                    project?.let { viewModel.updateProject(it.copy(projectIconPath = mapping.iconPath)) }
                }
                2 -> GlobalSettings(project, { viewModel.updateProject(it) })
            }
        }

        if (isSearchOverlayVisible) SearchOverlay(searchQuery, searchMode, { searchQuery = it }, { searchMode = it }, { isSearchActive = true; isSearchOverlayVisible = false }, { isSearchOverlayVisible = false })
        
        if (showAddDialog) {
            AddIconDialog(
                existingMappings = mappings,
                onDismiss = { showAddDialog = false },
                onAddFromGallery = { input ->
                    pendingNewIcon = input
                    addIconLauncher.launch("image/*")
                    showAddDialog = false
                },
                onAddFromFile = { input ->
                    pendingNewIcon = input
                    addIconLauncher.launch("*/*")
                    showAddDialog = false
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

        completedApk?.let { apkFile ->
            ExportCompleteDialog(
                fileName = apkFile.name,
                onInstall = {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开系统安装器", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenLocation = {
                    try {
                        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开下载目录", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { completedApk = null }
            )
        }
    }
}

@Composable
private fun ExportProgressDialog(progress: ApkGenerator.Progress) {
    Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("正在Build APK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { (progress.step.coerceIn(0, 5)) / 5f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("步骤 ${progress.step.coerceAtLeast(1)} / 5", style = MaterialTheme.typography.labelMedium)
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出完成") },
        text = { Text("$fileName 已保存到下载目录") },
        confirmButton = {
            Button(onClick = onInstall) {
                Icon(Icons.Default.InstallMobile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("安装")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenLocation) { Text("打开位置") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
fun ChangeIconDialog(onDismiss: () -> Unit, onFromFile: () -> Unit, onFromGallery: () -> Unit, onViaGenerator: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换图标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(headlineContent = { Text("从相册选择") }, leadingContent = { Icon(Icons.Default.Photo, null) }, modifier = Modifier.clickable { onFromGallery() })
                ListItem(headlineContent = { Text("从文件选择") }, leadingContent = { Icon(Icons.Default.FileOpen, null) }, modifier = Modifier.clickable { onFromFile() })
                ListItem(headlineContent = { Text("Fast Gen器") }, leadingContent = { Icon(Icons.Default.AutoFixHigh, null) }, modifier = Modifier.clickable { onViaGenerator() })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddIconDialog(
    existingMappings: List<IconMapping>,
    onDismiss: () -> Unit,
    onAddFromGallery: (NewIconInput) -> Unit,
    onAddFromFile: (NewIconInput) -> Unit,
    onAddViaGenerator: (NewIconInput) -> Unit
) {
    var iconName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var activityName by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    val packageDuplicate = remember(packageName, existingMappings) {
        packageName.isNotBlank() && existingMappings.any { it.targetPackageName == packageName }
    }
    val input = NewIconInput(
        iconName.trim(),
        packageName.trim(),
        activityName.trim().ifBlank { defaultActivityName(packageName) }
    )
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("添加图标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    iconName,
                    { iconName = it },
                    label = { Text("图标名称") },
                    isError = iconName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
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
                OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("从已安装应用填入包名")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onAddFromGallery(input) }, Modifier.weight(1f), enabled = iconName.isNotBlank() && packageName.isNotBlank() && !packageDuplicate) { Text("图库") }
                    OutlinedButton({ onAddFromFile(input) }, Modifier.weight(1f), enabled = iconName.isNotBlank() && packageName.isNotBlank() && !packageDuplicate) { Text("文件") }
                }
                Button({ onAddViaGenerator(input) }, Modifier.fillMaxWidth(), enabled = iconName.isNotBlank() && packageName.isNotBlank() && !packageDuplicate) { Text("快速生成") }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )

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
        t.isNotEmpty() && existingMappings.any { it.targetPackageName == t }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("复制") },
        text = {
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
                }
                else LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(uncoveredApps) { app ->
                        ListItem(headlineContent = { Text(app.name) }, supportingContent = { Text(app.packageName) },
                            leadingContent = { Image(app.icon.toBitmap().asImageBitmap(), null, Modifier.size(32.dp)) },
                            trailingContent = { RadioButton(selectedApp == app, { selectedApp = app }) },
                            modifier = Modifier.clickable { selectedApp = app })
                    }
                }
            }
        }, confirmButton = { Button({ onConfirm(if (isManual) inputPkg else selectedApp!!.packageName, if (isManual) inputActivity.ifBlank { defaultActivityName(inputPkg) } else selectedApp!!.activityName) }, enabled = !isDup && (if(isManual) inputPkg.isNotBlank() else selectedApp != null)) { Text("确认") } }
    )
}

@Composable
fun EditMappingDialog(title: String = "编辑", mapping: IconMapping, existingMappings: List<IconMapping>, onDismiss: () -> Unit, onConfirm: (IconMapping) -> Unit) {
    var iconName by remember { mutableStateOf(mapping.iconName) }
    var pkg by remember { mutableStateOf(mapping.targetPackageName) }
    var activity by remember { mutableStateOf(mapping.targetActivityName) }
    val isDup = remember(pkg, existingMappings) { pkg != mapping.targetPackageName && existingMappings.any { it.targetPackageName == pkg } }
    AlertDialog(onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(iconName, { iconName = it }, label = { Text("图标名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pkg, { pkg = it }, label = { Text("包名") }, isError = isDup, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    activity,
                    { activity = it },
                    label = { Text("Activity") },
                    placeholder = { Text(defaultActivityName(pkg)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button({ onConfirm(mapping.copy(iconName = iconName.trim(), targetPackageName = pkg.trim(), targetActivityName = activity.trim().ifBlank { defaultActivityName(pkg) })) }, enabled = pkg.isNotBlank() && !isDup) { Text("确认") } }
    )
}

@Composable
fun DynamicTabContent(mappings: List<IconMapping>, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit) {
    val calendars = mappings.filter { it.mappingType == 1 }
    val clocks = mappings.filter { it.mappingType == 2 }
    if (calendars.isEmpty() && clocks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(72.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("还没有动态图标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("导入包含动态日历或动态时钟的图标包后，会在这里按类型整理。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DynamicTypeCard(
            title = "动态日历",
            subtitle = "按日期切换图标的应用",
            icon = Icons.Default.CalendarMonth,
            mappings = calendars,
            onEdit = onEdit,
            onChangeIcon = onChangeIcon,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            onSetProjectIcon = onSetProjectIcon
        )
        DynamicTypeCard(
            title = "动态时钟",
            subtitle = "包含表盘与指针图层的图标",
            icon = Icons.Default.AccessTime,
            mappings = clocks,
            onEdit = onEdit,
            onChangeIcon = onChangeIcon,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            onSetProjectIcon = onSetProjectIcon
        )
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
    onSetProjectIcon: (IconMapping) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                MappingGridFixed(mappings, onEdit, onChangeIcon, onDuplicate, onDelete, onSetProjectIcon)
            }
        }
    }
}

@Composable
fun MappingGridFixed(mappings: List<IconMapping>, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit) {
    val rows = (mappings.size + 4) / 5
    Box(Modifier.height((rows * 110).dp).fillMaxWidth()) {
        LazyVerticalGrid(columns = GridCells.Fixed(5), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
            items(mappings, key = { "${it.id}_${it.targetPackageName}" }) { MappingItemView(it, onEdit, onChangeIcon, onDuplicate, onDelete, onSetProjectIcon) }
        }
    }
}

@Composable
fun MappingGridWithScrollbar(mappings: List<IconMapping>, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit) {
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(state = state, columns = GridCells.Fixed(5), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(mappings, key = { "${it.id}_${it.targetPackageName}" }) { MappingItemView(it, onEdit, onChangeIcon, onDuplicate, onDelete, onSetProjectIcon) }
        }
        VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(2.dp))
    }
}

@Composable
fun MappingItemView(mapping: IconMapping, onEdit: (IconMapping) -> Unit, onChangeIcon: (IconMapping) -> Unit, onDuplicate: (IconMapping) -> Unit, onDelete: (IconMapping) -> Unit, onSetProjectIcon: (IconMapping) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.aspectRatio(0.7f).clickable { showMenu = true }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AsyncImage(model = File(mapping.iconPath), null, Modifier.size(44.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
        Text(mapping.displayName(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        DropdownMenu(showMenu, { showMenu = false }) {
            DropdownMenuItem(text = { Text("设置Package") }, onClick = { showMenu = false; onEdit(mapping) })
            DropdownMenuItem(text = { Text("更换图标") }, onClick = { showMenu = false; onChangeIcon(mapping) })
            DropdownMenuItem(text = { Text("设为项目图标") }, onClick = { showMenu = false; onSetProjectIcon(mapping) })
            DropdownMenuItem(text = { Text("复制") }, onClick = { showMenu = false; onDuplicate(mapping) })
            DropdownMenuItem(text = { Text("删除", color = Color.Red) }, onClick = { showMenu = false; onDelete(mapping) })
        }
    }
}

private fun IconMapping.displayName(): String =
    iconName.ifBlank { targetPackageName.substringAfterLast(".") }

@Composable
fun SearchOverlay(q: String, m: Int, onQ: (String) -> Unit, onM: (Int) -> Unit, onS: () -> Unit, onD: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Dialog(onD) {
        Card(shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(24.dp), Arrangement.spacedBy(16.dp)) {
                Text("Search图标", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(q, onQ, Modifier.fillMaxWidth().focusRequester(fr), placeholder = { Text("e.g. mp3") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.large)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(m == 0, { onM(0) }); Text("包名", Modifier.clickable { onM(0) })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(m == 1, { onM(1) }); Text("Name", Modifier.clickable { onM(1) })
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
fun FabMenu(onAdd: () -> Unit, onSearch: () -> Unit, onStats: () -> Unit) {
    var ex by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        if (ex) {
            SmallFloatingActionButton({ ex = false; onStats() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.PieChart, null) }
            SmallFloatingActionButton({ ex = false; onSearch() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Search, null) }
            SmallFloatingActionButton({ ex = false; onAdd() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Add, null) }
        }
        FloatingActionButton({ ex = !ex }) { Icon(if (ex) Icons.Default.Close else Icons.Default.Menu, null) }
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
    AlertDialog(onDismiss, title = { Text("统计信息") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow("图标总数", mappings.size.toString()); StatRow("已安装应用", installedCount.toString()); StatRow("已覆盖", coveredCount.toString()); StatRow("未覆盖", (installedCount - coveredCount).toString())
            LinearProgressIndicator(progress = { if (installedCount > 0) coveredCount.toFloat() / installedCount else 0f }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("关闭") } })
}

@Composable
fun StatRow(l: String, v: String) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(l); Text(v, fontWeight = FontWeight.Bold) } }

@Composable
fun GlobalSettings(project: IconPackProject?, onUpdate: (IconPackProject) -> Unit) {
    if (project == null) return
    val context = LocalContext.current
    
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
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Scale: ${(project.scaleFactor * 100).toInt()}%")
            Slider(value = project.scaleFactor, onValueChange = { onUpdate(project.copy(scaleFactor = it)) }, valueRange = 0.5f..1.5f)
        } }
        
        IconSettingItem("蒙版", "Clip shape for all icons", project.iconMaskPath, { currentPickingType = "mask"; stylePickerLauncher.launch("image/*") }, { onUpdate(project.copy(iconMaskPath = null)) })
        IconSettingItem("叠层", "Top-most layer overlay", project.iconUponPath, { currentPickingType = "upon"; stylePickerLauncher.launch("image/*") }, { onUpdate(project.copy(iconUponPath = null)) })
        
        Text("背景", style = MaterialTheme.typography.titleMedium)
        project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.forEach { path ->
            IconSettingItem("背景图片", "图标背景", path, {}, { 
                val remaining = project.iconBackPaths.split(",").filter { it != path && it.isNotEmpty() }
                onUpdate(project.copy(iconBackPaths = if(remaining.isEmpty()) null else remaining.joinToString(",")))
            })
        }
        OutlinedButton(onClick = { currentPickingType = "back"; stylePickerLauncher.launch("image/*") }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加背景")
        }
    }
}

@Composable
fun IconSettingItem(title: String, desc: String, path: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            if (path != null) {
                Box {
                    AsyncImage(model = File(path), null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                    IconButton(onRemove, Modifier.align(Alignment.TopEnd).size(20.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                        Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                TextButton(onPick) { Text("选择") }
            }
        }
    }
}
