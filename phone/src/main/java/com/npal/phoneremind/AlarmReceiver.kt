package com.npal.phoneremind

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("event_id", -1)
        val title = intent.getStringExtra("event_title") ?: return
        val startTime = intent.getLongExtra("event_start", 0)
        val location = intent.getStringExtra("event_location") ?: ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(startTime))

        if (!eventExists(context, eventId, startTime)) {
            Log.d("PhoneRemind", "Event $eventId no longer exists, skipping")
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = try {
                    withTimeout(9_000) {
                        if (apiKey.isNotBlank()) {
                            GeminiApiClient.generateAnnouncement(apiKey, title, timeStr, location)
                        } else {
                            "Attenzione, tra 5 minuti hai: $title alle $timeStr"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PhoneRemind", "Gemini timeout or error: ${e.message}")
                    "Tra 5 minuti hai: $title alle $timeStr"
                }

                // Send directly to watch via Wearable Data Layer
                try {
                    val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                    if (nodes.isNotEmpty()) {
                        val msgClient = Wearable.getMessageClient(context)
                        for (node in nodes) {
                            Tasks.await(msgClient.sendMessage(node.id, "/remind", text.toByteArray(Charsets.UTF_8)))
                        }
                        Log.d("PhoneRemind", "Wearable message sent to ${nodes.size} node(s) for: $title")
                    } else {
                        Log.w("PhoneRemind", "No connected wearable nodes, falling back to notification")
                        NotificationSender.send(context, eventId, text)
                    }
                } catch (e: Exception) {
                    Log.e("PhoneRemind", "Wearable send failed: ${e.message}, falling back to notification")
                    NotificationSender.send(context, eventId, text)
                }
            } catch (e: Exception) {
                Log.e("PhoneRemind", "Unhandled error in AlarmReceiver: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun eventExists(context: Context, eventId: Long, startTime: Long): Boolean {
        if (eventId < 0) return false
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startTime - TimeUnit.MINUTES.toMillis(1))
        ContentUris.appendId(uriBuilder, startTime + TimeUnit.MINUTES.toMillis(1))
        val uri = uriBuilder.build()
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID),
            "${CalendarContract.Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        ) ?: return false
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }
}
