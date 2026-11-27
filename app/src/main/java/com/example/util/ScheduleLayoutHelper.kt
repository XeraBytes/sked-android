package com.example.util

import com.example.data.model.ClassBlock
import java.util.Locale

data class LanePlacement(
    val laneIndex: Int,
    val totalLanes: Int
)

/** Shared scheduling layout, overlap partitioning, and time-formatting helpers. */
object ScheduleLayoutHelper {

    private val hexColorRegex = Regex("^#[0-9A-Fa-f]{6}$")

    fun dayShortName(day: Int): String = when (day) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "Pool"
    }

    fun dayFullName(day: Int): String = when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unscheduled"
    }

    fun formatHourShort(hour24: Int): String {
        val normalized = ((hour24 % 24) + 24) % 24
        val display = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        val period = if (normalized >= 12) "PM" else "AM"
        return "$display $period"
    }

    fun formatTime12h(hour24: Int, minute: Int): String {
        val normalizedHour = ((hour24 % 24) + 24) % 24
        val normalizedMin = minute.coerceIn(0, 59)
        val displayHour = when {
            normalizedHour == 0 -> 12
            normalizedHour > 12 -> normalizedHour - 12
            else -> normalizedHour
        }
        val period = if (normalizedHour >= 12) "PM" else "AM"
        return String.format(Locale.US, "%d:%02d %s", displayHour, normalizedMin, period)
    }

    fun formatTimeRange(startHour: Int, startMinute: Int, durationMinutes: Int): String {
        val totalEnd = startHour * 60 + startMinute + durationMinutes.coerceAtLeast(1)
        val endHour = (totalEnd / 60) % 24
        val endMinute = totalEnd % 60
        return "${formatTime12h(startHour, startMinute)} – ${formatTime12h(endHour, endMinute)}"
    }

    fun formatDuration(durationMinutes: Int): String {
        val mins = durationMinutes.coerceAtLeast(0)
        val hours = mins / 60
        val rem = mins % 60
        return when {
            hours > 0 && rem > 0 -> "${hours}h ${rem}m"
            hours > 0 -> "${hours}h"
            else -> "${rem}m"
        }
    }

    fun normalizeColorHex(raw: String?, fallback: String = "#1E4E8C"): String {
        val trimmed = raw?.trim().orEmpty()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (hexColorRegex.matches(withHash)) withHash.uppercase(Locale.US) else fallback
    }

    /**
     * Groups overlapping scheduled blocks on a single day into connected conflict clusters
     * and assigns non-overlapping horizontal lane indices for side-by-side layout.
     */
    fun computeDayLanes(classesForDay: List<ClassBlock>): Map<Int, LanePlacement> {
        if (classesForDay.isEmpty()) return emptyMap()

        val sorted = classesForDay.sortedWith(
            compareBy<ClassBlock> { it.startHour * 60 + it.startMinute }
                .thenByDescending { it.durationMinutes }
                .thenBy { it.id }
        )

        val components = mutableListOf<MutableList<ClassBlock>>()
        for (cls in sorted) {
            val start = cls.startHour * 60 + cls.startMinute
            val end = start + cls.durationMinutes.coerceAtLeast(1)
            var merged: MutableList<ClassBlock>? = null
            for (comp in components) {
                val touches = comp.any { other ->
                    val oStart = other.startHour * 60 + other.startMinute
                    val oEnd = oStart + other.durationMinutes.coerceAtLeast(1)
                    start < oEnd && oStart < end
                }
                if (touches) {
                    if (merged == null) {
                        comp.add(cls)
                        merged = comp
                    } else {
                        merged.addAll(comp)
                        comp.clear()
                    }
                }
            }
            if (merged == null) {
                components.add(mutableListOf(cls))
            }
            components.removeAll { it.isEmpty() }
        }

        val result = HashMap<Int, LanePlacement>(sorted.size)
        for (component in components) {
            val orderedComp = component.sortedWith(
                compareBy<ClassBlock> { it.startHour * 60 + it.startMinute }
                    .thenByDescending { it.durationMinutes }
                    .thenBy { it.id }
            )
            val laneEndMinutes = mutableListOf<Int>()
            val assignedLane = HashMap<Int, Int>(orderedComp.size)

            for (cls in orderedComp) {
                val start = cls.startHour * 60 + cls.startMinute
                val end = start + cls.durationMinutes.coerceAtLeast(1)
                var chosen = -1
                for (i in laneEndMinutes.indices) {
                    if (laneEndMinutes[i] <= start) {
                        laneEndMinutes[i] = end
                        chosen = i
                        break
                    }
                }
                if (chosen == -1) {
                    laneEndMinutes.add(end)
                    chosen = laneEndMinutes.lastIndex
                }
                assignedLane[cls.id] = chosen
            }

            val totalLanes = laneEndMinutes.size.coerceAtLeast(1)
            for (cls in orderedComp) {
                result[cls.id] = LanePlacement(
                    laneIndex = assignedLane[cls.id] ?: 0,
                    totalLanes = totalLanes
                )
            }
        }
        return result
    }
}
