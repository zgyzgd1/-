package com.example.timetable

import com.example.timetable.ui.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityTest {
    @Test
    fun resolveLaunchTargetTrimsDateAndMapsDestination() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = " 2026-04-23 ",
            destination = "WEEK",
        )

        assertEquals("2026-04-23", target.selectedDate)
        assertEquals(AppDestination.WEEK, target.destination)
    }

    @Test
    fun resolveLaunchTargetFallsBackWhenValuesAreBlankOrInvalid() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = "   ",
            destination = "UNKNOWN",
        )

        assertNull(target.selectedDate)
        assertEquals(AppDestination.DAY, target.destination)
    }

    @Test
    fun resolveLaunchTargetDropsInvalidDateValues() {
        val target = MainActivity.resolveLaunchTarget(
            selectedDate = "2026-13-99",
            destination = "DAY",
        )

        assertNull(target.selectedDate)
        assertEquals(AppDestination.DAY, target.destination)
    }
}
