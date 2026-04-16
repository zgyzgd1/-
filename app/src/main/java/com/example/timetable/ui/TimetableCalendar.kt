package com.example.timetable.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timetable.data.*
import java.time.LocalDate
import java.time.YearMonth

/**
 * 星期选择标签页组件
 * 显示一周七天的选择按钮
 *
 * @param selectedDay 当前选中的星期
 * @param onDaySelected 选择星期时的回调
 */
@Composable
fun PerpetualCalendar(
    selectedDate: String,
    entries: List<TimetableEntry>,
    onDateChanged: (String) -> Unit,
) {
    val min = LocalDate.of(1970, 1, 1)
    val max = LocalDate.of(2100, 12, 31)
    val selected = parseEntryDate(selectedDate) ?: min
    var visibleMonthText by rememberSaveable(selected.withDayOfMonth(1).toString()) {
        mutableStateOf(selected.withDayOfMonth(1).toString())
    }

    val visibleMonth = parseEntryDate(visibleMonthText)?.let { YearMonth.from(it) } ?: YearMonth.from(selected)
    val monthStart = visibleMonth.atDay(1)
    val monthStartOffset = monthStart.dayOfWeek.value - 1
    val daysInMonth = visibleMonth.lengthOfMonth()
    val totalCells = monthStartOffset + daysInMonth
    val totalRows = (totalCells + 6) / 7
    val entriesByDate = remember(entries) { entries.groupingBy { it.date }.eachCount() }
    val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val prevMonth = visibleMonth.minusMonths(1)
                val nextMonth = visibleMonth.plusMonths(1)
                OutlinedButton(
                    onClick = { visibleMonthText = prevMonth.atDay(1).toString() },
                    enabled = prevMonth.atDay(1) >= min.withDayOfMonth(1),
                ) { Text("上月") }
                Text(
                    "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = { visibleMonthText = nextMonth.atDay(1).toString() },
                    enabled = nextMonth.atDay(1) <= max.withDayOfMonth(1),
                ) { Text("下月") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (column in 0 until 7) {
                        val dayNumber = row * 7 + column - monthStartOffset + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = visibleMonth.atDay(dayNumber)
                            val entryCount = entriesByDate[date.toString()] ?: 0
                            Surface(
                                onClick = { onDateChanged(date.toString()) },
                                enabled = date in min..max,
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = if (date == selected) 3.dp else 0.dp,
                                color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .border(
                                        width = if (date == selected) 2.dp else 1.dp,
                                        color = if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                                text = weekdayLabels[date.dayOfWeek.value - 1],
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (entryCount > 0) {
                                            Text(
                                                text = "$entryCount 门课",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}
