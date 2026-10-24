package com.tasknext.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.tasknext.app.MainActivity
import com.tasknext.app.R
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.DailyOutcomeStatus
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Small 2x1 launcher widget that mirrors TaskNext's mandatory Today task.
 * It intentionally uses classic RemoteViews so the widget stays lightweight.
 */
class TodayTaskWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Refresh all placed widget instances after the app changes Today state. */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, TodayTaskWidget::class.java))
            if (ids.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                updateWidgets(appContext, manager, ids)
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val dao = AppDatabase.getInstance(context).taskDao()
            val settings = SettingsStore(context)
            val locale = Locale.forLanguageTag(settings.languageCode)
            val localizedContext = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(locale) }
            )
            // Keep calendar rollover, Overdue state and streak correct even when the
            // widget refreshes before the user opens the app.
            val repository = TaskRepository(dao, settings)
            repository.rolloverDayIfNeeded()
            val allTasks = dao.getAllOnce()
            val todayTask = allTasks.firstOrNull {
                !it.isArchived && it.slot == 0 && it.overdueSinceEpochDay == null
            }
            val overdueCount = allTasks.count {
                !it.isArchived && it.overdueSinceEpochDay != null
            }
            val todayEpochDay = LocalDate.now().toEpochDay()
            val todayOutcome = dao.getDailyOutcome(todayEpochDay)
            val restToday = todayOutcome?.status == DailyOutcomeStatus.REST
            val dayComplete = settings.dayCompletedEpochDay == todayEpochDay
            val streak = repository.currentStreakCount()

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openApp = PendingIntent.getActivity(
                context,
                7001,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_today_task)
                views.setTextViewText(
                    R.id.widget_streak,
                    "🔥 " + localizedContext.getString(
                        if (streak == 1) R.string.day_count else R.string.days_count,
                        streak
                    )
                )
                when {
                    todayTask != null -> {
                        views.setTextViewText(
                            R.id.widget_meta,
                            localizedContext.getString(R.string.widget_today_group, todayTask.groupName)
                        )
                        views.setTextViewText(R.id.widget_title, todayTask.title)
                    }
                    restToday -> {
                        views.setTextViewText(R.id.widget_meta, localizedContext.getString(R.string.rest_today))
                        views.setTextViewText(R.id.widget_title, localizedContext.getString(R.string.streak_protected))
                    }
                    dayComplete -> {
                        views.setTextViewText(R.id.widget_meta, localizedContext.getString(R.string.widget_day_complete))
                        views.setTextViewText(R.id.widget_title, localizedContext.getString(R.string.widget_all_done))
                    }
                    overdueCount > 0 -> {
                        views.setTextViewText(R.id.widget_meta, localizedContext.getString(R.string.overdue_upper))
                        views.setTextViewText(
                            R.id.widget_title,
                            localizedContext.getString(R.string.widget_overdue_count, overdueCount)
                        )
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_meta, localizedContext.getString(R.string.today_upper))
                        views.setTextViewText(R.id.widget_title, localizedContext.getString(R.string.widget_no_task_selected))
                    }
                }
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
