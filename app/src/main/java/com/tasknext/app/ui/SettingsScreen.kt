package com.tasknext.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactSection(
                title = stringResource(R.string.settings_daily_planning)
            ) {
                CompactValueRow(
                    label = stringResource(R.string.today_lock_time),
                    value = "%02d:%02d".format(lockHour, lockMinute),
                    onValueClick = { showLockTimePicker = true }
                )
                Text(
                    stringResource(R.string.today_lock_description),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                )
            }

            CompactSection(
                title = stringResource(R.string.settings_notifications)
            ) {
                CompactToggleRow(
                    label = stringResource(R.string.notifications_enabled),
                    checked = notificationsEnabled,
                    onChange = { enabled ->
                        notificationsEnabled = enabled
                        viewModel.setNotificationsEnabled(enabled)
                        if (enabled) onRequestNotificationPermission()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                CompactValueRow(
                    label = stringResource(R.string.default_reminder_time),
                    value = "%02d:%02d".format(defaultHour, defaultMinute),
                    onValueClick = { showDefaultTimePicker = true },
                    enabled = notificationsEnabled
                )

                CompactSnoozeRow(
                    label = stringResource(R.string.snooze_duration),
                    value = snoozeText,
                    enabled = notificationsEnabled,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 4) {
                            snoozeText = value
                            value.toIntOrNull()?.coerceIn(1, 24 * 60)?.let(viewModel::setSnoozeMinutes)
                        }
                    }
                )

                CompactNotificationRow(
                    label = stringResource(R.string.plan_tomorrow_reminder),
                    checked = planEnabled,
                    time = "%02d:%02d".format(planHour, planMinute),
                    enabled = notificationsEnabled,
                    onToggle = {
                        planEnabled = it
                        settings.planTomorrowReminderEnabled = it
                    },
                    onTimeClick = { showPlanTimePicker = true }
                )

                CompactNotificationRow(
                    label = stringResource(R.string.streak_risk_reminder),
                    checked = streakReminderEnabled,
                    time = "%02d:%02d".format(streakHour, streakMinute),
                    enabled = notificationsEnabled,
                    onToggle = {
                        streakReminderEnabled = it
                        settings.streakRiskReminderEnabled = it
                    },
                    onTimeClick = { showStreakTimePicker = true }
                )
            }

            CompactSection(
                title = stringResource(R.string.settings_appearance_language)
            ) {
                CompactToggleRow(
                    label = stringResource(R.string.dark_theme),
                    checked = darkTheme,
                    onChange = {
                        darkTheme = it
                        settings.darkTheme = it
                        onDarkThemeChanged(it)
                    }
                )

                CompactToggleRow(
                    label = stringResource(R.string.group_history_by_day),
                    checked = groupByDay,
                    onChange = {
                        groupByDay = it
                        settings.groupHistoryByDay = it
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        OutlinedButton(
                            onClick = { languageExpanded = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                when (languageCode) {
                                    "ka" -> "ქართული"
                                    "ru" -> "Русский"
                                    else -> "English"
                                },
                                style = MaterialTheme.typography.labelMedium
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
                }
            }

            Spacer(Modifier.height(8.dp))
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
private fun CompactSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CompactToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled
        )
    }
}

@Composable
private fun CompactValueRow(
    label: String,
    value: String,
    onValueClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onValueClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CompactSnoozeRow(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(78.dp)
        )
    }
}

@Composable
private fun CompactNotificationRow(
    label: String,
    checked: Boolean,
    time: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        TextButton(
            onClick = onTimeClick,
            enabled = enabled && checked,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(time, style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled
        )
    }
}
