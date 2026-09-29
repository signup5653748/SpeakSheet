package com.speaksheet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SheetDao {
    @Query("SELECT * FROM file_sheets WHERE fileUri = :fileUri ORDER BY sheetIndex ASC")
    suspend fun getSheetsForFile(fileUri: String): List<SheetEntity>

    @Query("SELECT * FROM file_sheets WHERE fileUri = :fileUri ORDER BY sheetIndex ASC")
    fun observeSheetsForFile(fileUri: String): Flow<List<SheetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheet(sheet: SheetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheets(sheets: List<SheetEntity>)

    @Update
    suspend fun updateSheet(sheet: SheetEntity)

    @Query("DELETE FROM file_sheets WHERE fileUri = :fileUri")
    suspend fun deleteSheetsForFile(fileUri: String)

    @Query("DELETE FROM file_sheets WHERE fileUri = :fileUri AND sheetName = :sheetName")
    suspend fun deleteSheet(fileUri: String, sheetName: String)

    @androidx.room.Transaction
    suspend fun replaceSheetsForFile(fileUri: String, sheets: List<SheetEntity>) {
        deleteSheetsForFile(fileUri)
        insertSheets(sheets)
    }
}
