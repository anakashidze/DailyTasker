package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tasknext.app.data.Priority
import com.tasknext.app.data.Task
import kotlin.math.roundToInt

private data class NamedColor(val name: String, val hex: String)

private val palette = listOf(
    NamedColor("Indigo", "#4F46E5"),
    NamedColor("Green", "#22C55E"),
    NamedColor("Red", "#EF4444"),
    NamedColor("Amber", "#F59E0B"),
    NamedColor("Sky blue", "#0EA5E9"),
    NamedColor("Pink", "#EC4899"),
    NamedColor("Gray", "#6B7280")
)

private val lowPriority = Color(0xFFDDEBFF)
private val mediumPriority = Color(0xFFFFF1B8)
private val highPriority = Color(0xFFFFD7D7)

private fun parseRgb(hex: String): Triple<Int, Int, Int> = try {
    val value = android.graphics.Color.parseColor(hex)
    Triple(
        android.graphics.Color.red(value),
        android.graphics.Color.green(value),
        android.graphics.Color.blue(value)
    )
} catch (_: Exception) {
    Triple(79, 70, 229)
}

private fun rgbToHex(red: Int, green: Int, blue: Int): String =
    "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    existing: Task?,
    defaultReminderHour: Int,
    defaultReminderMinute: Int,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        color: String,
        colorName: String,
        priority: Priority,
        comment: String,
        reminderOn: Boolean,
        hour: Int?,
        minute: Int?
    ) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorHex ?: palette.first().hex) }
    var colorName by remember(existing?.id) { mutableStateOf(existing?.colorName ?: palette.first().name) }
    var priority by remember(existing?.id) { mutableStateOf(existing?.priority ?: Priority.MEDIUM) }
    var comment by remember(existing?.id) { mutableStateOf(existing?.comment ?: "") }
    var reminderOn by remember(existing?.id) { mutableStateOf(existing?.reminderEnabled ?: false) }
    var useDefaultTime by remember(existing?.id) {
        mutableStateOf(existing == null || (existing.reminderHour == null && existing.reminderMinute == null))
    }
    var hour by remember(existing?.id) {
        mutableIntStateOf((existing?.reminderHour ?: defaultReminderHour).coerceIn(0, 23))
    }
    var minute by remember(existing?.id) {
        mutableIntStateOf((existing?.reminderMinute ?: defaultReminderMinute).coerceIn(0, 59))
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    var customColorMode by remember(existing?.id) {
        mutableStateOf(palette.none { it.hex.equals(color, ignoreCase = true) })
    }
    val initialRgb = remember(existing?.id) { parseRgb(color) }
    var customRed by remember(existing?.id) { mutableIntStateOf(initialRgb.first) }
    var customGreen by remember(existing?.id) { mutableIntStateOf(initialRgb.second) }
    var customBlue by remember(existing?.id) { mutableIntStateOf(initialRgb.third) }

    fun updateCustomColor() {
        color = rgbToHex(customRed, customGreen, customBlue)
        if (colorName.isBlank() || palette.any { it.name == colorName }) colorName = "Custom"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                Text("Color")
                Box {
                    val swatch = try {
                        Color(android.graphics.Color.parseColor(color))
                    } catch (_: Exception) {
                        Color.Gray
                    }
                    OutlinedButton(onClick = { colorExpanded = true }) {
                        Box(Modifier.size(18.dp).background(swatch, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(colorName.ifBlank { "Custom" })
                    }
                    DropdownMenu(
                        expanded = colorExpanded,
                        onDismissRequest = { colorExpanded = false }
                    ) {
                        palette.forEach { choice ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .background(
                                                    Color(android.graphics.Color.parseColor(choice.hex)),
                                                    CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(choice.name)
                                    }
                                },
                                onClick = {
                                    color = choice.hex
                                    colorName = choice.name
                                    customColorMode = false
                                    val rgb = parseRgb(choice.hex)
                                    customRed = rgb.first
                                    customGreen = rgb.second
                                    customBlue = rgb.third
                                    colorExpanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Custom color…") },
                            onClick = {
                                customColorMode = true
                                if (palette.any { it.name == colorName }) colorName = "Custom"
                                colorExpanded = false
                            }
                        )
                    }
                }

                if (customColorMode) {
                    OutlinedTextField(
                        value = colorName,
                        onValueChange = { colorName = it },
                        label = { Text("Custom color name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        )
                    )
                    RgbSlider("Red", customRed) {
                        customRed = it
                        updateCustomColor()
                    }
                    RgbSlider("Green", customGreen) {
                        customGreen = it
                        updateCustomColor()
                    }
                    RgbSlider("Blue", customBlue) {
                        customBlue = it
                        updateCustomColor()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(Color(customRed, customGreen, customBlue), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(colorName.ifBlank { "Custom" })
                    }
                }

                Text("Priority")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Priority.entries.forEach { p ->
                        val selectedColor = when (p) {
                            Priority.LOW -> lowPriority
                            Priority.MEDIUM -> mediumPriority
                            Priority.HIGH -> highPriority
                        }
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selectedColor,
                                selectedLabelColor = Color(0xFF2B2B2B)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminderOn, onCheckedChange = { reminderOn = it })
                    Text("Remind me")
                }

                if (reminderOn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useDefaultTime,
                            onCheckedChange = { useDefaultTime = it }
                        )
                        Text(
                            "Use default time (%02d:%02d)".format(
                                defaultReminderHour,
                                defaultReminderMinute
                            )
                        )
                    }

                    if (!useDefaultTime) {
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Text("Reminder time  %02d:%02d".format(hour, minute))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && colorName.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        color,
                        colorName.trim(),
                        priority,
                        comment.trim(),
                        reminderOn,
                        if (reminderOn && !useDefaultTime) hour else null,
                        if (reminderOn && !useDefaultTime) minute else null
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        SimpleTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun RgbSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select time", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}
