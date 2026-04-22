package com.example.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceStoreTest {

    @Test
    fun resolvePersistedBackgroundModeFallsBackToBundledWhenCustomFileIsMissing() {
        val resolved = resolvePersistedBackgroundMode(
            storedModeValue = AppBackgroundMode.CUSTOM_IMAGE.storageValue,
            hasCustomBackground = false,
        )

        assertEquals(AppBackgroundMode.BUNDLED_IMAGE, resolved.mode)
        assertTrue(resolved.shouldPersist)
    }

    @Test
    fun resolvePersistedBackgroundModeKeepsBundledModeWhenAlreadyValid() {
        val resolved = resolvePersistedBackgroundMode(
            storedModeValue = AppBackgroundMode.BUNDLED_IMAGE.storageValue,
            hasCustomBackground = false,
        )

        assertEquals(AppBackgroundMode.BUNDLED_IMAGE, resolved.mode)
        assertFalse(resolved.shouldPersist)
    }

    @Test
    fun resolvePersistedBackgroundImageTransformPersistsMissingOrOutOfRangeValues() {
        val missingValues = resolvePersistedBackgroundImageTransform(
            storedScale = null,
            storedHorizontalBias = null,
            storedVerticalBias = null,
        )
        val outOfRangeValues = resolvePersistedBackgroundImageTransform(
            storedScale = 9f,
            storedHorizontalBias = 3f,
            storedVerticalBias = -5f,
        )

        assertEquals(BackgroundImageTransform(), missingValues.imageTransform)
        assertTrue(missingValues.shouldPersist)

        assertEquals(
            BackgroundImageTransform(
                scale = BackgroundImageTransform.MAX_SCALE,
                horizontalBias = 1f,
                verticalBias = -1f,
            ),
            outOfRangeValues.imageTransform,
        )
        assertTrue(outOfRangeValues.shouldPersist)
    }

    @Test
    fun sanitizeWeekTimeSlotsNormalizesOverlapAndFallsBackWhenEmpty() {
        val fallback = listOf(
            WeekTimeSlot(startMinutes = 8 * 60, endMinutes = 8 * 60 + 40),
            WeekTimeSlot(startMinutes = 8 * 60 + 45, endMinutes = 9 * 60 + 25),
        )

        val normalized = sanitizeWeekTimeSlots(
            slots = listOf(
                WeekTimeSlot(startMinutes = -10, endMinutes = 30),
                WeekTimeSlot(startMinutes = 20, endMinutes = 70),
            ),
            fallbackSlots = fallback,
        )
        val fallbackResult = sanitizeWeekTimeSlots(
            slots = emptyList(),
            fallbackSlots = fallback,
        )

        assertEquals(
            listOf(
                WeekTimeSlot(startMinutes = 0, endMinutes = 30),
                WeekTimeSlot(startMinutes = 30, endMinutes = 70),
            ),
            normalized,
        )
        assertEquals(fallback, fallbackResult)
    }
}
