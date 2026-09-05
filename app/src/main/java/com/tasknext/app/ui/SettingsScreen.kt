package com.tasknext.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasknext.app.viewmodel.TaskViewModel

@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onDarkThemeChanged: (Boolean) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val settings = viewModel.settings
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var defaultHour by remember { mutableStateOf(settings.defaultReminderHour) }
    var defaultMinute by remember { mutableStateOf(settings.defaultReminderMinute) }
    var snoozeMinutes by remember { mutableStateOf(settings.snoozeMinutes.coerceIn(1, 24 * 60)) }
    var autoFill by remember { mutableStateOf(settings.autoFillNextTask) }
    var darkTheme by remember { mutableStateOf(settings.darkTheme) }
    var groupByDay by remember { mutableStateOf(settings.groupHistoryByDay) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSwitchRow("Notifications enabled", notificationsEnabled) { enabled ->
                notificationsEnabled = enabled
                viewModel.setNotificationsEnabled(enabled)
                if (enabled) onRequestNotificationPermission()
            }

            Text("Default reminder time")
            Text(
                "Tasks set to use the default time follow this value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = defaultHour.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.coerceIn(0, 23)?.let {
                            defaultHour = it
                            viewModel.setDefaultReminderHour(it)
                        }
                    },
                    label = { Text("Hour") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = defaultMinute.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.coerceIn(0, 59)?.let {
                            defaultMinute = it
                            viewModel.setDefaultReminderMinute(it)
                        }
                    },
                    label = { Text("Minute") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Text("Snooze duration (minutes)")
            OutlinedTextField(
                value = snoozeMinutes.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.coerceIn(1, 24 * 60)?.let {
                        snoozeMinutes = it
                        viewModel.setSnoozeMinutes(it)
                    }
                },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SettingsSwitchRow("Auto-pick the next backlog task for tomorrow", autoFill) {
                autoFill = it
                settings.autoFillNextTask = it
            }

            SettingsSwitchRow("Dark theme", darkTheme) {
                darkTheme = it
                settings.darkTheme = it
                onDarkThemeChanged(it)
            }

            SettingsSwitchRow("Group history by day", groupByDay) {
                groupByDay = it
                settings.groupHistoryByDay = it
            }

            Button(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View completed task history")
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
