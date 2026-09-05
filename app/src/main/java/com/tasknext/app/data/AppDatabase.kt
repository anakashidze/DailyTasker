package com.tasknext.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, CompletionLog::class], version = 2, exportSchema = false)
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tasknext.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
