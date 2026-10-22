package com.tasknext.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private inline fun AppDatabase.useDatabase(block: (AppDatabase) -> Unit) {
        try { block(this) } finally { close() }
    }

    // A standalone released v6 schema, including defaults installed by v1-v6 upgrades.
    private fun legacyDatabase(version: Int, seed: (SQLiteDatabase) -> Unit): AppDatabase {
        val name = "migration-$version.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("""CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                colorHex TEXT NOT NULL, colorName TEXT NOT NULL DEFAULT 'Custom',
                groupIcon TEXT NOT NULL DEFAULT 'task', priority TEXT NOT NULL,
                comment TEXT NOT NULL, reminderHour INTEGER, reminderMinute INTEGER,
                reminderEnabled INTEGER NOT NULL, orderIndex INTEGER NOT NULL,
                slot INTEGER, overdueSinceEpochDay INTEGER, isArchived INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE completion_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL,
                titleSnapshot TEXT NOT NULL, colorHexSnapshot TEXT NOT NULL,
                prioritySnapshot TEXT NOT NULL DEFAULT 'MEDIUM',
                groupNameSnapshot TEXT NOT NULL DEFAULT 'Task',
                groupIconSnapshot TEXT NOT NULL DEFAULT 'task',
                commentSnapshot TEXT NOT NULL DEFAULT '', completedAtEpochMillis INTEGER NOT NULL)""")
            if (version >= 8) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN taskSize TEXT NOT NULL DEFAULT 'BIG'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN eventType TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN effectiveEpochDay INTEGER")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN recoveredFromLogId INTEGER")
                db.execSQL("ALTER TABLE completion_log ADD COLUMN taskSizeSnapshot TEXT NOT NULL DEFAULT 'BIG'")
                db.execSQL("""CREATE TABLE daily_outcome (epochDay INTEGER NOT NULL PRIMARY KEY,
                    status TEXT NOT NULL, sourceTaskId INTEGER, updatedAtEpochMillis INTEGER NOT NULL)""")
            }
            if (version >= 9) {
                db.execSQL("""CREATE TABLE small_task_completion (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    taskId INTEGER NOT NULL, epochDay INTEGER NOT NULL, completedAtEpochMillis INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX index_small_task_completion_epochDay ON small_task_completion(epochDay)")
            }
            seed(db)
            db.version = version
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
    }

    @Test fun v6UpgradePreservesTasksAndNormalizesBonus() {
        legacyDatabase(6) { db ->
            db.execSQL("""INSERT INTO tasks (title,colorHex,priority,comment,reminderEnabled,orderIndex,slot,isArchived)
                VALUES ('Keep me','#4F46E5','MEDIUM','Original comment',1,4,1,0)""")
        }.useDatabase { room ->
            // Opening through Room runs all migrations AND generated schema validation.
            room.openHelper.writableDatabase.query("SELECT title, comment, taskSize, slot FROM tasks").use { c ->
                c.moveToFirst()
                assertEquals("Keep me", c.getString(0))
                assertEquals("Original comment", c.getString(1))
                assertEquals("SMALL", c.getString(2))
                assertEquals(1, c.getInt(3))
            }
        }
    }

    private fun addHistory(db: SQLiteDatabase, id: Int, type: String = "COMPLETED", size: String = "SMALL") {
        db.execSQL("""INSERT INTO completion_log
            (taskId,titleSnapshot,colorHexSnapshot,completedAtEpochMillis,eventType,taskSizeSnapshot,effectiveEpochDay)
            VALUES ($id,'History','#4F46E5',1720000000000,'$type','$size',20000)""")
    }

    @Test fun v8UpgradeCarriesSmallCompletionsButNotDeletedRecoveredOrBigTasks() {
        legacyDatabase(8) { db ->
            for (id in 1..5) addHistory(db, id)
            addHistory(db, 6, "DELETED")
            addHistory(db, 7, "RECOVERED")
            addHistory(db, 8, size = "BIG")
        }.useDatabase { room ->
            room.openHelper.writableDatabase.query("SELECT COUNT(*), MIN(epochDay) FROM small_task_completion").use { c ->
                c.moveToFirst()
                assertEquals(5, c.getInt(0))
                assertEquals(20000L, c.getLong(1))
            }
        }
    }

    @Test fun v9RepairPreservesExistingCreditsWithoutDoubleCounting() {
        legacyDatabase(9) { db ->
            addHistory(db, 1)
            addHistory(db, 2)
            // v9 wrote the ledger a few milliseconds before its History row.
            db.execSQL("INSERT INTO small_task_completion (taskId,epochDay,completedAtEpochMillis) VALUES (1,20000,1719999999998)")
            // Credit remains valid even when its visible History has been cleared.
            db.execSQL("INSERT INTO small_task_completion (taskId,epochDay,completedAtEpochMillis) VALUES (3,20000,1720000000001)")
        }.useDatabase { room ->
            room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM small_task_completion").use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
            }
        }
    }
}
