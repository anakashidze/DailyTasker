package com.tasknext.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.R
import com.tasknext.app.TaskOpenRequest
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onOpenSettings: () -> Unit,
    openRequest: TaskOpenRequest? = null,
    onOpenRequestHandled: () -> Unit = {}
) {
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val dayComplete by viewModel.dayComplete.collectAsState()
    val streakCount by viewModel.streakCount.collectAsState()
    val restCredits by viewModel.restCredits.collectAsState()
    val restToday by viewModel.restToday.collectAsState()
    val showTomorrowPicker by viewModel.showTomorrowPicker.collectAsState()
    val pool by viewModel.pool.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    val clock = rememberAppClock()
    val todayLocked = viewModel.isTodayLocked()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val completeWithUndo = rememberCompleteWithUndo(viewModel, snackbarHost)
    var showChooseToday by rememberSaveable { mutableStateOf(false) }
    val bigTasks = pool.filter { it.taskSize == com.tasknext.app.data.TaskSize.BIG }
    LaunchedEffect(clock.toLocalDate()) { viewModel.onAppResumed() }

    var currentPage by rememberSaveable { mutableStateOf(HubPage.HOME) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    // Absolute position avoids rebasing a pixel offset when the settled page changes.
    var pagePosition by remember { mutableFloatStateOf(currentPage.index.toFloat()) }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var settling by remember { mutableStateOf(false) }
    var settleGeneration by remember { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val directionLockPx = with(density) { 8.dp.toPx() }
    val minimumFlingVelocityPx = with(density) { 720.dp.toPx() }
    val pullToAddThresholdPx = with(density) { 84.dp.toPx() }
    val widthPx = screenSize.width.toFloat().coerceAtLeast(1f)

    fun settleToPage(target: HubPage, initialVelocity: Float = 0f) {
        val generation = ++settleGeneration
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            try {
                animate(
                    initialValue = pagePosition,
                    targetValue = target.index.toFloat(),
                    initialVelocity = initialVelocity,
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 420f,
                        visibilityThreshold = 0.5f / widthPx
                    )
                ) { value, _ -> pagePosition = value.coerceIn(0f, 2f) }
                pagePosition = target.index.toFloat()
                currentPage = target
            } finally {
                if (generation == settleGeneration) settling = false
            }
        }
    }

    LaunchedEffect(openRequest) {
        openRequest?.let {
            showAdd = false
            showChooseToday = false
            detailId = it.taskId
            settleToPage(HubPage.HOME)
            onOpenRequestHandled()
        }
    }

    // Back can also interrupt a settle; it must not exit while a side page is moving.
    BackHandler(enabled = currentPage != HubPage.HOME || settling) {
        settleToPage(HubPage.HOME)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { screenSize = it }
            .pointerInput(screenSize, directionLockPx, minimumFlingVelocityPx, pullToAddThresholdPx) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val interrupted = settling
                        settleGeneration++
                        settleJob?.cancel()
                        settling = false
                        val startPosition = pagePosition
                        val startPage = startPosition.roundToInt().coerceIn(0, 2)
                        var totalX = 0f
                        var totalY = 0f
                        var gesture: HubGesture? = null
                        var pullHapticSent = false
                        var released = false
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }
                        pullOffset = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Hand off multi-touch and child-owned scrolling/swiping for
                            // the whole gesture, instead of stealing a later movement.
                            if (event.changes.any { it.id != down.id && it.pressed }) break
                            if (gesture == null && change.isConsumed && change.pressed) break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                released = true
                                if (gesture != null) change.consume()
                                break
                            }
                            val delta = change.position - change.previousPosition
                            totalX += delta.x
                            totalY += delta.y

                            if (gesture == null) {
                                val horizontalEnough = abs(totalX) >= directionLockPx &&
                                    abs(totalX) > abs(totalY) * 1.05f
                                val downwardEnough = !interrupted && startPage == HubPage.HOME.index &&
                                    totalY >= directionLockPx && abs(totalY) > abs(totalX) * 1.08f
                                if (horizontalEnough) {
                                    gesture = if (totalX < 0f) HubGesture.LEFT else HubGesture.RIGHT
                                } else if (downwardEnough) {
                                    gesture = HubGesture.DOWN
                                }
                            }

                            when (gesture) {
                                HubGesture.LEFT, HubGesture.RIGHT -> {
                                    // Direct state writes only: never launch work per pointer move.
                                    pagePosition = (startPosition - totalX / widthPx).coerceIn(
                                        (startPage - 1).coerceAtLeast(0).toFloat(),
                                        (startPage + 1).coerceAtMost(2).toFloat()
                                    )
                                    change.consume()
                                }
                                HubGesture.DOWN -> {
                                    pullOffset = totalY.coerceIn(0f, pullToAddThresholdPx * 1.18f)
                                    if (pullOffset >= pullToAddThresholdPx && !pullHapticSent) {
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
                                val velocity = if (released) {
                                    -velocityTracker.calculateVelocity().x / widthPx
                                } else 0f
                                val target = if (released) hubSwipeTarget(
                                    pagePosition, startPage, velocity, minimumFlingVelocityPx / widthPx
                                ) else pagePosition.roundToInt().coerceIn(0, 2)
                                settleToPage(HubPage.entries[target], velocity)
                            }
                            HubGesture.DOWN -> {
                                if (released && pullOffset >= pullToAddThresholdPx) showAdd = true
                            }
                            null -> if (interrupted) {
                                settleToPage(HubPage.entries[startPage])
                            }
                        }
                        pullOffset = 0f
                    }
                } finally {
                    // Rotation, navigation and cancelled input cannot strand a partial page.
                    settleGeneration++
                    settleJob?.cancel()
                    settling = false
                    pagePosition = currentPage.index.toFloat()
                    pullOffset = 0f
                }
            }
    ) {
        HubPage.entries.forEach { page ->
            // Read the same absolute position in each layer, with no per-frame composition.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (page.index - pagePosition) * widthPx }
                    .then(if (page != currentPage) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                when (page) {
                    HubPage.TASKS -> TaskListScreen(
                        viewModel = viewModel,
                        onBack = { settleToPage(HubPage.HOME) }
                    )
                    HubPage.HISTORY -> HistoryScreen(
                        viewModel = viewModel,
                        onBack = { settleToPage(HubPage.HOME) }
                    )
                    HubPage.HOME -> HomePageContent(
                        mandatory = mandatory,
                        bonusTasks = bonusTasks,
                        dayComplete = dayComplete,
                        streakCount = streakCount,
                        restCredits = restCredits,
                        restToday = restToday,
                        todayLocked = todayLocked,
                        onOpenTask = { detailId = it },
                        onCompleteMandatory = { id -> mandatory?.takeIf { it.id == id }?.let(completeWithUndo) },
                        onCompleteBonus = { id -> bonusTasks.firstOrNull { it.id == id }?.let(completeWithUndo) },
                        onUseRestCredit = { viewModel.useRestCreditToday() },
                        onOpenTasks = { settleToPage(HubPage.TASKS) },
                        onOpenHistory = { settleToPage(HubPage.HISTORY) },
                        onOpenSettings = onOpenSettings,
                        onAddTask = { showAdd = true },
                        onChooseToday = { if (bigTasks.isEmpty()) showAdd = true else showChooseToday = true },
                        hasBigTasks = bigTasks.isNotEmpty(),
                        lockTime = formatAppTime(context, viewModel.settings.todayLockHour, viewModel.settings.todayLockMinute),
                        clock = clock,
                        reminderStatus = mandatory?.let { taskReminderSummary(context, it, viewModel.settings, clock) }
                    )
                }
            }
        }

        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))

        // Isolate pull progress reads from the three persistent page compositions.
        PullToAddOverlay(
            progress = { (pullOffset / pullToAddThresholdPx).coerceIn(0f, 1f) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        )
    }

    detailId?.let { TaskDetailsDialog(it, viewModel, onDismiss = { detailId = null }) }

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
                settleToPage(HubPage.TASKS)
            }
        )
    }

    if (showChooseToday) {
        AlertDialog(
            onDismissRequest = { showChooseToday = false },
            title = { Text(stringResource(R.string.choose_today_task)) },
            text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(bigTasks, key = { it.id }) { task ->
                    TextButton(
                        onClick = { viewModel.chooseToday(task.id); showChooseToday = false },
                        enabled = !todayLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(task.title, Modifier.fillMaxWidth()) }
                }
            } },
            confirmButton = { TextButton(onClick = { showChooseToday = false; showAdd = true }) { Text(stringResource(R.string.add_task)) } },
            dismissButton = { TextButton(onClick = { showChooseToday = false }) { Text(stringResource(R.string.cancel)) } }
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
    restCredits: Int,
    restToday: Boolean,
    todayLocked: Boolean,
    onOpenTask: (Long) -> Unit,
    onCompleteMandatory: (Long) -> Unit,
    onCompleteBonus: (Long) -> Unit,
    onUseRestCredit: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddTask: () -> Unit,
    onChooseToday: () -> Unit,
    hasBigTasks: Boolean,
    lockTime: String,
    clock: java.time.LocalDateTime,
    reminderStatus: String?
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
                    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(clock.toLocalDate()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                Column(Modifier.widthIn(max = 144.dp), horizontalAlignment = Alignment.End) {
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
                            pluralStringResource(R.plurals.streak_days, streakCount, streakCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (restCredits > 0) {
                        Text(
                            pluralStringResource(R.plurals.rest_credit_total, restCredits, restCredits),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp)
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
                    restToday -> RestDayHero()
                    dayComplete -> DayCompleteHero(accent)
                    mandatory != null -> Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(if (todayLocked) R.string.today_locked else R.string.today_locks_at, lockTime),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OneThingHero(
                        task = mandatory,
                        accent = accent,
                        onDone = { onCompleteMandatory(mandatory.id) },
                        onOpen = { onOpenTask(mandatory.id) }
                        )
                        reminderStatus?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(
                                if (todayLocked) R.string.today_selection_closed else R.string.nothing_set_today
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!todayLocked) {
                            Text(stringResource(R.string.choose_before, lockTime), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onChooseToday) {
                                Text(stringResource(if (hasBigTasks) R.string.choose_today_task else R.string.add_first_task))
                            }
                        } else {
                            TextButton(onClick = onOpenTasks) { Text(stringResource(R.string.plan_next_tasks)) }
                        }
                        if (todayLocked && restCredits > 0) {
                            FilledTonalButton(onClick = onUseRestCredit) {
                                Text(stringResource(R.string.use_rest_credit))
                            }
                        }
                    }
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
                        BonusTaskRow(task = task, onDone = { onCompleteBonus(task.id) }, onOpen = { onOpenTask(task.id) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpenTasks, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tasks)) }
                FilledTonalButton(onClick = onAddTask, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
                TextButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.history)) }
            }
        }
    }
}

@Composable
private fun PullToAddOverlay(progress: () -> Float, modifier: Modifier = Modifier) {
    val value = progress()
    if (value > 0f) PullToAddIndicator(value, modifier)
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
private fun OneThingHero(task: Task, accent: Color, onDone: () -> Unit, onOpen: () -> Unit) {
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
        TextButton(onClick = onOpen) { Text(stringResource(R.string.task_details)) }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(accent, CircleShape)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = contrastingContentColor(accent), modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.tap_when_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RestDayHero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.rest_today),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.streak_protected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayCompleteHero(accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(110.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = contrastingContentColor(accent), modifier = Modifier.size(56.dp))
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
private fun BonusTaskRow(task: Task, onDone: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDone) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = MaterialTheme.colorScheme.primary)
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
