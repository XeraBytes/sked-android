package com.example

import com.example.data.model.ClassBlock
import com.example.data.model.getOverlappingClasses
import com.example.util.ScheduleLayoutHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLayoutHelperTest {

    @Test
    fun `computeDayLanes splits overlapping classes into parallel lanes`() {
        val a = ClassBlock(id = 1, name = "A", dayOfWeek = 1, startHour = 9, startMinute = 0, durationMinutes = 90)
        val b = ClassBlock(id = 2, name = "B", dayOfWeek = 1, startHour = 9, startMinute = 30, durationMinutes = 60)
        val c = ClassBlock(id = 3, name = "C", dayOfWeek = 1, startHour = 10, startMinute = 15, durationMinutes = 60)
        val d = ClassBlock(id = 4, name = "D", dayOfWeek = 1, startHour = 14, startMinute = 0, durationMinutes = 60)

        val lanes = ScheduleLayoutHelper.computeDayLanes(listOf(a, b, c, d))

        assertEquals(0, lanes[1]?.laneIndex)
        assertEquals(3, lanes[1]?.totalLanes)
        assertEquals(1, lanes[2]?.laneIndex)
        assertEquals(3, lanes[2]?.totalLanes)
        // A=9:00..10:30, B=9:30..10:30, C=10:15..11:15 all overlap at 10:15..10:30 -> 3 parallel lanes
        assertEquals(2, lanes[3]?.laneIndex)
        assertEquals(3, lanes[3]?.totalLanes)
        // Class D is separate in the afternoon -> single lane
        assertEquals(0, lanes[4]?.laneIndex)
        assertEquals(1, lanes[4]?.totalLanes)
    }

    @Test
    fun `computeDayLanes merges transitive overlap cluster width accurately`() {
        // Test sequential chain: A (9:00-10:30), B (9:30-11:00), C (10:45-12:00)
        // A and C do not overlap, but both overlap B -> cluster width is 2, A & C share lane 0, B takes lane 1
        val a = ClassBlock(id = 1, name = "A", dayOfWeek = 2, startHour = 9, startMinute = 0, durationMinutes = 90)
        val b = ClassBlock(id = 2, name = "B", dayOfWeek = 2, startHour = 9, startMinute = 30, durationMinutes = 90)
        val c = ClassBlock(id = 3, name = "C", dayOfWeek = 2, startHour = 10, startMinute = 45, durationMinutes = 75)

        val lanes = ScheduleLayoutHelper.computeDayLanes(listOf(a, b, c))
        assertEquals(0, lanes[1]?.laneIndex)
        assertEquals(2, lanes[1]?.totalLanes)
        assertEquals(1, lanes[2]?.laneIndex)
        assertEquals(2, lanes[2]?.totalLanes)
        assertEquals(0, lanes[3]?.laneIndex)
        assertEquals(2, lanes[3]?.totalLanes)
    }

    @Test
    fun `getOverlappingClasses detects partial overlaps and ignores unscheduled`() {
        val m1 = ClassBlock(id = 1, name = "Math", dayOfWeek = 1, startHour = 8, startMinute = 0, durationMinutes = 60)
        val m2 = ClassBlock(id = 2, name = "Lab", dayOfWeek = 1, startHour = 8, startMinute = 45, durationMinutes = 60)
        val m3 = ClassBlock(id = 3, name = "English", dayOfWeek = 1, startHour = 9, startMinute = 0, durationMinutes = 60)
        val pool = ClassBlock(id = 4, name = "Pool", dayOfWeek = 0, startHour = 8, startMinute = 0, durationMinutes = 60)

        val all = listOf(m1, m2, m3, pool)
        assertEquals(listOf(m2), m1.getOverlappingClasses(all))
        assertEquals(listOf(m1, m3), m2.getOverlappingClasses(all))
        assertTrue(pool.getOverlappingClasses(all).isEmpty())
    }

    @Test
    fun `formatting helpers output consistent 12h labels and colors`() {
        assertEquals("8 AM", ScheduleLayoutHelper.formatHourShort(8))
        assertEquals("12 PM", ScheduleLayoutHelper.formatHourShort(12))
        assertEquals("1 PM", ScheduleLayoutHelper.formatHourShort(13))
        assertEquals("12 AM", ScheduleLayoutHelper.formatHourShort(0))
        assertEquals("9:05 AM – 10:35 AM", ScheduleLayoutHelper.formatTimeRange(9, 5, 90))
        assertEquals("1h 30m", ScheduleLayoutHelper.formatDuration(90))
        assertEquals("#2E5A4F", ScheduleLayoutHelper.normalizeColorHex("not-a-color"))
        assertEquals("#B85D3B", ScheduleLayoutHelper.normalizeColorHex("b85d3b"))
    }
}
