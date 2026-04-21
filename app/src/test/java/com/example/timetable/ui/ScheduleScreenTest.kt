package com.example.timetable.ui

import com.example.timetable.data.WeekTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleScreenTest {

    @Test
    fun nextWeekTimeSlotReturnsNullWhenNoFullSlotFits() {
        val previous = WeekTimeSlot(
            startMinutes = 23 * 60 - 15,
            endMinutes = 23 * 60 + 35,
        )

        val next = nextWeekTimeSlot(previous)

        assertNull(next)
    }

    @Test
    fun resizeWeekTimeSlotsStopsExpandingWhenTimeRunsOut() {
        val slots = listOf(
            WeekTimeSlot(
                startMinutes = 22 * 60 + 40,
                endMinutes = 23 * 60 + 20,
            ),
        )

        val resized = resizeWeekTimeSlots(slots, targetCount = 3)

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 22 * 60 + 40, endMinutes = 23 * 60 + 20),
            ),
            resized,
        )
    }
}
