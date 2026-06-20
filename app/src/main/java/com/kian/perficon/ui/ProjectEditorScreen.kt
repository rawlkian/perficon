package com.kian.perficon.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorScreen(
    projectId: Long,
    viewModel: IconPackViewModel,
    onBack: () -> Unit
) {
    val mappings by remember(projectId) { viewModel.getMappingsForProject(projectId) }.collectAsState(initial = emptyList())
    val project by remember(projectId) { viewModel.getProjectById(projectId) }.collectAsState(initial = null)
    
    var showAppPicker by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(0) } // 0: Package, 1: Icon Name
    var isSearchOverlayVisible by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    val displayMappings = remember(mappings, selectedTab, isSearchActive, searchQuery, searchMode) {
        val type = if (selectedTab == 1) 1 else 0
        val baseList = mappings.filter { it.mappingType == type }
        val distinctList = if (type == 0) baseList.distinctBy { it.targetPackageName } else baseList
        
        if (!isSearchActive || searchQuery.isEmpty()) distinctList
        else {
            distinctList.filter { 
                val target = if (searchMode == 0) it.targetPackageName else it.targetPackageName.split(".").last()
                target.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    var mappingToEditPkg by remember { mutableStateOf<IconMapping?>(null) }
    var mappingToChangeIcon by remember { mutableStateOf<IconMapping?>(null) }
    var mappingToDuplicate by remember { mutableStateOf<IconMapping?>(null) }

    var currentPickingType by remember { mutableStateOf<String?>(null) }
    val genericIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val p = project ?: return@rememberLauncherForActivityResult
            val path = saveIconToInternalStorage(context, it, "global_${System.currentTimeMillis()}.png")
            if (path != null) {
                when (currentPickingType) {
                    "mask" -> viewModel.updateProject(p.copy(iconMaskPath = path))
                    "upon" -> viewModel.updateProject(p.copy(iconUponPath = path))
                    "back" -> {
                        val backs = p.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
                        backs.add(path)
                        viewModel.updateProject(p.copy(iconBackPaths = backs.joinToString(",")))
                    }
                }
            }
        }
        currentPickingType = null
    }

    val mappingIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mapping = mappingToChangeIcon ?: return@rememberLauncherForActivityResult
            val path = saveIconToInternalStorage(context, it, "custom_${System.currentTimeMillis()}.png")
            if (path != null) viewModel.updateMapping(mapping.copy(iconPath = path))
        }
        mappingToChangeIcon = null
    }

    val newMappingIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val app = selectedApp ?: return@rememberLauncherForActivityResult
            val path = saveIconToInternalStorage(context, it, "${app.packageName}.png")
            if (path != null) viewModel.insertMapping(projectId, app.packageName, app.activityName, path)
        }
        selectedApp = null
    }

    fun buildAndShareApk() {
        scope.launch {
            val p = project ?: return@launch
            Toast.makeText(context, "Building APK...", Toast.LENGTH_SHORT).show()
            val generator = ApkGenerator(context)
            val apkFile = generator.generateApk(p, mappings) ?: return@launch
            exportFileToDownloads(context, apkFile, "${p.packageName}.apk")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share APK"))
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive && isSearchOverlayVisible.not()) {
                CenterAlignedTopAppBar(
                    title = { Text("Search Results") },
                    navigationIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.ArrowBack, null) } }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(project?.name ?: "Editor") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = { IconButton(onClick = { buildAndShareApk() }) { Icon(Icons.Default.Build, "Build") } }
                )
            }
        },
        floatingActionButton = {
            Box {
                if (isSearchActive) {
                    FloatingActionButton(
                        onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                    ) {
                        Icon(Icons.Default.Close, "Clear Search")
                    }
                } else if (selectedTab != 2) {
                    FabMenu({ showAppPicker = true }, { isSearchOverlayVisible = true }, { showStatsDialog = true })
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Mappings", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Dynamic", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Styles", Modifier.padding(12.dp)) }
            }

            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Results for \"$searchQuery\" (${if(searchMode==0) "Package" else "Name"})",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            when (selectedTab) {
                0, 1 -> MappingGridWithScrollbar(displayMappings, { mappingToEditPkg = it }, { mappingToChangeIcon = it; mappingIconPicker.launch("image/*") }, { mappingToDuplicate = it }, { viewModel.deleteMapping(it) })
                2 -> GlobalSettings(project, { 
                    currentPickingType = it
                    genericIconPicker.launch("image/*") 
                }, { viewModel.updateProject(it) }, { type, path ->
                    project?.let { p ->
                        when (type) {
                            "mask" -> viewModel.updateProject(p.copy(iconMaskPath = null))
                            "upon" -> viewModel.updateProject(p.copy(iconUponPath = null))
                            "back" -> {
                                val currentBacks = p.iconBackPaths?.split(",")?.filter { it != path && it.isNotEmpty() }
                                viewModel.updateProject(p.copy(iconBackPaths = if (currentBacks.isNullOrEmpty()) null else currentBacks.joinToString(",")))
                            }
                        }
                    }
                })
            }
        }

        if (isSearchOverlayVisible) {
            SearchOverlay(
                query = searchQuery,
                mode = searchMode,
                onQueryChange = { searchQuery = it },
                onModeChange = { searchMode = it },
                onSearch = { 
                    isSearchActive = true
                    isSearchOverlayVisible = false 
                },
                onDismiss = { isSearchOverlayVisible = false }
            )
        }

        if (showAppPicker) AppPicker({ showAppPicker = false }, { selectedApp = it; showAppPicker = false; newMappingIconPicker.launch("image/*") })
        if (mappingToEditPkg != null) EditMappingDialog(mapping = mappingToEditPkg!!, onDismiss = { mappingToEditPkg = null }, onConfirm = { viewModel.updateMapping(it); mappingToEditPkg = null })
        if (mappingToDuplicate != null) EditMappingDialog(title = "Duplicate Icon", mapping = mappingToDuplicate!!.copy(targetPackageName = ""), onDismiss = { mappingToDuplicate = null }, onConfirm = { viewModel.insertMapping(projectId, it.targetPackageName, it.targetActivityName, it.iconPath); mappingToDuplicate = null })
        if (showStatsDialog) StatsDialog(mappings, { showStatsDialog = false })
    }
}

@Composable
fun MappingGridWithScrollbar(
    mappings: List<IconMapping>,
    onEdit: (IconMapping) -> Unit,
    onChangeIcon: (IconMapping) -> Unit,
    onDuplicate: (IconMapping) -> Unit,
    onDelete: (IconMapping) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(mappings, key = { "${it.id}_${it.targetPackageName}" }) { mapping ->
                var showMenu by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clickable { showMenu = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = File(mapping.iconPath),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Set Package") }, onClick = { showMenu = false; onEdit(mapping) })
                            DropdownMenuItem(text = { Text("Change Icon") }, onClick = { showMenu = false; onChangeIcon(mapping) })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate(mapping) })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete(mapping) })
                        }
                    }
                    Text(
                        text = mapping.targetPackageName.split(".").last(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).fillMaxWidth()
                    )
                }
            }
        }
        
        VerticalScrollbar(
            gridState = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp, top = 8.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun SearchOverlay(
    query: String,
    mode: Int,
    onQueryChange: (String) -> Unit,
    onModeChange: (Int) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Search Icons", style = MaterialTheme.typography.headlineSmall)
                
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("e.g. mp3 or com.app") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )

                Column {
                    Text("Search by:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == 0, onClick = { onModeChange(0) })
                        Text("Package Name", Modifier.clickable { onModeChange(0) })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = mode == 1, onClick = { onModeChange(1) })
                        Text("Icon Name", Modifier.clickable { onModeChange(1) })
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onSearch, enabled = query.isNotBlank()) { Text("Search") }
                }
            }
        }
    }
}

@Composable
fun FabMenu(onAdd: () -> Unit, onSearch: () -> Unit, onStats: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        if (expanded) {
            SmallFloatingActionButton(onClick = { expanded = false; onStats() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.PieChart, null) }
            SmallFloatingActionButton(onClick = { expanded = false; onSearch() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Search, null) }
            SmallFloatingActionButton(onClick = { expanded = false; onAdd() }, Modifier.padding(bottom = 8.dp)) { Icon(Icons.Default.Add, null) }
        }
        FloatingActionButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.Close else Icons.Default.Menu, null) }
    }
}

@Composable
fun EditMappingDialog(title: String = "Edit Mapping", mapping: IconMapping, onDismiss: () -> Unit, onConfirm: (IconMapping) -> Unit) {
    var pkg by remember { mutableStateOf(mapping.targetPackageName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("Package Name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onConfirm(mapping.copy(targetPackageName = pkg)) }, enabled = pkg.isNotBlank()) { Text("Confirm") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Statistics") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow("Total Icons", mappings.size.toString())
            StatRow("Installed Apps", installedCount.toString())
            StatRow("Covered", coveredCount.toString())
            StatRow("Missing", (installedCount - coveredCount).toString())
            LinearProgressIndicator(progress = { if (installedCount > 0) coveredCount.toFloat() / installedCount else 0f }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun StatRow(l: String, v: String) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(l); Text(v, fontWeight = FontWeight.Bold) } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSettings(project: IconPackProject?, onPick: (String) -> Unit, onUpdate: (IconPackProject) -> Unit, onRemove: (String, String) -> Unit) {
    if (project == null) return
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Uncovered Apps Strategy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Icon Scale: ${(project.scaleFactor * 100).toInt()}%")
            Slider(value = project.scaleFactor, onValueChange = { onUpdate(project.copy(scaleFactor = it)) }, valueRange = 0.5f..1.5f)
        } }
        IconSettingItem("Mask", "Clip shape", project.iconMaskPath, { onPick("mask") }, { t, p -> onRemove(t, p) })
        IconSettingItem("Upon", "Overlay", project.iconUponPath, { onPick("upon") }, { t, p -> onRemove(t, p) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backgrounds", style = MaterialTheme.typography.titleMedium)
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.forEach { path ->
                    Box {
                        AsyncImage(model = File(path), contentDescription = null, modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                        IconButton({ onRemove("back", path) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Surface(onClick = { onPick("back") }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(80.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
fun IconSettingItem(t: String, d: String, p: String?, onP: () -> Unit, onR: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(t, fontWeight = FontWeight.Bold); Text(d, style = MaterialTheme.typography.bodySmall) }
        if (p != null) Box {
            AsyncImage(model = File(p), null, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
            IconButton({ onR(t, p) }, Modifier.align(Alignment.TopEnd).size(20.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) { Icon(Icons.Default.Delete, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error) }
        } else FilledTonalButton(onP) { Text("Select") }
    } }
}
