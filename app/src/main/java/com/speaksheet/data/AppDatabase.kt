package com.speaksheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecentFile::class, SheetEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recentFileDao(): RecentFileDao
    abstract fun sheetDao(): SheetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `file_sheets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `sheetName` TEXT NOT NULL,
                        `sheetIndex` INTEGER NOT NULL,
                        `zoom` REAL NOT NULL,
                        `scrollX` REAL NOT NULL,
                        `scrollY` REAL NOT NULL,
                        `frozenRows` INTEGER NOT NULL,
                        `frozenCols` INTEGER NOT NULL,
                        FOREIGN KEY(`fileUri`) REFERENCES `recent_files`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_sheets_fileUri` ON `file_sheets` (`fileUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_sheets_fileUri_sheetIndex` ON `file_sheets` (`fileUri`, `sheetIndex`)")

                // Treat existing files as "Sheet1"
                db.execSQL("""
                    INSERT INTO `file_sheets` (`fileUri`, `sheetName`, `sheetIndex`, `zoom`, `scrollX`, `scrollY`, `frozenRows`, `frozenCols`)
                    SELECT `uri`, 'Sheet1', 0, 1.0, 0.0, 0.0, 0, 0 FROM `recent_files`
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speaksheet_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
