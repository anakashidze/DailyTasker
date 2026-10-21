package com.tasknext.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, CompletionLog::class, DailyOutcome::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN colorName TEXT NOT NULL DEFAULT 'Custom'")
                db.execSQL("""
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
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE tasks
                    SET isArchived = 1,
                        slot = NULL,
                        reminderEnabled = 0
                    WHERE id IN (SELECT DISTINCT taskId FROM completion_log)
                """.trimIndent())
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completion_log ADD COLUMN prioritySnapshot TEXT NOT NULL DEFAULT 'MEDIUM'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN groupNameSnapshot TEXT NOT NULL DEFAULT 'Task'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN groupIconSnapshot TEXT NOT NULL DEFAULT 'task'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN commentSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE completion_log
                    SET prioritySnapshot = COALESCE((SELECT priority FROM tasks WHERE tasks.id = completion_log.taskId), 'MEDIUM'),
                        groupNameSnapshot = COALESCE((SELECT colorName FROM tasks WHERE tasks.id = completion_log.taskId), 'Task'),
                        groupIconSnapshot = COALESCE((SELECT groupIcon FROM tasks WHERE tasks.id = completion_log.taskId), 'task'),
                        commentSnapshot = COALESCE((SELECT comment FROM tasks WHERE tasks.id = completion_log.taskId), '')
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN overdueSinceEpochDay INTEGER")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN taskSize TEXT NOT NULL DEFAULT 'BIG'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN eventType TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN effectiveEpochDay INTEGER")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN recoveredFromLogId INTEGER")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_outcome (
                        epochDay INTEGER NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        sourceTaskId INTEGER,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tasknext.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
