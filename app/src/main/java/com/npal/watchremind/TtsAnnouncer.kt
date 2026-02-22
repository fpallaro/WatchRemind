package com.npal.watchremind

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsAnnouncer {

    fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "watchremind_utterance")
                // Shutdown TTS after enough time to finish speaking
                Handler(Looper.getMainLooper()).postDelayed({ tts?.shutdown() }, 15_000)
            }
        }
    }
}
