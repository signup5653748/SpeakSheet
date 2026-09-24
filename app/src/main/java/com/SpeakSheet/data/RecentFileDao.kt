package com.SpeakSheet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT COUNT(*) FROM recent_files")
    suspend fun getRecentFilesCount(): Int

    @Query("SELECT * FROM recent_files ORDER BY lastModified DESC LIMIT 50")
    fun getAllRecentFiles(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent_files ORDER BY lastModified DESC")
    suspend fun getRecentFilesList(): List<RecentFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFile)

    @Query("DELETE FROM recent_files WHERE uri = :uri OR (path = :path AND path != '')")
    suspend fun deleteByUriOrPath(uri: String, path: String)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteRecentFileByUri(uri: String)

    @Transaction
    suspend fun upsertRecentFile(recentFile: RecentFile) {
        deleteByUriOrPath(recentFile.uri, recentFile.path)
        insertRecentFile(recentFile)
    }
}
