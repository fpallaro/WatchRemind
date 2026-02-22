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
        val id: Long,        // parent EVENT_ID
        val title: String,
        val startTime: Long, // instance BEGIN time
        val location: String
    )

    fun getUpcomingEvents(context: Context): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)

        val uri = CalendarContract.Instances.buildQueryUri(now, weekFromNow)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION
        )

        val cursor = try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
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
        Log.d(TAG, "Found ${events.size} upcoming events (including recurring)")
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
            // Unique request code per instance: combines eventId and minute-precision start time
            val requestCode = ((event.id xor (event.startTime / 60000)) % Int.MAX_VALUE).toInt()
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Log.d(TAG, "Alarm set for: ${event.title} at triggerAt=$triggerAt")
        }
    }
}
