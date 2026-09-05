package com.tasknext.app.data

import android.content.Context

/**
 * Simple wrapper around SharedPreferences for app-wide settings.
 * Kept outside Room since these are single scalar values, not a table.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE)

    var defaultReminderHour: Int
        get() = prefs.getInt("default_hour", 9)
        set(value) = prefs.edit().putInt("default_hour", value).apply()

    var defaultReminderMinute: Int
        get() = prefs.getInt("default_minute", 0)
        set(value) = prefs.edit().putInt("default_minute", value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    var snoozeMinutes: Int
        get() = prefs.getInt("snooze_minutes", 10)
        set(value) = prefs.edit().putInt("snooze_minutes", value).apply()

    /** If true, when a slot frees up the app auto-pulls the next pool task in without asking. */
    var autoFillNextTask: Boolean
        get() = prefs.getBoolean("auto_fill_next", false)
        set(value) = prefs.edit().putBoolean("auto_fill_next", value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    /** History grouping: true = group by day, false = flat timeline. */
    var groupHistoryByDay: Boolean
        get() = prefs.getBoolean("group_history_by_day", true)
        set(value) = prefs.edit().putBoolean("group_history_by_day", value).apply()

    // --- Day-achievement tracking ---

    /** Epoch day (days since 1970-01-01) the app last considered "active". Used to detect rollover. */
    var lastActiveEpochDay: Long
        get() = prefs.getLong("last_active_epoch_day", -1L)
        set(value) = prefs.edit().putLong("last_active_epoch_day", value).apply()

    /** Epoch day on which the mandatory task was completed. -1 means not completed today. */
    var dayCompletedEpochDay: Long
        get() = prefs.getLong("day_completed_epoch_day", -1L)
        set(value) = prefs.edit().putLong("day_completed_epoch_day", value).apply()

    /** Task id chosen to become tomorrow's mandatory task, or -1 if none chosen yet. */
    var queuedTomorrowTaskId: Long
        get() = prefs.getLong("queued_tomorrow_task_id", -1L)
        set(value) = prefs.edit().putLong("queued_tomorrow_task_id", value).apply()
}
