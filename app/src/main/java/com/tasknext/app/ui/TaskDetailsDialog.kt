package com.tasknext.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tasknext.app.R
import com.tasknext.app.data.Task
import com.tasknext.app.data.TaskGroups
import com.tasknext.app.data.TaskSize
import com.tasknext.app.viewmodel.TaskViewModel

/** Load by stable ID so a stale reminder cannot resurrect or edit a removed task. */
@Composable
fun TaskDetailsDialog(taskId: Long, viewModel: TaskViewModel, onDismiss: () -> Unit) {
    var task by remember(taskId) { mutableStateOf<Task?>(null) }
    var loaded by remember(taskId) { mutableStateOf(false) }
    var editing by rememberSaveable(taskId) { mutableStateOf(false) }
    val groups by viewModel.taskGroups.collectAsState()
    val clock = rememberAppClock()
    val notificationAccess = rememberNotificationAvailability()
    val context = LocalContext.current
    LaunchedEffect(taskId, viewModel) {
        viewModel.observeTask(taskId).collect { task = it; loaded = true }
    }
    val current = task
    val locked = current?.slot == 0 && viewModel.isTodayLocked()
    if (editing && current != null && !locked) {
        AddEditTaskDialog(
            existing = current,
            availableGroups = groups,
            defaultReminderHour = viewModel.settings.defaultReminderHour,
            defaultReminderMinute = viewModel.settings.defaultReminderMinute,
            onAddGroup = { viewModel.addCustomGroup(it) },
            onRemoveGroup = { viewModel.removeCustomGroup(it) },
            onDismiss = { editing = false },
            onSave = { title, group, size, comment, reminder, hour, minute ->
                viewModel.updateTask(current.copy(title = title, groupName = group.name,
                    groupIcon = group.iconKey, colorHex = group.colorHex, taskSize = size,
                    comment = comment, reminderEnabled = reminder, reminderHour = hour, reminderMinute = minute))
                editing = false
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.task_details)) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        !loaded -> CircularProgressIndicator()
                        current == null -> Text(stringResource(R.string.task_no_longer_available))
                        else -> {
                            Text(current.title, style = MaterialTheme.typography.titleLarge)
                            Text(TaskGroups.displayName(context, current.groupName, current.groupIcon) + " · " +
                                stringResource(if (current.taskSize == TaskSize.BIG) R.string.task_size_big else R.string.task_size_small),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (current.comment.isNotBlank()) Text(current.comment)
                            // Observe permission changes when returning from Android Settings.
                            val reminder = remember(current, clock, notificationAccess) {
                                taskReminderSummary(context, current, viewModel.settings, clock)
                            }
                            Text(reminder, style = MaterialTheme.typography.bodySmall)
                            if (locked) Text(stringResource(R.string.task_details_locked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
            dismissButton = {
                if (current != null && !locked) TextButton(onClick = { editing = true }) {
                    Text(stringResource(R.string.edit_task))
                }
            }
        )
    }
}
