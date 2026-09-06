package com.tasknext.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasknext.app.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    var defaultHour by remember { mutableIntStateOf(settings.defaultReminderHour) }
    var defaultMinute by remember { mutableIntStateOf(settings.defaultReminderMinute) }
    var snoozeText by remember { mutableStateOf(settings.snoozeMinutes.coerceIn(1, 24 * 60).toString()) }
    var autoFill by remember { mutableStateOf(settings.autoFillNextTask) }
    var darkTheme by remember { mutableStateOf(settings.darkTheme) }
    var groupByDay by remember { mutableStateOf(settings.groupHistoryByDay) }
    var showDefaultTimePicker by remember { mutableStateOf(false) }
    val backSwipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val scrollState = rememberScrollState()

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
                .pointerInput(onBack) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (!triggered && scrollState.value == 0) {
                                val delta = change.position - change.previousPosition
                                totalX += delta.x
                                totalY += delta.y
                                if (totalY >= backSwipeThresholdPx &&
                                    kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.10f
                                ) {
                                    triggered = true
                                    change.consume()
                                    onBack()
                                }
                            }
                        }
                    }
                }
                .verticalScroll(scrollState)
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
            OutlinedButton(onClick = { showDefaultTimePicker = true }) {
                Text("%02d:%02d".format(defaultHour, defaultMinute))
            }

            Text("Snooze duration (minutes)")
            OutlinedTextField(
                value = snoozeText,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= 4) {
                        snoozeText = value
                        value.toIntOrNull()?.coerceIn(1, 24 * 60)?.let {
                            viewModel.setSnoozeMinutes(it)
                        }
                    }
                },
                modifier = Modifier.width(120.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    if (snoozeText.isBlank()) {
                        Text("Enter 1–1440")
                    }
                }
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

    if (showDefaultTimePicker) {
        SimpleTimePickerDialog(
            initialHour = defaultHour,
            initialMinute = defaultMinute,
            onDismiss = { showDefaultTimePicker = false },
            onConfirm = { hour, minute ->
                defaultHour = hour
                defaultMinute = minute
                viewModel.setDefaultReminderHour(hour)
                viewModel.setDefaultReminderMinute(minute)
                showDefaultTimePicker = false
            }
        )
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
