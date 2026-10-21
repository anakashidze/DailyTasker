package com.tasknext.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tasknext_settings", Context.MODE_PRIVATE)

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
    var autoFillNextTask: Boolean
        get() = prefs.getBoolean("auto_fill_next", false)
        set(value) = prefs.edit().putBoolean("auto_fill_next", value).apply()
    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()
    var groupHistoryByDay: Boolean
        get() = prefs.getBoolean("group_history_by_day", true)
        set(value) = prefs.edit().putBoolean("group_history_by_day", value).apply()

    var customGroups: List<TaskGroupDefinition>
        get() {
            val raw = prefs.getString("custom_groups", "[]") ?: "[]"
            return try {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString("name").trim()
                        val color = item.optString("colorHex", "#6B7280")
                        val icon = item.optString("iconKey", "star")
                        if (name.isNotBlank()) add(TaskGroupDefinition(name, color, icon))
                    }
                }
            } catch (_: Exception) { emptyList() }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { group ->
                array.put(JSONObject().put("name", group.name).put("colorHex", group.colorHex).put("iconKey", group.iconKey))
            }
            prefs.edit().putString("custom_groups", array.toString()).apply()
        }

    var todayLockHour: Int
        get() = prefs.getInt("today_lock_hour", 11)
        set(value) = prefs.edit().putInt("today_lock_hour", value.coerceIn(0, 12)).apply()
    var todayLockMinute: Int
        get() = prefs.getInt("today_lock_minute", 0)
        set(value) = prefs.edit().putInt("today_lock_minute", value.coerceIn(0, 59)).apply()

    var planTomorrowReminderEnabled: Boolean
        get() = prefs.getBoolean("plan_tomorrow_reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("plan_tomorrow_reminder_enabled", value).apply()
    var planTomorrowReminderHour: Int
        get() = prefs.getInt("plan_tomorrow_reminder_hour", 20)
        set(value) = prefs.edit().putInt("plan_tomorrow_reminder_hour", value.coerceIn(0, 23)).apply()
    var planTomorrowReminderMinute: Int
        get() = prefs.getInt("plan_tomorrow_reminder_minute", 0)
        set(value) = prefs.edit().putInt("plan_tomorrow_reminder_minute", value.coerceIn(0, 59)).apply()

    var streakRiskReminderEnabled: Boolean
        get() = prefs.getBoolean("streak_risk_reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("streak_risk_reminder_enabled", value).apply()
    var streakRiskReminderHour: Int
        get() = prefs.getInt("streak_risk_reminder_hour", 18)
        set(value) = prefs.edit().putInt("streak_risk_reminder_hour", value.coerceIn(0, 23)).apply()
    var streakRiskReminderMinute: Int
        get() = prefs.getInt("streak_risk_reminder_minute", 0)
        set(value) = prefs.edit().putInt("streak_risk_reminder_minute", value.coerceIn(0, 59)).apply()

    var autoUseRestCredit: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    var languageCode: String
        get() = prefs.getString("language_code", "en") ?: "en"
        set(value) = prefs.edit().putString("language_code", value).apply()

    var lastActiveEpochDay: Long
        get() = prefs.getLong("last_active_epoch_day", -1L)
        set(value) = prefs.edit().putLong("last_active_epoch_day", value).apply()
    var dayCompletedEpochDay: Long
        get() = prefs.getLong("day_completed_epoch_day", -1L)
        set(value) = prefs.edit().putLong("day_completed_epoch_day", value).apply()
    var queuedTomorrowTaskId: Long
        get() = prefs.getLong("queued_tomorrow_task_id", -1L)
        set(value) = prefs.edit().putLong("queued_tomorrow_task_id", value).apply()
    var streakCount: Int
        get() = prefs.getInt("streak_count", 0)
        set(value) = prefs.edit().putInt("streak_count", value.coerceAtLeast(0)).apply()
    var streakLastCompletedEpochDay: Long
        get() = prefs.getLong("streak_last_completed_epoch_day", -1L)
        set(value) = prefs.edit().putLong("streak_last_completed_epoch_day", value).apply()
}
