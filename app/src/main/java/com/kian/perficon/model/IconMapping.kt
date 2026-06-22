package com.kian.perficon.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "icon_mappings",
    foreignKeys = [
        ForeignKey(
            entity = IconPackProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class IconMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val iconName: String = "",
    val targetPackageName: String,
    val targetActivityName: String,
    val iconPath: String,
    val mappingType: Int = 0, // 0: Normal, 1: Calendar, 2: Clock
    val extraInfo: String? = null // JSON for specialized data (prefix, hand paths, etc.)
)
