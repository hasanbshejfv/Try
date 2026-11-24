package com.screenmentor

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.max

/** تسجيل الميكروفون 16kHz PCM مع إلغاء الصدى (ضروري كي لا يسمع المساعد نفسه). */
class MicStreamer(private val onChunk: (ByteArray) -> Unit) {
    private val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    @SuppressLint("MissingPermission")
    private val rec = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 6400)
    )
    @Volatile private var running = false

    fun start() {
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
        rec.startRecording()
        running = true
        thread(name = "mic") {
            val buf = ByteArray(3200) // 100ms
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) onChunk(buf.copyOf(n))
            }
        }
    }

    fun stop() {
        running = false
        runCatching { rec.stop() }
        rec.release()
    }
}

/** تشغيل صوت الرد (24kHz PCM) مع إمكانية القطع الفوري عند المقاطعة. */
class SpeakerPlayer {
    private val minBuf = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        .setAudioFormat(
            AudioFormat.Builder().setSampleRate(24000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        )
        .setBufferSizeInBytes(max(minBuf, 48000))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = true

    init {
        track.play()
        thread(name = "speaker") {
            while (running) {
                try { val b = queue.take(); track.write(b, 0, b.size) } catch (_: InterruptedException) { break }
            }
        }
    }

    fun enqueue(pcm: ByteArray) { queue.offer(pcm) }

    fun interrupt() {
        queue.clear()
        runCatching { track.pause(); track.flush(); track.play() }
    }

    fun release() {
        running = false
        queue.clear()
        runCatching { track.stop() }
        track.release()
    }
}
