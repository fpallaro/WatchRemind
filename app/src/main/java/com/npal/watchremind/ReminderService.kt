package com.npal.watchremind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class ReminderService : Service(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WatchRemind"
        private const val CHANNEL_ID = "watchremind_service"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var messageClient: MessageClient

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(
            this,
            Uri.parse("wear://*/remind"),
            MessageClient.FILTER_LITERAL
        )
        Log.d(TAG, "ReminderService started, listening on /remind")
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/remind") {
            val text = String(event.data, Charsets.UTF_8)
            Log.d(TAG, "Message received: $text")
            TtsAnnouncer.speak(applicationContext, text)
        }
    }

    override fun onDestroy() {
        messageClient.removeListener(this)
        Log.d(TAG, "ReminderService stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WatchRemind",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("WatchRemind")
        .setContentText("In ascolto promemoria")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .build()
}
