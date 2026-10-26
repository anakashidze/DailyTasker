package com.tasknext.app.data

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

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

    private fun currentWeekBounds(date: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.toEpochDay() to monday.plusDays(6).toEpochDay()
    }

    suspend fun availableRestCredits(): Int {
        val (start, end) = currentWeekBounds()
        val smallCompleted = dao.countSmallTaskCompletions(start, end)
        val restUsed = dao.countRestDays(start, end)
        return (smallCompleted / 5 - restUsed).coerceAtLeast(0)
    }

    suspend fun isRestToday(): Boolean =
        dao.getDailyOutcome(todayEpochDay())?.status == DailyOutcomeStatus.REST

    fun isTodayLocked(now: LocalTime = LocalTime.now()): Boolean {
        val lock = LocalTime.of(settings.todayLockHour, settings.todayLockMinute)
        return !now.isBefore(lock)
    }

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
    suspend fun updateTask(task: Task) {
        dao.updateTaskDetails(task, todayLocked = isTodayLocked())
    }

    suspend fun reassignRemovedGroup(removedGroupName: String, defaultGroup: TaskGroupDefinition) {
        dao.reassignRemovedGroup(
            removedGroupName = removedGroupName,
            defaultColorHex = defaultGroup.colorHex,
            defaultGroupName = defaultGroup.name,
            defaultIconKey = defaultGroup.iconKey
        )
    }

    suspend fun deleteTask(id: Long) {
        val task = dao.getById(id) ?: return
        if (task.slot == 0 && isTodayLocked()) return
        if (settings.queuedTomorrowTaskId == id) {
            settings.queuedTomorrowTaskId = -1L
        }
        dao.insertLog(
            CompletionLog(
                taskId = task.id,
                titleSnapshot = task.title,
                colorHexSnapshot = task.colorHex,
                prioritySnapshot = task.priority.name,
                groupNameSnapshot = task.groupName,
                groupIconSnapshot = task.groupIcon,
                commentSnapshot = task.comment,
                taskSizeSnapshot = task.taskSize,
                eventType = HistoryEventType.DELETED,
                effectiveEpochDay = todayEpochDay(),
                completedAtEpochMillis = System.currentTimeMillis()
            )
        )
        dao.delete(id)
    }

    suspend fun restoreDeletedTask(task: Task, previousQueuedTomorrowTaskId: Long) {
        // 15-second Undo is a reversal of the deletion, not a Recovery event.
        dao.deleteLatestLogForTask(task.id)
        dao.insert(task)
        if (previousQueuedTomorrowTaskId == task.id) {
            settings.queuedTomorrowTaskId = task.id
        }
    }

    suspend fun recoverHistoryRecord(logId: Long): Long? {
        val source = dao.getHistoryById(logId) ?: return null
        if (source.eventType == HistoryEventType.RECOVERED) return null
        if (dao.recoveryCountFor(logId) > 0) return null

        val maxOrder = dao.getAllOnce().maxOfOrNull { it.orderIndex } ?: -1
        val newId = dao.insert(
            Task(
                title = source.titleSnapshot,
                colorHex = source.colorHexSnapshot,
                groupName = source.groupNameSnapshot,
                groupIcon = source.groupIconSnapshot,
                priority = runCatching { Priority.valueOf(source.prioritySnapshot) }
                    .getOrDefault(Priority.MEDIUM),
                taskSize = source.taskSizeSnapshot,
                comment = source.commentSnapshot,
                reminderEnabled = false,
                reminderHour = null,
                reminderMinute = null,
                orderIndex = maxOrder + 1,
                slot = null,
                overdueSinceEpochDay = null,
                isArchived = false
            )
        )

        dao.insertLog(
            CompletionLog(
                taskId = newId,
                titleSnapshot = source.titleSnapshot,
                colorHexSnapshot = source.colorHexSnapshot,
                prioritySnapshot = source.prioritySnapshot,
                groupNameSnapshot = source.groupNameSnapshot,
                groupIconSnapshot = source.groupIconSnapshot,
                commentSnapshot = source.commentSnapshot,
                taskSizeSnapshot = source.taskSizeSnapshot,
                eventType = HistoryEventType.RECOVERED,
                effectiveEpochDay = todayEpochDay(),
                recoveredFromLogId = source.id,
                completedAtEpochMillis = System.currentTimeMillis()
            )
        )
        return newId
    }

    suspend fun undoCompletion(
        task: Task,
        previousQueuedTomorrowTaskId: Long,
        previousDayCompletedEpochDay: Long,
        previousStreakCount: Int,
        previousStreakLastCompletedEpochDay: Long
    ) {
        if (dao.getById(task.id)?.isArchived != true) return
        val today = todayEpochDay()
        val outcome = dao.getDailyOutcome(today)
        val slotOccupied = task.slot != null && dao.getAllOnce().any { it.id != task.id && it.slot == task.slot }
        val laterMainCompleted = task.slot == 0 && outcome?.status == DailyOutcomeStatus.COMPLETED && outcome.sourceTaskId != task.id
        // Undo must not displace a later selection or create two tasks in one slot.
        dao.undoCompletion(if (slotOccupied || laterMainCompleted) task.copy(slot = null) else task)
        if (task.slot == 0 && outcome?.sourceTaskId == task.id) {
            dao.deleteDailyOutcome(today)
        }
        settings.queuedTomorrowTaskId = previousQueuedTomorrowTaskId
        settings.dayCompletedEpochDay = if (dao.getDailyOutcome(today)?.status in
            setOf(DailyOutcomeStatus.COMPLETED, DailyOutcomeStatus.REST)) today else -1L
        recalculateStreakCacheFromLedger()
    }

    suspend fun reorderPool(orderedIds: List<Long>) {
        val candidates = dao.getAllOnce().filter { it.slot == null && it.overdueSinceEpochDay == null }.associateBy { it.id }
        val reordered = orderedIds.distinct().mapIndexedNotNull { index, id -> candidates[id]?.copy(orderIndex = index) }
        // Room updates this batch in one transaction: no partially reordered emissions.
        dao.updateTasks(reordered)
    }

    suspend fun activateBonusTask(taskId: Long) {
        val usedSlots = dao.getAllOnce().mapNotNull { it.slot }
        val freeSlot = listOf(1, 2).firstOrNull { it !in usedSlots } ?: return
        val task = dao.getById(taskId) ?: return
        if (
            task.taskSize != TaskSize.SMALL ||
            task.slot != null ||
            task.isArchived ||
            task.overdueSinceEpochDay != null
        ) return
        dao.update(task.copy(slot = freeSlot, overdueSinceEpochDay = null))
    }

    suspend fun rolloverDayIfNeeded() {
        seedOutcomeLedgerFromLegacyStreakIfNeeded()
        val today = todayEpochDay()
        if (settings.lastActiveEpochDay == today) return

        val previousActiveDay = settings.lastActiveEpochDay
        val previousDayWasCompleted = previousActiveDay != -1L &&
            settings.dayCompletedEpochDay == previousActiveDay

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

        // v2: Today is always chosen explicitly by the user. Day rollover never
        // promotes Tomorrow/Next automatically.
        settings.dayCompletedEpochDay = -1L
        settings.queuedTomorrowTaskId = -1L
        settings.lastActiveEpochDay = today
        recalculateStreakCacheFromLedger()
    }

    private suspend fun recalculateStreakCacheFromLedger() {
        val outcomes = dao.getDailyOutcomesOnce().associateBy { it.epochDay }
        var day = todayEpochDay()
        if (outcomes[day] == null) day -= 1L

        var completedCount = 0
        var latestCompletedDay = -1L
        while (true) {
            when (val outcome = outcomes[day]) {
                null -> break
                else -> when (outcome.status) {
                    DailyOutcomeStatus.COMPLETED -> {
                        if (latestCompletedDay == -1L) latestCompletedDay = day
                        completedCount++
                    }
                    DailyOutcomeStatus.REST -> Unit
                    DailyOutcomeStatus.MISSED -> break
                }
            }
            if (outcomes[day]?.status == DailyOutcomeStatus.MISSED) break
            day -= 1L
        }

        settings.streakCount = completedCount
        settings.streakLastCompletedEpochDay = latestCompletedDay
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
        recalculateStreakCacheFromLedger()
    }

    suspend fun useRestCreditToday(): Boolean {
        val today = todayEpochDay()
        if (!isTodayLocked()) return false
        if (settings.dayCompletedEpochDay == today) return false
        val hasToday = dao.getAllOnce().any {
            !it.isArchived && it.slot == 0 && it.overdueSinceEpochDay == null
        }
        if (hasToday || availableRestCredits() <= 0) return false

        dao.upsertDailyOutcome(
            DailyOutcome(
                epochDay = today,
                status = DailyOutcomeStatus.REST
            )
        )
        settings.dayCompletedEpochDay = today
        recalculateStreakCacheFromLedger()
        return true
    }

    suspend fun currentStreakCount(): Int {
        // REST preserves continuity without adding a completed day. The last
        // completion alone cannot tell whether intervening days were protected.
        recalculateStreakCacheFromLedger()
        return settings.streakCount
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

    suspend fun completeOverdue(taskId: Long, completedYesterday: Boolean): Boolean {
        val task = dao.getById(taskId) ?: return false
        if (task.isArchived || task.overdueSinceEpochDay == null) return false

        val yesterday = todayEpochDay() - 1L
        val effectiveDay = if (
            completedYesterday && task.overdueSinceEpochDay == yesterday
        ) yesterday else todayEpochDay()

        logAndArchiveCompletion(task, effectiveDay)

        if (completedYesterday && effectiveDay == yesterday) {
            dao.upsertDailyOutcome(
                DailyOutcome(
                    epochDay = yesterday,
                    status = DailyOutcomeStatus.COMPLETED,
                    sourceTaskId = task.id
                )
            )
            recalculateStreakCacheFromLedger()
        }
        return true
    }

    private suspend fun logAndArchiveCompletion(
        task: Task,
        effectiveEpochDay: Long = todayEpochDay()
    ) {
        val log = CompletionLog(
            taskId = task.id,
            titleSnapshot = task.title,
            colorHexSnapshot = task.colorHex,
            prioritySnapshot = task.priority.name,
            groupNameSnapshot = task.groupName,
            groupIconSnapshot = task.groupIcon,
            commentSnapshot = task.comment,
            taskSizeSnapshot = task.taskSize,
            eventType = HistoryEventType.COMPLETED,
            effectiveEpochDay = effectiveEpochDay,
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
        if (
            selected.taskSize != TaskSize.BIG ||
            selected.slot != null ||
            selected.isArchived ||
            selected.overdueSinceEpochDay != null
        ) return

        val big = dao.getAllOnce()
            .filter {
                it.taskSize == TaskSize.BIG &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null &&
                it.id != taskId
            }
            .sortedBy { it.orderIndex }
        val small = dao.getAllOnce()
            .filter {
                it.taskSize == TaskSize.SMALL &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null
            }
            .sortedBy { it.orderIndex }

        val ordered = listOf(selected) + big + small
        dao.updateTasks(ordered.mapIndexed { index, task -> task.copy(orderIndex = index) })
        settings.queuedTomorrowTaskId = -1L
    }

    suspend fun replaceMandatoryWith(
        taskId: Long,
        backlogOrderAfterSwap: List<Long>? = null
    ): Boolean {
        if (isTodayLocked()) return false
        val selected = dao.getById(taskId) ?: return false
        if (
            selected.taskSize != TaskSize.BIG ||
            selected.slot != null ||
            selected.isArchived ||
            selected.overdueSinceEpochDay != null
        ) return false

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
        return true
    }

    suspend fun moveTodayToBacklogAt(taskId: Long, insertionIndex: Int): Long? {
        if (isTodayLocked()) return null
        val all = dao.getAllOnce()
        val today = all.firstOrNull { it.id == taskId } ?: return null
        if (today.slot != 0 || today.isArchived || today.taskSize != TaskSize.BIG) return null

        val bigPool = all
            .filter {
                it.taskSize == TaskSize.BIG &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null &&
                it.id != taskId
            }
            .sortedBy { it.orderIndex }
            .toMutableList()
        val smallPool = all
            .filter {
                it.taskSize == TaskSize.SMALL &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null
            }
            .sortedBy { it.orderIndex }

        bigPool.add(insertionIndex.coerceIn(0, bigPool.size), today.copy(slot = null, overdueSinceEpochDay = null))
        val ordered = bigPool + smallPool
        dao.updateTasks(ordered.mapIndexed { index, task -> task.copy(orderIndex = index, slot = null) })

        settings.dayCompletedEpochDay = -1L
        return null
    }

    suspend fun moveTodayToBacklog(taskId: Long): Long? =
        moveTodayToBacklogAt(taskId, 0)

    suspend fun moveBonusToBacklogAt(taskId: Long, insertionIndex: Int) {
        val all = dao.getAllOnce()
        val task = all.firstOrNull { it.id == taskId } ?: return
        if (
            task.taskSize != TaskSize.SMALL ||
            task.slot == null ||
            task.slot == 0 ||
            task.isArchived
        ) return

        val bigPool = all
            .filter {
                it.taskSize == TaskSize.BIG &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null
            }
            .sortedBy { it.orderIndex }
        val smallPool = all
            .filter {
                it.taskSize == TaskSize.SMALL &&
                it.slot == null &&
                !it.isArchived &&
                it.overdueSinceEpochDay == null &&
                it.id != taskId
            }
            .sortedBy { it.orderIndex }
            .toMutableList()

        smallPool.add(insertionIndex.coerceIn(0, smallPool.size), task.copy(slot = null, overdueSinceEpochDay = null))
        val ordered = bigPool + smallPool
        dao.updateTasks(ordered.mapIndexed { index, poolTask -> poolTask.copy(slot = null, orderIndex = index) })
    }

    suspend fun moveBonusToBacklog(taskId: Long) {
        val poolSize = dao.getAllOnce().count {
            it.taskSize == TaskSize.SMALL &&
            it.slot == null &&
            !it.isArchived &&
            it.overdueSinceEpochDay == null
        }
        moveBonusToBacklogAt(taskId, poolSize)
    }

    suspend fun moveTodayToBonus(taskId: Long): Long? {
        // Big Today tasks cannot be converted into Bonus in v2. Change Task Size
        // explicitly before the daily lock if the task should become Small.
        return null
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
        .filter {
            it.taskSize == TaskSize.BIG &&
            it.slot == null &&
            !it.isArchived &&
            it.overdueSinceEpochDay == null
        }
        .minByOrNull { it.orderIndex }
}
