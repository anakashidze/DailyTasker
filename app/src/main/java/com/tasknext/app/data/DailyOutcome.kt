package com.tasknext.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DailyOutcomeStatus {
    COMPLETED,
    REST,
    MISSED
}

@Entity(tableName = "daily_outcome")
data class DailyOutcome(
    @PrimaryKey val epochDay: Long,
    val status: DailyOutcomeStatus,
    val sourceTaskId: Long? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
