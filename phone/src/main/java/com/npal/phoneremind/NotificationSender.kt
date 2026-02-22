package com.npal.phoneremind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object NotificationSender {

    private const val CHANNEL_ID = "phone_remind_events"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Promemoria Appuntamenti",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Annunci vocali promemoria appuntamenti"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun send(context: Context, eventId: Long, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Promemoria")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify((2000 + eventId).toInt(), notification)
    }
}
