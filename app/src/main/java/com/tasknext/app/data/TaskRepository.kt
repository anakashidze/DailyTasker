package com.tasknext.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val settings: SettingsStore) {

    fun observeAllTasks(): Flow<List<Task>> = dao.observeAllTasks()
    fun observeActiveSlots(): Flow<List<Task>> = dao.observeActiveSlots()
    fun observePool(): Flow<List<Task>> = dao.observePool()
    fun observeOverdue(): Flow<List<Task>> = dao.observeOverdue()
    fun observeHistory(): Flow<List<CompletionLog>> = dao.observeHistory()
    fun observeDailyOutcomes(): Flow<List<DailyOutcome>> = dao.observeDailyOutcomes()

    suspend fun deleteHistoryRecord(id: Long) = dao.deleteHistoryRecord(id)
    suspend fun clearHistory() = dao.clearHistory()

    private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private suspend fun seedOutcomeLedgerFromLegacyStreakIfNeeded() {
        if (dao.getDailyOutcomesOnce().isNotEmpty()) return
        val last = settings.streakLastCompletedEpochDay
        val count = settings.streakCount
        if (last < 0L || count <= 0) return
        repeat(count) { offset ->
            dao.upsertDailyOutcome(
                DailyOutcome(
                    epochDay = last - offset,
                    status = DailyOutcomeStatus.COMPLETED
                )
            )
        }
    }

    suspend fun addTask(task: Task): Long {
        val existing = dao.getAllOnce()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        return dao.insert(task.copy(orderIndex = nextOrder, slot = null))
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

    suspend fun restoreDeletedTask(task: Task, previousQueuedTomorrowTaskId: Long) {
        dao.insert(task)
        if (previousQueuedTomorrowTaskId == task.id) {
            settings.queuedTomorrowTaskId = task.id
        }
    }

    suspend fun undoCompletion(
        task: Task,
        previousQueuedTomorrowTaskId: Long,
        previousDayCompletedEpochDay: Long,
        previousStreakCount: Int,
        previousStreakLastCompletedEpochDay: Long
    ) {
        dao.undoCompletion(task)
        if (task.slot == 0) {
            dao.deleteDailyOutcome(todayEpochDay())
        }
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
    }

    suspend fun activateBonusTask(taskId: Long) {
        if (settings.queuedTomorrowTaskId == taskId) return
        val usedSlots = dao.getAllOnce().mapNotNull { it.slot }
        val freeSlot = listOf(1, 2).firstOrNull { it !in usedSlots } ?: return
        val task = dao.getById(taskId) ?: return
        if (task.slot != null || task.isArchived || task.overdueSinceEpochDay != null) return
        dao.update(task.copy(slot = freeSlot, overdueSinceEpochDay = null))
    }

    suspend fun rolloverDayIfNeeded() {
        seedOutcomeLedgerFromLegacyStreakIfNeeded()
        val today = todayEpochDay()
        if (settings.lastActiveEpochDay == today) return

        val previousActiveDay = settings.lastActiveEpochDay
        val previousDayWasCompleted = previousActiveDay != -1L &&
            settings.dayCompletedEpochDay == previousActiveDay

        if (settings.streakLastCompletedEpochDay < today - 1L) {
            settings.streakCount = 0
        }

        val allBeforeRollover = dao.getAllOnce()
        val previousToday = allBeforeRollover.firstOrNull {
            !it.isArchived && it.slot == 0 && it.overdueSinceEpochDay == null
        }

        if (previousToday != null && !previousDayWasCompleted && previousActiveDay != -1L) {
            dao.update(
                previousToday.copy(
                    slot = null,
                    reminderEnabled = false,
                    overdueSinceEpochDay = previousActiveDay
                )
            )
        }

        if (previousActiveDay != -1L && !previousDayWasCompleted && dao.getDailyOutcome(previousActiveDay) == null) {
            dao.upsertDailyOutcome(
                DailyOutcome(epochDay = previousActiveDay, status = DailyOutcomeStatus.MISSED)
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

        val nextToday = queued

        if (nextToday != null) {
            dao.getAllOnce()
                .filter { !it.isArchived && it.slot == 0 && it.id != nextToday.id }
                .forEach { task -> dao.update(task.copy(slot = null)) }
            dao.update(nextToday.copy(slot = 0, overdueSinceEpochDay = null))
        }

        settings.queuedTomorrowTaskId = -1L
        settings.lastActiveEpochDay = today
    }

    private suspend fun recordTodayStreakCompletion(taskId: Long) {
        val today = todayEpochDay()
        dao.upsertDailyOutcome(
            DailyOutcome(
                epochDay = today,
                status = DailyOutcomeStatus.COMPLETED,
                sourceTaskId = taskId
            )
        )
        when (settings.streakLastCompletedEpochDay) {
            today -> Unit
            today - 1L -> settings.streakCount = settings.streakCount + 1
            else -> settings.streakCount = 1
        }
        settings.streakLastCompletedEpochDay = today
    }

    fun currentStreakCount(): Int {
        val today = todayEpochDay()
        if (settings.streakLastCompletedEpochDay == -1L &&
            settings.dayCompletedEpochDay == today
        ) {
            settings.streakCount = 1
            settings.streakLastCompletedEpochDay = today
        }
        return if (settings.streakLastCompletedEpochDay < today - 1L) 0 else settings.streakCount
    }

    fun isDayComplete(): Boolean = settings.dayCompletedEpochDay == todayEpochDay()

    suspend fun completeMandatory(taskId: Long) {
        val task = dao.getById(taskId) ?: return
        if (task.isArchived) return
        logAndArchiveCompletion(task)
        settings.dayCompletedEpochDay = todayEpochDay()
        recordTodayStreakCompletion(task.id)
    }

    suspend fun completeBonus(taskId: Long) {
        val task = dao.getById(taskId) ?: return
        if (task.isArchived) return
        logAndArchiveCompletion(task)
    }

    private suspend fun logAndArchiveCompletion(task: Task) {
        val log = CompletionLog(
            taskId = task.id,
            titleSnapshot = task.title,
            colorHexSnapshot = task.colorHex,
            prioritySnapshot = task.priority.name,
            groupNameSnapshot = task.groupName,
            groupIconSnapshot = task.groupIcon,
            commentSnapshot = task.comment,
            eventType = HistoryEventType.COMPLETED,
            effectiveEpochDay = todayEpochDay(),
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

    suspend fun completeFromList(taskId: Long): Int? {
        val task = dao.getById(taskId) ?: return null
        if (task.isArchived) return null
        val previousSlot = task.slot
        logAndArchiveCompletion(task)
        if (previousSlot == 0) {
            settings.dayCompletedEpochDay = todayEpochDay()
            recordTodayStreakCompletion(task.id)
        }
        return previousSlot
    }

    fun queuedTomorrowTaskId(): Long = settings.queuedTomorrowTaskId

    suspend fun suggestNext(): Task? = dao.getAllOnce()
        .asSequence()
        .filter { it.slot == null && !it.isArchived && it.overdueSinceEpochDay == null }
        .minByOrNull { it.orderIndex }
}
