package com.example.timetable.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseReminderReceiverTest {
    @Test
    fun formatReminderLeadTimeSupportsMinutesAndHours() {
        assertEquals("20 分钟后上课", formatReminderLeadTime(20))
        assertEquals("1 小时后上课", formatReminderLeadTime(60))
        assertEquals("1 小时 30 分钟后上课", formatReminderLeadTime(90))
    }

    @Test
    fun buildReminderNotificationTitleUsesLeadTime() {
        assertEquals(
            "45 分钟后上课：Linear Algebra",
            buildReminderNotificationTitle("Linear Algebra", 45),
        )
    }

    @Test
    fun buildReminderNotificationTextIncludesLocationWhenPresent() {
        assertEquals(
            "2026-04-23 08:00 · A-203",
            buildReminderNotificationText(
                date = "2026-04-23",
                startMinutes = 8 * 60,
                location = "A-203",
            ),
        )
    }

    @Test
    fun buildReminderNotificationTextFallsBackToTimeOnlyWhenDateBlank() {
        assertEquals(
            "08:00",
            buildReminderNotificationText(
                date = "",
                startMinutes = 8 * 60,
                location = "",
            ),
        )
    }
}
