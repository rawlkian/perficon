package com.kian.perficon.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "icon_pack_projects")
data class IconPackProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    val description: String = "",
    val projectIconPath: String? = null,
    
    // Masking and styling for uncovered apps
    val iconMaskPath: String? = null,
    val iconBackPaths: String? = null, // Comma-separated paths
    val iconUponPath: String? = null,
    val scaleFactor: Float = 1.0f
)
