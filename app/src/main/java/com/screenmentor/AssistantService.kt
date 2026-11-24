package com.screenmentor

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat

const val ACTION_START = "com.screenmentor.START"
const val ACTION_STOP = "com.screenmentor.STOP"

class AssistantService : Service() {
    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var mic: MicStreamer? = null
    private var player: SpeakerPlayer? = null
    private var client: LiveClient? = null
    @Volatile private var closed = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> shutdown()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (client != null) return
        closed = false
        SessionState.transcript.value = ""
        SessionState.status.value = Status.Connecting

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("share", "Screen sharing", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, "share")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.st_live))
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopPi)
            .build()
        ServiceCompat.startForeground(
            this, 1, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        val data = IntentCompat.getParcelableExtra(intent, "data", Intent::class.java) ?: return shutdown()
        val code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        try {
            projection = getSystemService(Context.MEDIA_PROJECTION_SERVICE).let { it as MediaProjectionManager }
                .getMediaProjection(code, data)
        } catch (e: Exception) {
            SessionState.status.value = Status.Error
            shutdown(keepError = true)
            return
        }

        val prompt = when (intent.getStringExtra("mode")) {
            "learn" -> Prompts.LEARN
            "chess_coach" -> Prompts.CHESS_COACH
            "chess_training" -> Prompts.CHESS_TRAINING
            else -> Prompts.ASSIST
        }
        player = SpeakerPlayer()
        client = LiveClient(BuildConfig.GEMINI_API_KEY, BuildConfig.LIVE_MODEL, prompt, object : LiveClient.Listener {
            override fun onReady() {
                val c = client ?: return
                try {
                    val activeProjection = projection ?: error("Screen capture permission is unavailable")
                    capturer = ScreenCapturer(
                        this@AssistantService, activeProjection,
                        onFrame = { c.sendFrame(it) }, onStopped = { shutdown() }
                    ).also { it.start() }
                    mic = MicStreamer { c.sendAudio(it) }.also { it.start() }
                    SessionState.status.value = Status.Live
                } catch (e: Exception) {
                    SessionState.status.value = Status.Error
                    shutdown(keepError = true)
                }
            }
            override fun onAudio(pcm24k: ByteArray) { player?.enqueue(pcm24k) }
            override fun onInterrupted() { player?.interrupt() }
            override fun onTranscript(text: String) = SessionState.append(text)
            override fun onClosed(error: String?) {
                if (error != null && !closed) SessionState.status.value = Status.Error
                shutdown(keepError = error != null)
            }
        }).also { it.connect() }
    }

    private fun shutdown(keepError: Boolean = false) {
        if (closed) return
        closed = true
        runCatching { mic?.stop() }
        runCatching { capturer?.stop() }
        runCatching { client?.close() }
        runCatching { player?.release() }
        runCatching { projection?.stop() }
        mic = null; capturer = null; client = null; player = null; projection = null
        if (!keepError) SessionState.status.value = Status.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { shutdown(); super.onDestroy() }
}

object Prompts {
    private const val BASE = """You are a live voice assistant that sees the user's Android screen as a stream of frames (about 1 per second) and hears them through the microphone.
Rules:
- Prefer natural Iraqi Arabic when the user speaks Arabic; otherwise reply in the user's language. Keep spoken answers short and natural.
- Always ground answers in what is CURRENTLY visible. If the screen is unclear or changed, say so or ask.
- You cannot touch the screen. Tell the user where to tap using visible labels and screen position (top/bottom, left/right).
- Track the user's steps, notice mistakes, and correct them gently. Remember the current task.
- Never read out passwords, codes or private data visible on screen."""

    const val ASSIST = BASE + "\nMode: ASSIST. Be fast and practical: give the next single action."
    const val LEARN = BASE + """
Mode: LEARN. Act as a patient teacher: explain WHY, go step by step, ask a short check question now and then.
For chess or Blender, first identify what is actually visible. If a detail is uncertain, ask the user to confirm it.
Blender: identify the active editor/tool/menu, say exactly where to find the next tool, and explain the modeling concept."""

    const val CHESS_COACH = BASE + """
Mode: CHESS COACH.
- Focus on teaching chess from the board currently visible on screen.
- First identify the board orientation, side to move, and piece locations. If any square or piece is unclear, ask the user to confirm; never guess.
- Explain candidate moves and the strategic idea in concise spoken language.
- Give one recommendation at a time, with a short reason and the main threat to watch for.
- Distinguish a verified legal move from a tentative visual interpretation.
- Do not claim engine-level accuracy: this build does not include a verified chess engine.
- Ask whether the user wants a hint or a full explanation when appropriate."""

    const val CHESS_TRAINING = BASE + """
Mode: CHESS TRAINING.
- Help the user learn by letting them choose their own move first.
- Ask what move they are considering when the position is visible.
- After the user makes or names a move, explain its strengths, weaknesses, tactical risks, and a possible improvement.
- Classify feedback as excellent, good, inaccurate, or a mistake only when the board position and move are clear.
- Prefer hints before revealing a solution; reveal the answer when the user asks.
- Track recurring mistakes within the current conversation and suggest a short exercise.
- If the board cannot be read reliably, ask for the position or move instead of inventing it.
- Do not claim engine-level accuracy: this build does not include a verified chess engine."""
}
