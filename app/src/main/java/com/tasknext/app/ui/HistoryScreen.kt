package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tasknext.app.R
import com.tasknext.app.data.CompletionLog
import com.tasknext.app.data.TaskSize
import com.tasknext.app.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val groupByDay = viewModel.settings.groupHistoryByDay
    var confirmClearAll by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<CompletionLog?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_history))
                        }
                    }
                }
            )
        }
    ) { padding ->
        val dayFormat = remember { SimpleDateFormat("EEEE, MMM d yyyy", Locale.getDefault()) }
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val fullFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

        if (history.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_history))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (groupByDay) {
                    val grouped = history.groupBy {
                        dayFormat.format(Date(it.completedAtEpochMillis))
                    }
                    grouped.forEach { (day, logs) ->
                        item {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(logs, key = { it.id }) { log ->
                            HistoryRow(
                                log = log,
                                timeFormat = timeFormat,
                                onOpen = { selectedLog = log },
                                onDelete = { viewModel.deleteHistoryRecord(log.id) }
                            )
                        }
                    }
                } else {
                    items(history, key = { it.id }) { log ->
                        HistoryRow(
                            log = log,
                            timeFormat = fullFormat,
                            onOpen = { selectedLog = log },
                            onDelete = { viewModel.deleteHistoryRecord(log.id) }
                        )
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        HistoryDetailDialog(log = log, onDismiss = { selectedLog = null })
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.clear_history_question)) },
            text = { Text(stringResource(R.string.clear_history_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClearAll = false
                    }
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    log: CompletionLog,
    timeFormat: SimpleDateFormat,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val color = historyColor(log.colorHexSnapshot)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(26.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.titleSnapshot, maxLines = 2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GroupIcon(
                    key = log.groupIconSnapshot,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    log.groupNameSnapshot,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            timeFormat.format(Date(log.completedAtEpochMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HistoryDetailDialog(log: CompletionLog, onDismiss: () -> Unit) {
    val color = historyColor(log.colorHexSnapshot)
    val completedFormat = remember { SimpleDateFormat("EEEE, MMM d yyyy • HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(log.titleSnapshot) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            GroupIcon(
                                key = log.groupIconSnapshot,
                                contentDescription = log.groupNameSnapshot,
                                tint = color,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Column {
                        Text(log.groupNameSnapshot, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                if (log.taskSizeSnapshot == TaskSize.BIG) R.string.task_size_big
                                else R.string.task_size_small
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (log.commentSnapshot.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.comment), style = MaterialTheme.typography.labelMedium)
                        Text(log.commentSnapshot, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.completed), style = MaterialTheme.typography.labelMedium)
                    Text(
                        completedFormat.format(Date(log.completedAtEpochMillis)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun historyColor(hex: String): Color = remember(hex) {
    try { Color(android.graphics.Color.parseColor(hex)) }
    catch (_: Exception) { Color.Gray }
}
