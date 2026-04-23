package com.example.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timetable.widget.TimetableWidgetUpdater

class CourseReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shouldResync = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> true
            else -> false
        }

        if (!shouldResync) return

        val pendingResult = goAsync()
        TimetableWidgetUpdater.refreshAllFromStorage(context)
        CourseReminderScheduler.resyncFromStorage(context) {
            pendingResult.finish()
        }
    }
}
