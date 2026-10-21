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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasknext.app.R
import com.tasknext.app.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onLanguageChanged: (String) -> Unit = {}
) {
    val settings = viewModel.settings

    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var defaultHour by remember { mutableIntStateOf(settings.defaultReminderHour) }
    var defaultMinute by remember { mutableIntStateOf(settings.defaultReminderMinute) }
    var snoozeText by remember { mutableStateOf(settings.snoozeMinutes.coerceIn(1, 24 * 60).toString()) }
    var darkTheme by remember { mutableStateOf(settings.darkTheme) }
    var groupByDay by remember { mutableStateOf(settings.groupHistoryByDay) }

    var lockHour by remember { mutableIntStateOf(settings.todayLockHour) }
    var lockMinute by remember { mutableIntStateOf(settings.todayLockMinute) }

    var planEnabled by remember { mutableStateOf(settings.planTomorrowReminderEnabled) }
    var planHour by remember { mutableIntStateOf(settings.planTomorrowReminderHour) }
    var planMinute by remember { mutableIntStateOf(settings.planTomorrowReminderMinute) }

    var streakReminderEnabled by remember { mutableStateOf(settings.streakRiskReminderEnabled) }
    var streakHour by remember { mutableIntStateOf(settings.streakRiskReminderHour) }
    var streakMinute by remember { mutableIntStateOf(settings.streakRiskReminderMinute) }

    var languageCode by remember { mutableStateOf(settings.languageCode) }
    var languageExpanded by remember { mutableStateOf(false) }

    var showDefaultTimePicker by remember { mutableStateOf(false) }
    var showLockTimePicker by remember { mutableStateOf(false) }
    var showPlanTimePicker by remember { mutableStateOf(false) }
    var showStreakTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
            Text(
                stringResource(R.string.settings_daily_planning),
                style = MaterialTheme.typography.titleMedium
            )

            Text(stringResource(R.string.today_lock_time))
            Text(
                stringResource(R.string.today_lock_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showLockTimePicker = true }) {
                Text("%02d:%02d".format(lockHour, lockMinute))
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_notifications),
                style = MaterialTheme.typography.titleMedium
            )

            SettingsSwitchRow(stringResource(R.string.notifications_enabled), notificationsEnabled) { enabled ->
                notificationsEnabled = enabled
                viewModel.setNotificationsEnabled(enabled)
                if (enabled) onRequestNotificationPermission()
            }

            Text(stringResource(R.string.default_reminder_time))
            Text(
                stringResource(R.string.default_reminder_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showDefaultTimePicker = true }) {
                Text("%02d:%02d".format(defaultHour, defaultMinute))
            }

            Text(stringResource(R.string.snooze_duration))
            OutlinedTextField(
                value = snoozeText,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= 4) {
                        snoozeText = value
                        value.toIntOrNull()?.coerceIn(1, 24 * 60)?.let(viewModel::setSnoozeMinutes)
                    }
                },
                modifier = Modifier.width(130.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SettingsSwitchRow(stringResource(R.string.plan_tomorrow_reminder), planEnabled) {
                planEnabled = it
                settings.planTomorrowReminderEnabled = it
            }
            if (planEnabled) {
                OutlinedButton(onClick = { showPlanTimePicker = true }) {
                    Text("%02d:%02d".format(planHour, planMinute))
                }
            }

            SettingsSwitchRow(stringResource(R.string.streak_risk_reminder), streakReminderEnabled) {
                streakReminderEnabled = it
                settings.streakRiskReminderEnabled = it
            }
            if (streakReminderEnabled) {
                OutlinedButton(onClick = { showStreakTimePicker = true }) {
                    Text("%02d:%02d".format(streakHour, streakMinute))
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_appearance_language),
                style = MaterialTheme.typography.titleMedium
            )

            SettingsSwitchRow(stringResource(R.string.dark_theme), darkTheme) {
                darkTheme = it
                settings.darkTheme = it
                onDarkThemeChanged(it)
            }

            SettingsSwitchRow(stringResource(R.string.group_history_by_day), groupByDay) {
                groupByDay = it
                settings.groupHistoryByDay = it
            }

            Text(stringResource(R.string.language))
            Box {
                OutlinedButton(onClick = { languageExpanded = true }) {
                    Text(
                        when (languageCode) {
                            "ka" -> "ქართული"
                            "ru" -> "Русский"
                            else -> "English"
                        }
                    )
                }
                DropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    listOf(
                        "en" to "English",
                        "ka" to "ქართული",
                        "ru" to "Русский"
                    ).forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                languageExpanded = false
                                if (languageCode != code) {
                                    languageCode = code
                                    settings.languageCode = code
                                    onLanguageChanged(code)
                                }
                            }
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.language_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    if (showLockTimePicker) {
        SimpleTimePickerDialog(
            initialHour = lockHour,
            initialMinute = lockMinute,
            onDismiss = { showLockTimePicker = false },
            onConfirm = { hour, minute ->
                val safeHour = hour.coerceAtMost(12)
                val safeMinute = if (hour > 12) 0 else minute
                lockHour = safeHour
                lockMinute = safeMinute
                settings.todayLockHour = safeHour
                settings.todayLockMinute = safeMinute
                showLockTimePicker = false
            }
        )
    }

    if (showPlanTimePicker) {
        SimpleTimePickerDialog(
            initialHour = planHour,
            initialMinute = planMinute,
            onDismiss = { showPlanTimePicker = false },
            onConfirm = { hour, minute ->
                planHour = hour
                planMinute = minute
                settings.planTomorrowReminderHour = hour
                settings.planTomorrowReminderMinute = minute
                showPlanTimePicker = false
            }
        )
    }

    if (showStreakTimePicker) {
        SimpleTimePickerDialog(
            initialHour = streakHour,
            initialMinute = streakMinute,
            onDismiss = { showStreakTimePicker = false },
            onConfirm = { hour, minute ->
                streakHour = hour
                streakMinute = minute
                settings.streakRiskReminderHour = hour
                settings.streakRiskReminderMinute = minute
                showStreakTimePicker = false
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
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
