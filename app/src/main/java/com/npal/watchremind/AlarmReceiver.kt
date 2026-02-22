package com.npal.watchremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("event_title") ?: return
        val startTime = intent.getLongExtra("event_start", 0)
        val location = intent.getStringExtra("event_location") ?: ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(startTime))

        val prefs = context.getSharedPreferences("watchremind", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = if (apiKey.isNotBlank()) {
                    GeminiApiClient.generateAnnouncement(apiKey, title, timeStr, location)
                } else {
                    "Attenzione, tra 5 minuti hai: $title alle $timeStr"
                }
                TtsAnnouncer.speak(context, text)
            } catch (e: Exception) {
                TtsAnnouncer.speak(context, "Tra 5 minuti hai: $title alle $timeStr")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
