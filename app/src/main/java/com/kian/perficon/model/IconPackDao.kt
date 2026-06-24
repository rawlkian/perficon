package com.kian.perficon.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IconPackDao {
    @Query("SELECT * FROM icon_pack_projects")
    fun getAllProjects(): Flow<List<IconPackProject>>

    @Query("SELECT * FROM icon_pack_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): IconPackProject?

    @Insert
    suspend fun insertProject(project: IconPackProject): Long

    @Update
    suspend fun updateProject(project: IconPackProject)

    @Delete
    suspend fun deleteProject(project: IconPackProject)

    @Query("SELECT * FROM icon_mappings WHERE projectId = :projectId")
    fun getMappingsForProject(projectId: Long): Flow<List<IconMapping>>

    @Insert
    suspend fun insertMapping(mapping: IconMapping)

    @Insert
    suspend fun insertMappings(mappings: List<IconMapping>)

    @Update
    suspend fun updateMapping(mapping: IconMapping)

    @Delete
    suspend fun deleteMapping(mapping: IconMapping)
}
