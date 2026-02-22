package com.npal.phoneremind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ReminderService : Service() {

    companion object {
        private const val TAG = "PhoneRemind"
        private const val FOREGROUND_ID = 1
        private const val CHANNEL_SERVICE = "phone_remind_service"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            Log.d(TAG, "Calendar changed, rescanning...")
            rescan()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_SERVICE, "Servizio Promemoria", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            calendarObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        rescan()
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(calendarObserver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun rescan() {
        serviceScope.launch {
            try {
                val events = CalendarPoller.getUpcomingEvents(this@ReminderService)
                CalendarPoller.scheduleAlarms(this@ReminderService, events)
            } catch (e: Exception) {
                Log.e(TAG, "Rescan error: ${e.message}")
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("PhoneRemind")
            .setContentText("Monitoraggio calendario attivo")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setOngoing(true)
            .build()
    }
}
