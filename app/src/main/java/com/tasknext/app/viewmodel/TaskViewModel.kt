package com.tasknext.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.Priority
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import com.tasknext.app.data.TaskGroupDefinition
import com.tasknext.app.data.TaskGroups
import com.tasknext.app.data.TaskRepository
import com.tasknext.app.notifications.NotificationScheduler
import com.tasknext.app.widget.TodayTaskWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).taskDao()
    val settings = SettingsStore(application)
    private val repo = TaskRepository(dao, settings)

    val mandatoryTask: StateFlow<Task?> = repo.observeActiveSlots()
        .map { slots -> slots.firstOrNull { it.slot == 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bonusTasks: StateFlow<List<Task>> = repo.observeActiveSlots()
        .map { slots -> slots.filter { it.slot != 0 }.sortedBy { it.slot } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pool = repo.observePool()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overdueTasks = repo.observeOverdue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks = repo.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _taskGroups = MutableStateFlow(TaskGroups.builtIns + settings.customGroups)
    val taskGroups: StateFlow<List<TaskGroupDefinition>> = _taskGroups

    private val _dayComplete = MutableStateFlow(false)
    val dayComplete: StateFlow<Boolean> = _dayComplete

    private val _streakCount = MutableStateFlow(repo.currentStreakCount())
    val streakCount: StateFlow<Int> = _streakCount

    private val _showTomorrowPicker = MutableStateFlow(false)
    val showTomorrowPicker: StateFlow<Boolean> = _showTomorrowPicker

    private val _queuedTomorrowTaskId = MutableStateFlow(repo.queuedTomorrowTaskId())
    val queuedTomorrowTaskId: StateFlow<Long> = _queuedTomorrowTaskId

    private fun refreshTodayWidget() = TodayTaskWidget.requestUpdate(getApplication())

    init { onAppResumed() }

    fun onAppResumed() = viewModelScope.launch {
        repo.rolloverDayIfNeeded()
        _dayComplete.value = repo.isDayComplete()
        _streakCount.value = repo.currentStreakCount()
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        syncReminderAlarms()
        refreshTodayWidget()
    }

    fun addTask(
        title: String,
        colorHex: String,
        groupName: String,
        groupIcon: String,
        priority: Priority,
        comment: String,
        reminderEnabled: Boolean,
        hour: Int?,
        minute: Int?
    ) = viewModelScope.launch {
        val id = repo.addTask(
            Task(
                title = title,
                colorHex = colorHex,
                groupName = groupName,
                groupIcon = groupIcon,
                priority = priority,
                comment = comment,
                reminderEnabled = reminderEnabled,
                reminderHour = hour,
                reminderMinute = minute
            )
        )
        repo.getTask(id)?.let { syncTaskReminder(it) }
        refreshTodayWidget()
    }

    fun updateTask(task: Task) = viewModelScope.launch {
        repo.updateTask(task)
        syncTaskReminder(task)
        refreshTodayWidget()
    }

    fun addCustomGroup(group: TaskGroupDefinition) {
        val clean = group.copy(name = group.name.trim(), builtIn = false)
        if (clean.name.isBlank()) return
        if (TaskGroups.builtIns.any { it.name.equals(clean.name, ignoreCase = true) }) return
        val custom = settings.customGroups
            .filterNot { it.name.equals(clean.name, ignoreCase = true) } + clean
        settings.customGroups = custom
        _taskGroups.value = TaskGroups.builtIns + custom
    }

    fun removeCustomGroup(group: TaskGroupDefinition) = viewModelScope.launch {
        if (group.builtIn || TaskGroups.builtIns.any { it.name.equals(group.name, ignoreCase = true) }) return@launch
        val defaultGroup = TaskGroups.default
        repo.reassignRemovedGroup(group.name, defaultGroup)
        val custom = settings.customGroups.filterNot { it.name.equals(group.name, ignoreCase = true) }
        settings.customGroups = custom
        _taskGroups.value = TaskGroups.builtIns + custom
        refreshTodayWidget()
    }

    fun deleteHistoryRecord(id: Long) = viewModelScope.launch { repo.deleteHistoryRecord(id) }
    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }

    fun deleteTask(task: Task) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), task.id)
        repo.deleteTask(task.id)
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        refreshTodayWidget()
    }

    fun undoDelete(task: Task, previousQueuedTomorrowTaskId: Long) = viewModelScope.launch {
        repo.restoreDeletedTask(task, previousQueuedTomorrowTaskId)
        repo.getTask(task.id)?.let { syncTaskReminder(it) }
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        refreshTodayWidget()
    }

    fun undoCompletion(
        task: Task,
        previousQueuedTomorrowTaskId: Long,
        previousDayCompletedEpochDay: Long,
        previousStreakCount: Int,
        previousStreakLastCompletedEpochDay: Long
    ) = viewModelScope.launch {
        repo.undoCompletion(
            task,
            previousQueuedTomorrowTaskId,
            previousDayCompletedEpochDay,
            previousStreakCount,
            previousStreakLastCompletedEpochDay
        )
        repo.getTask(task.id)?.let { syncTaskReminder(it) }
        _dayComplete.value = repo.isDayComplete()
        _streakCount.value = repo.currentStreakCount()
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        _showTomorrowPicker.value = false
        refreshTodayWidget()
    }

    fun reorderPool(orderedIds: List<Long>) = viewModelScope.launch {
        repo.reorderPool(orderedIds)
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
    }

    fun activateBonusFromPool(taskId: Long) = viewModelScope.launch {
        repo.activateBonusTask(taskId)
        repo.getTask(taskId)?.let { syncTaskReminder(it) }
    }

    fun moveBonusToBacklog(taskId: Long) = viewModelScope.launch {
        repo.moveBonusToBacklog(taskId)
        NotificationScheduler.cancel(getApplication(), taskId)
    }

    fun moveBonusToBacklogAt(taskId: Long, insertionIndex: Int) = viewModelScope.launch {
        repo.moveBonusToBacklogAt(taskId, insertionIndex)
        NotificationScheduler.cancel(getApplication(), taskId)
    }

    fun moveTodayToBacklog(taskId: Long) = moveTodayToBacklogAt(taskId, 0)

    fun moveTodayToBacklogAt(taskId: Long, insertionIndex: Int) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        val newTodayId = repo.moveTodayToBacklogAt(taskId, insertionIndex)
        newTodayId?.let { id -> repo.getTask(id)?.let { syncTaskReminder(it) } }
        _dayComplete.value = false
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        refreshTodayWidget()
    }

    fun moveTodayToBonus(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        val newTodayId = repo.moveTodayToBonus(taskId)
        repo.getTask(taskId)?.let { syncTaskReminder(it) }
        newTodayId?.let { id -> repo.getTask(id)?.let { syncTaskReminder(it) } }
        _dayComplete.value = false
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        refreshTodayWidget()
    }

    fun replaceTodayWith(taskId: Long, backlogOrderAfterSwap: List<Long>? = null) = viewModelScope.launch {
        val previousToday = mandatoryTask.value
        repo.replaceMandatoryWith(taskId, backlogOrderAfterSwap)
        previousToday?.let { NotificationScheduler.cancel(getApplication(), it.id) }
        repo.getTask(taskId)?.let { syncTaskReminder(it) }
        _dayComplete.value = false
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        refreshTodayWidget()
    }

    fun completeFromList(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        val previousSlot = repo.completeFromList(taskId)
        if (previousSlot == 0) {
            _dayComplete.value = true
            _streakCount.value = repo.currentStreakCount()
            if (settings.autoFillNextTask) {
                repo.suggestNext()?.let { repo.setTomorrowTask(it.id) }
                _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
                _showTomorrowPicker.value = false
            } else {
                _showTomorrowPicker.value = true
            }
        } else {
            _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        }
        refreshTodayWidget()
    }

    fun completeMandatory(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        repo.completeMandatory(taskId)
        _dayComplete.value = true
        _streakCount.value = repo.currentStreakCount()
        if (settings.autoFillNextTask) {
            repo.suggestNext()?.let { repo.setTomorrowTask(it.id) }
            _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
            _showTomorrowPicker.value = false
        } else {
            _showTomorrowPicker.value = true
        }
        refreshTodayWidget()
    }

    fun completeBonus(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        repo.completeBonus(taskId)
        refreshTodayWidget()
    }

    fun dismissTomorrowPicker() { _showTomorrowPicker.value = false }

    fun setTomorrowTask(taskId: Long) = viewModelScope.launch {
        repo.setTomorrowTask(taskId)
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        _showTomorrowPicker.value = false
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.notificationsEnabled = enabled
        viewModelScope.launch { syncReminderAlarms() }
    }

    fun setDefaultReminderHour(hour: Int) {
        settings.defaultReminderHour = hour.coerceIn(0, 23)
        viewModelScope.launch { syncReminderAlarms() }
    }

    fun setDefaultReminderMinute(minute: Int) {
        settings.defaultReminderMinute = minute.coerceIn(0, 59)
        viewModelScope.launch { syncReminderAlarms() }
    }

    fun setSnoozeMinutes(minutes: Int) { settings.snoozeMinutes = minutes.coerceIn(1, 24 * 60) }

    private suspend fun syncReminderAlarms() {
        repo.getAllTasksOnce().forEach { syncTaskReminder(it) }
    }

    private fun syncTaskReminder(task: Task) {
        if (task.slot != null && task.reminderEnabled && settings.notificationsEnabled) {
            NotificationScheduler.schedule(
                getApplication(),
                task,
                settings.defaultReminderHour,
                settings.defaultReminderMinute
            )
        } else {
            NotificationScheduler.cancel(getApplication(), task.id)
        }
    }
}
