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
    @Query("SELECT * FROM tasks WHERE id = :id AND isArchived = 0")
    fun observeTask(id: Long): Flow<Task?>
    @Insert suspend fun insert(task: Task): Long
    // Keep the read and write in one Room transaction: a reminder action or reorder
    // must not race an editor and restore an archived task or an obsolete slot.
    @Transaction
    suspend fun updateTaskDetails(task: Task, todayLocked: Boolean) {
        val existing = getById(task.id) ?: return
        if (existing.isArchived || (existing.slot == 0 && todayLocked)) return
        val edited = existing.copy(title = task.title, colorHex = task.colorHex,
            groupName = task.groupName, groupIcon = task.groupIcon, taskSize = task.taskSize,
            comment = task.comment, reminderEnabled = task.reminderEnabled,
            reminderHour = task.reminderHour, reminderMinute = task.reminderMinute)
        val sizeNoLongerFits = (edited.taskSize == TaskSize.SMALL && existing.slot == 0) ||
            (edited.taskSize == TaskSize.BIG && existing.slot != null && existing.slot != 0)
        update(if (sizeNoLongerFits) edited.copy(slot = null, overdueSinceEpochDay = null) else edited)
    }

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
        // Keep credit progress, History and task archival in one transaction.
        if (task.taskSize == TaskSize.SMALL) {
            insertSmallTaskCompletion(SmallTaskCompletion(
                taskId = task.id,
                epochDay = requireNotNull(log.effectiveEpochDay),
                completedAtEpochMillis = log.completedAtEpochMillis
            ))
        }
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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreHistoryRecord(log: CompletionLog)
    @Query("SELECT * FROM completion_log WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): CompletionLog?
    @Query("SELECT COUNT(*) FROM completion_log WHERE eventType = 'RECOVERED' AND recoveredFromLogId = :sourceLogId")
    suspend fun recoveryCountFor(sourceLogId: Long): Int
    @Query("SELECT * FROM completion_log ORDER BY completedAtEpochMillis DESC")
    fun observeHistory(): Flow<List<CompletionLog>>
    @Query("DELETE FROM completion_log WHERE id = :id")
    suspend fun deleteHistoryRecord(id: Long)
    @Query("DELETE FROM completion_log")
    suspend fun clearHistory()

    @Insert
    suspend fun insertSmallTaskCompletion(event: SmallTaskCompletion)

    @Query("""
        SELECT COUNT(*) FROM small_task_completion
        WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay
    """)
    suspend fun countSmallTaskCompletions(startEpochDay: Long, endEpochDay: Long): Int

    @Query("""
        DELETE FROM small_task_completion
        WHERE id = (
            SELECT id FROM small_task_completion
            WHERE taskId = :taskId
            ORDER BY completedAtEpochMillis DESC, id DESC
            LIMIT 1
        )
    """)
    suspend fun deleteLatestSmallTaskCompletionForTask(taskId: Long)

    @Query("""
        SELECT COUNT(*) FROM daily_outcome
        WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay
          AND status = 'REST'
    """)
    suspend fun countRestDays(startEpochDay: Long, endEpochDay: Long): Int
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
        deleteLatestSmallTaskCompletionForTask(task.id)
        update(task)
    }
}
