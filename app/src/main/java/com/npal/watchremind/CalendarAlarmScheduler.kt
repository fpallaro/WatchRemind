package com.npal.watchremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit

object CalendarAlarmScheduler {

    private val SAMSUNG_CALENDAR_URI = Uri.parse("content://com.samsung.android.calendar.watch/events")

    fun scheduleUpcoming(context: Context): Int {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Try standard Android CalendarContract first, fallback to Samsung Calendar
        val count = scheduleFromUri(context, alarmManager, CalendarContract.Events.CONTENT_URI, now, weekFromNow)
        if (count > 0) return count
        return scheduleFromUri(context, alarmManager, SAMSUNG_CALENDAR_URI, now, weekFromNow)
    }

    private fun scheduleFromUri(
        context: Context,
        alarmManager: AlarmManager,
        uri: Uri,
        now: Long,
        weekFromNow: Long
    ): Int {
        val cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf("_id", "title", "dtstart", "eventLocation"),
                "dtstart BETWEEN ? AND ? AND deleted = 0",
                arrayOf(now.toString(), weekFromNow.toString()),
                "dtstart ASC"
            )
        } catch (e: Exception) {
            null
        } ?: return 0

        var count = 0
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val title = it.getString(1) ?: "Appuntamento"
                val startTime = it.getLong(2)
                val location = it.getString(3) ?: ""

                val triggerAt = startTime - TimeUnit.MINUTES.toMillis(5)
                if (triggerAt <= now) continue

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("event_id", id)
                    putExtra("event_title", title)
                    putExtra("event_start", startTime)
                    putExtra("event_location", location)
                }

                val pending = PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                count++
            }
        }
        return count
    }
}
