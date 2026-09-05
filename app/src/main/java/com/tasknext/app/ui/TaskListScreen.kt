package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.tasknext.app.data.Priority
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlin.math.abs

private val priorityLow = Color(0xFFDDEBFF)
private val priorityMedium = Color(0xFFFFF1B8)
private val priorityHigh = Color(0xFFFFD7D7)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TaskListScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val pool by viewModel.pool.collectAsState()
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // Backlog is now shown in its real queue order: the first item is next.
    val displayPool = remember(pool) { pool.sortedBy { it.orderIndex } }
    val tomorrowTask = remember(displayPool, queuedTomorrowId) {
        displayPool.firstOrNull { it.id == queuedTomorrowId }
            ?: displayPool.firstOrNull()
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
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mandatory?.let { task ->
                item { SectionTitle("Today") }
                item(key = "today-${task.id}") {
                    SwipeableTaskRow(
                        task = task,
                        tag = "TODAY",
                        onEdit = { editing = task },
                        onDelete = { viewModel.deleteTask(task) },
                        onComplete = { viewModel.completeFromList(task.id) }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (bonusTasks.isNotEmpty()) {
                item { SectionTitle("Bonus today") }
                items(bonusTasks, key = { "bonus-${it.id}" }) { task ->
                    SwipeableTaskRow(
                        task = task,
                        tag = "BONUS",
                        onEdit = { editing = task },
                        onDelete = { viewModel.deleteTask(task) },
                        onComplete = { viewModel.completeFromList(task.id) },
                        dragHint = "Drag down to return to backlog",
                        onDragDown = { viewModel.moveBonusToBacklog(task.id) }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            item {
                Column {
                    SectionTitle("Backlog")
                    Text(
                        "Top item is next. Drag the handle to reorder; drag the first item upward to make it Today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (displayPool.isEmpty()) {
                item {
                    Text(
                        "No backlog tasks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            itemsIndexed(displayPool, key = { _, task -> "pool-${task.id}" }) { index, task ->
                val isTomorrow = task.id == tomorrowTask?.id
                val canMakeBonus = bonusTasks.size < 2 && task.id != queuedTomorrowId

                SwipeableTaskRow(
                    task = task,
                    tag = if (isTomorrow) {
                        if (queuedTomorrowId != -1L) "TOMORROW" else "NEXT"
                    } else null,
                    onEdit = { editing = task },
                    onDelete = { viewModel.deleteTask(task) },
                    onComplete = { viewModel.completeFromList(task.id) },
                    onMakeBonus = if (canMakeBonus) {
                        { viewModel.activateBonusFromPool(task.id) }
                    } else null,
                    dragHint = if (index == 0) {
                        "Drag up to make Today; down to reorder"
                    } else {
                        "Drag up or down to reorder"
                    },
                    onDragUp = {
                        if (index == 0) {
                            viewModel.replaceTodayWith(task.id)
                        } else {
                            val ids = displayPool.map { it.id }.toMutableList()
                            ids[index - 1] = task.id
                            ids[index] = displayPool[index - 1].id
                            viewModel.reorderPool(ids)
                        }
                    },
                    onDragDown = if (index < displayPool.lastIndex) {
                        {
                            val ids = displayPool.map { it.id }.toMutableList()
                            ids[index + 1] = task.id
                            ids[index] = displayPool[index + 1].id
                            viewModel.reorderPool(ids)
                        }
                    } else null
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
            onSave = { title, color, colorName, priority, comment, remOn, h, m ->
                viewModel.addTask(title, color, colorName, priority, comment, remOn, h, m)
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
            onSave = { title, color, colorName, priority, comment, remOn, h, m ->
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        colorHex = color,
                        colorName = colorName,
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeableTaskRow(
    task: Task,
    tag: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    onDragUp: (() -> Unit)? = null,
    onDragDown: (() -> Unit)? = null
) {
    var askDelete by remember(task.id) { mutableStateOf(false) }
    var askComplete by remember(task.id) { mutableStateOf(false) }

    val dismissState = rememberDismissState(
        confirmStateChange = { value ->
            when (value) {
                DismissValue.DismissedToStart -> askDelete = true
                DismissValue.DismissedToEnd -> askComplete = true
                DismissValue.Default -> Unit
            }
            false // Always snap back; the confirmed action changes the data instead.
        }
    )

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        background = {
            val direction = dismissState.dismissDirection
            val isComplete = direction == DismissDirection.StartToEnd
            val bg = if (isComplete) Color(0xFFE1F5E7) else Color(0xFFFFE2E2)
            val icon = if (isComplete) Icons.Default.Check else Icons.Default.Delete
            val label = if (isComplete) "Complete" else "Delete"
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isComplete) Arrangement.Start else Arrangement.End
            ) {
                if (!isComplete) Text(label, fontWeight = FontWeight.SemiBold)
                if (!isComplete) Spacer(Modifier.width(8.dp))
                Icon(icon, contentDescription = label)
                if (isComplete) Spacer(Modifier.width(8.dp))
                if (isComplete) Text(label, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissContent = {
            TaskRow(
                task = task,
                tag = tag,
                onEdit = onEdit,
                onMakeBonus = onMakeBonus,
                dragHint = dragHint,
                onDragUp = onDragUp,
                onDragDown = onDragDown
            )
        }
    )

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete task?") },
            text = { Text("Delete “${task.title}” permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (askComplete) {
        AlertDialog(
            onDismissRequest = { askComplete = false },
            title = { Text("Mark as completed?") },
            text = {
                Text(
                    if (task.slot == null) {
                        "Mark “${task.title}” as completed without making it Today’s task?"
                    } else {
                        "Mark “${task.title}” as completed?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askComplete = false
                    onComplete()
                }) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { askComplete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TaskRow(
    task: Task,
    tag: String?,
    onEdit: () -> Unit,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    onDragUp: (() -> Unit)? = null,
    onDragDown: (() -> Unit)? = null
) {
    val color = remember(task.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(task.colorHex))
        } catch (_: Exception) {
            Color.Gray
        }
    }
    val thresholdPx = with(LocalDensity.current) { 54.dp.toPx() }
    var dragOffset by remember(task.id) { mutableFloatStateOf(0f) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    val currentDragUp by rememberUpdatedState(onDragUp)
    val currentDragDown by rememberUpdatedState(onDragDown)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = if (dragging) dragOffset * 0.25f else 0f }
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(36.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(8.dp))

        if (onDragUp != null || onDragDown != null) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = dragHint ?: "Drag to move task",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                dragOffset = 0f
                            }
                        ) { _, dragAmount ->
                            dragOffset += dragAmount.y
                            if (dragOffset <= -thresholdPx && currentDragUp != null) {
                                currentDragUp?.invoke()
                                dragOffset = 0f
                            } else if (dragOffset >= thresholdPx && currentDragDown != null) {
                                currentDragDown?.invoke()
                                dragOffset = 0f
                            }
                        }
                    }
            )
            Spacer(Modifier.width(4.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            PriorityPill(task.priority)
        }

        if (onMakeBonus != null) {
            IconButton(onClick = onMakeBonus, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.StarBorder,
                    contentDescription = "Make bonus today",
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        if (tag != null) {
            StatusPill(tag)
            Spacer(Modifier.width(4.dp))
        }

        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun StatusPill(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            tag,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PriorityPill(priority: Priority) {
    val background = when (priority) {
        Priority.LOW -> priorityLow
        Priority.MEDIUM -> priorityMedium
        Priority.HIGH -> priorityHigh
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            priority.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF2B2B2B)
        )
    }
}
