package com.npal.watchremind

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
        Log.d("WatchRemind", "onNotificationPosted pkg=${sbn.packageName} text=$text")
        if (sbn.packageName != "com.npal.phoneremind") return
        if (text == null) return
        TtsAnnouncer.speak(applicationContext, text)
    }
}
