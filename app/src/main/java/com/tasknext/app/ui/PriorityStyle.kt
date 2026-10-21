package com.tasknext.app.ui

import androidx.compose.ui.graphics.Color
import com.tasknext.app.data.Priority
import com.tasknext.app.data.TaskSize

internal data class PriorityPalette(
    val container: Color,
    val content: Color
)

internal fun priorityPalette(priority: Priority): PriorityPalette = when (priority) {
    Priority.LOW -> PriorityPalette(Color(0xFFE0F2FE), Color(0xFF0369A1))
    Priority.MEDIUM -> PriorityPalette(Color(0xFFEDE9FE), Color(0xFF6D28D9))
    Priority.HIGH -> PriorityPalette(Color(0xFFFFE4E6), Color(0xFFBE123C))
}

internal fun taskSizePalette(taskSize: TaskSize): PriorityPalette = when (taskSize) {
    TaskSize.BIG -> PriorityPalette(
        container = Color(0xFFEDE9FE), // violet 100
        content = Color(0xFF6D28D9)   // violet 700
    )
    TaskSize.SMALL -> PriorityPalette(
        container = Color(0xFFEEF2FF), // indigo 50
        content = Color(0xFF4F46E5)    // indigo 600
    )
}
