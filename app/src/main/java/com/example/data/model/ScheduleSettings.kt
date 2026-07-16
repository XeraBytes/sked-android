package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_settings")
data class ScheduleSettings(
    @PrimaryKey val id: Int = 1,
    val schoolName: String = "My School Schedule",
    val termName: String = "First Semester",
    val startHour: Int = 8,  // e.g. 8 AM
    val endHour: Int = 18,   // e.g. 6 PM
    val showWeekends: Boolean = false,
    val referenceScreenshotUri: String? = null
)
