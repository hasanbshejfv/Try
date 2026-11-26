package com.screenmentor

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class ChessSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        val result = tts.setLanguage(Locale("ar", "SA"))

        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            ready = false
            return
        }

        // محاولة اختيار صوت رجالي إذا كان متوفراً
        val maleVoice = tts.voices?.firstOrNull {
            it.locale.language == "ar" &&
            (it.name.contains("male", ignoreCase = true) ||
             it.name.contains("masculine", ignoreCase = true))
        }

        if (maleVoice != null) {
            tts.voice = maleVoice
        }

        ready = true
    }

    fun speakMove(move: String) {
        if (!ready || move.length < 4) return

        val from = move.substring(0, 2)
        val to = move.substring(2, 4)

        val message = "أفضل نقلة هي من المربع $from إلى المربع $to"

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
