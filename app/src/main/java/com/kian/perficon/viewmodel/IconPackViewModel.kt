package com.kian.perficon.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kian.perficon.model.AppDatabase
import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackProject
import com.kian.perficon.repository.IconPackRepository
import com.kian.perficon.util.IconPackImporter
import com.kian.perficon.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class IconPackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: IconPackRepository
    private val importer: IconPackImporter

    init {
        val dao = AppDatabase.getDatabase(application).iconPackDao()
        repository = IconPackRepository(dao)
        importer = IconPackImporter(application, repository)
    }

    val allProjects: StateFlow<List<IconPackProject>> = repository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val installedIconPacks: StateFlow<List<IconPackImporter.IconPackInfo>> = flow {
        emit(importer.getInstalledIconPacks())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertProject(name: String, packageName: String, projectIconPath: String? = null) = viewModelScope.launch {
        repository.insertProject(IconPackProject(name = name, packageName = packageName, projectIconPath = projectIconPath))
    }

    fun deleteProject(project: IconPackProject) = viewModelScope.launch {
        repository.deleteProject(project)
        // Cleanup files
        StorageHelper.getProjectDir(project.id).deleteRecursively()
    }

    fun duplicateProject(project: IconPackProject) = viewModelScope.launch(Dispatchers.IO) {
        val newName = "${project.name} (Copy)"
        val newPkg = "${project.packageName}.copy"
        
        // 1. Insert new project
        val newProjectId = repository.insertProject(project.copy(id = 0, name = newName, packageName = newPkg))
        
        // 2. Setup folders
        val oldIconsDir = StorageHelper.getProjectIconsDir(project.id)
        val newIconsDir = StorageHelper.getProjectIconsDir(newProjectId)
        newIconsDir.mkdirs()
        
        // 3. Duplicate Global Assets
        val newMask = project.iconMaskPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val newFile = File(newIconsDir, "mask_${System.currentTimeMillis()}.png")
                file.copyTo(newFile)
                newFile.absolutePath
            } else null
        }
        val newUpon = project.iconUponPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val newFile = File(newIconsDir, "upon_${System.currentTimeMillis()}.png")
                file.copyTo(newFile)
                newFile.absolutePath
            } else null
        }
        val newBacks = project.iconBackPaths?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) {
                val newFile = File(newIconsDir, "back_${System.currentTimeMillis()}_${file.name}")
                file.copyTo(newFile)
                newFile.absolutePath
            } else null
        }?.joinToString(",")
        val newProjectIcon = project.projectIconPath?.let { path ->
            File(path).takeIf(File::isFile)?.let { file ->
                val target = File(newIconsDir, "project_icon_${System.currentTimeMillis()}.png")
                file.copyTo(target)
                target.absolutePath
            }
        }
        
        // Update project with new asset paths
        repository.updateProject(repository.getProjectById(newProjectId)!!.copy(
            iconMaskPath = newMask,
            iconUponPath = newUpon,
            iconBackPaths = newBacks,
            projectIconPath = newProjectIcon
        ))

        // 4. Duplicate Mappings
        val oldMappings = repository.getMappingsForProject(project.id).first()
        oldMappings.forEach { mapping ->
            val oldFile = File(mapping.iconPath)
            if (oldFile.exists()) {
                val newFile = File(newIconsDir, "${mapping.targetPackageName}_${System.currentTimeMillis()}.png")
                oldFile.copyTo(newFile)
                repository.insertMapping(mapping.copy(id = 0, projectId = newProjectId, iconPath = newFile.absolutePath))
            }
        }
    }

    fun getProjectById(id: Long): Flow<IconPackProject?> =
        repository.allProjects.map { projects -> projects.find { it.id == id } }

    fun getMappingsForProject(projectId: Long): Flow<List<IconMapping>> =
        repository.getMappingsForProject(projectId)

    fun insertMapping(
        projectId: Long,
        iconName: String,
        targetPackage: String,
        targetActivity: String,
        iconPath: String
    ) =
        viewModelScope.launch {
            repository.insertMapping(
                IconMapping(
                    projectId = projectId,
                    iconName = iconName,
                    targetPackageName = targetPackage,
                    targetActivityName = targetActivity,
                    iconPath = iconPath
                )
            )
        }

    fun updateProject(project: IconPackProject) = viewModelScope.launch {
        repository.updateProject(project)
    }

    fun deleteMapping(mapping: IconMapping) = viewModelScope.launch {
        repository.deleteMapping(mapping)
    }

    fun updateMapping(mapping: IconMapping) = viewModelScope.launch {
        repository.updateMapping(mapping)
    }

    fun importFromInstalledApp(
        sourcePkg: String, 
        name: String, 
        targetPkg: String,
        progressFlow: MutableStateFlow<IconPackImporter.ImportProgress>
    ) = viewModelScope.launch {
        val success = importer.importFromInstalledApp(sourcePkg, name, targetPkg, progressFlow)
        withContext(Dispatchers.Main) {
            if (!success) {
                Toast.makeText(getApplication(), "Import failed or partially completed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
