package com.tasknext.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.R
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class HubPage(val index: Int) {
    TASKS(0), HOME(1), HISTORY(2)
}

private enum class HubGesture { LEFT, RIGHT, DOWN }

/**
 * One persistent three-page surface (Tasks <-> Home <-> History).
 * Pages never get recreated during a horizontal gesture, so the finger tracking,
 * release settle, and final page are one continuous motion with no route flash.
 */
@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    onOpenSettings: () -> Unit
) {
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val dayComplete by viewModel.dayComplete.collectAsState()
    val streakCount by viewModel.streakCount.collectAsState()
    val showTomorrowPicker by viewModel.showTomorrowPicker.collectAsState()
    val pool by viewModel.pool.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    val todayLocked = viewModel.isTodayLocked()

    var currentPage by remember { mutableStateOf(HubPage.HOME) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val directionLockPx = with(density) { 8.dp.toPx() }
    val swipeThresholdPx = with(density) { 28.dp.toPx() }
    val pullToAddThresholdPx = with(density) { 84.dp.toPx() }
    val widthPx = screenSize.width.toFloat().coerceAtLeast(1f)
    val pullProgress = (pullOffset / pullToAddThresholdPx).coerceIn(0f, 1f)

    fun targetForHorizontal(totalX: Float): HubPage? = when (currentPage) {
        HubPage.HOME -> when {
            totalX < 0f -> HubPage.HISTORY
            totalX > 0f -> HubPage.TASKS
            else -> null
        }
        HubPage.HISTORY -> if (totalX > 0f) HubPage.HOME else null
        HubPage.TASKS -> if (totalX < 0f) HubPage.HOME else null
    }

    fun settleToPage(
        target: HubPage,
        initialOffset: Float = dragOffsetX,
        initialVelocity: Float = 0f
    ) {
        if (settling || target == currentPage) return
        settling = true
        scope.launch {
            val targetOffset = (currentPage.index - target.index) * widthPx
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 520f
                )
            ) { value, _ -> dragOffsetX = value }
            currentPage = target
            dragOffsetX = 0f
            settling = false
        }
    }

    fun cancelHorizontal(
        initialOffset: Float = dragOffsetX,
        initialVelocity: Float = 0f
    ) {
        if (settling) return
        settling = true
        scope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = 0f,
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = 0.94f,
                    stiffness = 560f
                )
            ) { value, _ -> dragOffsetX = value }
            dragOffsetX = 0f
            settling = false
        }
    }

    BackHandler(enabled = currentPage != HubPage.HOME && !settling) {
        settleToPage(HubPage.HOME, 0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { screenSize = it }
            .then(
                if (!settling) {
                    Modifier.pointerInput(
                        currentPage,
                        screenSize,
                        directionLockPx,
                        swipeThresholdPx,
                        pullToAddThresholdPx
                    ) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var totalX = 0f
                            var totalY = 0f
                            var gesture: HubGesture? = null
                            var horizontalTarget: HubPage? = null
                            var pullHapticSent = false
                            val velocityTracker = VelocityTracker()
                            dragOffsetX = 0f
                            pullOffset = 0f

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break

                                // A task card owns its own horizontal swipe/drag. The page
                                // gesture only starts when child content has not consumed it.
                                if (gesture == null && change.isConsumed) continue

                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.position - change.previousPosition
                                totalX += delta.x
                                totalY += delta.y

                                if (gesture == null) {
                                    val horizontalEnough = abs(totalX) >= directionLockPx &&
                                        abs(totalX) > abs(totalY) * 1.05f
                                    val downwardEnough = currentPage == HubPage.HOME &&
                                        totalY >= directionLockPx &&
                                        abs(totalY) > abs(totalX) * 1.08f

                                    if (horizontalEnough) {
                                        val target = targetForHorizontal(totalX)
                                        if (target != null) {
                                            horizontalTarget = target
                                            gesture = if (totalX < 0f) HubGesture.LEFT else HubGesture.RIGHT
                                        }
                                    } else if (downwardEnough) {
                                        gesture = HubGesture.DOWN
                                    }
                                }

                                when (gesture) {
                                    HubGesture.LEFT, HubGesture.RIGHT -> {
                                        val target = horizontalTarget
                                        if (target != null) {
                                            val targetOffset = (currentPage.index - target.index) * widthPx
                                            dragOffsetX = if (targetOffset < 0f) {
                                                totalX.coerceIn(targetOffset, 0f)
                                            } else {
                                                totalX.coerceIn(0f, targetOffset)
                                            }
                                            change.consume()
                                        }
                                    }
                                    HubGesture.DOWN -> {
                                        val pulled = totalY.coerceAtLeast(0f)
                                            .coerceAtMost(pullToAddThresholdPx * 1.18f)
                                        pullOffset = pulled
                                        if (pulled >= pullToAddThresholdPx && !pullHapticSent) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            pullHapticSent = true
                                        }
                                        change.consume()
                                    }
                                    null -> Unit
                                }
                            }

                            when (gesture) {
                                HubGesture.LEFT, HubGesture.RIGHT -> {
                                    val target = horizontalTarget
                                    if (target != null) {
                                        val velocityX = runCatching {
                                            velocityTracker.calculateVelocity().x
                                        }.getOrDefault(0f)
                                        val flingThreshold = 720f * density.density
                                        val velocityMatchesTarget =
                                            (target.index > currentPage.index && velocityX < -flingThreshold) ||
                                            (target.index < currentPage.index && velocityX > flingThreshold)
                                        val commit =
                                            abs(dragOffsetX) >= widthPx * 0.24f ||
                                            velocityMatchesTarget ||
                                            abs(dragOffsetX) >= swipeThresholdPx
                                        if (commit) {
                                            settleToPage(target, dragOffsetX, velocityX)
                                        } else {
                                            cancelHorizontal(dragOffsetX, velocityX)
                                        }
                                    }
                                }
                                HubGesture.DOWN -> {
                                    val armed = pullOffset >= pullToAddThresholdPx
                                    pullOffset = 0f
                                    if (armed) showAdd = true
                                }
                                null -> Unit
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        HubPage.entries.forEach { page ->
            // Keep the high-frequency drag state out of composition. Reading
            // dragOffsetX inside graphicsLayer invalidates only the render layer
            // instead of recomposing Tasks/Home/History on every pointer move.
            val pageBaseOffset = (page.index - currentPage.index) * widthPx
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = pageBaseOffset + dragOffsetX }
            ) {
                when (page) {
                    HubPage.TASKS -> TaskListScreen(
                        viewModel = viewModel,
                        onBack = { settleToPage(HubPage.HOME, 0f) }
                    )
                    HubPage.HISTORY -> HistoryScreen(
                        viewModel = viewModel,
                        onBack = { settleToPage(HubPage.HOME, 0f) }
                    )
                    HubPage.HOME -> HomePageContent(
                        mandatory = mandatory,
                        bonusTasks = bonusTasks,
                        dayComplete = dayComplete,
                        streakCount = streakCount,
                        todayLocked = todayLocked,
                        onCompleteMandatory = { viewModel.completeMandatory(it) },
                        onCompleteBonus = { viewModel.completeBonus(it) },
                        onOpenTasks = { settleToPage(HubPage.TASKS, 0f) },
                        onOpenHistory = { settleToPage(HubPage.HISTORY, 0f) },
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }

        if (currentPage == HubPage.HOME && pullProgress > 0f) {
            PullToAddIndicator(
                progress = pullProgress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
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

    if (showTomorrowPicker) {
        TomorrowPickerDialog(
            pool = pool,
            onPick = { viewModel.setTomorrowTask(it) },
            onDismiss = { viewModel.dismissTomorrowPicker() }
        )
    }
}

@Composable
private fun HomePageContent(
    mandatory: Task?,
    bonusTasks: List<Task>,
    dayComplete: Boolean,
    streakCount: Int,
    todayLocked: Boolean,
    onCompleteMandatory: (Long) -> Unit,
    onCompleteBonus: (Long) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val accent = remember(mandatory?.colorHex) {
        mandatory?.colorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        } ?: Color(0xFF22C55E)
    }
    val bgTint by animateFloatAsState(
        targetValue = if (dayComplete) 0.14f else 0.06f,
        animationSpec = tween(350),
        label = "home-bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(accent.copy(alpha = bgTint))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(LocalDate.now()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.streak),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFFF7A2F),
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            stringResource(
                                if (streakCount == 1) R.string.day_count else R.string.days_count,
                                streakCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    dayComplete -> DayCompleteHero(accent)
                    mandatory != null -> OneThingHero(
                        task = mandatory,
                        accent = accent,
                        onDone = { onCompleteMandatory(mandatory.id) }
                    )
                    else -> Text(
                        stringResource(
                            if (todayLocked) R.string.today_selection_closed else R.string.nothing_set_today
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bonusTasks.isNotEmpty()) {
                Text(
                    stringResource(R.string.bonus_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 140.dp)
                ) {
                    items(bonusTasks, key = { it.id }) { task ->
                        BonusTaskRow(task = task, onDone = { onCompleteBonus(task.id) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onOpenTasks) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = stringResource(R.string.tasks),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PullToAddIndicator(progress: Float, modifier: Modifier = Modifier) {
    val armed = progress >= 1f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp
                )
                Icon(
                    imageVector = if (armed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (armed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(if (armed) R.string.release_to_add_task else R.string.pull_to_add_task),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (armed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (armed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OneThingHero(task: Task, accent: Color, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (task.comment.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                task.comment,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(accent, CircleShape)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.tap_when_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCompleteHero(accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(110.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.day_complete),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.day_complete_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BonusTaskRow(task: Task, onDone: () -> Unit) {
    var completed by remember(task.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(enabled = !completed) { completed = true; onDone() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (completed) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        if (!completed) {
            Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TomorrowPickerDialog(
    pool: List<Task>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val suggested = pool.minByOrNull { it.orderIndex }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set tomorrow's one thing") },
        text = {
            Column {
                Text("This won't start today — it becomes tomorrow's task.")
                Spacer(Modifier.height(8.dp))
                if (suggested != null) {
                    Text("Suggested: ${suggested.title}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(pool, key = { it.id }) { t ->
                        TextButton(onClick = { onPick(t.id) }) {
                            Text(t.title + if (t.id == suggested?.id) "  (suggested)" else "")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (suggested != null) {
                TextButton(onClick = { onPick(suggested.id) }) { Text("Use suggested") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Decide later") }
        }
    )
}
