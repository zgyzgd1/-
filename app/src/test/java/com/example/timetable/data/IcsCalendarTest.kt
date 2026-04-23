package com.example.timetable.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarTest {
    @Test(timeout = 1000)
    fun parseWeeklyRRuleRespectsCount() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-1
            SUMMARY:离散数学
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            RRULE:FREQ=WEEKLY;COUNT=1;BYDAY=MO,WE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals("2026-04-13", parsed.first().date)
    }

    @Test
    fun writeAddsDtStampField() {
        val entry = TimetableEntry(
            id = "entry-1",
            title = "线性代数",
            date = "2026-04-15",
            dayOfWeek = 3,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("DTSTAMP:"))
    }

    @Test
    fun writeSupportsEntriesEndingAtMidnight() {
        val entry = TimetableEntry(
            id = "entry-midnight",
            title = "晚课",
            date = "2026-04-15",
            dayOfWeek = 3,
            startMinutes = 23 * 60,
            endMinutes = 24 * 60,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(
            text.contains("DTEND;TZID=${ZoneId.systemDefault().id}:20260416T000000"),
        )
    }

    @Test
    fun writeWeeklyEntryIncludesRRule() {
        val entry = TimetableEntry(
            id = "weekly-entry",
            title = "Operating Systems",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ALL.name,
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
    }

    @Test
    fun writeOddWeekEntryIncludesIntervalAndExDate() {
        val entry = TimetableEntry(
            id = "odd-entry",
            title = "Physics",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.ODD.name,
            skipWeekList = "3",
        )

        val text = IcsCalendar.write(listOf(entry))

        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"))
        assertTrue(
            text.contains("EXDATE;TZID=${ZoneId.systemDefault().id}:20260316T080000"),
        )
    }

    @Test
    fun writeCustomWeekEntryExportsEveryOccurrence() {
        val entry = TimetableEntry(
            id = "custom-entry",
            title = "Compiler",
            date = "2026-03-02",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 9 * 60,
            recurrenceType = RecurrenceType.WEEKLY.name,
            semesterStartDate = "2026-03-02",
            weekRule = WeekRule.CUSTOM.name,
            customWeekList = "1,3,5",
            skipWeekList = "3",
        )

        val text = IcsCalendar.write(listOf(entry))
        val parsed = IcsCalendar.parse(text)

        assertFalse(text.contains("RRULE:"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(text).count())
        assertEquals(
            listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 30)).map(LocalDate::toString),
            parsed.map { it.date },
        )
    }

    @Test
    fun parseWeeklyRRuleSortsByDayBeforeApplyingUntil() {
        val content = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//CN
            BEGIN:VEVENT
            UID:test-ordered
            SUMMARY:Algorithms
            DTSTART;TZID=Asia/Shanghai:20260413T080000
            DTEND;TZID=Asia/Shanghai:20260413T090000
            RRULE:FREQ=WEEKLY;UNTIL=20260414T235959;BYDAY=WE,MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = IcsCalendar.parse(content)

        assertEquals(1, parsed.size)
        assertEquals("2026-04-13", parsed.single().date)
    }
}
