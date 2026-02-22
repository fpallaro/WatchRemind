package com.npal.watchremind

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class MessageListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/remind") {
            val text = String(messageEvent.data, Charsets.UTF_8)
            Log.d("WatchRemind", "Message received: $text")
            TtsAnnouncer.speak(applicationContext, text)
        }
    }
}
