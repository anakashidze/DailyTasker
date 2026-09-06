package com.tasknext.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val settings: SettingsStore) {

    fun observeAllTasks(): Flow<List<Task>> = dao.observeAllTasks()
    fun observeActiveSlots(): Flow<List<Task>> = dao.observeActiveSlots()
    fun observePool(): Flow<List<Task>> = dao.observePool()
    fun observeOverdue(): Flow<List<Task>> = dao.observeOverdue()
    fun observeHistory(): Flow<List<CompletionLog>> = dao.observeHistory()

    suspend fun deleteHistoryRecord(id: Long) = dao.deleteHistoryRecord(id)
    suspend fun clearHistory() = dao.clearHistory()

    private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    suspend fun addTask(task: Task): Long {
        val existing = dao.getAllOnce()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val id = dao.insert(task.copy(orderIndex = nextOrder))

        // Do not silently create a new "today" task after the day was already completed.
        if (!isDayComplete() && existing.none { it.slot == 0 }) {
            dao.getById(id)?.let { dao.update(it.copy(slot = 0)) }
        }
        return id
    }

    suspend fun getTask(id: Long): Task? = dao.getById(id)

    suspend fun getAllTasksOnce(): List<Task> = dao.getAllOnce()

    suspend fun updateTask(task: Task) = dao.update(task)

    suspend fun reassignRemovedGroup(removedGroupName: String, defaultGroup: TaskGroupDefinition) {
        dao.reassignRemovedGroup(
            removedGroupName = removedGroupName,
            defaultColorHex = defaultGroup.colorHex,
            defaultGroupName = defaultGroup.name,
            defaultIconKey = defaultGroup.iconKey
        )
    }

    suspend fun deleteTask(id: Long) {
        if (settings.queuedTomorrowTaskId == id) {
            settings.queuedTomorrowTaskId = -1L
        }
        dao.delete(id)
    }

    /** Reinsert a task deleted by a swipe Undo, preserving its original id/state. */
    suspend fun restoreDeletedTask(task: Task, previousQueuedTomorrowTaskId: Long) {
        dao.insert(task)
        if (previousQueuedTomorrowTaskId == task.id) {
            settings.queuedTomorrowTaskId = task.id
        }
    }

    /**
     * Reverse the latest completion: restore the original active task, remove only
     * the History row created by that completion, and restore day/tomorrow state.
     */
    suspend fun undoCompletion(
        task: Task,
        previousQueuedTomorrowTaskId: Long,
        previousDayCompletedEpochDay: Long,
        previousStreakCount: Int,
        previousStreakLastCompletedEpochDay: Long
    ) {
        dao.undoCompletion(task)
        settings.queuedTomorrowTaskId = previousQueuedTomorrowTaskId
        settings.dayCompletedEpochDay = previousDayCompletedEpochDay
        settings.streakCount = previousStreakCount
        settings.streakLastCompletedEpochDay = previousStreakLastCompletedEpochDay
    }

    suspend fun reorderPool(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            dao.getById(id)?.takeIf { it.slot == null && it.overdueSinceEpochDay == null }?.let {
                dao.update(it.copy(orderIndex = index))
            }
        }
        // An explicitly selected tomorrow task follows the task itself, not its visual position.
    }

    /** Pulls a bonus (non-mandatory) task from the pool into slot 1 or 2. */
    suspend fun activateBonusTask(taskId: Long) {
        // A task explicitly reserved for tomorrow should not also become a bonus task today.
        if (settings.queuedTomorrowTaskId == taskId) return

        val usedSlots = dao.getAllOnce().mapNotNull { it.slot }
        val freeSlot = listOf(1, 2).firstOrNull { it !in usedSlots } ?: return
        val task = dao.getById(taskId) ?: return
        if (task.slot != null || task.isArchived || task.overdueSinceEpochDay != null) return
        dao.update(task.copy(slot = freeSlot, overdueSinceEpochDay = null))
    }

    /**
     * Call when the app opens/resumes. If the local calendar day changed,
     * clear yesterday's completion state and promote an explicitly queued task.
     */
    suspend fun rolloverDayIfNeeded() {
        val today = todayEpochDay()
        if (settings.lastActiveEpochDay == today) return

        val previousActiveDay = settings.lastActiveEpochDay
        val previousDayWasCompleted = previousActiveDay != -1L &&
            settings.dayCompletedEpochDay == previousActiveDay

        // A streak is broken only after at least one full local day was missed.
        if (settings.streakLastCompletedEpochDay < today - 1L) {
            settings.streakCount = 0
        }

        val allBeforeRollover = dao.getAllOnce()
        val previousToday = allBeforeRollover.firstOrNull {
            !it.isArchived && it.slot == 0 && it.overdueSinceEpochDay == null
        }

        // Yesterday's unfinished mandatory task becomes a separate Overdue item.
        if (previousToday != null && !previousDayWasCompleted && previousActiveDay != -1L) {
            dao.update(
                previousToday.copy(
                    slot = null,
                    reminderEnabled = false,
                    overdueSinceEpochDay = previousActiveDay
                )
            )
        }

        settings.dayCompletedEpochDay = -1L
        val queuedId = settings.queuedTomorrowTaskId

        val freshAll = dao.getAllOnce()
        val queued = if (queuedId != -1L) {
            freshAll.firstOrNull {
                it.id == queuedId && !it.isArchived && it.overdueSinceEpochDay == null
            }
        } else null

        val nextToday = queued ?: freshAll
            .asSequence()
            .filter { !it.isArchived && it.slot == null && it.overdueSinceEpochDay == null }
            .minByOrNull { it.orderIndex }

        if (nextToday != null) {
            // Clear any stale mandatory slot first, then promote exactly one task.
            dao.getAllOnce()
                .filter { !it.isArchived && it.slot == 0 && it.id != nextToday.id }
                .forEach { task -> dao.update(task.copy(slot = null)) }
            dao.update(nextToday.copy(slot = 0, overdueSinceEpochDay = null))
        }

        settings.queuedTomorrowTaskId = -1L
        settings.lastActiveEpochDay = today
    }

    private fun recordTodayStreakCompletion() {
        val today = todayEpochDay()
        when (settings.streakLastCompletedEpochDay) {
            today -> Unit // already counted today
            today - 1L -> settings.streakCount = settings.streakCount + 1
            else -> settings.streakCount = 1
        }
        settings.streakLastCompletedEpochDay = today
    }

    fun currentStreakCount(): Int {
        val today = todayEpochDay()
        // When upgrading from an older version on a day that was already completed,
        // seed the new streak feature with today rather than displaying 0 immediately.
        if (settings.streakLastCompletedEpochDay == -1L &&
            settings.dayCompletedEpochDay == today
        ) {
            settings.streakCount = 1
            settings.streakLastCompletedEpochDay = today
        }
        return if (settings.streakLastCompletedEpochDay < today - 1L) 0 else settings.streakCount
    }

    fun isDayComplete(): Boolean = settings.dayCompletedEpochDay == todayEpochDay()

    /**
     * Completes the mandatory task. A completed task is terminal: it is logged to
     * History and archived so it disappears from Today, Bonus and Backlog lists.
     */
    suspend fun completeMandatory(taskId: Long) {
        val task = dao.getById(taskId) ?: return
        if (task.isArchived) return
        logAndArchiveCompletion(task)
        settings.dayCompletedEpochDay = todayEpochDay()
        recordTodayStreakCompletion()
    }

    /** Completes a bonus task and removes it from every active task list. */
    suspend fun completeBonus(taskId: Long) {
        val task = dao.getById(taskId) ?: return
        if (task.isArchived) return
        logAndArchiveCompletion(task)
    }

    /**
     * Single completion invariant used by every UI entry point. The Room DAO
     * writes History and archives the task in one database transaction.
     */
    private suspend fun logAndArchiveCompletion(task: Task) {
        val log = CompletionLog(
            taskId = task.id,
            titleSnapshot = task.title,
            colorHexSnapshot = task.colorHex,
            prioritySnapshot = task.priority.name,
            groupNameSnapshot = task.groupName,
            groupIconSnapshot = task.groupIcon,
            commentSnapshot = task.comment,
            completedAtEpochMillis = System.currentTimeMillis()
        )

        dao.archiveAsCompleted(task, log)

        if (settings.queuedTomorrowTaskId == task.id) {
            settings.queuedTomorrowTaskId = -1L
        }
    }

    private suspend fun moveTaskToBack(task: Task) {
        val maxOrder = dao.getAllOnce()
            .asSequence()
            .filter { it.id != task.id }
            .maxOfOrNull { it.orderIndex } ?: -1

        dao.update(task.copy(slot = null, orderIndex = maxOrder + 1, overdueSinceEpochDay = null))
    }

    /**
     * Queues a pool task for tomorrow and makes it the top/next item in the backlog,
     * keeping the visible queue and the stored tomorrow choice consistent.
     */
    suspend fun setTomorrowTask(taskId: Long) {
        val selected = dao.getById(taskId) ?: return
        if (selected.slot != null || selected.isArchived || selected.overdueSinceEpochDay != null) return

        val rest = dao.getAllOnce()
            .filter { it.slot == null && it.overdueSinceEpochDay == null && it.id != taskId }
            .sortedBy { it.orderIndex }

        dao.update(selected.copy(orderIndex = 0))
        rest.forEachIndexed { index, task ->
            dao.update(task.copy(orderIndex = index + 1))
        }
        settings.queuedTomorrowTaskId = taskId
    }


    /**
     * Swap a backlog task into today's mandatory slot while returning the previous
     * Today task to the backlog. When the UI supplies the desired backlog order,
     * persist that exact order so a recently reordered queue cannot snap back.
     */
    suspend fun replaceMandatoryWith(
        taskId: Long,
        backlogOrderAfterSwap: List<Long>? = null
    ) {
        val selected = dao.getById(taskId) ?: return
        if (selected.slot != null || selected.isArchived || selected.overdueSinceEpochDay != null) return

        val selectedOrder = selected.orderIndex
        val current = dao.getAllOnce().firstOrNull { it.slot == 0 && !it.isArchived }

        current?.let {
            dao.update(it.copy(slot = null, orderIndex = selectedOrder, overdueSinceEpochDay = null))
        }
        dao.update(selected.copy(slot = 0, overdueSinceEpochDay = null))

        backlogOrderAfterSwap?.forEachIndexed { index, id ->
            dao.getById(id)
                ?.takeIf { !it.isArchived && it.id != taskId }
                ?.let { dao.update(it.copy(slot = null, orderIndex = index)) }
        }

        if (settings.queuedTomorrowTaskId == taskId) {
            settings.queuedTomorrowTaskId = -1L
        }
        settings.dayCompletedEpochDay = -1L
    }

    /**
     * Move Today's task back into the backlog at the release position. If a backlog
     * task exists, its current top item becomes Today; the dragged task is inserted
     * near the finger position among the remaining backlog tasks.
     */
    suspend fun moveTodayToBacklogAt(taskId: Long, insertionIndex: Int): Long? {
        val all = dao.getAllOnce()
        val today = all.firstOrNull { it.id == taskId } ?: return null
        if (today.slot != 0 || today.isArchived) return null

        val pool = all
            .filter { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null && it.id != taskId }
            .sortedBy { it.orderIndex }

        val next = pool.firstOrNull()
        if (next == null) {
            dao.updateTasks(listOf(today.copy(slot = null, orderIndex = 0, overdueSinceEpochDay = null)))
        } else {
            val remaining = pool.drop(1).toMutableList()
            // insertionIndex is based on the pool before the top task is promoted.
            val adjustedIndex = (insertionIndex - 1).coerceIn(0, remaining.size)
            remaining.add(adjustedIndex, today.copy(slot = null, overdueSinceEpochDay = null))

            val updates = buildList {
                add(next.copy(slot = 0, overdueSinceEpochDay = null))
                remaining.forEachIndexed { index, task ->
                    add(task.copy(slot = null, orderIndex = index))
                }
            }
            dao.updateTasks(updates)

            if (settings.queuedTomorrowTaskId == next.id) {
                settings.queuedTomorrowTaskId = -1L
            }
        }

        settings.dayCompletedEpochDay = -1L
        return next?.id
    }

    suspend fun moveTodayToBacklog(taskId: Long): Long? =
        moveTodayToBacklogAt(taskId, 0)

    /** Move a Bonus task into Backlog at the exact release position. */
    suspend fun moveBonusToBacklogAt(taskId: Long, insertionIndex: Int) {
        val all = dao.getAllOnce()
        val task = all.firstOrNull { it.id == taskId } ?: return
        if (task.slot == null || task.slot == 0 || task.isArchived) return

        val pool = all
            .filter { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null && it.id != taskId }
            .sortedBy { it.orderIndex }
            .toMutableList()

        pool.add(insertionIndex.coerceIn(0, pool.size), task.copy(slot = null, overdueSinceEpochDay = null))
        dao.updateTasks(
            pool.mapIndexed { index, poolTask ->
                poolTask.copy(slot = null, orderIndex = index)
            }
        )
    }

    suspend fun moveBonusToBacklog(taskId: Long) {
        val poolSize = dao.getAllOnce().count { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null }
        moveBonusToBacklogAt(taskId, poolSize)
    }

    /**
     * Convert Today into a Bonus task immediately. The top backlog task becomes Today
     * when available so the daily mandatory slot remains populated.
     */
    suspend fun moveTodayToBonus(taskId: Long): Long? {
        val all = dao.getAllOnce()
        val today = all.firstOrNull { it.id == taskId } ?: return null
        if (today.slot != 0 || today.isArchived) return null

        val usedBonusSlots = all.mapNotNull { it.slot }.filter { it in 1..2 }.toSet()
        val freeBonusSlot = listOf(1, 2).firstOrNull { it !in usedBonusSlots } ?: return null
        val pool = all
            .filter { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null && it.id != taskId }
            .sortedBy { it.orderIndex }
        val next = pool.firstOrNull()

        val updates = buildList {
            add(today.copy(slot = freeBonusSlot, overdueSinceEpochDay = null))
            if (next != null) {
                add(next.copy(slot = 0, overdueSinceEpochDay = null))
                pool.drop(1).forEachIndexed { index, task ->
                    add(task.copy(slot = null, orderIndex = index))
                }
            }
        }
        dao.updateTasks(updates)

        if (next != null && settings.queuedTomorrowTaskId == next.id) {
            settings.queuedTomorrowTaskId = -1L
        }
        settings.dayCompletedEpochDay = -1L
        return next?.id
    }

    /**
     * Marks any task complete from the task list without first making a backlog
     * task Today's task. Completed tasks are removed from all active lists.
     */
    suspend fun completeFromList(taskId: Long): Int? {
        val task = dao.getById(taskId) ?: return null
        if (task.isArchived) return null
        val previousSlot = task.slot
        logAndArchiveCompletion(task)
        if (previousSlot == 0) {
            settings.dayCompletedEpochDay = todayEpochDay()
            recordTodayStreakCompletion()
        }
        return previousSlot
    }

    fun queuedTomorrowTaskId(): Long = settings.queuedTomorrowTaskId

    /** Suggests the top/next item in the backlog queue. */
    suspend fun suggestNext(): Task? = dao.getAllOnce()
        .asSequence()
        .filter { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null }
        .minByOrNull { it.orderIndex }
}
