package com.tasknext.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, CompletionLog::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN colorName TEXT NOT NULL DEFAULT 'Custom'")
                db.execSQL(
                    """
                    UPDATE tasks
                    SET colorName = CASE colorHex
                        WHEN '#4F46E5' THEN 'Indigo'
                        WHEN '#22C55E' THEN 'Green'
                        WHEN '#EF4444' THEN 'Red'
                        WHEN '#F59E0B' THEN 'Amber'
                        WHEN '#0EA5E9' THEN 'Sky blue'
                        WHEN '#EC4899' THEN 'Pink'
                        WHEN '#6B7280' THEN 'Gray'
                        ELSE 'Custom'
                    END
                    """.trimIndent()
                )
            }
        }

        /**
         * Older TaskNext versions logged completions but returned the same task to
         * the active backlog. Under the new terminal-completion rule, any task that
         * already has a History record must no longer appear in active lists.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE tasks
                    SET isArchived = 1,
                        slot = NULL,
                        reminderEnabled = 0
                    WHERE id IN (SELECT DISTINCT taskId FROM completion_log)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN groupIcon TEXT NOT NULL DEFAULT 'task'")
                db.execSQL("UPDATE tasks SET colorName = 'Task', groupIcon = 'task' WHERE colorName = 'Indigo'")
                db.execSQL("UPDATE tasks SET colorName = 'Health', groupIcon = 'health' WHERE colorName = 'Green'")
                db.execSQL("UPDATE tasks SET colorName = 'Work', groupIcon = 'work' WHERE colorName = 'Sky blue'")
                db.execSQL("UPDATE tasks SET colorName = 'Home', groupIcon = 'home', colorHex = '#8B5E3C' WHERE colorName = 'Amber'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tasknext.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
