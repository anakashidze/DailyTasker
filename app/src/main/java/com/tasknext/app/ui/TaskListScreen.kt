package com.tasknext.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tasknext.app.data.Priority
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// More visible than the earlier very pale priority chips while still keeping a soft UI.
private val priorityLow = Color(0xFF8DBBFF)
private val priorityMedium = Color(0xFFFFD45C)
private val priorityHigh = Color(0xFFFF8A8A)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun TaskListScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val pool by viewModel.pool.collectAsState()
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var draggingTaskId by remember { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val displayPool = remember(pool) { pool.sortedBy { it.orderIndex } }
    val tomorrowTask = remember(displayPool, queuedTomorrowId) {
        displayPool.firstOrNull { it.id == queuedTomorrowId }
            ?: displayPool.firstOrNull()
    }

    suspend fun showUndoSnackbar(message: String, undo: () -> Unit) {
        // Keep one clear undo decision visible at a time. A newer action replaces an older snackbar.
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = withTimeoutOrNull(15_000L) {
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "UNDO",
                withDismissAction = false,
                duration = SnackbarDuration.Indefinite
            )
        }
        if (result == SnackbarResult.ActionPerformed) undo()
    }

    fun deleteWithUndo(task: Task) {
        val previousQueuedTomorrowId = queuedTomorrowId
        val job = viewModel.deleteTask(task)
        scope.launch {
            job.join()
            showUndoSnackbar("Task deleted") {
                viewModel.undoDelete(task, previousQueuedTomorrowId)
            }
        }
    }

    fun completeWithUndo(task: Task) {
        val previousQueuedTomorrowId = queuedTomorrowId
        val previousDayCompletedEpochDay = viewModel.settings.dayCompletedEpochDay
        val job = viewModel.completeFromList(task.id)
        scope.launch {
            job.join()
            showUndoSnackbar("Task completed") {
                viewModel.undoCompletion(
                    task = task,
                    previousQueuedTomorrowTaskId = previousQueuedTomorrowId,
                    previousDayCompletedEpochDay = previousDayCompletedEpochDay
                )
            }
        }
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 12.dp)
            ) { data ->
                Snackbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    shape = RoundedCornerShape(14.dp),
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            TextButton(
                                onClick = { data.performAction() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.inversePrimary
                                )
                            ) {
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
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
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) }
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
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) },
                        dragHint = "Long-press and drag down to return to backlog",
                        onDragDown = { viewModel.moveBonusToBacklog(task.id) },
                        onDraggingStateChanged = { isDragging ->
                            draggingTaskId = if (isDragging) task.id else null
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            item {
                Column {
                    SectionTitle("Backlog")
                    Text(
                        "Top item is next. Long-press any task card to drag; drag the first item upward to make it Today.",
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
                val itemModifier = if (draggingTaskId == task.id) {
                    Modifier.zIndex(10f)
                } else {
                    Modifier.animateItemPlacement(animationSpec = tween(durationMillis = 300))
                }

                SwipeableTaskRow(
                    modifier = itemModifier,
                    task = task,
                    tag = if (isTomorrow) {
                        if (queuedTomorrowId != -1L) "TOMORROW" else "NEXT"
                    } else null,
                    onEdit = { editing = task },
                    onDelete = { deleteWithUndo(task) },
                    onComplete = { completeWithUndo(task) },
                    onMakeBonus = if (canMakeBonus) {
                        { viewModel.activateBonusFromPool(task.id) }
                    } else null,
                    dragHint = if (index == 0) {
                        "Long-press and drag up to make Today; drag down to reorder"
                    } else {
                        "Long-press and drag up or down to reorder"
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
                    } else null,
                    onDraggingStateChanged = { isDragging ->
                        draggingTaskId = if (isDragging) task.id else null
                    }
                )
            }
        }
    }

    if (showAdd) {
        AddEditTaskDialog(
            existing = null,
            availableGroups = taskGroups,
            defaultReminderHour = viewModel.settings.defaultReminderHour,
            defaultReminderMinute = viewModel.settings.defaultReminderMinute,
            onAddGroup = { viewModel.addCustomGroup(it) },
            onDismiss = { showAdd = false },
            onSave = { title, group, priority, comment, remOn, h, m ->
                viewModel.addTask(
                    title = title,
                    colorHex = group.colorHex,
                    groupName = group.name,
                    groupIcon = group.iconKey,
                    priority = priority,
                    comment = comment,
                    reminderEnabled = remOn,
                    hour = h,
                    minute = m
                )
                showAdd = false
            }
        )
    }

    editing?.let { task ->
        AddEditTaskDialog(
            existing = task,
            availableGroups = taskGroups,
            defaultReminderHour = viewModel.settings.defaultReminderHour,
            defaultReminderMinute = viewModel.settings.defaultReminderMinute,
            onAddGroup = { viewModel.addCustomGroup(it) },
            onDismiss = { editing = null },
            onSave = { title, group, priority, comment, remOn, h, m ->
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        colorHex = group.colorHex,
                        groupName = group.name,
                        groupIcon = group.iconKey,
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
    modifier: Modifier = Modifier,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    onDragUp: (() -> Unit)? = null,
    onDragDown: (() -> Unit)? = null,
    onDraggingStateChanged: (Boolean) -> Unit = {}
) {
    var rowDragging by remember(task.id) { mutableStateOf(false) }

    val dismissState = rememberDismissState(
        confirmStateChange = { value ->
            if (rowDragging) {
                false
            } else {
                when (value) {
                    DismissValue.DismissedToStart -> {
                        onDelete()
                        true
                    }
                    DismissValue.DismissedToEnd -> {
                        onComplete()
                        true
                    }
                    DismissValue.Default -> true
                }
            }
        }
    )

    SwipeToDismiss(
        modifier = modifier.zIndex(if (rowDragging) 10f else 0f),
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        background = {
            val direction = dismissState.dismissDirection
            if (direction != null && !rowDragging) {
                val isComplete = direction == DismissDirection.StartToEnd
                val bg = if (isComplete) Color(0xFFD7F3DF) else Color(0xFFFFD8D8)
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
            } else {
                Box(Modifier.fillMaxSize())
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
                onDragDown = onDragDown,
                onDraggingChanged = { isDragging ->
                    rowDragging = isDragging
                    onDraggingStateChanged(isDragging)
                }
            )
        }
    )
}

@Composable
private fun TaskRow(
    task: Task,
    tag: String?,
    onEdit: () -> Unit,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    onDragUp: (() -> Unit)? = null,
    onDragDown: (() -> Unit)? = null,
    onDraggingChanged: (Boolean) -> Unit = {}
) {
    val color = remember(task.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(task.colorHex))
        } catch (_: Exception) {
            Color.Gray
        }
    }

    val cardColor = lerp(MaterialTheme.colorScheme.surface, color, 0.10f)

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val rowSpacingPx = with(density) { 8.dp.toPx() }
    val fallbackThresholdPx = with(density) { 72.dp.toPx() }
    val dragElevationPx = with(density) { 10.dp.toPx() }
    var rowHeightPx by remember(task.id) { mutableIntStateOf(0) }
    var dragOffset by remember(task.id) { mutableFloatStateOf(0f) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    val currentDragUp by rememberUpdatedState(onDragUp)
    val currentDragDown by rememberUpdatedState(onDragDown)

    val canDrag = onDragUp != null || onDragDown != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }
            .graphicsLayer(
                translationY = if (dragging) dragOffset else 0f,
                scaleX = if (dragging) 1.018f else 1f,
                scaleY = if (dragging) 1.018f else 1f,
                shadowElevation = if (dragging) dragElevationPx else 0f,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (dragging) lerp(MaterialTheme.colorScheme.surface, color, 0.16f) else cardColor,
                RoundedCornerShape(12.dp)
            )
            .then(
                if (canDrag) {
                    Modifier.pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = true
                                onDraggingChanged(true)
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                onDraggingChanged(false)
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                onDraggingChanged(false)
                                dragOffset = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y

                            // Swap only after the dragged card has travelled roughly one full
                            // row. This makes the card follow the finger before any reorder occurs.
                            val swapDistance = if (rowHeightPx > 0) {
                                rowHeightPx.toFloat() + rowSpacingPx
                            } else {
                                fallbackThresholdPx
                            }

                            if (dragOffset <= -swapDistance && currentDragUp != null) {
                                currentDragUp?.invoke()
                                // The item's layout position moves one row after reordering.
                                // Compensate by one row so the card stays under the finger.
                                dragOffset += swapDistance
                            } else if (dragOffset >= swapDistance && currentDragDown != null) {
                                currentDragDown?.invoke()
                                dragOffset -= swapDistance
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                GroupIcon(
                    key = task.groupIcon,
                    contentDescription = task.groupName,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(6.dp))

        // A normal tap on the center opens Edit. Long-pressing anywhere on the card starts drag.
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PriorityPill(task.priority)
                Text(
                    task.groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (tag != null) StatusPill(tag)

            if (onMakeBonus != null) {
                IconButton(onClick = onMakeBonus, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = "Make bonus today",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
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
            text = tag,
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
            color = Color(0xFF202020)
        )
    }
}
