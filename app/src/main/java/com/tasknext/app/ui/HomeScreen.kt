package com.tasknext.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "One Thing"-style layout: one giant task fills almost the whole screen,
 * nearly no chrome, a single accent color, one big tap-to-complete action.
 * Secondary nav (list/settings) is reduced to two small icons that fade
 * into the background instead of a normal toolbar/bottom bar.
 */
@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    onOpenList: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val mandatory by viewModel.mandatoryTask.collectAsState()
    val bonusTasks by viewModel.bonusTasks.collectAsState()
    val dayComplete by viewModel.dayComplete.collectAsState()
    val showTomorrowPicker by viewModel.showTomorrowPicker.collectAsState()
    val pool by viewModel.pool.collectAsState()

    val accent = remember(mandatory?.colorHex) {
        mandatory?.colorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
        } ?: Color(0xFF22C55E)
    }
    val bgTint by animateFloatAsState(if (dayComplete) 0.14f else 0.06f, tween(500), label = "bg")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = bgTint))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)
        ) {

            // Minimal top row: date only, no title bar.
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 140.dp)) {
                    items(bonusTasks, key = { it.id }) { task ->
                        BonusTaskRow(task = task, onDone = { viewModel.completeBonus(task.id) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Two quiet icons instead of a toolbar — tucked at the very bottom.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onOpenList) {
                    Icon(
                        Icons.Default.List, contentDescription = "Task list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings, contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
