package com.speaksheet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_sheets",
    foreignKeys = [
        ForeignKey(
            entity = RecentFile::class,
            parentColumns = ["uri"],
            childColumns = ["fileUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fileUri"]),
        Index(value = ["fileUri", "sheetIndex"])
    ]
)
data class SheetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileUri: String,
    val sheetName: String,
    val sheetIndex: Int,
    val zoom: Float = 1.0f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val frozenRows: Int = 0,
    val frozenCols: Int = 0
)
