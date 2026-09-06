package com.tasknext.app.ui

import androidx.compose.ui.graphics.Color
import com.tasknext.app.data.Priority

/**
 * Modern priority chips: distinct hues instead of a traffic-light palette.
 * Each content/container pair keeps strong text contrast while the text label
 * remains the primary meaning cue (color is supplementary).
 */
internal data class PriorityPalette(
    val container: Color,
    val content: Color
)

internal fun priorityPalette(priority: Priority): PriorityPalette = when (priority) {
    Priority.LOW -> PriorityPalette(
        container = Color(0xFFE0F2FE), // sky 100
        content = Color(0xFF0369A1)    // sky 700
    )
    Priority.MEDIUM -> PriorityPalette(
        container = Color(0xFFEDE9FE), // violet 100
        content = Color(0xFF6D28D9)    // violet 700
    )
    Priority.HIGH -> PriorityPalette(
        container = Color(0xFFFFE4E6), // rose 100
        content = Color(0xFFBE123C)    // rose 700
    )
}
