package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_blocks")
data class ClassBlock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val professor: String = "",
    val room: String = "",
    val dayOfWeek: Int = 0, // 0 = pool (unscheduled), 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri, 6 = Sat, 7 = Sun
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val durationMinutes: Int = 60,
    val colorHex: String = "#4F46E5" // Default Indigo
) {
    val isScheduled: Boolean get() = dayOfWeek > 0
}

fun ClassBlock.getOverlappingClasses(allClasses: List<ClassBlock>): List<ClassBlock> {
    if (!this.isScheduled) return emptyList()
    val thisStart = startHour * 60 + startMinute
    val thisEnd = thisStart + durationMinutes
    return allClasses.filter { other ->
        other.id != this.id &&
                other.isScheduled &&
                other.dayOfWeek == this.dayOfWeek &&
                run {
                    val otherStart = other.startHour * 60 + other.startMinute
                    val otherEnd = otherStart + other.durationMinutes
                    thisStart < otherEnd && otherStart < thisEnd
                }
    }
}
