package com.example.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.data.NextCourseSnapshot
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.dayLabel
import com.example.timetable.data.entriesForDate
import com.example.timetable.data.findNextCourseSnapshot
import com.example.timetable.data.formatMinutes
import com.example.timetable.ui.AppDestination
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TimetableWidgetUpdater {
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAllFromStorage(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        if (!hasAnyActiveWidgets(appContext, appWidgetManager)) return
        refreshScope.launch {
            val entries = TimetableRepository.getEntriesNow(appContext)
            refreshAll(appContext, entries, appWidgetManager = appWidgetManager)
        }
    }

    fun refreshAll(
        context: Context,
        entries: List<TimetableEntry>,
        today: LocalDate = LocalDate.now(),
        nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext),
    ) {
        val appContext = context.applicationContext
        if (!hasAnyActiveWidgets(appContext, appWidgetManager)) return
        updateNextCourseWidgets(appContext, appWidgetManager, entries, today, nowMinutes)
        updateTodayScheduleWidgets(appContext, appWidgetManager, entries, today)
    }

    internal fun hasAnyActiveWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
    ): Boolean {
        val todayWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
        if (todayWidgetIds.isNotEmpty()) return true
        val nextWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NextCourseWidgetProvider::class.java))
        return nextWidgetIds.isNotEmpty()
    }

    private fun updateNextCourseWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entries: List<TimetableEntry>,
        today: LocalDate,
        nowMinutes: Int,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NextCourseWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        val state = buildNextCourseWidgetState(entries, today, nowMinutes)
        appWidgetIds.forEach { appWidgetId ->
            val views = buildNextCourseRemoteViews(context, appWidgetId, state)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateTodayScheduleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entries: List<TimetableEntry>,
        today: LocalDate,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        val state = buildTodayScheduleWidgetState(entries, today)
        appWidgetIds.forEach { appWidgetId ->
            val views = buildTodayScheduleRemoteViews(context, appWidgetId, state)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

internal data class TodayScheduleWidgetState(
    val dateLabel: String,
    val summaryText: String,
    val entryLines: List<String>,
    val overflowText: String,
    val targetDate: LocalDate,
)

internal data class NextCourseWidgetState(
    val statusText: String,
    val title: String,
    val timeLabel: String,
    val locationText: String,
    val targetDate: LocalDate,
)

internal fun buildTodayScheduleWidgetState(
    entries: List<TimetableEntry>,
    today: LocalDate = LocalDate.now(),
): TodayScheduleWidgetState {
    val todayEntries = entriesForDate(entries, today)
    val entryLines = todayEntries
        .take(3)
        .map { entry ->
            buildString {
                append(formatMinutes(entry.startMinutes))
                append(" - ")
                append(formatMinutes(entry.endMinutes))
                append("  ")
                append(entry.title.ifBlank { "未命名课程" })
                if (entry.location.isNotBlank()) {
                    append(" / ")
                    append(entry.location)
                }
            }
        }

    return TodayScheduleWidgetState(
        dateLabel = formatWidgetDateLabel(today),
        summaryText = if (todayEntries.isEmpty()) {
            "今天暂无课程"
        } else {
            "今天共 ${todayEntries.size} 节"
        },
        entryLines = if (entryLines.isEmpty()) listOf("点按打开课表并添加课程") else entryLines,
        overflowText = if (todayEntries.size > entryLines.size) {
            "还有 ${todayEntries.size - entryLines.size} 节未显示"
        } else {
            ""
        },
        targetDate = today,
    )
}

internal fun buildNextCourseWidgetState(
    entries: List<TimetableEntry>,
    today: LocalDate = LocalDate.now(),
    nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
): NextCourseWidgetState {
    val snapshot = findNextCourseSnapshot(entries, today, nowMinutes)
    if (snapshot == null) {
        val title = if (entries.isEmpty()) "课表还是空的" else "当前没有待上课程"
        return NextCourseWidgetState(
            statusText = "下一节课",
            title = title,
            timeLabel = formatWidgetDateLabel(today),
            locationText = "点按打开完整课表",
            targetDate = today,
        )
    }

    return NextCourseWidgetState(
        statusText = snapshot.statusText,
        title = snapshot.entry.title.ifBlank { "未命名课程" },
        timeLabel = buildNextCourseTimeLabel(snapshot, today),
        locationText = snapshot.entry.location.ifBlank { "点按查看对应日期" },
        targetDate = snapshot.occurrenceDate,
    )
}

private fun buildTodayScheduleRemoteViews(
    context: Context,
    appWidgetId: Int,
    state: TodayScheduleWidgetState,
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.widget_today_schedule).apply {
        setTextViewText(R.id.widget_today_date, state.dateLabel)
        setTextViewText(R.id.widget_today_summary, state.summaryText)
        bindTextLine(R.id.widget_today_line_one, state.entryLines.getOrNull(0))
        bindTextLine(R.id.widget_today_line_two, state.entryLines.getOrNull(1))
        bindTextLine(R.id.widget_today_line_three, state.entryLines.getOrNull(2))
        bindTextLine(R.id.widget_today_overflow, state.overflowText.ifBlank { null })
        setOnClickPendingIntent(
            R.id.widget_today_root,
            createOpenAppPendingIntent(
                context = context,
                widgetType = "today",
                appWidgetId = appWidgetId,
                selectedDate = state.targetDate.toString(),
            ),
        )
    }
}

private fun buildNextCourseRemoteViews(
    context: Context,
    appWidgetId: Int,
    state: NextCourseWidgetState,
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.widget_next_course).apply {
        setTextViewText(R.id.widget_next_status, state.statusText)
        setTextViewText(R.id.widget_next_title, state.title)
        setTextViewText(R.id.widget_next_time, state.timeLabel)
        setTextViewText(R.id.widget_next_location, state.locationText)
        setOnClickPendingIntent(
            R.id.widget_next_root,
            createOpenAppPendingIntent(
                context = context,
                widgetType = "next",
                appWidgetId = appWidgetId,
                selectedDate = state.targetDate.toString(),
            ),
        )
    }
}

private fun RemoteViews.bindTextLine(viewId: Int, text: String?) {
    if (text.isNullOrBlank()) {
        setViewVisibility(viewId, View.GONE)
    } else {
        setViewVisibility(viewId, View.VISIBLE)
        setTextViewText(viewId, text)
    }
}

private fun createOpenAppPendingIntent(
    context: Context,
    widgetType: String,
    appWidgetId: Int,
    selectedDate: String,
): PendingIntent {
    val intent = MainActivity.createLaunchIntent(
        context = context,
        selectedDate = selectedDate,
        destination = AppDestination.DAY,
    ).apply {
        data = Uri.parse("timetable://widget/$widgetType/$appWidgetId?date=$selectedDate")
    }
    return PendingIntent.getActivity(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

private fun buildNextCourseTimeLabel(
    snapshot: NextCourseSnapshot,
    today: LocalDate,
): String {
    val dateLabel = when (snapshot.occurrenceDate) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> formatWidgetDateLabel(snapshot.occurrenceDate)
    }
    return "$dateLabel ${formatMinutes(snapshot.entry.startMinutes)} - ${formatMinutes(snapshot.entry.endMinutes)}"
}

internal fun formatWidgetDateLabel(date: LocalDate): String {
    return "${date.monthValue}月${date.dayOfMonth}日 ${dayLabel(date.dayOfWeek.value)}"
}
