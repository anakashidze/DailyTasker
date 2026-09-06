package com.tasknext.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completion_log")
data class CompletionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val titleSnapshot: String,
    val colorHexSnapshot: String,
    @ColumnInfo(defaultValue = "'MEDIUM'") val prioritySnapshot: String = Priority.MEDIUM.name,
    @ColumnInfo(defaultValue = "'Task'") val groupNameSnapshot: String = "Task",
    @ColumnInfo(defaultValue = "'task'") val groupIconSnapshot: String = "task",
    @ColumnInfo(defaultValue = "''") val commentSnapshot: String = "",
    val completedAtEpochMillis: Long
)
