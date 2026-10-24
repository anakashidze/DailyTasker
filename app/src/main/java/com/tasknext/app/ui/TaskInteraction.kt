package com.tasknext.app.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.tasknext.app.R
import com.tasknext.app.data.Task
import com.tasknext.app.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Composable
internal fun rememberAppClock(): LocalDateTime {
    val clock by produceState(LocalDateTime.now()) {
        while (isActive) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalDateTime.now()
        }
    }
    return clock
}

internal fun formatAppTime(context: Context, hour: Int, minute: Int): String =
    DateFormat.getTimeFormat(context).format(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.time)

internal fun isWeekendQueueDate(today: LocalDate, index: Int): Boolean =
    today.plusDays(index.toLong() + 1).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

internal fun queueDateLabel(context: Context, index: Int, today: LocalDate): String = when (index) {
    0 -> context.getString(R.string.tomorrow_upper)
    1 -> context.getString(R.string.day_after_upper)
    else -> today.plusDays(index.toLong() + 1).format(DateTimeFormatter.ofPattern("dd-MMM", Locale.getDefault()))
}

/** Indices are in the full candidate list, including candidates outside the viewport. */
internal fun insertionIndexAt(center: Float, visibleCenters: List<Pair<Int, Float>>, fallback: Int): Int =
    visibleCenters.firstOrNull { center < it.second }?.first
        ?: visibleCenters.lastOrNull()?.first?.plus(1) ?: fallback

internal fun previewDisplacement(origin: Int, target: Int, index: Int, extent: Float): Float = when {
    origin < 0 && index >= target -> extent
    target > origin && index in (origin + 1)..target -> -extent
    target < origin && index in target until origin -> extent
    else -> 0f
}

internal suspend fun showTaskUndo(host: SnackbarHostState, message: String, undoLabel: String, undo: () -> Unit) {
    host.currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(15_000L) {
        host.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    }
    if (result == SnackbarResult.ActionPerformed) undo()
}

@Composable
internal fun rememberCompleteWithUndo(viewModel: TaskViewModel, host: SnackbarHostState): (Task) -> Unit {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return { task ->
        val settings = viewModel.settings
        val queue = settings.queuedTomorrowTaskId
        val completedDay = settings.dayCompletedEpochDay
        val streak = settings.streakCount
        val lastDay = settings.streakLastCompletedEpochDay
        val job = viewModel.completeFromList(task.id)
        scope.launch {
            job.join()
            showTaskUndo(host, context.getString(R.string.task_completed), context.getString(R.string.undo)) {
                viewModel.undoCompletion(task, queue, completedDay, streak, lastDay)
            }
        }
    }
}
