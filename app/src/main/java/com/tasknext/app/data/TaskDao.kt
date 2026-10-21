package com.tasknext.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY orderIndex ASC")
    fun observeAllTasks(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND overdueSinceEpochDay IS NULL AND slot IS NOT NULL ORDER BY slot ASC")
    fun observeActiveSlots(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND slot IS NULL AND overdueSinceEpochDay IS NULL ORDER BY orderIndex ASC")
    fun observePool(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND overdueSinceEpochDay IS NOT NULL ORDER BY overdueSinceEpochDay ASC, orderIndex ASC")
    fun observeOverdue(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?
    @Insert suspend fun insert(task: Task): Long
    @Update suspend fun update(task: Task)
    @Update suspend fun updateTasks(tasks: List<Task>)
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM tasks WHERE isArchived = 0")
    suspend fun activeCount(): Int
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY orderIndex ASC")
    suspend fun getAllOnce(): List<Task>

    @Query("""
        UPDATE tasks
        SET colorHex = :defaultColorHex,
            colorName = :defaultGroupName,
            groupIcon = :defaultIconKey
        WHERE colorName = :removedGroupName
    """)
    suspend fun reassignRemovedGroup(
        removedGroupName: String,
        defaultColorHex: String,
        defaultGroupName: String,
        defaultIconKey: String
    )

    @Transaction
    suspend fun archiveAsCompleted(task: Task, log: CompletionLog) {
        insertLog(log)
        update(task.copy(slot = null, reminderEnabled = false, isArchived = true))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyOutcome(outcome: DailyOutcome)
    @Query("SELECT * FROM daily_outcome WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getDailyOutcome(epochDay: Long): DailyOutcome?
    @Query("SELECT * FROM daily_outcome ORDER BY epochDay DESC")
    suspend fun getDailyOutcomesOnce(): List<DailyOutcome>
    @Query("SELECT * FROM daily_outcome ORDER BY epochDay DESC")
    fun observeDailyOutcomes(): Flow<List<DailyOutcome>>
    @Query("DELETE FROM daily_outcome WHERE epochDay = :epochDay")
    suspend fun deleteDailyOutcome(epochDay: Long)

    @Insert suspend fun insertLog(log: CompletionLog)
    @Query("SELECT * FROM completion_log ORDER BY completedAtEpochMillis DESC")
    fun observeHistory(): Flow<List<CompletionLog>>
    @Query("DELETE FROM completion_log WHERE id = :id")
    suspend fun deleteHistoryRecord(id: Long)
    @Query("DELETE FROM completion_log")
    suspend fun clearHistory()
    @Query("""
        DELETE FROM completion_log
        WHERE id = (
            SELECT id FROM completion_log
            WHERE taskId = :taskId
            ORDER BY completedAtEpochMillis DESC, id DESC
            LIMIT 1
        )
    """)
    suspend fun deleteLatestLogForTask(taskId: Long)

    @Transaction
    suspend fun undoCompletion(task: Task) {
        deleteLatestLogForTask(task.id)
        update(task)
    }
}
