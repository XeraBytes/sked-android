package com.example.util

import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import kotlin.math.ceil

data class AutoArrangeResult(
    val placedBlocks: List<Pair<ClassBlock, ClassBlock>>, // before to after
    val unplacedBlocks: List<ClassBlock>
)

/**
 * Places unscheduled Pool courses into open, conflict-free slots within the user's
 * configured grid hours and active days, balancing load across days.
 */
object ScheduleAutoArranger {

    fun planAutoArrange(
        allClasses: List<ClassBlock>,
        settings: ScheduleSettings
    ): AutoArrangeResult {
        val pool = allClasses
            .filter { !it.isScheduled }
            .sortedWith(compareByDescending<ClassBlock> { it.durationMinutes }.thenBy { it.id })

        if (pool.isEmpty()) return AutoArrangeResult(emptyList(), emptyList())

        val activeDays = if (settings.showWeekends) (1..7).toList() else (1..6).toList()
        val safeStartMin = settings.startHour.coerceIn(0, 22) * 60
        val safeEndMin = settings.endHour.coerceIn(settings.startHour.coerceIn(0, 22) + 1, 23) * 60

        val occupied = allClasses.filter { it.isScheduled }.toMutableList()
        val placed = mutableListOf<Pair<ClassBlock, ClassBlock>>()
        val unplaced = mutableListOf<ClassBlock>()

        for (course in pool) {
            val duration = course.durationMinutes.coerceIn(15, 480)
            // Order days by fewest currently scheduled minutes so the week stays balanced
            val dayOrder = activeDays.sortedWith(
                compareBy<Int> { day ->
                    occupied.filter { it.dayOfWeek == day }.sumOf { it.durationMinutes }
                }.thenBy { it }
            )

            var chosenSlot: Pair<Int, Int>? = null // day to startTotalMinutes
            for (day in dayOrder) {
                val dayIntervals = occupied
                    .filter { it.dayOfWeek == day }
                    .map { cls ->
                        val s = cls.startHour * 60 + cls.startMinute
                        val e = s + cls.durationMinutes.coerceAtLeast(15)
                        s to e
                    }
                    .sortedBy { it.first }

                val latestStart = safeEndMin - duration
                if (latestStart < safeStartMin) continue

                var candidate = safeStartMin
                while (candidate <= latestStart) {
                    val candEnd = candidate + duration
                    val conflict = dayIntervals.firstOrNull { (s, e) ->
                        candidate < e && s < candEnd
                    }
                    if (conflict == null) {
                        chosenSlot = day to candidate
                        break
                    }
                    // Jump past the conflicting block aligned to 30-minute boundaries
                    val nextBlockEnd = conflict.second
                    val alignedNext = ceil(nextBlockEnd / 30.0).toInt() * 30
                    candidate = maxOf(candidate + 30, alignedNext)
                }
                if (chosenSlot != null) break
            }

            if (chosenSlot != null) {
                val (day, startTotal) = chosenSlot
                val updated = course.copy(
                    dayOfWeek = day,
                    startHour = startTotal / 60,
                    startMinute = startTotal % 60
                )
                occupied.add(updated)
                placed.add(course to updated)
            } else {
                unplaced.add(course)
            }
        }

        return AutoArrangeResult(placedBlocks = placed, unplacedBlocks = unplaced)
    }
}
