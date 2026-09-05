package com.tasknext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completion_log")
data class CompletionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val titleSnapshot: String,
    val colorHexSnapshot: String,
    val completedAtEpochMillis: Long
)
