package com.kian.perficon.repository

import com.kian.perficon.model.IconMapping
import com.kian.perficon.model.IconPackDao
import com.kian.perficon.model.IconPackProject
import kotlinx.coroutines.flow.Flow

class IconPackRepository(private val dao: IconPackDao) {
    val allProjects: Flow<List<IconPackProject>> = dao.getAllProjects()

    suspend fun getProjectById(id: Long) = dao.getProjectById(id)

    suspend fun insertProject(project: IconPackProject) = dao.insertProject(project)

    suspend fun updateProject(project: IconPackProject) = dao.updateProject(project)

    suspend fun deleteProject(project: IconPackProject) = dao.deleteProject(project)

    fun getMappingsForProject(projectId: Long): Flow<List<IconMapping>> =
        dao.getMappingsForProject(projectId)

    suspend fun insertMapping(mapping: IconMapping) = dao.insertMapping(mapping)

    suspend fun insertMappings(mappings: List<IconMapping>) = dao.insertMappings(mappings)

    suspend fun updateMapping(mapping: IconMapping) = dao.updateMapping(mapping)

    suspend fun deleteMapping(mapping: IconMapping) = dao.deleteMapping(mapping)
}
