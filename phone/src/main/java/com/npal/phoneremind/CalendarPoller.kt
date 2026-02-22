package com.npal.phoneremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import java.util.concurrent.TimeUnit

object CalendarPoller {

    private const val TAG = "PhoneRemind"

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startTime: Long,
        val location: String
    )

    fun getUpcomingEvents(context: Context): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? " +
            "AND ${CalendarContract.Events.DTSTART} <= ? " +
            "AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(now.toString(), weekFromNow.toString())

        val cursor = try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "No calendar permission: ${e.message}")
            return emptyList()
        } ?: return emptyList()

        val events = mutableListOf<CalendarEvent>()
        cursor.use {
            while (it.moveToNext()) {
                events.add(CalendarEvent(
                    id = it.getLong(0),
                    title = it.getString(1) ?: "Appuntamento",
                    startTime = it.getLong(2),
                    location = it.getString(3) ?: ""
                ))
            }
        }
        Log.d(TAG, "Found ${events.size} upcoming events")
        return events
    }

    fun scheduleAlarms(context: Context, events: List<CalendarEvent>) {
        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (event in events) {
            val triggerAt = event.startTime - TimeUnit.MINUTES.toMillis(5)
            if (triggerAt <= now) continue

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_title", event.title)
                putExtra("event_start", event.startTime)
                putExtra("event_location", event.location)
            }
            val pending = PendingIntent.getBroadcast(
                context, (event.id % Int.MAX_VALUE).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Log.d(TAG, "Alarm set for: ${event.title} at triggerAt=$triggerAt")
        }
    }
}
