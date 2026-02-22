package com.npal.watchremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import java.util.concurrent.TimeUnit

object CalendarAlarmScheduler {

    private const val TAG = "WatchRemind"
    private val SAMSUNG_CALENDAR_URI = Uri.parse("content://com.samsung.android.calendar.watch/events")

    fun scheduleUpcoming(context: Context): Int {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
        Log.d(TAG, "Querying URI: $uri")
        val cursor = try {
            context.contentResolver.query(
                uri,
                null, // null = all columns, so we can log them
                null, // no filter yet, get everything
                null,
                "dtstart ASC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception querying $uri: ${e.message}")
            null
        }

        if (cursor == null) {
            Log.e(TAG, "Cursor is null for $uri")
            return 0
        }

        Log.d(TAG, "Cursor count for $uri: ${cursor.count}")
        if (cursor.count > 0) {
            Log.d(TAG, "Columns: ${cursor.columnNames.joinToString()}")
        }

        var count = 0
        cursor.use {
            while (it.moveToNext()) {
                val colNames = it.columnNames
                // Try to find columns by name dynamically
                val idIdx = colNames.indexOfFirst { c -> c == "_id" }
                val titleIdx = colNames.indexOfFirst { c -> c.lowercase() in listOf("title", "subject", "event_title") }
                val startIdx = colNames.indexOfFirst { c -> c.lowercase() in listOf("dtstart", "start_time", "starttime") }
                val locIdx = colNames.indexOfFirst { c -> c.lowercase() in listOf("eventlocation", "event_location", "location") }

                if (idIdx < 0 || titleIdx < 0 || startIdx < 0) {
                    Log.w(TAG, "Required columns not found. Available: ${colNames.joinToString()}")
                    break
                }

                val id = it.getLong(idIdx)
                val title = it.getString(titleIdx) ?: "Appuntamento"
                val startTime = it.getLong(startIdx)
                val location = if (locIdx >= 0) it.getString(locIdx) ?: "" else ""

                Log.d(TAG, "Event: id=$id title=$title start=$startTime")

                // Filter by time range
                if (startTime < now || startTime > weekFromNow) continue

                val triggerAt = startTime - TimeUnit.MINUTES.toMillis(5)
                if (triggerAt <= now) continue

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("event_id", id)
                    putExtra("event_title", title)
                    putExtra("event_start", startTime)
                    putExtra("event_location", location)
                }
                val pending = PendingIntent.getBroadcast(
                    context, id.toInt(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                count++
            }
        }
        Log.d(TAG, "Scheduled $count alarms from $uri")
        return count
    }
}
