package com.tasknext.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * "One Thing"-style layout: one giant task fills almost the whole screen,
 * nearly no chrome, a single accent color, one big tap-to-complete action.
 * Secondary nav (list/settings) is reduced to two small icons that fade
 * into the background instead of a normal toolbar/bottom bar.
 */
private enum class HomeSwipeDirection { LEFT, RIGHT, UP, DOWN }

@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    onOpenList: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val dayComplete by viewModel.dayComplete.collectAsState()
    val showTomorrowPicker by viewModel.showTomorrowPicker.collectAsState()
    val pool by viewModel.pool.collectAsState()
    val taskGroups by viewModel.taskGroups.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    val accent = remember(mandatory?.colorHex) {
        mandatory?.colorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
        } ?: Color(0xFF22C55E)
    }
    val bgTint by animateFloatAsState(if (dayComplete) 0.14f else 0.06f, tween(500), label = "bg")

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { 36.dp.toPx() }
    val directionLockPx = with(density) { 8.dp.toPx() }
    val pullToAddThresholdPx = with(density) { 84.dp.toPx() }

    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var activeDirection by remember { mutableStateOf<HomeSwipeDirection?>(null) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var pullOffset by remember { mutableFloatStateOf(0f) }

    val widthPx = screenSize.width.toFloat().coerceAtLeast(1f)
    val heightPx = screenSize.height.toFloat().coerceAtLeast(1f)
    val pullProgress = (pullOffset / pullToAddThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { screenSize = it }
            .pointerInput(
                onOpenList,
                onOpenHistory,
                onOpenSettings,
                screenSize,
                swipeThresholdPx,
                directionLockPx,
                pullToAddThresholdPx
            ) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var direction: HomeSwipeDirection? = null
                    var pullHapticSent = false

                    // Every gesture starts from a clean coordinate system. This also
                    // prevents a previous partial swipe from influencing the next touch.
                    horizontalOffset = 0f
                    verticalOffset = 0f
                    pullOffset = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break

                        // If a child has already claimed the gesture (for example a
                        // task action), keep page navigation out of the way.
                        if (direction == null && change.isConsumed) continue

                        val delta = change.position - change.previousPosition
                        totalX += delta.x
                        totalY += delta.y

                        if (direction == null) {
                            val horizontalEnough = abs(totalX) >= directionLockPx &&
                                abs(totalX) > abs(totalY) * 1.10f
                            val verticalEnough = abs(totalY) >= directionLockPx &&
                                abs(totalY) > abs(totalX) * 1.10f

                            direction = when {
                                horizontalEnough && totalX < 0f -> HomeSwipeDirection.LEFT
                                horizontalEnough && totalX > 0f -> HomeSwipeDirection.RIGHT
                                verticalEnough && totalY < 0f -> HomeSwipeDirection.UP
                                verticalEnough && totalY > 0f -> HomeSwipeDirection.DOWN
                                else -> null
                            }
                            if (direction != null) activeDirection = direction
                        }

                        // These are ordinary Compose state writes rather than suspending
                        // Animatable calls. That keeps the page attached 1:1 to the finger
                        // and is legal inside the restricted pointer-event scope.
                        when (direction) {
                            HomeSwipeDirection.LEFT -> {
                                horizontalOffset = totalX.coerceIn(-widthPx, 0f)
                                change.consume()
                            }
                            HomeSwipeDirection.RIGHT -> {
                                horizontalOffset = totalX.coerceIn(0f, widthPx)
                                change.consume()
                            }
                            HomeSwipeDirection.UP -> {
                                verticalOffset = totalY.coerceIn(-heightPx, 0f)
                                change.consume()
                            }
                            HomeSwipeDirection.DOWN -> {
                                // Pull-down is a fill-to-arm action, not a page transition.
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

                    // Commit only on release. If the page has not crossed the configured
                    // swipe threshold it simply returns to Home. Navigation itself is
                    // immediate so there is no post-release delay.
                    when (direction) {
                        HomeSwipeDirection.LEFT -> {
                            val distance = abs(horizontalOffset)
                            val commit = distance >= widthPx / 2f || distance >= swipeThresholdPx
                            if (commit) onOpenHistory()
                            horizontalOffset = 0f
                            activeDirection = null
                        }
                        HomeSwipeDirection.RIGHT -> {
                            val distance = abs(horizontalOffset)
                            val commit = distance >= widthPx / 2f || distance >= swipeThresholdPx
                            if (commit) onOpenList()
                            horizontalOffset = 0f
                            activeDirection = null
                        }
                        HomeSwipeDirection.UP -> {
                            val distance = abs(verticalOffset)
                            val commit = distance >= heightPx / 2f || distance >= swipeThresholdPx
                            if (commit) onOpenSettings()
                            verticalOffset = 0f
                            activeDirection = null
                        }
                        HomeSwipeDirection.DOWN -> {
                            val armed = pullOffset >= pullToAddThresholdPx
                            pullOffset = 0f
                            activeDirection = null
                            if (armed) showAdd = true
                        }
                        null -> Unit
                    }
                }
            }
    ) {
        // The destination page is actually rendered behind Home and translated with
        // the finger. This gives the same direct-manipulation feel as a pager instead
        // of navigating as soon as a distance threshold is crossed.
        when (activeDirection) {
            HomeSwipeDirection.LEFT -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = widthPx + horizontalOffset }
                ) {
                    HistoryScreen(viewModel = viewModel, onBack = {})
                }
            }
            HomeSwipeDirection.RIGHT -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -widthPx + horizontalOffset }
                ) {
                    TaskListScreen(viewModel = viewModel, onBack = {})
                }
            }
            HomeSwipeDirection.UP -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = heightPx + verticalOffset }
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onOpenHistory = {},
                        onDarkThemeChanged = {},
                        onRequestNotificationPermission = {}
                    )
                }
            }
            HomeSwipeDirection.DOWN, null -> Unit
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = when (activeDirection) {
                        HomeSwipeDirection.LEFT, HomeSwipeDirection.RIGHT -> horizontalOffset
                        else -> 0f
                    }
                    translationY = if (activeDirection == HomeSwipeDirection.UP) verticalOffset else 0f
                }
                .background(accent.copy(alpha = bgTint))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp)
            ) {
                Text(
                    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(LocalDate.now()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        dayComplete -> DayCompleteHero(accent)
                        mandatory != null -> mandatory?.let { task ->
                            OneThingHero(
                                task = task,
                                accent = accent,
                                onDone = { viewModel.completeMandatory(task.id) }
                            )
                        }
                        else -> Text(
                            "Nothing set for today.\nAdd one thing.",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (bonusTasks.isNotEmpty()) {
                    Text(
                        "Also today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 140.dp)
                    ) {
                        items(bonusTasks, key = { it.id }) { task ->
                            BonusTaskRow(task = task, onDone = { viewModel.completeBonus(task.id) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onOpenList) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Task list",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (pullProgress > 0f) {
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

    if (showTomorrowPicker) {
        TomorrowPickerDialog(
            pool = pool,
            onPick = { viewModel.setTomorrowTask(it) },
            onDismiss = { viewModel.dismissTomorrowPicker() }
        )
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
                text = if (armed) "Release to add task" else "Pull to add task",
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
            Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("tap when it's done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Day complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your one thing is done for today.",
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
