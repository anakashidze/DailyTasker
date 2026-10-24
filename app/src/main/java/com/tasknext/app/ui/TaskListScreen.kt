package com.tasknext.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

import androidx.compose.animation.core.spring
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import kotlinx.coroutines.isActive

private data class TaskPlacement(val bounds: Rect, val section: String, val index: Int)

@Stable
private class FloatingTask(val task: Task, val origin: TaskPlacement, val tag: String?) {
    var top by mutableFloatStateOf(origin.bounds.top)
    var alpha by mutableFloatStateOf(1f)
    var released by mutableStateOf(false)
    var destination by mutableStateOf(origin.section)
    var targetIndex by mutableIntStateOf(origin.index)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    // One Room snapshot keeps IDs unique while tasks cross section boundaries.
    val tasksSnapshot by viewModel.allTasks.collectAsState()
    val pool = tasksSnapshot.filter { it.slot == null && it.overdueSinceEpochDay == null }
    val mandatory = tasksSnapshot.firstOrNull { it.slot == 0 && it.overdueSinceEpochDay == null }
    val bonusTasks = tasksSnapshot.filter { it.slot != null && it.slot != 0 && it.overdueSinceEpochDay == null }.sortedBy { it.slot }
    val overdueTasks = tasksSnapshot.filter { it.overdueSinceEpochDay != null }.sortedBy { it.overdueSinceEpochDay }
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    val clock = rememberAppClock()
    val todayLocked = viewModel.isTodayLocked()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var backdateTask by remember { mutableStateOf<Task?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val spacingPx = with(density) { 8.dp.toPx() }
    val edgeZonePx = with(density) { 72.dp.toPx() }
    val maxScrollPxPerSecond = with(density) { 650.dp.toPx() }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val placements = remember { mutableMapOf<Long, TaskPlacement>() }
    val poolHeaders = remember { mutableMapOf<TaskSize, Rect>() }
    var floating by remember { mutableStateOf<FloatingTask?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var localPool by remember { mutableStateOf(pool) }
    var pendingOrder by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(pool, pendingOrder, floating) {
        if (floating?.released == false) return@LaunchedEffect
        val ids = pool.map { it.id }
        if (pendingOrder == null || ids == pendingOrder || ids.toSet() != pendingOrder?.toSet()) {
            localPool = pool
            pendingOrder = null
        }
    }
    val activeIds = (overdueTasks + listOfNotNull(mandatory) + bonusTasks).map { it.id }.toSet()
    val bigPool = localPool.filter { it.taskSize == TaskSize.BIG && it.id !in activeIds }
    val smallPool = localPool.filter { it.taskSize == TaskSize.SMALL && it.id !in activeIds }
    val allTasks = tasksSnapshot
    SideEffect { placements.keys.retainAll(tasksSnapshot.map { it.id }.toSet()) }
    val editing = allTasks.firstOrNull { it.id == editingId }

    fun deleteWithUndo(task: Task) {
        if (task.slot == 0 && viewModel.isTodayLocked()) return
        val previousQueue = queuedTomorrowId
        val job = viewModel.deleteTask(task)
        scope.launch {
            job.join()
            showTaskUndo(snackbarHostState, context.getString(R.string.task_deleted), context.getString(R.string.undo)) {
                viewModel.undoDelete(task, previousQueue)
            }
        }
    }
    val completeWithUndo = rememberCompleteWithUndo(viewModel, snackbarHostState)

    fun updatePreview(drag: FloatingTask) {
        if (drag.released) return
        val visibleKeys = listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        val todayBounds = mandatory?.takeIf { it.id in visibleKeys }?.let { placements[it.id]?.bounds }
        if (drag.task.taskSize == TaskSize.BIG && !todayLocked && todayBounds != null &&
            drag.top < todayBounds.center.y) {
            drag.destination = "today"
            drag.targetIndex = 0
            return
        }
        // An active task stays in its own section until it reaches the backlog.
        val headerKey = if (drag.task.taskSize == TaskSize.BIG) "big-header" else "small-header"
        val firstPoolTop = if (headerKey in visibleKeys) poolHeaders[drag.task.taskSize]?.bottom else
            localPool.filter { it.taskSize == drag.task.taskSize && it.id in visibleKeys }
                .mapNotNull { placements[it.id]?.bounds?.top }.minOrNull()
        if (drag.origin.section != "pool" && (firstPoolTop == null || drag.top < firstPoolTop - spacingPx)) {
            drag.destination = drag.origin.section
            drag.targetIndex = drag.origin.index
            return
        }
        drag.destination = "pool"
        val candidates = localPool.filter { it.taskSize == drag.task.taskSize && it.id != drag.task.id }
        val visibleIds = listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.toSet()
        val centers = candidates.mapIndexedNotNull { index, task ->
            placements[task.id]?.takeIf { task.id in visibleIds }?.let { index to it.bounds.center.y }
        }
        drag.targetIndex = insertionIndexAt(drag.top + drag.origin.bounds.height / 2f, centers,
            drag.targetIndex.coerceIn(0, candidates.size))
    }
    val currentUpdatePreview by rememberUpdatedState<(FloatingTask) -> Unit>({ updatePreview(it) })

    fun finishDrag(cancelled: Boolean) {
        val drag = floating ?: return
        if (drag.released) return
        autoScrollJob?.cancel()
        drag.released = true
        if (cancelled) {
            drag.destination = drag.origin.section
            drag.targetIndex = drag.origin.index
        }
        val destination = drag.destination
        var expectedIndex = drag.targetIndex
        val mutation = if (cancelled || destination == drag.origin.section && destination != "pool") null
        else if (destination == "today") {
            viewModel.replaceTodayWith(drag.task.id)
        } else if (drag.origin.section == "today") {
            viewModel.moveTodayToBacklogAt(drag.task.id, drag.targetIndex)
        } else if (drag.origin.section == "bonus") {
            viewModel.moveBonusToBacklogAt(drag.task.id, drag.targetIndex)
        } else {
            val sameSize = localPool.filter { it.taskSize == drag.task.taskSize && it.id != drag.task.id }.toMutableList()
            expectedIndex = drag.targetIndex.coerceIn(0, sameSize.size)
            sameSize.add(expectedIndex, drag.task)
            val otherSize = localPool.filter { it.taskSize != drag.task.taskSize }
            localPool = (if (drag.task.taskSize == TaskSize.BIG) sameSize + otherSize else otherSize + sameSize)
                .mapIndexed { index, task -> task.copy(orderIndex = index) }
            pendingOrder = localPool.map { it.id }
            viewModel.reorderPool(pendingOrder!!)
        }
        scope.launch {
            try {
                mutation?.join()
                // The floating card survives lazy item disposal and database emissions.
                // Wait for the destination layout, never animate from the old slot.
                val target = withTimeoutOrNull(350) {
                    withFrameNanos { }
                    withFrameNanos { }
                    var found: TaskPlacement? = null
                    while (found == null) {
                        val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == drag.task.id }
                        found = placements[drag.task.id]?.takeIf {
                            visible && it.section == destination && (destination != "pool" || it.index == expectedIndex)
                        }
                        if (found == null) withFrameNanos { }
                    }
                    found
                }
                if (target != null) {
                    val releaseTop = drag.top
                    animate(0f, 1f, animationSpec = spring(dampingRatio = 1f, stiffness = 650f)) { progress, _ ->
                        val destinationTop = placements[drag.task.id]?.bounds?.top ?: target.bounds.top
                        drag.top = releaseTop + (destinationTop - releaseTop) * progress
                    }
                } else {
                    // Offscreen destinations do not fly back through the viewport.
                    animate(1f, 0f, animationSpec = tween(100)) { value, _ -> drag.alpha = value }
                }
            } finally {
                if (floating === drag) floating = null
            }
        }
    }

    val startDrag by rememberUpdatedState<(Offset) -> Unit>({ point ->
        if (floating == null) {
            val rootPoint = point + listBounds.topLeft
            val hit = placements.entries.firstOrNull { (id, placement) ->
                listState.layoutInfo.visibleItemsInfo.any { it.key == id } && placement.bounds.contains(rootPoint)
            }
            val task = hit?.key?.let { id -> allTasks.firstOrNull { it.id == id } }
            if (task != null && hit != null && task.overdueSinceEpochDay == null && !(task.slot == 0 && todayLocked)) {
                val tag = when (hit.value.section) {
                    "today" -> context.getString(R.string.today_upper)
                    "bonus" -> context.getString(R.string.bonus_upper)
                    else -> if (task.taskSize == TaskSize.BIG) queueDateLabel(context, hit.value.index, clock.toLocalDate()) else null
                }
                val drag = FloatingTask(task, hit.value, tag)
                floating = drag
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                autoScrollJob = scope.launch {
                    var previousFrame = withFrameNanos { it }
                    while (isActive && floating === drag && !drag.released) {
                        val frame = withFrameNanos { it }
                        val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
                        previousFrame = frame
                        val center = drag.top + drag.origin.bounds.height / 2f
                        val edge = when {
                            center < listBounds.top + edgeZonePx -> -(1f - (center - listBounds.top) / edgeZonePx).coerceIn(0f, 1f)
                            center > listBounds.bottom - edgeZonePx -> (1f - (listBounds.bottom - center) / edgeZonePx).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        if (edge != 0f) {
                            listState.scrollBy(edge * maxScrollPxPerSecond * seconds)
                            withFrameNanos { }
                            currentUpdatePreview(drag)
                        }
                    }
                }
            }
        }
    })
    val moveDrag by rememberUpdatedState<(Float) -> Unit>({ delta ->
        floating?.takeIf { !it.released }?.let { drag -> drag.top += delta; updatePreview(drag) }
    })
    val endDrag by rememberUpdatedState<(Boolean) -> Unit>({ finishDrag(it) })
    val hasActiveDrag by rememberUpdatedState<() -> Boolean>({ floating?.released == false })
    DisposableEffect(Unit) { onDispose { autoScrollJob?.cancel() } }

    @Composable
    fun TaskCard(task: Task, section: String, index: Int = 0, tag: String? = null) {
        val drag = floating
        val hidden = drag?.task?.id == task.id
        val originIndex = if (drag?.origin?.section == "pool") drag.origin.index else -1
        val shift = if (drag != null && !drag.released && section == "pool" && drag.destination == "pool" &&
            drag.task.taskSize == task.taskSize && !hidden) {
            previewDisplacement(originIndex, drag.targetIndex, index, drag.origin.bounds.height + spacingPx)
        } else 0f
        val preview by animateFloatAsState(shift, tween(120), label = "reorder-gap")
        Box(Modifier.fillMaxWidth().testTag("task-card-${task.id}").onGloballyPositioned {
            placements[task.id] = TaskPlacement(it.boundsInRoot(), section, index)
        }) {
            SwipeableTaskRow(
                modifier = Modifier.graphicsLayer {
                    alpha = if (hidden) 0f else 1f
                    translationY = if (drag != null && !drag.released) preview else 0f
                }.then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier),
                task = task,
                tag = tag,
                weekend = section == "pool" && task.taskSize == TaskSize.BIG && isWeekendQueueDate(clock.toLocalDate(), index),
                onEdit = { if (!(task.slot == 0 && todayLocked)) editingId = task.id },
                onDelete = { deleteWithUndo(task) },
                onComplete = {
                    if (task.overdueSinceEpochDay == clock.toLocalDate().minusDays(1).toEpochDay()) backdateTask = task
                    else completeWithUndo(task)
                },
                allowDelete = !(task.slot == 0 && todayLocked),
                onMakeToday = if (section == "pool" && task.taskSize == TaskSize.BIG && !todayLocked) {
                    { viewModel.chooseToday(task.id) }
                } else null,
                onMakeBonus = if (section == "pool" && task.taskSize == TaskSize.SMALL && bonusTasks.size < 2) {
                    { viewModel.activateBonusFromPool(task.id) }
                } else null
            )
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() }) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.tasks)) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_task))
            } }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                            startDrag(held.position)
                            if (!hasActiveDrag()) return@awaitEachGesture
                            var released = false
                            try {
                                while (true) {
                                    // Once the hold wins, consume before the LazyColumn's
                                    // scrollable and card swipe handlers can take the move.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (event.changes.any { it.id != down.id && it.pressed }) break
                                    if (!change.pressed) {
                                        change.consume()
                                        endDrag(false)
                                        released = true
                                        break
                                    }
                                    moveDrag(change.position.y - change.previousPosition.y)
                                    change.consume()
                                }
                            } finally {
                                if (!released) endDrag(true)
                            }
                        }
                    }.padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (overdueTasks.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.overdue)) }
                    items(overdueTasks, key = { it.id }) { TaskCard(it, "overdue", tag = stringResource(R.string.overdue_upper)) }
                }
                mandatory?.let { task ->
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle(stringResource(R.string.today))
                        if (todayLocked) StatusPill(stringResource(R.string.locked_upper))
                    } }
                    item(key = task.id) { TaskCard(task, "today", tag = stringResource(R.string.today_upper)) }
                }
                if (bonusTasks.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.bonus_today)) }
                    items(bonusTasks, key = { it.id }) { TaskCard(it, "bonus", tag = stringResource(R.string.bonus_upper)) }
                }
                item(key = "big-header") { Column(Modifier.onGloballyPositioned { poolHeaders[TaskSize.BIG] = it.boundsInRoot() }) {
                    SectionTitle(stringResource(R.string.next_tasks))
                    Text(stringResource(R.string.next_tasks_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                if (bigPool.isEmpty()) item { Text(stringResource(R.string.no_big_tasks)) }
                itemsIndexed(bigPool, key = { _, task -> task.id }) { index, task ->
                    TaskCard(task, "pool", index, queueDateLabel(context, index, clock.toLocalDate()))
                }
                item(key = "small-header") { Column(Modifier.onGloballyPositioned { poolHeaders[TaskSize.SMALL] = it.boundsInRoot() }) {
                    SectionTitle(stringResource(R.string.bonus_backlog))
                    Text(stringResource(R.string.bonus_backlog_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                if (smallPool.isEmpty()) item { Text(stringResource(R.string.no_small_tasks)) }
                itemsIndexed(smallPool, key = { _, task -> task.id }) { index, task -> TaskCard(task, "pool", index) }
            }
        }
        floating?.let { drag ->
            Box(Modifier.width(with(density) { drag.origin.bounds.width.toDp() })
                .graphicsLayer {
                    translationX = drag.origin.bounds.left - rootBounds.left
                    translationY = drag.top - rootBounds.top
                    alpha = drag.alpha
                    shadowElevation = with(density) { 8.dp.toPx() }
                    shape = RoundedCornerShape(12.dp)
                }.clearAndSetSemantics { this.testTag = "drag-overlay" }) {
                TaskRow(task = drag.task, tag = drag.tag, onEdit = {},
                    weekend = drag.origin.section == "pool" && drag.task.taskSize == TaskSize.BIG &&
                        isWeekendQueueDate(clock.toLocalDate(), drag.origin.index),
                    onMakeToday = if (drag.origin.section == "pool" && drag.task.taskSize == TaskSize.BIG && !todayLocked) ({}) else null,
                    onMakeBonus = if (drag.origin.section == "pool" && drag.task.taskSize == TaskSize.SMALL && bonusTasks.size < 2) ({}) else null)
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
            onDismiss = { editingId = null },
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
                editingId = null
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
    weekend: Boolean = false,
    onMakeToday: (() -> Unit)? = null,
    onMakeBonus: (() -> Unit)? = null,
) {

    val dismissState = rememberDismissState(
        confirmStateChange = { value ->
            when (value) {
                    DismissValue.DismissedToStart -> {
                        onDelete()
                        true
                    }
                    DismissValue.DismissedToEnd -> {
                        onComplete()
                        false
                    }
                    DismissValue.Default -> true
            }
        }
    )

    SwipeToDismiss(
        modifier = modifier,
        state = dismissState,
        directions = if (allowDelete) {
            setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart)
        } else {
            setOf(DismissDirection.StartToEnd)
        },
        background = {
            val direction = dismissState.dismissDirection
            if (direction != null) {
                val isComplete = direction == DismissDirection.StartToEnd
                val bg = if (isComplete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
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
                    if (!isComplete) Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (!isComplete) Spacer(Modifier.width(8.dp))
                    Icon(icon, contentDescription = label, tint = if (isComplete) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                    if (isComplete) Spacer(Modifier.width(8.dp))
                    if (isComplete) Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            } else {
                Box(Modifier.fillMaxSize())
            }
        },
        dismissContent = {
            TaskRow(
                task = task,
                tag = tag,
                weekend = weekend,
                onEdit = onEdit,
                onMakeToday = onMakeToday,
                onMakeBonus = onMakeBonus,
            )
        }
    )
}

@Composable
private fun TaskRow(
    task: Task,
    tag: String?,
    onEdit: () -> Unit,
    weekend: Boolean = false,
    onMakeToday: (() -> Unit)? = null,
    onMakeBonus: (() -> Unit)? = null,
) {
    val color = remember(task.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(task.colorHex))
        } catch (_: Exception) {
            Color.Gray
        }
    }

    val cardColor = lerp(MaterialTheme.colorScheme.surface, color, 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor, RoundedCornerShape(12.dp))
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
            if (tag != null) StatusPill(tag, weekend)

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
private fun StatusPill(tag: String, weekend: Boolean = false) {
    val overdue = tag == stringResource(R.string.overdue_upper)
    val content = if (overdue || weekend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val container = if (overdue) MaterialTheme.colorScheme.errorContainer else content.copy(alpha = 0.10f)
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
