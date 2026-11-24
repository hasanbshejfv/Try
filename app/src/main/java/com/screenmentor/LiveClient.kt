package com.screenmentor

import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** WebSocket ثنائي الاتجاه مع Gemini Live API: صوت + إطارات شاشة داخل، صوت + نص خارج. */
class LiveClient(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val l: Listener,
) {
    interface Listener {
        fun onReady()
        fun onAudio(pcm24k: ByteArray)
        fun onInterrupted()
        fun onTranscript(text: String)
        fun onClosed(error: String?)
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var ready = false

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setup().toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false; l.onClosed(t.message ?: "failure")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false; l.onClosed(if (code == 1000) null else "$code $reason")
            }
        })
    }

    private fun setup() = JSONObject().put("setup", JSONObject()
        .put("model", "models/$model")
        .put("generationConfig", JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede")))))
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
        .put("inputAudioTranscription", JSONObject())
        .put("outputAudioTranscription", JSONObject())
        // بدون الضغط يُقطع الاتصال بعد دقيقتين تقريباً عند إرسال فيديو
        .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject())))

    private fun handle(raw: String) {
        val j = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (j.has("setupComplete")) { ready = true; l.onReady(); return }
        if (j.has("goAway")) { l.onClosed("session ending (goAway)"); return }
        val sc = j.optJSONObject("serverContent") ?: return
        if (sc.optBoolean("interrupted")) l.onInterrupted()
        sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { l.onAudio(Base64.decode(it, Base64.DEFAULT)) }
            }
        }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { l.onTranscript(it) }
        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { l.onTranscript("\nأنت: $it") }
    }

    /** Gemini Live realtime input has separate audio and video Blob fields. */
    private fun sendRealtime(field: String, mime: String, data: ByteArray) {
        val s = ws ?: return
        if (!ready) return
        // Avoid unbounded latency when the network is slower than capture/audio.
        if (s.queueSize() > 1_000_000) return
        val blob = JSONObject()
            .put("mimeType", mime)
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        val msg = JSONObject().put("realtimeInput", JSONObject().put(field, blob))
        s.send(msg.toString())
    }

    fun sendAudio(pcm16k: ByteArray) = sendRealtime("audio", "audio/pcm;rate=16000", pcm16k)
    fun sendFrame(jpeg: ByteArray) = sendRealtime("video", "image/jpeg", jpeg)

    fun close() { ready = false; ws?.close(1000, null); ws = null }
}
