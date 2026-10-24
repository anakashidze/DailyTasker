package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.data.Priority
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TomorrowScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val pool by viewModel.pool.collectAsState()
    val queuedTomorrowId by viewModel.queuedTomorrowTaskId.collectAsState()
    val tomorrow = remember(pool, queuedTomorrowId) {
        pool.firstOrNull { it.id == queuedTomorrowId } ?: pool.minByOrNull { it.orderIndex }
    }
    val isExplicit = queuedTomorrowId != -1L && tomorrow?.id == queuedTomorrowId
    val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

    val accent = remember(tomorrow?.colorHex) {
        tomorrow?.colorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        } ?: Color(0xFF4F46E5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tomorrow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(accent.copy(alpha = 0.06f))
                .pointerInput(onBack) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (!change.isConsumed && !triggered) {
                                val delta = change.position - change.previousPosition
                                totalX += delta.x
                                totalY += delta.y
                                if (abs(totalY) >= thresholdPx && abs(totalY) > abs(totalX) * 1.10f) {
                                    triggered = true
                                    change.consume()
                                    // User requested swipe-down to return; swipe-up is accepted too.
                                    onBack()
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (tomorrow == null) {
                Text(
                    "No task is queued for tomorrow yet.",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TomorrowTaskCard(task = tomorrow, isExplicit = isExplicit, accent = accent)
            }
        }
    }
}

@Composable
private fun TomorrowTaskCard(task: Task, isExplicit: Boolean, accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (isExplicit) "Tomorrow's task" else "Next task for tomorrow",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                GroupIcon(
                    key = task.groupIcon,
                    contentDescription = TaskGroups.displayName(LocalContext.current, task.groupName, task.groupIcon),
                    tint = accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            task.title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 42.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3
        )
        if (task.comment.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                task.comment,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GroupIcon(
                        key = task.groupIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(TaskGroups.displayName(LocalContext.current, task.groupName, task.groupIcon), style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                color = when (task.priority) {
                    Priority.LOW -> Color(0xFF8DBBFF)
                    Priority.MEDIUM -> Color(0xFFFFD45C)
                    Priority.HIGH -> Color(0xFFFF8A8A)
                },
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    task.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF202020)
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "Swipe vertically to return Home",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
