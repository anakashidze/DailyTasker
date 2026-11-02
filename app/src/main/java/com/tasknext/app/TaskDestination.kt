package com.tasknext.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit, read-only destinations shared by notifications and the Today widget. */
data class TaskOpenRequest(val taskId: Long?, val openQueue: Boolean = false)

object TaskDestination {
    private const val ACTION_OPEN = "com.tasknext.app.OPEN_TASK"
    private const val EXTRA_ID = "destination_task_id"
    private const val EXTRA_QUEUE = "destination_queue"

    fun intent(context: Context, taskId: Long? = null, openQueue: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            // PendingIntent identity includes data, not extras. Keep each task distinct.
            data = Uri.parse("tasknext://open/${taskId ?: if (openQueue) "queue" else "today"}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            taskId?.let { putExtra(EXTRA_ID, it) }
            putExtra(EXTRA_QUEUE, openQueue)
        }

    fun request(intent: Intent?): TaskOpenRequest? {
        if (intent?.action != ACTION_OPEN) return null
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        return TaskOpenRequest(id.takeIf { it > 0 }, intent.getBooleanExtra(EXTRA_QUEUE, false))
    }
}
