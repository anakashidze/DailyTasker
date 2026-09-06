package com.tasknext.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val settings: SettingsStore) {

    fun observeAllTasks(): Flow<List<Task>> = dao.observeAllTasks()
    fun observeActiveSlots(): Flow<List<Task>> = dao.observeActiveSlots()
    fun observePool(): Flow<List<Task>> = dao.observePool()
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
        previousDayCompletedEpochDay: Long
    ) {
        dao.undoCompletion(task)
        settings.queuedTomorrowTaskId = previousQueuedTomorrowTaskId
        settings.dayCompletedEpochDay = previousDayCompletedEpochDay
    }

    suspend fun reorderPool(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            dao.getById(id)?.takeIf { it.slot == null }?.let {
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
        if (task.slot != null || task.isArchived) return
        dao.update(task.copy(slot = freeSlot))
    }

    /**
     * Call when the app opens/resumes. If the local calendar day changed,
     * clear yesterday's completion state and promote an explicitly queued task.
     */
    suspend fun rolloverDayIfNeeded() {
        val today = todayEpochDay()
        if (settings.lastActiveEpochDay == today) return

        settings.dayCompletedEpochDay = -1L
        val queuedId = settings.queuedTomorrowTaskId

        if (queuedId != -1L) {
            // Enforce the invariant that there can be only one mandatory slot.
            dao.getAllOnce()
                .filter { it.slot == 0 && it.id != queuedId }
                .forEach { moveTaskToBack(it) }

            dao.getById(queuedId)
                ?.takeIf { !it.isArchived }
                ?.let { dao.update(it.copy(slot = 0)) }

            settings.queuedTomorrowTaskId = -1L
        }

        settings.lastActiveEpochDay = today
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

        dao.update(task.copy(slot = null, orderIndex = maxOrder + 1))
    }

    /**
     * Queues a pool task for tomorrow and makes it the top/next item in the backlog,
     * keeping the visible queue and the stored tomorrow choice consistent.
     */
    suspend fun setTomorrowTask(taskId: Long) {
        val selected = dao.getById(taskId) ?: return
        if (selected.slot != null || selected.isArchived) return

        val rest = dao.getAllOnce()
            .filter { it.slot == null && it.id != taskId }
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
        if (selected.slot != null || selected.isArchived) return

        val selectedOrder = selected.orderIndex
        val current = dao.getAllOnce().firstOrNull { it.slot == 0 && !it.isArchived }

        current?.let {
            dao.update(it.copy(slot = null, orderIndex = selectedOrder))
        }
        dao.update(selected.copy(slot = 0))

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
     * Drag Today's task back into the backlog. If a backlog item exists, the current
     * top backlog item is promoted to Today and the old Today task takes its old
     * queue position. This preserves the one-thing-per-day invariant.
     */
    suspend fun moveTodayToBacklog(taskId: Long): Long? {
        val today = dao.getById(taskId) ?: return null
        if (today.slot != 0 || today.isArchived) return null

        val next = dao.getAllOnce()
            .asSequence()
            .filter { it.slot == null && !it.isArchived && it.id != taskId }
            .minByOrNull { it.orderIndex }

        if (next == null) {
            dao.update(today.copy(slot = null, orderIndex = 0))
        } else {
            val nextOrder = next.orderIndex
            dao.update(today.copy(slot = null, orderIndex = nextOrder))
            dao.update(next.copy(slot = 0))
            if (settings.queuedTomorrowTaskId == next.id) {
                settings.queuedTomorrowTaskId = -1L
            }
        }

        settings.dayCompletedEpochDay = -1L
        return next?.id
    }

    /** Removes Bonus status without deleting the task; the task returns to its existing backlog position. */
    suspend fun moveBonusToBacklog(taskId: Long) {
        val task = dao.getById(taskId) ?: return
        if (task.slot == null || task.slot == 0 || task.isArchived) return
        dao.update(task.copy(slot = null))
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
        }
        return previousSlot
    }

    fun queuedTomorrowTaskId(): Long = settings.queuedTomorrowTaskId

    /** Suggests the top/next item in the backlog queue. */
    suspend fun suggestNext(): Task? = dao.getAllOnce()
        .asSequence()
        .filter { it.slot == null && !it.isArchived }
        .minByOrNull { it.orderIndex }
}
