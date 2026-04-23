package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityLabelsTest {
    @Test
    fun buildCalendarDayContentDescriptionIncludesStateAndCoursePresence() {
        val description = buildCalendarDayContentDescription(
            date = LocalDate.of(2026, 4, 23),
            selected = true,
            today = true,
            hasCourse = true,
        )

        assertTrue(description.contains("2026年4月23日"))
        assertTrue(description.contains("周四"))
        assertTrue(description.contains("今天"))
        assertTrue(description.contains("已选中"))
        assertTrue(description.contains("有课程"))
    }

    @Test
    fun buildWeekEntryContentDescriptionIncludesLocationAndNote() {
        val entry = TimetableEntry(
            title = "数据库",
            date = "2026-04-23",
            dayOfWeek = 4,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            location = "B-201",
            note = "带实验报告",
        )

        val description = buildWeekEntryContentDescription(entry)

        assertTrue(description.contains("数据库"))
        assertTrue(description.contains("08:00 到 09:00"))
        assertTrue(description.contains("地点 B-201"))
        assertTrue(description.contains("备注 带实验报告"))
    }

    @Test
    fun buildTimeSlotContentDescriptionUsesIndexAndRange() {
        assertEquals(
            "第 2 节，10:00 到 11:30，点按编辑节次时间",
            buildTimeSlotContentDescription(
                index = 1,
                slot = WeekTimeSlot(startMinutes = 10 * 60, endMinutes = 11 * 60 + 30),
            ),
        )
    }
}
