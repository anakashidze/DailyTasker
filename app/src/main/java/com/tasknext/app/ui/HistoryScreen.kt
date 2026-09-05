package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.tasknext.app.data.CompletionLog
import com.tasknext.app.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val groupByDay = viewModel.settings.groupHistoryByDay
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No completed tasks yet.")
            }
            return@Scaffold
        }

        val dayFormat = remember { SimpleDateFormat("EEEE, MMM d yyyy", Locale.getDefault()) }
        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
        val fullFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (groupByDay) {
                val grouped = history.groupBy { dayFormat.format(Date(it.completedAtEpochMillis)) }
                grouped.forEach { (day, logs) ->
                    item {
                        Text(
                            day,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(logs, key = { it.id }) { log ->
                        HistoryRow(log, timeFormat, onDelete = { viewModel.deleteHistoryRecord(log.id) })
                    }
                }
            } else {
                items(history, key = { it.id }) { log ->
                    HistoryRow(log, fullFormat, onDelete = { viewModel.deleteHistoryRecord(log.id) })
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all completion records. Your active tasks are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClearAll = false
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    log: CompletionLog,
    timeFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    val color = remember(log.colorHexSnapshot) {
        try { Color(android.graphics.Color.parseColor(log.colorHexSnapshot)) }
        catch (_: Exception) { Color.Gray }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(26.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(log.titleSnapshot, modifier = Modifier.weight(1f), maxLines = 2)
        Text(
            timeFormat.format(Date(log.completedAtEpochMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove history record",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
