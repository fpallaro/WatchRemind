package com.npal.watchremind

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object TtsAnnouncer {

    fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) { tts?.shutdown() }
                    override fun onError(utteranceId: String?) { tts?.shutdown() }
                    override fun onStart(utteranceId: String?) {}
                })
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "watchremind_utterance")
            }
        }
    }
}
