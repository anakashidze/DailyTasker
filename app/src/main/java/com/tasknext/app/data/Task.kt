package com.tasknext.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority { LOW, MEDIUM, HIGH }
enum class TaskSize { BIG, SMALL }

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val colorHex: String = "#4F46E5",
    @ColumnInfo(name = "colorName") val groupName: String = "Task",
    val groupIcon: String = "task",
    val priority: Priority = Priority.MEDIUM,
    @ColumnInfo(defaultValue = "'BIG'") val taskSize: TaskSize = TaskSize.BIG,
    val comment: String = "",
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val reminderEnabled: Boolean = true,
    val orderIndex: Int = 0,
    val slot: Int? = null,
    val overdueSinceEpochDay: Long? = null,
    val isArchived: Boolean = false
)
