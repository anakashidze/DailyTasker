package com.tasknext.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tasknext.app.MainActivity
import com.tasknext.app.R
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

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
            val todayTask = dao.getAllOnce().firstOrNull { !it.isArchived && it.slot == 0 }
            val settings = SettingsStore(context)
            val dayComplete = settings.dayCompletedEpochDay == LocalDate.now().toEpochDay()

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
                when {
                    todayTask != null -> {
                        views.setTextViewText(R.id.widget_meta, "TODAY • ${todayTask.groupName}")
                        views.setTextViewText(R.id.widget_title, todayTask.title)
                    }
                    dayComplete -> {
                        views.setTextViewText(R.id.widget_meta, "DAY COMPLETE")
                        views.setTextViewText(R.id.widget_title, "All done for today")
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_meta, "TODAY")
                        views.setTextViewText(R.id.widget_title, "No task selected")
                    }
                }
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
