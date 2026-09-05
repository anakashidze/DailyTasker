package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.LOW -> lowPriority
    Priority.MEDIUM -> mediumPriority
    Priority.HIGH -> highPriority
}

private fun priorityLabel(priority: Priority): String =
    priority.name.lowercase().replaceFirstChar { it.uppercase() }

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
    var priorityExpanded by remember { mutableStateOf(false) }
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
        title = {
            Text(
                if (existing == null) "Add task" else "Edit task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                // Keep the two most frequently changed attributes together.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Priority",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { priorityExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = priorityColor(priority)
                                )
                            ) {
                                Text(priorityLabel(priority), color = Color(0xFF2B2B2B))
                            }
                            DropdownMenu(
                                expanded = priorityExpanded,
                                onDismissRequest = { priorityExpanded = false }
                            ) {
                                Priority.entries.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text(priorityLabel(choice)) },
                                        leadingIcon = {
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .background(priorityColor(choice), CircleShape)
                                            )
                                        },
                                        onClick = {
                                            priority = choice
                                            priorityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            "Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.fillMaxWidth()) {
                            val swatch = try {
                                Color(android.graphics.Color.parseColor(color))
                            } catch (_: Exception) {
                                Color.Gray
                            }
                            OutlinedButton(
                                onClick = { colorExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Box(Modifier.size(16.dp).background(swatch, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    colorName.ifBlank { "Custom" },
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = colorExpanded,
                                onDismissRequest = { colorExpanded = false }
                            ) {
                                palette.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text(choice.name) },
                                        leadingIcon = {
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .background(
                                                        Color(android.graphics.Color.parseColor(choice.hex)),
                                                        CircleShape
                                                    )
                                            )
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
                    }
                }

                if (customColorMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            OutlinedTextField(
                                value = colorName,
                                onValueChange = { colorName = it },
                                modifier = Modifier.fillMaxWidth(),
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
                        }
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comment (optional)") },
                    minLines = 1,
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        CompactCheckRow(
                            checked = reminderOn,
                            text = "Remind me",
                            onToggle = { reminderOn = !reminderOn }
                        )

                        if (reminderOn) {
                            CompactCheckRow(
                                checked = useDefaultTime,
                                text = "Use default time (%02d:%02d)".format(
                                    defaultReminderHour,
                                    defaultReminderMinute
                                ),
                                onToggle = { useDefaultTime = !useDefaultTime }
                            )

                            if (!useDefaultTime) {
                                TextButton(
                                    onClick = { showTimePicker = true },
                                    modifier = Modifier.align(Alignment.Start),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Reminder time  %02d:%02d".format(hour, minute))
                                }
                            }
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
private fun CompactCheckRow(
    checked: Boolean,
    text: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RgbSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value.toString(), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.height(28.dp)
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
