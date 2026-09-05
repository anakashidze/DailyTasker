package com.tasknext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority { LOW, MEDIUM, HIGH }

/**
 * A task in the backlog pool.
 * - orderIndex: position in the "up next" queue (lower = sooner).
 * - slot: null if sitting in the pool; 0/1/2 if currently one of today's active tasks.
 *   Slot 0 is always the "primary" one — only completing the highest occupied slot
 *   triggers the next-task suggestion, per the app's flow.
 * - reminderHour/Minute: null means "use the global default from Settings".
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val colorHex: String = "#4F46E5",
    val priority: Priority = Priority.MEDIUM,
    val comment: String = "",
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val reminderEnabled: Boolean = false,
    val orderIndex: Int = 0,
    val slot: Int? = null,
    val isArchived: Boolean = false
)
