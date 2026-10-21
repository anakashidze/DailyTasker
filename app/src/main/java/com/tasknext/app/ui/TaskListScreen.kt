package com.tasknext.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tasknext.app.data.TaskSize
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val overdueTasks by viewModel.overdueTasks.collectAsState()
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var draggingTaskId by remember { mutableStateOf<Long?>(null) }
    var todayCardTopPx by remember { mutableStateOf<Float?>(null) }
    var firstBacklogCardTopPx by remember { mutableStateOf<Float?>(null) }
    var backlogHeaderTopPx by remember { mutableStateOf<Float?>(null) }
    val taskCardBounds = remember { mutableMapOf<Long, Rect>() }

    var pendingTodayTask by remember { mutableStateOf<Task?>(null) }
    var pendingPreviousTodayId by remember { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val sortedPool = remember(pool) { pool.sortedBy { it.orderIndex } }
    var localPool by remember { mutableStateOf<List<Task>>(sortedPool) }
    var pendingPersistedOrder by remember { mutableStateOf<List<Long>?>(null) }

    LaunchedEffect(
        sortedPool,
        mandatory,
        draggingTaskId,
        pendingPersistedOrder,
        pendingTodayTask,
        pendingPreviousTodayId
    ) {
        if (draggingTaskId != null) return@LaunchedEffect
        val incomingIds = sortedPool.map { it.id }

        pendingTodayTask?.let { promoted ->
            val previousTodayId = pendingPreviousTodayId
            val todayConfirmed = mandatory?.id == promoted.id
            val poolConfirmed = promoted.id !in incomingIds &&
                (previousTodayId == null || previousTodayId in incomingIds)

            if (todayConfirmed && poolConfirmed) {
                localPool = sortedPool
                pendingTodayTask = null
                pendingPreviousTodayId = null
                pendingPersistedOrder = null
            } else {
                val incomingById = sortedPool.associateBy { it.id }
                localPool = localPool.map { incomingById[it.id] ?: it }
            }
            return@LaunchedEffect
        }

        val pendingIds = pendingPersistedOrder
        when {
            pendingIds == null -> localPool = sortedPool
            incomingIds == pendingIds -> {
                localPool = sortedPool
                pendingPersistedOrder = null
            }
            incomingIds.toSet() != pendingIds.toSet() -> {
                localPool = sortedPool
                pendingPersistedOrder = null
            }
            else -> {
                val incomingById = sortedPool.associateBy { it.id }
                localPool = localPool.mapNotNull { incomingById[it.id] }
            }
        }
    }

    val displayPool = localPool
    val displayedMandatory = pendingTodayTask ?: mandatory
    val tomorrowTask = remember(displayPool, queuedTomorrowId) {
        displayPool.firstOrNull { it.id == queuedTomorrowId } ?: displayPool.firstOrNull()
    }

    SideEffect {
        val activeIds = buildSet {
            displayedMandatory?.let { add(it.id) }
            bonusTasks.forEach { add(it.id) }
            overdueTasks.forEach { add(it.id) }
            displayPool.forEach { add(it.id) }
        }
        taskCardBounds.keys.retainAll(activeIds)
    }

    fun backlogInsertionIndexForTop(draggedTaskId: Long?, draggedTopPx: Float): Int {
        val candidates = localPool.filter { it.id != draggedTaskId }
        var index = 0
        for (candidate in candidates) {
            val top = taskCardBounds[candidate.id]?.top ?: continue
            if (draggedTopPx >= top) index++ else break
        }
        return index.coerceIn(0, candidates.size)
    }

    fun reorderBacklogOnRelease(task: Task, draggedTopPx: Float) {
        val others = localPool.filter { it.id != task.id }.toMutableList()
        val targetIndex = backlogInsertionIndexForTop(task.id, draggedTopPx)
        others.add(targetIndex.coerceIn(0, others.size), task)
        val reordered = others.mapIndexed { index, item -> item.copy(orderIndex = index) }
        val finalOrder = reordered.map { it.id }
        if (finalOrder != localPool.map { it.id }) {
            localPool = reordered
            pendingPersistedOrder = finalOrder
            viewModel.reorderPool(finalOrder)
        }
    }

    suspend fun showUndoSnackbar(message: String, undo: () -> Unit) {
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
        val previousStreakCount = viewModel.settings.streakCount
        val previousStreakLastCompletedEpochDay = viewModel.settings.streakLastCompletedEpochDay
        val job = viewModel.completeFromList(task.id)
        scope.launch {
            job.join()
            showUndoSnackbar("Task completed") {
                viewModel.undoCompletion(
                    task = task,
                    previousQueuedTomorrowTaskId = previousQueuedTomorrowId,
                    previousDayCompletedEpochDay = previousDayCompletedEpochDay,
                    previousStreakCount = previousStreakCount,
                    previousStreakLastCompletedEpochDay = previousStreakLastCompletedEpochDay
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 12.dp)
            ) { data ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            data.visuals.message,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        data.visuals.actionLabel?.let { label ->
                            TextButton(
                                onClick = { data.performAction() },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.inversePrimary
                                )
                            ) {
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
            if (overdueTasks.isNotEmpty()) {
                item { SectionTitle("Overdue") }
                items(overdueTasks, key = { "overdue-${it.id}" }) { task ->
                    SwipeableTaskRow(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            taskCardBounds[task.id] = coordinates.boundsInRoot()
                        },
                        task = task,
                        tag = "OVERDUE",
                        onEdit = { editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            displayedMandatory?.let { task ->
                item { SectionTitle("Today") }
                item(key = "today-${task.id}") {
                    SwipeableTaskRow(
                        modifier = (if (draggingTaskId == task.id) Modifier.zIndex(10f) else Modifier)
                            .onGloballyPositioned { coordinates ->
                                todayCardTopPx = coordinates.positionInRoot().y
                                taskCardBounds[task.id] = coordinates.boundsInRoot()
                            },
                        task = task,
                        tag = "TODAY",
                        onEdit = { editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) },
                        onMakeBonus = if (bonusTasks.size < 2) {
                            { viewModel.moveTodayToBonus(task.id) }
                        } else null,
                        dragHint = "Long-press and drag into Backlog; release to move",
                        onDragRelease = { offsetY ->
                            val startTop = taskCardBounds[task.id]?.top ?: todayCardTopPx
                            val headerTop = backlogHeaderTopPx
                            if (startTop != null && headerTop != null) {
                                val draggedTop = startTop + offsetY
                                if (draggedTop >= headerTop) {
                                    val insertionIndex = backlogInsertionIndexForTop(null, draggedTop)
                                    viewModel.moveTodayToBacklogAt(task.id, insertionIndex)
                                }
                            }
                        },
                        onDraggingStateChanged = { isDragging ->
                            draggingTaskId = if (isDragging) task.id else null
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (bonusTasks.isNotEmpty()) {
                item { SectionTitle("Bonus today") }
                items(bonusTasks, key = { "bonus-${it.id}" }) { task ->
                    SwipeableTaskRow(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            taskCardBounds[task.id] = coordinates.boundsInRoot()
                        },
                        task = task,
                        tag = "BONUS",
                        onEdit = { editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) },
                        dragHint = "Long-press and drag into Backlog; release to move",
                        onDragRelease = { offsetY ->
                            val startTop = taskCardBounds[task.id]?.top
                            val headerTop = backlogHeaderTopPx
                            if (startTop != null && headerTop != null) {
                                val draggedTop = startTop + offsetY
                                if (draggedTop >= headerTop) {
                                    val insertionIndex = backlogInsertionIndexForTop(null, draggedTop)
                                    viewModel.moveBonusToBacklogAt(task.id, insertionIndex)
                                }
                            }
                        },
                        onDraggingStateChanged = { isDragging ->
                            draggingTaskId = if (isDragging) task.id else null
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            item {
                Column(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        backlogHeaderTopPx = coordinates.positionInRoot().y
                    }
                ) {
                    SectionTitle("Backlog")
                    Text(
                        "Top item is next. Long-press a task to drag; release to drop.",
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
                val baseItemModifier = if (draggingTaskId == task.id) {
                    Modifier.zIndex(10f)
                } else {
                    Modifier.animateItemPlacement(animationSpec = tween(durationMillis = 170))
                }
                val itemModifier = baseItemModifier.onGloballyPositioned { coordinates ->
                    taskCardBounds[task.id] = coordinates.boundsInRoot()
                    if (index == 0 && draggingTaskId != task.id) {
                        firstBacklogCardTopPx = coordinates.positionInRoot().y
                    }
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
                    dragHint = "Long-press and drag; release to drop",
                    onDragRelease = { offsetY ->
                        val startTop = taskCardBounds[task.id]?.top
                        if (startTop != null) {
                            val draggedTop = startTop + offsetY
                            val todayTop = todayCardTopPx
                            if (displayedMandatory != null && todayTop != null && draggedTop <= todayTop) {
                                val sourceIndex = localPool.indexOfFirst { it.id == task.id }
                                if (sourceIndex >= 0) {
                                    val previousToday = displayedMandatory
                                    val updatedPool = localPool.toMutableList().apply {
                                        removeAll { it.id == task.id }
                                        if (previousToday != null && previousToday.id != task.id) {
                                            add(
                                                sourceIndex.coerceIn(0, size),
                                                previousToday.copy(slot = null, overdueSinceEpochDay = null)
                                            )
                                        }
                                    }.mapIndexed { newIndex, poolTask ->
                                        poolTask.copy(orderIndex = newIndex)
                                    }

                                    localPool = updatedPool
                                    pendingTodayTask = task.copy(slot = 0, overdueSinceEpochDay = null)
                                    pendingPreviousTodayId = previousToday?.id
                                    pendingPersistedOrder = null
                                    viewModel.replaceTodayWith(task.id, updatedPool.map { it.id })
                                }
                            } else {
                                reorderBacklogOnRelease(task, draggedTop)
                            }
                        }
                    },
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
            onRemoveGroup = { viewModel.removeCustomGroup(it) },
            onDismiss = { showAdd = false },
            onSave = { title, group, taskSize, comment, remOn, h, m ->
                viewModel.addTask(
                    title = title,
                    colorHex = group.colorHex,
                    groupName = group.name,
                    groupIcon = group.iconKey,
                    taskSize = taskSize,
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
            onRemoveGroup = { viewModel.removeCustomGroup(it) },
            onDismiss = { editing = null },
            onSave = { title, group, taskSize, comment, remOn, h, m ->
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        colorHex = group.colorHex,
                        groupName = group.name,
                        groupIcon = group.iconKey,
                        taskSize = taskSize,
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
    onDragRelease: ((Float) -> Unit)? = null,
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
                onDragRelease = onDragRelease,
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
    onDragRelease: ((Float) -> Unit)? = null,
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
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dragElevationPx = with(density) { 10.dp.toPx() }
    var dragOffset by remember(task.id) { mutableFloatStateOf(0f) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    val currentOnDragRelease by rememberUpdatedState(onDragRelease)

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                if (onDragRelease != null) {
                    Modifier.pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffset = 0f
                                dragging = true
                                onDraggingChanged(true)
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = 0f
                                onDraggingChanged(false)
                            },
                            onDragEnd = {
                                val finalOffset = dragOffset
                                currentOnDragRelease?.invoke(finalOffset)
                                dragging = false
                                dragOffset = 0f
                                onDraggingChanged(false)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            // The card itself follows the finger continuously. No task
                            // membership or list order is mutated until onDragEnd.
                            dragOffset += dragAmount.y
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
                TaskSizePill(task.taskSize)
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
    val overdue = tag == "OVERDUE"
    val content = if (overdue) Color(0xFFB4233A) else MaterialTheme.colorScheme.primary
    val container = if (overdue) Color(0xFFFFE7EC) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Surface(
        color = container,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TaskSizePill(taskSize: TaskSize) {
    val palette = taskSizePalette(taskSize)
    Surface(
        color = palette.container,
        contentColor = palette.content,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(palette.content, CircleShape)
            )
            Text(
                taskSize.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.content
            )
        }
    }
}
