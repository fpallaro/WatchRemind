package com.npal.watchremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit

object CalendarAlarmScheduler {

    fun scheduleUpcoming(context: Context): Int {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION
            ),
            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(now.toString(), weekFromNow.toString()),
            CalendarContract.Events.DTSTART + " ASC"
        ) ?: return 0

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
