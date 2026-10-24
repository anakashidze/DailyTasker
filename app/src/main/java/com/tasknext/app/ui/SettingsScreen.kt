package com.tasknext.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasknext.app.R
import com.tasknext.app.data.ThemeMode
import com.tasknext.app.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onLanguageChanged: (String) -> Unit = {}
) {
    val settings = viewModel.settings
    val context = LocalContext.current
    val notificationAccess = rememberNotificationAvailability()
    var showRestInfo by remember { mutableStateOf(false) }

    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var defaultHour by remember { mutableIntStateOf(settings.defaultReminderHour) }
    var defaultMinute by remember { mutableIntStateOf(settings.defaultReminderMinute) }
    var snoozeMinutes by remember { mutableIntStateOf(settings.snoozeMinutes.coerceIn(1, 24 * 60)) }
    var snoozeText by remember { mutableStateOf(snoozeMinutes.toString()) }
    var showSnoozeEditor by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var showThemePicker by remember { mutableStateOf(false) }

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
                    value = formatAppTime(context, lockHour, lockMinute),
                    onValueClick = { showLockTimePicker = true }
                )
                Text(
                    stringResource(R.string.today_lock_description),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                )
            }

            TextButton(onClick = { showRestInfo = true }) { Text(stringResource(R.string.rest_credits_help)) }

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

                if (notificationsEnabled && !notificationAccess) {
                    Text(stringResource(R.string.notifications_blocked), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text(stringResource(R.string.open_android_settings)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                CompactValueRow(
                    label = stringResource(R.string.default_reminder_time),
                    value = formatAppTime(context, defaultHour, defaultMinute),
                    onValueClick = { showDefaultTimePicker = true },
                    enabled = notificationsEnabled
                )

                CompactValueRow(
                    label = stringResource(R.string.snooze_label),
                    value = stringResource(R.string.snooze_minutes_value, snoozeMinutes),
                    enabled = notificationsEnabled,
                    onValueClick = {
                        snoozeText = snoozeMinutes.toString()
                        showSnoozeEditor = true
                    }
                )

                CompactNotificationRow(
                    label = stringResource(R.string.plan_tomorrow_reminder),
                    checked = planEnabled,
                    time = formatAppTime(context, planHour, planMinute),
                    enabled = notificationsEnabled,
                    onToggle = {
                        planEnabled = it
                        settings.planTomorrowReminderEnabled = it
                        viewModel.rescheduleDailyReminders()
                    },
                    onTimeClick = { showPlanTimePicker = true }
                )

                CompactNotificationRow(
                    label = stringResource(R.string.streak_risk_reminder),
                    checked = streakReminderEnabled,
                    time = formatAppTime(context, streakHour, streakMinute),
                    enabled = notificationsEnabled,
                    onToggle = {
                        streakReminderEnabled = it
                        settings.streakRiskReminderEnabled = it
                        viewModel.rescheduleDailyReminders()
                    },
                    onTimeClick = { showStreakTimePicker = true }
                )
            }

            CompactSection(
                title = stringResource(R.string.settings_appearance_language)
            ) {
                CompactValueRow(
                    label = stringResource(R.string.appearance),
                    value = themeModeLabel(themeMode),
                    onValueClick = { showThemePicker = true }
                )
                Box {
                    CompactValueRow(
                        label = stringResource(R.string.language),
                        value = when (languageCode) {
                            "ka" -> "ქართული"
                            "ru" -> "Русский"
                            else -> "English"
                        },
                        onValueClick = { languageExpanded = true }
                    )
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

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text(stringResource(R.string.appearance)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth().clickable {
                            themeMode = mode
                            settings.themeMode = mode
                            onThemeChanged(mode)
                            showThemePicker = false
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Text(themeModeLabel(mode), Modifier.padding(start = 12.dp).heightIn(min = 48.dp).wrapContentHeight())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showRestInfo) {
        AlertDialog(
            onDismissRequest = { showRestInfo = false },
            title = { Text(stringResource(R.string.rest_credits_help)) },
            text = { Text(stringResource(R.string.rest_credits_explanation)) },
            confirmButton = { TextButton(onClick = { showRestInfo = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showSnoozeEditor) {
        val minutes = snoozeText.toIntOrNull()
        val valid = minutes != null && minutes in 1..1440
        fun saveSnooze() {
            if (valid) {
                snoozeMinutes = minutes!!
                viewModel.setSnoozeMinutes(snoozeMinutes)
                showSnoozeEditor = false
            }
        }
        AlertDialog(
            onDismissRequest = { showSnoozeEditor = false },
            title = { Text(stringResource(R.string.snooze_duration)) },
            text = {
                OutlinedTextField(
                    value = snoozeText,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it in '0'..'9' }) snoozeText = value
                    },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text(stringResource(R.string.snooze_range)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveSnooze() }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { saveSnooze() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSnoozeEditor = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
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
                val safeMinute = if (hour >= 12) 0 else minute
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
                viewModel.rescheduleDailyReminders()
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
                viewModel.rescheduleDailyReminders()
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
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
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
            onCheckedChange = null,
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
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onValueClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp))
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
            modifier = Modifier.heightIn(min = 48.dp)
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

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
})
