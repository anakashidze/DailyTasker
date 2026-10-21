package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tasknext.app.R
import com.tasknext.app.data.TaskSize
import com.tasknext.app.data.Task
import com.tasknext.app.data.TaskGroupDefinition
import com.tasknext.app.data.TaskGroups

private data class GroupColorChoice(val hex: String, val label: String)

private val groupColors = listOf(
    GroupColorChoice("#4F46E5", "Indigo"),
    GroupColorChoice("#8B5E3C", "Brown"),
    GroupColorChoice("#2E9D60", "Green"),
    GroupColorChoice("#2563EB", "Blue"),
    GroupColorChoice("#E58A22", "Orange"),
    GroupColorChoice("#7C3AED", "Purple"),
    GroupColorChoice("#DC3545", "Red"),
    GroupColorChoice("#0F9D8A", "Teal"),
    GroupColorChoice("#DB4F86", "Pink"),
    GroupColorChoice("#667085", "Gray")
)

private val groupIconChoices = listOf(
    "task", "home", "health", "work", "star", "fitness",
    "bike", "shopping", "study", "food", "travel", "pet", "book",
    "car", "calendar", "flag", "bolt", "place", "person", "phone",
    "mail", "build", "palette", "idea", "public", "bookmark", "label",
    "folder", "schedule", "circle", "list", "computer", "money",
    "headphones", "camera", "lock", "search", "refresh", "share",
    "send", "notifications"
)

@Composable
private fun taskSizeLabel(taskSize: TaskSize): String = when (taskSize) {
    TaskSize.BIG -> stringResource(R.string.task_size_big)
    TaskSize.SMALL -> stringResource(R.string.task_size_small)
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Gray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    existing: Task?,
    availableGroups: List<TaskGroupDefinition>,
    defaultReminderHour: Int,
    defaultReminderMinute: Int,
    onAddGroup: (TaskGroupDefinition) -> Unit,
    onRemoveGroup: (TaskGroupDefinition) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        group: TaskGroupDefinition,
        taskSize: TaskSize,
        comment: String,
        reminderOn: Boolean,
        hour: Int?,
        minute: Int?
    ) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var selectedGroup by remember(existing?.id) {
        mutableStateOf(
            existing?.let {
                TaskGroupDefinition(
                    name = it.groupName,
                    colorHex = it.colorHex,
                    iconKey = it.groupIcon
                )
            } ?: TaskGroups.default
        )
    }
    var taskSize by remember(existing?.id) { mutableStateOf(existing?.taskSize ?: TaskSize.BIG) }
    var comment by remember(existing?.id) { mutableStateOf(existing?.comment ?: "") }
    var reminderOn by remember(existing?.id) { mutableStateOf(existing?.reminderEnabled ?: true) }
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
    var groupExpanded by remember { mutableStateOf(false) }
    var taskSizeExpanded by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupIcon by remember { mutableStateOf("star") }
    var newGroupColor by remember { mutableStateOf("#7C3AED") }
    val titleFocusRequester = remember { FocusRequester() }
    val groupNameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(existing?.id) {
        if (existing == null) {
            // Let the dialog and text field enter the composition before requesting focus.
            withFrameNanos { }
            titleFocusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
        }
    }
    LaunchedEffect(showNewGroup) {
        if (showNewGroup) {
            withFrameNanos { }
            groupNameFocusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
        }
    }

    val menuGroups = remember(availableGroups, selectedGroup) {
        if (availableGroups.any { it.name.equals(selectedGroup.name, ignoreCase = true) }) {
            availableGroups
        } else {
            listOf(selectedGroup) + availableGroups
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) stringResource(R.string.add_task) else stringResource(R.string.edit_task),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.task_size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { taskSizeExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = taskSizePalette(taskSize).container,
                                    contentColor = taskSizePalette(taskSize).content
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    taskSizePalette(taskSize).content.copy(alpha = 0.22f)
                                )
                            ) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(taskSizePalette(taskSize).content, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    taskSizeLabel(taskSize),
                                    color = taskSizePalette(taskSize).content,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = taskSizeExpanded,
                                onDismissRequest = { taskSizeExpanded = false }
                            ) {
                                TaskSize.entries.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text(taskSizeLabel(choice)) },
                                        leadingIcon = {
                                            Box(
                                                Modifier
                                                    .size(9.dp)
                                                    .background(taskSizePalette(choice).content, CircleShape)
                                            )
                                        },
                                        onClick = {
                                            taskSize = choice
                                            taskSizeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.group),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { groupExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                GroupIcon(
                                    key = selectedGroup.iconKey,
                                    contentDescription = null,
                                    tint = parseColor(selectedGroup.colorHex),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(selectedGroup.name, maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = groupExpanded,
                                onDismissRequest = { groupExpanded = false }
                            ) {
                                menuGroups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.name) },
                                        leadingIcon = {
                                            GroupIcon(
                                                key = group.iconKey,
                                                contentDescription = null,
                                                tint = parseColor(group.colorHex),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (!group.builtIn) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        onRemoveGroup(group)
                                                        if (selectedGroup.name.equals(group.name, ignoreCase = true)) {
                                                            selectedGroup = TaskGroups.default
                                                        }
                                                        groupExpanded = false
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Remove ${group.name} group",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        onClick = {
                                            selectedGroup = group
                                            groupExpanded = false
                                            showNewGroup = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_new_group)) },
                                    leadingIcon = {
                                        GroupIcon(
                                            key = "star",
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        groupExpanded = false
                                        showNewGroup = true
                                    }
                                )
                            }
                        }
                    }
                }

                if (showNewGroup) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(stringResource(R.string.new_group), style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(groupNameFocusRequester),
                                label = { Text(stringResource(R.string.group_name)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                )
                            )

                            Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelMedium)
                            val iconColumns = remember {
                                groupIconChoices.chunked(2)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                iconColumns.forEach { columnIcons ->
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        columnIcons.forEach { key ->
                                            val selected = newGroupIcon == key
                                            Surface(
                                                shape = CircleShape,
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                                                border = if (selected) {
                                                    androidx.compose.foundation.BorderStroke(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                } else null
                                            ) {
                                                IconButton(
                                                    onClick = { newGroupIcon = key },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    GroupIcon(
                                                        key = key,
                                                        contentDescription = "Choose $key icon",
                                                        tint = parseColor(newGroupColor),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (columnIcons.size == 1) {
                                            Spacer(Modifier.size(38.dp))
                                        }
                                    }
                                }
                            }

                            Text(stringResource(R.string.color), style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                groupColors.forEach { choice ->
                                    val selected = choice.hex.equals(newGroupColor, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(parseColor(choice.hex), CircleShape)
                                            .then(
                                                if (selected) {
                                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                } else Modifier
                                            )
                                            .clickable { newGroupColor = choice.hex }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showNewGroup = false }) { Text(stringResource(R.string.cancel)) }
                                TextButton(
                                    enabled = newGroupName.isNotBlank() &&
                                        menuGroups.none { it.name.equals(newGroupName.trim(), ignoreCase = true) },
                                    onClick = {
                                        val group = TaskGroupDefinition(
                                            name = newGroupName.trim(),
                                            colorHex = newGroupColor,
                                            iconKey = newGroupIcon
                                        )
                                        onAddGroup(group)
                                        selectedGroup = group
                                        newGroupName = ""
                                        showNewGroup = false
                                    }
                                ) {
                                    Text(stringResource(R.string.add))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.comment_optional)) },
                    minLines = 1,
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )

                // One compact reminder control. New tasks use the global default time;
                // tapping the time turns it into a task-specific override.
                val displayedReminderHour = if (useDefaultTime) defaultReminderHour else hour
                val displayedReminderMinute = if (useDefaultTime) defaultReminderMinute else minute
                ReminderRow(
                    checked = reminderOn,
                    hour = displayedReminderHour,
                    minute = displayedReminderMinute,
                    onToggle = { reminderOn = !reminderOn },
                    onTimeClick = {
                        hour = displayedReminderHour
                        minute = displayedReminderMinute
                        showTimePicker = true
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        selectedGroup,
                        taskSize,
                        comment.trim(),
                        reminderOn,
                        if (reminderOn && !useDefaultTime) hour else null,
                        if (reminderOn && !useDefaultTime) minute else null
                    )
                }
            ) {
                Text(if (existing == null) stringResource(R.string.add) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                useDefaultTime = false
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun ReminderRow(
    checked: Boolean,
    hour: Int,
    minute: Int,
    onToggle: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
                .padding(vertical = 5.dp),
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
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.remind_me),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (checked) {
            TextButton(
                onClick = onTimeClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "%02d:%02d".format(hour, minute),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
                Text(stringResource(R.string.select_time), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}
