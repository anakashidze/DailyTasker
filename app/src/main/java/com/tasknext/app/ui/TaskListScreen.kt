package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val pool by viewModel.pool.collectAsState()
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // The underlying queue is soonest-first. We show it reversed so the bottom item is next/tomorrow.
    val displayPool = remember(pool) { pool.sortedByDescending { it.orderIndex } }
    val tomorrowTask = remember(pool, queuedTomorrowId) {
        pool.firstOrNull { it.id == queuedTomorrowId }
            ?: pool.minByOrNull { it.orderIndex }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mandatory?.let { task ->
                item { Text("Today's mandatory task", style = MaterialTheme.typography.titleMedium) }
                item {
                    TaskRow(
                        task,
                        tag = "TODAY",
                        onEdit = { editing = task },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (bonusTasks.isNotEmpty()) {
                item { Text("Today's bonus tasks", style = MaterialTheme.typography.titleMedium) }
                items(bonusTasks, key = { "bonus-${it.id}" }) { task ->
                    TaskRow(
                        task,
                        tag = "BONUS",
                        onEdit = { editing = task },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item {
                Text(
                    "Backlog — last item is next",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (displayPool.isEmpty()) {
                item {
                    Text(
                        "No backlog tasks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(displayPool, key = { _, task -> "pool-${task.id}" }) { displayIndex, task ->
                val isTomorrow = task.id == tomorrowTask?.id
                val realIndex = pool.indexOfFirst { it.id == task.id }

                TaskRow(
                    task = task,
                    tag = if (isTomorrow) {
                        if (queuedTomorrowId != -1L) "TOMORROW" else "NEXT"
                    } else null,
                    onEdit = { editing = task },
                    onDelete = { viewModel.deleteTask(task) },
                    onMakeBonus = if (
                        bonusTasks.size < 2 &&
                        task.id != queuedTomorrowId
                    ) {
                        { viewModel.activateBonusFromPool(task.id) }
                    } else {
                        null
                    },
                    onMoveUp = if (displayIndex > 0 && realIndex >= 0 && realIndex < pool.lastIndex) {
                        {
                            val ids = pool.map { it.id }.toMutableList()
                            ids.removeAt(realIndex)
                            ids.add(realIndex + 1, task.id)
                            viewModel.reorderPool(ids)
                        }
                    } else {
                        null
                    },
                    onMoveDown = if (displayIndex < displayPool.lastIndex && realIndex > 0) {
                        {
                            val ids = pool.map { it.id }.toMutableList()
                            ids.removeAt(realIndex)
                            ids.add(realIndex - 1, task.id)
                            viewModel.reorderPool(ids)
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    if (showAdd) {
        AddEditTaskDialog(
            existing = null,
            defaultReminderHour = viewModel.settings.defaultReminderHour,
            defaultReminderMinute = viewModel.settings.defaultReminderMinute,
            onDismiss = { showAdd = false },
            onSave = { title, color, priority, comment, remOn, h, m ->
                viewModel.addTask(title, color, priority, comment, remOn, h, m)
                showAdd = false
            }
        )
    }

    editing?.let { task ->
        AddEditTaskDialog(
            existing = task,
            defaultReminderHour = viewModel.settings.defaultReminderHour,
            defaultReminderMinute = viewModel.settings.defaultReminderMinute,
            onDismiss = { editing = null },
            onSave = { title, color, priority, comment, remOn, h, m ->
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        colorHex = color,
                        priority = priority,
                        comment = comment,
                        reminderEnabled = remOn,
                        reminderHour = h,
                        reminderMinute = m
                    )
                )
                editing = null
            }
        )
    }
}

@Composable
private fun TaskRow(
    task: Task,
    tag: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakeBonus: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val color = remember(task.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(task.colorHex))
        } catch (_: Exception) {
            Color.Gray
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(32.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(task.priority.name, style = MaterialTheme.typography.labelSmall)
        }

        if (onMoveDown != null) {
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move toward tomorrow")
            }
        }
        if (onMoveUp != null) {
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move away from tomorrow")
            }
        }
        if (onMakeBonus != null) {
            IconButton(onClick = onMakeBonus) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Add as bonus today")
            }
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
