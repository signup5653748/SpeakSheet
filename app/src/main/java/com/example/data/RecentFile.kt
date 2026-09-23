package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_files",
    indices = [
        Index(value = ["lastModified"]),
        Index(value = ["path"])
    ]
)
data class RecentFile(
    @PrimaryKey
    val uri: String,
    val name: String,
    val path: String,
    val lastModified: Long,
    val sizeBytes: Long
)
