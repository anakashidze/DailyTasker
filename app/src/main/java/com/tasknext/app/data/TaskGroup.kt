package com.tasknext.app.data

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

    val default: TaskGroupDefinition get() = builtIns.first()
}
