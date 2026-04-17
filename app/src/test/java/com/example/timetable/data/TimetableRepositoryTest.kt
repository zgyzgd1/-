package com.example.timetable.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableRepositoryTest {
    @Test
    fun shouldSeedSampleEntriesOnlyOnFirstEmptyLaunch() {
        assertTrue(TimetableRepository.shouldSeedSampleEntries(hasEntries = false, hasSeededSampleEntries = false))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = true, hasSeededSampleEntries = false))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = false, hasSeededSampleEntries = true))
        assertFalse(TimetableRepository.shouldSeedSampleEntries(hasEntries = true, hasSeededSampleEntries = true))
    }
}