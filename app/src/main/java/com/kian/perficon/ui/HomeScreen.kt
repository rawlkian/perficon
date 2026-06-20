package com.kian.perficon.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.viewmodel.IconPackViewModel
import com.kian.perficon.util.IconPackImporter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: IconPackViewModel,
    onNavigateToProject: (Long) -> Unit
) {
    val projects by viewModel.allProjects.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showInstalledPacksDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingInstalledPack by remember { mutableStateOf<IconPackImporter.IconPackInfo?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<IconPackProject?>(null) }
    
    // Progress Dialog State
    var showProgressDialog by remember { mutableStateOf(false) }
    val progressFlow = remember { MutableStateFlow(IconPackImporter.ImportProgress()) }
    val currentProgress by progressFlow.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "Perficon",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBottomSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (projects.isEmpty()) {
                EmptyState(onNewProject = { showBottomSheet = true })
            } else {
                Text(
                    "My Icon Packs",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            ExpressiveProjectItem(
                                project = project,
                                onClick = { onNavigateToProject(project.id) },
                                onDelete = { projectToDelete = project }
                            )
                        }
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                ) {
                    Text(
                        "Create Icon Pack",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                    
                    ListItem(
                        headlineContent = { Text("Create from Scratch") },
                        supportingContent = { Text("Start with a blank project and add icons manually") },
                        leadingContent = { 
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            showBottomSheet = false
                            showAddDialog = true
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Import from Installed App") },
                        supportingContent = { Text("Pick an icon pack already installed on your device") },
                        leadingContent = { 
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Apps, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            showBottomSheet = false
                            showInstalledPacksDialog = true
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddProjectDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, pkg ->
                    viewModel.insertProject(name, pkg)
                    showAddDialog = false
                }
            )
        }

        if (pendingInstalledPack != null) {
            AddProjectDialog(
                title = "Import ${pendingInstalledPack?.name}",
                confirmLabel = "Import",
                onDismiss = { pendingInstalledPack = null },
                onConfirm = { name, pkg ->
                    val packToImport = pendingInstalledPack ?: return@AddProjectDialog
                    pendingInstalledPack = null
                    showProgressDialog = true
                    scope.launch {
                        viewModel.importFromInstalledApp(
                            packToImport.packageName, 
                            name, 
                            pkg, 
                            progressFlow
                        )
                    }
                }
            )
        }

        if (showInstalledPacksDialog) {
            InstalledPacksDialog(
                viewModel = viewModel,
                onDismiss = { showInstalledPacksDialog = false },
                onPackSelected = { pack ->
                    pendingInstalledPack = pack
                    showInstalledPacksDialog = false
                }
            )
        }

        if (showProgressDialog) {
            ImportProgressDialog(
                progress = currentProgress,
                onDismiss = { 
                    showProgressDialog = false
                    progressFlow.value = IconPackImporter.ImportProgress() // Reset
                }
            )
        }

        if (projectToDelete != null) {
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text("Delete Project?") },
                text = { Text("Are you sure you want to delete '${projectToDelete?.name}'? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            projectToDelete?.let { viewModel.deleteProject(it) }
                            projectToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Cancel")
                    }
                },
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    }
}

@Composable
fun ImportProgressDialog(
    progress: IconPackImporter.ImportProgress,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (progress.isFinished) onDismiss() },
        title = { Text(if (progress.isFinished) "Import Finished" else "Importing Icons...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (progress.error != null) {
                    Text("Error: ${progress.error}", color = MaterialTheme.colorScheme.error)
                } else {
                    val percent = if (progress.totalItems > 0) progress.currentItem.toFloat() / progress.totalItems else 0f
                    
                    LinearProgressIndicator(
                        progress = percent,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Processed: ${progress.currentItem} / ${progress.totalItems}")
                    
                    HorizontalDivider()
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (progress.hasMask) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = if (progress.hasMask) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Icon Mask", color = if (progress.hasMask) Color.Unspecified else MaterialTheme.colorScheme.outline)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (progress.hasUpon) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = if (progress.hasUpon) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Icon Overlay (Upon)", color = if (progress.hasUpon) Color.Unspecified else MaterialTheme.colorScheme.outline)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (progress.backCount > 0) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = if (progress.backCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Backgrounds Found: ${progress.backCount}", color = if (progress.backCount > 0) Color.Unspecified else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = {
            if (progress.isFinished) {
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    )
}

@Composable
fun InstalledPacksDialog(
    viewModel: IconPackViewModel,
    onDismiss: () -> Unit,
    onPackSelected: (IconPackImporter.IconPackInfo) -> Unit
) {
    val packs by viewModel.installedIconPacks.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Installed Icon Packs") },
        text = {
            if (packs.isEmpty()) {
                Text("No supported icon packs found.")
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(packs) { pack ->
                        ListItem(
                            headlineContent = { Text(pack.name) },
                            supportingContent = { Text(pack.packageName) },
                            leadingContent = {
                                Image(
                                    bitmap = pack.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                                )
                            },
                            modifier = Modifier.clickable { onPackSelected(pack) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
fun EmptyState(onNewProject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.extraLarge
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Start your first icon pack",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Design, map and export your custom icons easily.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNewProject,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Text("Get Started")
        }
    }
}

@Composable
fun ExpressiveProjectItem(
    project: IconPackProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.large
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        project.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = project.name, 
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = project.packageName, 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun AddProjectDialog(
    title: String = "New Icon Pack",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("com.example.iconpack") }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var pkgError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = if (it.isBlank()) "Project name cannot be empty" else null
                    },
                    label = { Text("Project Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { 
                        pkg = it
                        pkgError = when {
                            it.isBlank() -> "Package name cannot be empty"
                            !it.contains(".") -> "Invalid package name (e.g. com.example.app)"
                            else -> null
                        }
                    },
                    label = { Text("Package Name") },
                    isError = pkgError != null,
                    supportingText = pkgError?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val hasNameError = name.isBlank()
                    val hasPkgError = pkg.isBlank() || !pkg.contains(".")
                    
                    if (hasNameError) nameError = "Project name cannot be empty"
                    if (hasPkgError) pkgError = "Valid package name required"
                    
                    if (!hasNameError && !hasPkgError) {
                        onConfirm(name, pkg)
                    }
                },
                shape = MaterialTheme.shapes.large
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
