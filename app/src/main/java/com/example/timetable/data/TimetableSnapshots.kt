package com.example.timetable.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal data class NextCourseSnapshot(
    val entry: TimetableEntry,
    val occurrenceDate: LocalDate,
    val statusText: String,
)

internal fun findNextCourseSnapshot(
    entries: List<TimetableEntry>,
    nowDate: LocalDate = LocalDate.now(),
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
): NextCourseSnapshot? {
    val todayEntries = entriesForDate(entries, nowDate)

    val ongoingEntry = todayEntries.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }
    if (ongoingEntry != null) {
        val remainingMinutes = (ongoingEntry.endMinutes - nowMinutes).coerceAtLeast(0)
        val status = if (remainingMinutes > 0) {
            "正在进行，距离下课 $remainingMinutes 分钟"
        } else {
            "正在进行"
        }
        return NextCourseSnapshot(
            entry = ongoingEntry,
            occurrenceDate = nowDate,
            statusText = status,
        )
    }

    val nextTodayEntry = todayEntries.firstOrNull { it.startMinutes >= nowMinutes }
    if (nextTodayEntry != null) {
        val waitMinutes = (nextTodayEntry.startMinutes - nowMinutes).coerceAtLeast(0)
        val status = if (waitMinutes == 0) {
            "即将开始"
        } else {
            "$waitMinutes 分钟后开始"
        }
        return NextCourseSnapshot(
            entry = nextTodayEntry,
            occurrenceDate = nowDate,
            statusText = status,
        )
    }

    val futureOccurrence = entries
        .asSequence()
        .mapNotNull { entry ->
            val nextDate = nextOccurrenceDate(entry, nowDate.plusDays(1)) ?: return@mapNotNull null
            entry to nextDate
        }
        .sortedWith(
            compareBy<Pair<TimetableEntry, LocalDate>>(
                { it.second },
                { it.first.startMinutes },
                { it.first.endMinutes },
                { it.first.title },
            ),
        )
        .firstOrNull()
        ?: return null

    val resolvedEntry = futureOccurrence.first
    val resolvedDate = futureOccurrence.second
    val dayOffset = ChronoUnit.DAYS.between(nowDate, resolvedDate).toInt().coerceAtLeast(0)
    val dayLabel = when (dayOffset) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> "$dayOffset 天后"
    }
    return NextCourseSnapshot(
        entry = resolvedEntry,
        occurrenceDate = resolvedDate,
        statusText = "$dayLabel ${formatMinutes(resolvedEntry.startMinutes)} 开始",
    )
}

internal fun entriesForDate(
    entries: List<TimetableEntry>,
    date: LocalDate,
): List<TimetableEntry> {
    return entries
        .asSequence()
        .filter { occursOnDate(it, date) }
        .sortedBy { it.startMinutes }
        .toList()
}
