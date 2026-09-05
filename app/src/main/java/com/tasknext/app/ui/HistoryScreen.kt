package com.tasknext.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@Composable
fun HistoryScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val groupByDay = viewModel.settings.groupHistoryByDay

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            if (groupByDay) {
                val grouped = history.groupBy { dayFormat.format(Date(it.completedAtEpochMillis)) }
                grouped.forEach { (day, logs) ->
                    item { Text(day, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(logs, key = { it.id }) { log -> HistoryRow(log, timeFormat) }
                }
            } else {
                items(history, key = { it.id }) { log ->
                    HistoryRow(log, SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(log: CompletionLog, timeFormat: SimpleDateFormat) {
    val color = remember(log.colorHexSnapshot) {
        try { Color(android.graphics.Color.parseColor(log.colorHexSnapshot)) } catch (e: Exception) { Color.Gray }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(24.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(log.titleSnapshot, modifier = Modifier.weight(1f))
        Text(timeFormat.format(Date(log.completedAtEpochMillis)), style = MaterialTheme.typography.labelSmall)
    }
}
