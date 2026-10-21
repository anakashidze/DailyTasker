package com.tasknext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Independent progress ledger for Rest Credits.
 * History can be cleared without changing earned weekly progress.
 */
@Entity(tableName = "small_task_completion")
data class SmallTaskCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val epochDay: Long,
    val completedAtEpochMillis: Long = System.currentTimeMillis()
)
