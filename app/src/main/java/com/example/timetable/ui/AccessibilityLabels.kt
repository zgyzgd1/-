package com.example.timetable.ui

import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekTimeSlot
import com.example.timetable.data.dayLabel
import com.example.timetable.data.formatMinutes
import java.time.LocalDate

internal fun buildCalendarDayContentDescription(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    hasCourse: Boolean,
): String {
    val parts = mutableListOf("${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}")
    if (today) parts += "今天"
    if (selected) parts += "已选中"
    if (hasCourse) parts += "有课程"
    return parts.joinToString("，")
}

internal fun buildWeekCalendarDayContentDescription(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
): String {
    val parts = mutableListOf("${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}")
    if (today) parts += "今天"
    if (selected) parts += "当前选中"
    return parts.joinToString("，")
}

internal fun buildWeekEntryContentDescription(entry: TimetableEntry): String {
    val parts = mutableListOf(
        entry.title.ifBlank { "未命名课程" },
        "${formatMinutes(entry.startMinutes)} 到 ${formatMinutes(entry.endMinutes)}",
    )
    if (entry.location.isNotBlank()) parts += "地点 ${entry.location}"
    if (entry.note.isNotBlank()) parts += "备注 ${entry.note}"
    parts += "点按编辑课程"
    return parts.joinToString("，")
}

internal fun buildTimeSlotContentDescription(index: Int, slot: WeekTimeSlot): String {
    return "第 ${index + 1} 节，${formatMinutes(slot.startMinutes)} 到 ${formatMinutes(slot.endMinutes)}，点按编辑节次时间"
}

internal fun buildHeroActionContentDescription(label: String): String {
    return "$label，按钮"
}
