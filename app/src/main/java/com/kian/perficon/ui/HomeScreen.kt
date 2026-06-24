package com.kian.perficon.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.viewmodel.IconPackViewModel
import com.kian.perficon.util.IconPackImporter
import com.kian.perficon.util.saveIconToInternalStorage
import com.kian.perficon.ui.components.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: IconPackViewModel,
    onNavigateToProject: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val projects by viewModel.allProjects.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<IconPackProject?>(null) }
    var showInstalledPacksDialog by remember { mutableStateOf(false) }
    var pendingInstalledPack by remember { mutableStateOf<IconPackImporter.IconPackInfo?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<IconPackProject?>(null) }
    var showWelcomeDialog by remember { mutableStateOf(viewModel.isFirstLaunch()) }
    
    // Progress Dialog State
    var showProgressDialog by remember { mutableStateOf(false) }
    val progressFlow = remember { MutableStateFlow(IconPackImporter.ImportProgress()) }
    val currentProgress by progressFlow.collectAsState()
    var importJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission Check
    var showPermissionRequest by remember { mutableStateOf(!com.kian.perficon.util.StorageHelper.isStorageManager()) }

    if (showPermissionRequest) {
        RetroDialog(
            onDismissRequest = { }
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("需要存储访问权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Perficon 需要“所有文件访问权限”来管理 /Perficon 中的图标包项目。")
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    RetroButton(onClick = { 
                        com.kian.perficon.util.StorageHelper.requestAllFilesAccess(context)
                        showPermissionRequest = false
                    }) {
                        Text("授权访问")
                    }
                }
            }
        }
    }

    if (showWelcomeDialog) {
        RetroDialog(
            onDismissRequest = {
                viewModel.setFirstLaunchCompleted()
                showWelcomeDialog = false
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PixelWolfAnimation(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "欢迎使用 Perficon ！",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "您可以选择完全从新创建图标包或者导入已安装的图标包！现在就开始你的图标包创作之旅吧！",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                RetroButton(
                    onClick = {
                        viewModel.setFirstLaunchCompleted()
                        showWelcomeDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始使用")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Perficon", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            RetroButton(
                onClick = { showBottomSheet = true }
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建项目")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (projects.isEmpty()) {
                EmptyState(onNewProject = { showBottomSheet = true })
            } else {
                Text(
                    "我的图标包",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ExpressiveProjectItem(
                            project = project,
                            onClick = { onNavigateToProject(project.id) },
                            onEdit = { showEditDialog = project },
                            onDuplicate = { viewModel.duplicateProject(project) },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(top = 12.dp).size(width = 36.dp, height = 4.dp),
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {}
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 8.dp)) {
                    Text("创建图标包", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    ListItem(
                        headlineContent = { Text("从零创建") },
                        supportingContent = { Text("从空白项目开始") },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null)
                                }
                            }
                        },
                        modifier = Modifier.clickable { showBottomSheet = false; showAddDialog = true }
                    )
                    ListItem(
                        headlineContent = { Text("导入已安装图标包") },
                        supportingContent = { Text("从设备中选择图标包") },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Apps, null)
                                }
                            }
                        },
                        modifier = Modifier.clickable { showBottomSheet = false; showInstalledPacksDialog = true }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddProjectDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, pkg, iconPath, description, _, _ ->
                    viewModel.insertProject(name, pkg, iconPath, description)
                    showAddDialog = false
                }
            )
        }

        if (showEditDialog != null) {
            AddProjectDialog(
                title = "编辑项目",
                initialName = showEditDialog!!.name,
                initialPkg = showEditDialog!!.packageName,
                initialIconPath = showEditDialog!!.projectIconPath,
                initialDescription = showEditDialog!!.description,
                confirmLabel = "保存",
                onDismiss = { showEditDialog = null },
                onConfirm = { name, pkg, iconPath, description, _, _ ->
                    viewModel.updateProject(showEditDialog!!.copy(name = name, packageName = pkg, projectIconPath = iconPath, description = description))
                    showEditDialog = null
                }
            )
        }

        val importedIconPath = remember(pendingInstalledPack) {
            pendingInstalledPack?.let { pack ->
                try {
                    val bitmap = pack.icon.toBitmap()
                    com.kian.perficon.util.saveBitmapToInternalStorage(context, bitmap, "project_icon_${System.currentTimeMillis()}.png")
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        if (pendingInstalledPack != null) {
            AddProjectDialog(
                title = "导入 ${pendingInstalledPack?.name}",
                initialName = pendingInstalledPack?.name ?: "",
                initialPkg = (pendingInstalledPack?.packageName ?: "") + "Imported",
                initialIconPath = importedIconPath,
                confirmLabel = "导入",
                onDismiss = { pendingInstalledPack = null },
                onConfirm = { name, pkg, iconPath, description, _, _ ->
                    val pack = pendingInstalledPack ?: return@AddProjectDialog
                    pendingInstalledPack = null
                    showProgressDialog = true
                    importJob = scope.launch {
                        viewModel.importFromInstalledApp(pack.packageName, name, pkg, iconPath, description, progressFlow)
                    }
                }
            )
        }

        if (showInstalledPacksDialog) {
            InstalledPacksDialog(viewModel = viewModel, onDismiss = { showInstalledPacksDialog = false }, onPackSelected = { pendingInstalledPack = it; showInstalledPacksDialog = false })
        }

        if (showProgressDialog) {
            ImportProgressDialog(
                progress = currentProgress,
                onDismiss = { 
                    showProgressDialog = false
                    importJob = null
                    progressFlow.value = IconPackImporter.ImportProgress() 
                },
                onCancel = {
                    importJob?.cancel()
                    importJob = null
                    showProgressDialog = false
                    progressFlow.value = IconPackImporter.ImportProgress()
                }
            )
        }

        if (projectToDelete != null) {
            RetroDialog(
                onDismissRequest = { projectToDelete = null }
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("删除项目？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("确定要删除“${projectToDelete?.name}”吗？此操作无法撤销。")
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RetroOutlinedButton(onClick = { projectToDelete = null }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        RetroButton(
                            onClick = {
                                val p = projectToDelete!!
                                projectToDelete = null
                                viewModel.deleteProject(p)
                            },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveProjectItem(
    project: IconPackProject,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                val projectIcon = project.projectIconPath?.let(::File)?.takeIf(File::isFile)
                if (projectIcon != null) {
                    AsyncImage(model = projectIcon, contentDescription = null, modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small))
                } else {
                    Text(project.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(text = project.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                RetroDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text("复制") }, onClick = { showMenu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                    Divider()
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@Composable
fun EmptyState(onNewProject: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("开始制作第一个图标包", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("轻松 design 并导出自定义图标。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        RetroButton(onClick = onNewProject, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) { Text("开始使用") }
    }
}

@Composable
fun AddProjectDialog(
    title: String = "新建图标包",
    initialName: String = "",
    initialPkg: String = "com.example.iconpack",
    initialIconPath: String? = null,
    initialDescription: String = "",
    showDynamicOptions: Boolean = false,
    initialUseDynamicCalendar: Boolean = true,
    initialUseDynamicClock: Boolean = true,
    confirmLabel: String = "创建",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var pkg by remember { mutableStateOf(initialPkg) }
    var iconPath by remember { mutableStateOf(initialIconPath) }
    var description by remember { mutableStateOf(initialDescription) }
    var useDynamicCalendar by remember { mutableStateOf(initialUseDynamicCalendar) }
    var useDynamicClock by remember { mutableStateOf(initialUseDynamicClock) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var pkgError by remember { mutableStateOf<String?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            iconPath = saveIconToInternalStorage(context, it, "project_icon_${System.currentTimeMillis()}.png")
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            iconPath = saveIconToInternalStorage(context, it, "project_icon_${System.currentTimeMillis()}.png")
        }
    }

    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; nameError = if (it.isBlank()) "必填" else null }, label = { Text("项目名称") }, isError = nameError != null, supportingText = nameError?.let { { Text(it) } }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pkg, onValueChange = { pkg = it; pkgError = if (!it.contains(".")) "包名格式无效" else null }, label = { Text("项目包名") }, isError = pkgError != null, supportingText = pkgError?.let { { Text(it) } }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("说明 / 备注") }, placeholder = { Text("说明 / 备注（可选）") }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconPath != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(40.dp)
                        ) {
                            AsyncImage(model = File(iconPath!!), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(if (iconPath == null) "未选择项目图标" else "已选择项目图标", style = MaterialTheme.typography.labelMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroOutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("从图库选择") }
                    RetroOutlinedButton(onClick = { fileLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("从文件选择") }
                }
                if (showDynamicOptions) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("使用动态日历") },
                        trailingContent = { Switch(checked = useDynamicCalendar, onCheckedChange = { useDynamicCalendar = it }) },
                        modifier = Modifier.clickable { useDynamicCalendar = !useDynamicCalendar }
                    )
                    ListItem(
                        headlineContent = { Text("使用动态时钟") },
                        trailingContent = { Switch(checked = useDynamicClock, onCheckedChange = { useDynamicClock = it }) },
                        modifier = Modifier.clickable { useDynamicClock = !useDynamicClock }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                RetroButton(
                    onClick = { if (name.isNotBlank() && pkg.contains(".")) onConfirm(name, pkg, iconPath, description, useDynamicCalendar, useDynamicClock) },
                    enabled = name.isNotBlank() && pkg.contains(".")
                ) { Text(confirmLabel) }
            }
        }
    }
}

@Composable
fun ImportProgressDialog(
    progress: IconPackImporter.ImportProgress,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    RetroDialog(
        onDismissRequest = { if (progress.isFinished) onDismiss() }
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(if (progress.isFinished) "导入完成" else "正在导入图标...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (progress.error != null) {
                Text("错误：${progress.error}", color = MaterialTheme.colorScheme.error)
            } else {
                LinearProgressIndicator(progress = { if (progress.totalItems > 0) progress.currentItem.toFloat() / progress.totalItems else 0f }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("已处理：${progress.currentItem} / ${progress.totalItems}")
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                ProgressStatusItem("图标蒙版", progress.hasMask)
                Spacer(modifier = Modifier.height(8.dp))
                ProgressStatusItem("图标叠层", progress.hasUpon)
                Spacer(modifier = Modifier.height(8.dp))
                ProgressStatusItem("背景：${progress.backCount}", progress.backCount > 0)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (progress.isFinished) {
                    RetroButton(onClick = onDismiss) {
                        Text("确定")
                    }
                } else {
                    RetroOutlinedButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressStatusItem(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (active) Icons.Default.CheckCircle else Icons.Default.FileUpload, null, tint = if (active) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (active) Color.Unspecified else MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun InstalledPacksDialog(viewModel: IconPackViewModel, onDismiss: () -> Unit, onPackSelected: (IconPackImporter.IconPackInfo) -> Unit) {
    val packs by viewModel.installedIconPacks.collectAsState()
    RetroDialog(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("已安装的图标包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (packs.isEmpty()) {
                Text("未找到图标包")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(packs) { pack ->
                        Surface(
                            onClick = { onPackSelected(pack) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(pack.name) },
                                supportingContent = { Text(pack.packageName) },
                                leadingContent = {
                                    Image(
                                        bitmap = pack.icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RetroOutlinedButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}
