package com.npal.watchremind

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.npal.phoneremind") return
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT) ?: return
        TtsAnnouncer.speak(applicationContext, text)
    }
}
