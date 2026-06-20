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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

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

    fun insertProject(name: String, packageName: String) = viewModelScope.launch {
        repository.insertProject(IconPackProject(name = name, packageName = packageName))
    }

    fun deleteProject(project: IconPackProject) = viewModelScope.launch {
        repository.deleteProject(project)
    }

    /**
     * Returns a Flow for a specific project. 
     * Refactored from StateFlow to simple Flow to prevent object creation overhead in getters.
     */
    fun getProjectById(id: Long): Flow<IconPackProject?> =
        repository.allProjects.map { projects -> projects.find { it.id == id } }

    fun getMappingsForProject(projectId: Long): Flow<List<IconMapping>> =
        repository.getMappingsForProject(projectId)

    fun insertMapping(projectId: Long, targetPackage: String, targetActivity: String, iconPath: String) =
        viewModelScope.launch {
            repository.insertMapping(
                IconMapping(
                    projectId = projectId,
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

    fun duplicateMapping(mapping: IconMapping) = viewModelScope.launch {
        repository.insertMapping(mapping.copy(id = 0))
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
        // Project creation logic is now inside the importer's method for atomic import if needed, 
        // but here we keep the structure for compatibility.
        val success = importer.importFromInstalledApp(sourcePkg, name, targetPkg, progressFlow)
        withContext(Dispatchers.Main) {
            if (!success) {
                Toast.makeText(getApplication(), "Import failed or partially completed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
