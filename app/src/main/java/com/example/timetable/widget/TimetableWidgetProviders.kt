package com.example.timetable.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

abstract class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        TimetableWidgetUpdater.refreshAllFromStorage(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        TimetableWidgetUpdater.refreshAllFromStorage(context)
    }
}

class TodayScheduleWidgetProvider : TimetableWidgetProvider()

class NextCourseWidgetProvider : TimetableWidgetProvider()
