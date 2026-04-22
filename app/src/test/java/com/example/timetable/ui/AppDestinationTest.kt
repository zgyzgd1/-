package com.example.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {

    @Test
    fun fromSavedStateValueMapsLegacyBooleanToDestination() {
        assertEquals(AppDestination.DAY, AppDestination.fromSavedStateValue(false))
        assertEquals(AppDestination.WEEK, AppDestination.fromSavedStateValue(true))
    }

    @Test
    fun fromSavedStateValueFallsBackToDayForInvalidInput() {
        assertEquals(AppDestination.DAY, AppDestination.fromSavedStateValue(null))
        assertEquals(AppDestination.DAY, AppDestination.fromSavedStateValue(42))
        assertEquals(AppDestination.DAY, AppDestination.fromSavedStateValue("UNKNOWN"))
    }
}
