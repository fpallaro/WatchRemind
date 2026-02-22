package com.npal.phoneremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("event_id", -1)
        val title = intent.getStringExtra("event_title") ?: return
        val startTime = intent.getLongExtra("event_start", 0)
        val location = intent.getStringExtra("event_location") ?: ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(startTime))

        if (!eventExists(context, eventId)) {
            Log.d("PhoneRemind", "Event $eventId no longer exists, skipping")
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = if (apiKey.isNotBlank()) {
                    GeminiApiClient.generateAnnouncement(apiKey, title, timeStr, location)
                } else {
                    "Attenzione, tra 5 minuti hai: $title alle $timeStr"
                }
                NotificationSender.send(context, eventId, text)
                Log.d("PhoneRemind", "Notification sent for: $title")
            } catch (e: Exception) {
                Log.e("PhoneRemind", "Error generating announcement: ${e.message}")
                NotificationSender.send(context, eventId, "Tra 5 minuti hai: $title alle $timeStr")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun eventExists(context: Context, eventId: Long): Boolean {
        if (eventId < 0) return false
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(eventId.toString()), null
        ) ?: return false
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }
}
