package com.tasknext.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit, read-only destinations shared by notifications and the Today widget. */
data class TaskOpenRequest(val taskId: Long?)

object TaskDestination {
    private const val ACTION_OPEN = "com.tasknext.app.OPEN_TASK"
    private const val EXTRA_ID = "destination_task_id"

    fun intent(context: Context, taskId: Long? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            // PendingIntent identity includes data, not extras. Keep each task distinct.
            data = Uri.parse("tasknext://open/${taskId ?: "today"}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            taskId?.let { putExtra(EXTRA_ID, it) }
        }

    fun request(intent: Intent?): TaskOpenRequest? {
        if (intent?.action != ACTION_OPEN) return null
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        return TaskOpenRequest(id.takeIf { it > 0 })
    }
}
