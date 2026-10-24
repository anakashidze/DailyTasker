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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tasknext.app.data.TaskGroups
import com.tasknext.app.R
import com.tasknext.app.data.CompletionLog
import com.tasknext.app.data.HistoryEventType
import com.tasknext.app.data.TaskSize
import com.tasknext.app.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var groupByDay by remember { mutableStateOf(viewModel.settings.groupHistoryByDay) }
    var showOptions by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun deleteWithUndo(log: CompletionLog) {
        val job = viewModel.deleteHistoryRecord(log.id)
        scope.launch {
            job.join()
            showTaskUndo(snackbarHost, context.getString(R.string.history_record_deleted), context.getString(R.string.undo)) {
                viewModel.restoreHistoryRecord(log)
            }
        }
    }
    var confirmClearAll by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<CompletionLog?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
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
                    Box {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.history_options))
                        }
                        DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_history_by_day)) },
                                leadingIcon = { Checkbox(checked = groupByDay, onCheckedChange = null) },
                                onClick = {
                                    groupByDay = !groupByDay
                                    viewModel.settings.groupHistoryByDay = groupByDay
                                    showOptions = false
                                }
                            )
                            if (history.isNotEmpty()) DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_history)) },
                                onClick = { showOptions = false; confirmClearAll = true }
                            )
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
                                onDelete = { deleteWithUndo(log) }
                            )
                        }
                    }
                } else {
                    items(history, key = { it.id }) { log ->
                        HistoryRow(
                            log = log,
                            timeFormat = fullFormat,
                            onOpen = { selectedLog = log },
                            onDelete = { deleteWithUndo(log) }
                        )
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        val alreadyRecovered = history.any {
            it.eventType == HistoryEventType.RECOVERED && it.recoveredFromLogId == log.id
        }
        HistoryDetailDialog(
            log = log,
            canRecover = log.eventType != HistoryEventType.RECOVERED && !alreadyRecovered,
            onRecover = {
                viewModel.recoverHistoryRecord(log.id)
                selectedLog = null
            },
            onDismiss = { selectedLog = null }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.clear_history_question)) },
            text = { Text(stringResource(R.string.clear_history_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        snackbarHost.currentSnackbarData?.dismiss()
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
                    TaskGroups.displayName(LocalContext.current, log.groupNameSnapshot, log.groupIconSnapshot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "• " + historyEventLabel(log.eventType),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (log.eventType) {
                        HistoryEventType.COMPLETED -> MaterialTheme.colorScheme.primary
                        HistoryEventType.DELETED -> MaterialTheme.colorScheme.error
                        HistoryEventType.RECOVERED -> MaterialTheme.colorScheme.tertiary
                    }
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
private fun HistoryDetailDialog(
    log: CompletionLog,
    canRecover: Boolean,
    onRecover: () -> Unit,
    onDismiss: () -> Unit
) {
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
                                contentDescription = TaskGroups.displayName(LocalContext.current, log.groupNameSnapshot, log.groupIconSnapshot),
                                tint = color,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Column {
                        Text(TaskGroups.displayName(LocalContext.current, log.groupNameSnapshot, log.groupIconSnapshot), fontWeight = FontWeight.SemiBold)
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
                    Text(historyEventLabel(log.eventType), style = MaterialTheme.typography.labelMedium)
                    Text(
                        completedFormat.format(Date(log.completedAtEpochMillis)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                log.effectiveEpochDay?.let { effective ->
                    val eventEpochDay = java.time.Instant.ofEpochMilli(log.completedAtEpochMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toEpochDay()
                    if (effective != eventEpochDay) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.effective_date), style = MaterialTheme.typography.labelMedium)
                            Text(
                                LocalDate.ofEpochDay(effective)
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canRecover) {
                    TextButton(onClick = onRecover) {
                        Text(stringResource(R.string.recover_task))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

@Composable
private fun historyEventLabel(eventType: HistoryEventType): String = when (eventType) {
    HistoryEventType.COMPLETED -> stringResource(R.string.history_completed)
    HistoryEventType.DELETED -> stringResource(R.string.history_deleted)
    HistoryEventType.RECOVERED -> stringResource(R.string.history_recovered)
}

@Composable
private fun historyColor(hex: String): Color = remember(hex) {
    try { Color(android.graphics.Color.parseColor(hex)) }
    catch (_: Exception) { Color.Gray }
}
