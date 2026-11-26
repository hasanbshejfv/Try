package com.screenmentor

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class ChessSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    private var language = Locale("ar", "IQ")

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        val arabicResult = tts.setLanguage(language)

        if (arabicResult == TextToSpeech.LANG_MISSING_DATA ||
            arabicResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            language = Locale.US
            tts.setLanguage(language)
        }

        ready = true
    }

    fun speakMove(move: String) {
        if (!ready || move.length < 4) return

        val from = move.substring(0, 2)
        val to = move.substring(2, 4)

        val message = if (language.language == "ar") {
            "أفضل نقلة، من المربع $from إلى المربع $to"
        } else {
            "Best move, $from to $to"
        }

        tts.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "chess_move"
        )
    }

    fun release() {
        ready = false
        tts.stop()
        tts.shutdown()
    }
}
