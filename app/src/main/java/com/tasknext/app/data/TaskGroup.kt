package com.tasknext.app.data

import android.content.Context
import com.tasknext.app.R

data class TaskGroupDefinition(
    val name: String,
    val colorHex: String,
    val iconKey: String,
    val builtIn: Boolean = false
)

object TaskGroups {
    val builtIns = listOf(
        TaskGroupDefinition("Task", "#4F46E5", "task", builtIn = true),
        TaskGroupDefinition("Home", "#8B5E3C", "home", builtIn = true),
        TaskGroupDefinition("Health", "#2E9D60", "health", builtIn = true),
        TaskGroupDefinition("Work", "#2563EB", "work", builtIn = true)
    )

    // Translate only known built-ins. Persisted names and custom groups remain stable.
    fun displayName(context: Context, name: String, iconKey: String): String {
        if (builtIns.none { it.name == name && it.iconKey == iconKey }) return name
        val resource = when (name) {
            "Task" -> R.string.group_task
            "Home" -> R.string.group_home
            "Health" -> R.string.group_health
            "Work" -> R.string.group_work
            else -> return name
        }
        return context.getString(resource)
    }

    val default: TaskGroupDefinition get() = builtIns.first()
}
