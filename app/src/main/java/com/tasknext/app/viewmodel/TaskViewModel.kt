package com.tasknext.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasknext.app.data.AppDatabase
import com.tasknext.app.data.Priority
import com.tasknext.app.data.SettingsStore
import com.tasknext.app.data.Task
import com.tasknext.app.data.TaskRepository
import com.tasknext.app.notifications.NotificationScheduler
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

    val allTasks = repo.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dayComplete = MutableStateFlow(false)
    val dayComplete: StateFlow<Boolean> = _dayComplete

    private val _showTomorrowPicker = MutableStateFlow(false)
    val showTomorrowPicker: StateFlow<Boolean> = _showTomorrowPicker

    private val _queuedTomorrowTaskId = MutableStateFlow(repo.queuedTomorrowTaskId())
    val queuedTomorrowTaskId: StateFlow<Long> = _queuedTomorrowTaskId

    init {
        onAppResumed()
    }

    fun onAppResumed() = viewModelScope.launch {
        repo.rolloverDayIfNeeded()
        _dayComplete.value = repo.isDayComplete()
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
        syncReminderAlarms()
    }

    fun addTask(
        title: String,
        colorHex: String,
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
                priority = priority,
                comment = comment,
                reminderEnabled = reminderEnabled,
                reminderHour = hour,
                reminderMinute = minute
            )
        )
        repo.getTask(id)?.let { syncTaskReminder(it) }
    }

    fun updateTask(task: Task) = viewModelScope.launch {
        repo.updateTask(task)
        syncTaskReminder(task)
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), task.id)
        repo.deleteTask(task.id)
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
    }

    fun reorderPool(orderedIds: List<Long>) = viewModelScope.launch {
        repo.reorderPool(orderedIds)
        _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
    }

    fun activateBonusFromPool(taskId: Long) = viewModelScope.launch {
        repo.activateBonusTask(taskId)
        repo.getTask(taskId)?.let { syncTaskReminder(it) }
    }

    fun completeMandatory(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        repo.completeMandatory(taskId)
        _dayComplete.value = true

        if (settings.autoFillNextTask) {
            repo.suggestNext()?.let { repo.setTomorrowTask(it.id) }
            _queuedTomorrowTaskId.value = repo.queuedTomorrowTaskId()
            _showTomorrowPicker.value = false
        } else {
            _showTomorrowPicker.value = true
        }
    }

    fun completeBonus(taskId: Long) = viewModelScope.launch {
        NotificationScheduler.cancel(getApplication(), taskId)
        repo.completeBonus(taskId)
    }

    fun dismissTomorrowPicker() {
        _showTomorrowPicker.value = false
    }

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

    fun setSnoozeMinutes(minutes: Int) {
        settings.snoozeMinutes = minutes.coerceIn(1, 24 * 60)
    }

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
