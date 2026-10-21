package com.tasknext.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tasknext.app.R
import com.tasknext.app.data.TaskSize
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    var backdateTask by remember { mutableStateOf<Task?>(null) }
    var draggingTaskId by remember { mutableStateOf<Long?>(null) }
    var todayCardTopPx by remember { mutableStateOf<Float?>(null) }
    var firstBacklogCardTopPx by remember { mutableStateOf<Float?>(null) }
    var backlogHeaderTopPx by remember { mutableStateOf<Float?>(null) }
    val taskCardBounds = remember { mutableMapOf<Long, Rect>() }
    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    var dragPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var dragOriginalIndex by remember { mutableStateOf<Int?>(null) }
    var draggingTaskSize by remember { mutableStateOf<TaskSize?>(null) }
    var dragScrollCompensation by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

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
    val bigPool = displayPool.filter { it.taskSize == TaskSize.BIG }
    val smallPool = displayPool.filter { it.taskSize == TaskSize.SMALL }
    val displayedMandatory = pendingTodayTask ?: mandatory
    val todayLocked = viewModel.isTodayLocked()
    val density = LocalDensity.current
    val dragSpacingPx = with(density) { 8.dp.toPx() }
    val edgeZonePx = with(density) { 72.dp.toPx() }
    val autoScrollStepPx = with(density) { 28.dp.toPx() }

    fun clearDragPreview() {
        dragPreviewIndex = null
        dragOriginalIndex = null
        draggingTaskSize = null
        dragScrollCompensation = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun previewShiftFor(taskSize: TaskSize, index: Int, taskId: Long): Float {
        if (draggingTaskId == null || draggingTaskId == taskId || draggingTaskSize != taskSize) return 0f
        val target = dragPreviewIndex ?: return 0f
        val origin = dragOriginalIndex ?: return 0f
        val extent = (draggingTaskId?.let { taskCardBounds[it]?.height } ?: 0f) + dragSpacingPx
        if (extent <= dragSpacingPx) return 0f

        return when {
            origin < 0 -> if (index >= target) extent else 0f
            target > origin && index in (origin + 1)..target -> -extent
            target < origin && index in target until origin -> extent
            else -> 0f
        }
    }

    fun maybeAutoScroll(draggedCenterY: Float) {
        val viewport = listBounds ?: return
        val direction = when {
            draggedCenterY < viewport.top + edgeZonePx -> -1f
            draggedCenterY > viewport.bottom - edgeZonePx -> 1f
            else -> 0f
        }
        if (direction == 0f || autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            val consumed = listState.scrollBy(direction * autoScrollStepPx)
            // Counter the LazyColumn movement so the floating card stays under the finger.
            dragScrollCompensation += consumed
        }
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

    fun poolInsertionIndexForTop(taskSize: TaskSize, draggedTaskId: Long?, draggedTopPx: Float): Int {
        val candidates = localPool.filter { it.taskSize == taskSize && it.id != draggedTaskId }
        var index = 0
        for (candidate in candidates) {
            val top = taskCardBounds[candidate.id]?.top ?: continue
            if (draggedTopPx >= top) index++ else break
        }
        return index.coerceIn(0, candidates.size)
    }

    fun updatePoolDragPreview(task: Task, effectiveOffsetY: Float, originalIndex: Int) {
        val bounds = taskCardBounds[task.id] ?: return
        val draggedTop = bounds.top + effectiveOffsetY
        val center = draggedTop + bounds.height / 2f
        maybeAutoScroll(center)

        val todayTop = todayCardTopPx
        if (
            task.taskSize == TaskSize.BIG &&
            !todayLocked &&
            displayedMandatory != null &&
            todayTop != null &&
            draggedTop <= todayTop
        ) {
            dragPreviewIndex = null
            return
        }

        dragPreviewIndex = poolInsertionIndexForTop(task.taskSize, task.id, draggedTop)
        dragOriginalIndex = originalIndex
        draggingTaskSize = task.taskSize
    }

    fun reorderPoolOnRelease(task: Task, draggedTopPx: Float) {
        val sameSize = localPool
            .filter { it.taskSize == task.taskSize && it.id != task.id }
            .toMutableList()
        val otherSize = localPool.filter { it.taskSize != task.taskSize }
        val targetIndex = poolInsertionIndexForTop(task.taskSize, task.id, draggedTopPx)
        sameSize.add(targetIndex.coerceIn(0, sameSize.size), task)

        val ordered = if (task.taskSize == TaskSize.BIG) {
            sameSize + otherSize
        } else {
            otherSize + sameSize
        }
        val reordered = ordered.mapIndexed { index, item -> item.copy(orderIndex = index) }
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
                title = { Text(stringResource(R.string.tasks)) },
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
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    listBounds = coordinates.boundsInRoot()
                }
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (overdueTasks.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.overdue)) }
                items(overdueTasks, key = { "overdue-${it.id}" }) { task ->
                    SwipeableTaskRow(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            taskCardBounds[task.id] = coordinates.boundsInRoot()
                        },
                        task = task,
                        tag = stringResource(R.string.overdue_upper),
                        onEdit = { editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = {
                            val yesterday = LocalDate.now().minusDays(1).toEpochDay()
                            if (task.overdueSinceEpochDay == yesterday) {
                                backdateTask = task
                            } else {
                                completeWithUndo(task)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            displayedMandatory?.let { task ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionTitle(stringResource(R.string.today))
                        if (todayLocked) StatusPill(stringResource(R.string.locked_upper))
                    }
                }
                item(key = "today-${task.id}") {
                    SwipeableTaskRow(
                        modifier = (if (draggingTaskId == task.id) Modifier.zIndex(10f) else Modifier)
                            .onGloballyPositioned { coordinates ->
                                todayCardTopPx = coordinates.positionInRoot().y
                                taskCardBounds[task.id] = coordinates.boundsInRoot()
                            },
                        task = task,
                        tag = stringResource(R.string.today_upper),
                        onEdit = { if (!todayLocked) editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) },
                        allowDelete = !todayLocked,
                        onMakeBonus = null,
                        dragHint = if (!todayLocked) stringResource(R.string.drag_to_next_tasks) else stringResource(R.string.today_locked),
                        onDragRelease = if (!todayLocked) { offsetY ->
                            val startTop = taskCardBounds[task.id]?.top ?: todayCardTopPx
                            val headerTop = backlogHeaderTopPx
                            if (startTop != null && headerTop != null) {
                                val draggedTop = startTop + offsetY
                                if (draggedTop >= headerTop) {
                                    val insertionIndex = poolInsertionIndexForTop(TaskSize.BIG, null, draggedTop)
                                    viewModel.moveTodayToBacklogAt(task.id, insertionIndex)
                                }
                            }
                        } else null,
                        onDraggingStateChanged = { isDragging ->
                            draggingTaskId = if (isDragging) task.id else null
                        }
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (bonusTasks.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bonus_today)) }
                items(bonusTasks, key = { "bonus-${it.id}" }) { task ->
                    SwipeableTaskRow(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            taskCardBounds[task.id] = coordinates.boundsInRoot()
                        },
                        task = task,
                        tag = stringResource(R.string.bonus_upper),
                        onEdit = { editing = task },
                        onDelete = { deleteWithUndo(task) },
                        onComplete = { completeWithUndo(task) },
                        dragHint = stringResource(R.string.drag_to_bonus_backlog),
                        onDragRelease = { offsetY ->
                            val startTop = taskCardBounds[task.id]?.top
                            val headerTop = backlogHeaderTopPx
                            if (startTop != null && headerTop != null) {
                                val draggedTop = startTop + offsetY
                                if (draggedTop >= headerTop) {
                                    val insertionIndex = poolInsertionIndexForTop(TaskSize.SMALL, null, draggedTop)
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
                    SectionTitle(stringResource(R.string.next_tasks))
                    Text(
                        stringResource(R.string.next_tasks_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bigPool.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_big_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            itemsIndexed(bigPool, key = { _, task -> "big-${task.id}" }) { index, task ->
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
                    tag = scheduledQueueTag(index),
                    onEdit = { editing = task },
                    onDelete = { deleteWithUndo(task) },
                    onComplete = { completeWithUndo(task) },
                    onMakeToday = if (!todayLocked) {
                        { viewModel.chooseToday(task.id) }
                    } else null,
                    onMakeBonus = null,
                    dragHint = stringResource(R.string.drag_to_reorder),
                    onDragRelease = { offsetY ->
                        val startTop = taskCardBounds[task.id]?.top
                        if (startTop != null) {
                            val draggedTop = startTop + offsetY
                            val todayTop = todayCardTopPx
                            if (
                                !todayLocked &&
                                displayedMandatory != null &&
                                todayTop != null &&
                                draggedTop <= todayTop
                            ) {
                                viewModel.replaceTodayWith(task.id)
                            } else {
                                reorderPoolOnRelease(task, draggedTop)
                            }
                        }
                    },
                    onDraggingStateChanged = { isDragging ->
                        draggingTaskId = if (isDragging) task.id else null
                    }
                )
            }

            item { Spacer(Modifier.height(6.dp)) }

            item {
                Column {
                    SectionTitle(stringResource(R.string.bonus_backlog))
                    Text(
                        stringResource(R.string.bonus_backlog_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (smallPool.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_small_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            itemsIndexed(smallPool, key = { _, task -> "small-${task.id}" }) { _, task ->
                val baseItemModifier = if (draggingTaskId == task.id) {
                    Modifier.zIndex(10f)
                } else {
                    Modifier.animateItemPlacement(animationSpec = tween(durationMillis = 170))
                }
                SwipeableTaskRow(
                    modifier = baseItemModifier.onGloballyPositioned { coordinates ->
                        taskCardBounds[task.id] = coordinates.boundsInRoot()
                    },
                    task = task,
                    tag = null,
                    onEdit = { editing = task },
                    onDelete = { deleteWithUndo(task) },
                    onComplete = { completeWithUndo(task) },
                    onMakeBonus = if (bonusTasks.size < 2) {
                        { viewModel.activateBonusFromPool(task.id) }
                    } else null,
                    dragHint = stringResource(R.string.drag_to_reorder),
                    onDragRelease = { offsetY ->
                        val startTop = taskCardBounds[task.id]?.top
                        if (startTop != null) {
                            reorderPoolOnRelease(task, startTop + offsetY)
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

    backdateTask?.let { task ->
        AlertDialog(
            onDismissRequest = { backdateTask = null },
            title = { Text(stringResource(R.string.complete_overdue_title)) },
            text = { Text(stringResource(R.string.complete_overdue_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.completeOverdue(task.id, completedYesterday = true)
                        backdateTask = null
                    }
                ) {
                    Text(stringResource(R.string.completed_yesterday))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.completeOverdue(task.id, completedYesterday = false)
                        backdateTask = null
                    }
                ) {
                    Text(stringResource(R.string.complete_today))
                }
            }
        )
    }
}

@Composable
private fun scheduledQueueTag(index: Int): String = when (index) {
    0 -> stringResource(R.string.tomorrow_upper)
    1 -> stringResource(R.string.day_after_upper)
    else -> LocalDate.now()
        .plusDays(index.toLong() + 1L)
        .format(DateTimeFormatter.ofPattern("dd-MMM", Locale.getDefault()))
        .uppercase(Locale.getDefault())
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
    allowDelete: Boolean = true,
    onMakeToday: (() -> Unit)? = null,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    dragCompensation: Float = 0f,
    onDragMove: ((Float) -> Unit)? = null,
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
        directions = if (allowDelete) {
            setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)
        } else {
            setOf(DismissDirection.StartToEnd)
        },
        background = {
            val direction = dismissState.dismissDirection
            if (direction != null && !rowDragging) {
                val isComplete = direction == DismissDirection.StartToEnd
                val bg = if (isComplete) Color(0xFFD7F3DF) else Color(0xFFFFD8D8)
                val icon = if (isComplete) Icons.Default.Check else Icons.Default.Delete
                val label = stringResource(if (isComplete) R.string.complete else R.string.delete)
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
                onMakeToday = onMakeToday,
                onMakeBonus = onMakeBonus,
                dragHint = dragHint,
                dragCompensation = dragCompensation,
                onDragMove = onDragMove,
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
    onMakeToday: (() -> Unit)? = null,
    onMakeBonus: (() -> Unit)? = null,
    dragHint: String? = null,
    dragCompensation: Float = 0f,
    onDragMove: ((Float) -> Unit)? = null,
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
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragRelease by rememberUpdatedState(onDragRelease)
    val currentDragCompensation by rememberUpdatedState(dragCompensation)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                translationY = if (dragging) dragOffset + currentDragCompensation else 0f,
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
                                val finalOffset = dragOffset + currentDragCompensation
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
                            currentOnDragMove?.invoke(dragOffset + currentDragCompensation)
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

            if (onMakeToday != null) {
                IconButton(onClick = onMakeToday, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Today,
                        contentDescription = stringResource(R.string.make_today),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            if (onMakeBonus != null) {
                IconButton(onClick = onMakeBonus, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.make_bonus_today),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(tag: String) {
    val overdue = tag == stringResource(R.string.overdue_upper)
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
                stringResource(
                    if (taskSize == TaskSize.BIG) R.string.task_size_big else R.string.task_size_small
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.content
            )
        }
    }
}
