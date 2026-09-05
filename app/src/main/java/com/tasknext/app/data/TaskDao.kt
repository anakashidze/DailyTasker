package com.tasknext.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY orderIndex ASC")
    fun observeAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND slot IS NOT NULL ORDER BY slot ASC")
    fun observeActiveSlots(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND slot IS NULL ORDER BY orderIndex ASC")
    fun observePool(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE isArchived = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY orderIndex ASC")
    suspend fun getAllOnce(): List<Task>

    // History
    @Insert
    suspend fun insertLog(log: CompletionLog)

    @Query("SELECT * FROM completion_log ORDER BY completedAtEpochMillis DESC")
    fun observeHistory(): Flow<List<CompletionLog>>
}
