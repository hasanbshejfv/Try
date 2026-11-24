package com.screenmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** يلتقط الشاشة باستمرار (MediaProjection → VirtualDisplay) ويرسل إطار JPEG مصغّر عند تغيّر المحتوى. */
class ScreenCapturer(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val intervalMs: Long = 1000,
    private val maxSide: Int = 768,
    private val onFrame: (ByteArray) -> Unit,
    private val onStopped: () -> Unit,
) {
    private val thread = HandlerThread("capture").apply { start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var lastHash = 0L
    private var lastSent = 0L
    @Volatile private var running = false

    fun start() {
        val m = ctx.resources.displayMetrics
        val scale = maxSide.toFloat() / max(m.widthPixels, m.heightPixels)
        val w = (m.widthPixels * scale).roundToInt().coerceAtLeast(2)
        val h = (m.heightPixels * scale).roundToInt().coerceAtLeast(2)

        // Android 14+: يجب تسجيل callback قبل إنشاء VirtualDisplay
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop(); onStopped() }
        }, handler)

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection.createVirtualDisplay(
            "ScreenMentor", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        running = true
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            runCatching { grab() }
            handler.postDelayed(this, intervalMs)
        }
    }

    private fun grab() {
        val img = reader?.acquireLatestImage() ?: return // null = لا إطار جديد
        img.use {
            val p = it.planes[0]
            val w = it.width; val h = it.height
            val rowPad = p.rowStride - p.pixelStride * w
            val full = Bitmap.createBitmap(w + rowPad / p.pixelStride, h, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(p.buffer)
            val bmp = if (rowPad > 0) Bitmap.createBitmap(full, 0, 0, w, h) else full
            try {
                val hash = sampleHash(bmp)
                val now = System.currentTimeMillis()
                // أرسل فقط عند التغيّر، مع إطار تحديث كل 5 ثوانٍ
                if (hash != lastHash || now - lastSent > 5000) {
                    lastHash = hash; lastSent = now
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    onFrame(out.toByteArray())
                }
            } finally {
                if (bmp !== full) full.recycle()
                bmp.recycle()
            }
        }
    }

    private fun sampleHash(b: Bitmap): Long {
        var hsh = 1125899906842597L
        for (y in 0 until 24) for (x in 0 until 24) {
            hsh = 31 * hsh + b.getPixel(x * (b.width - 1) / 23, y * (b.height - 1) / 23)
        }
        return hsh
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { display?.release() }
        runCatching { reader?.close() }
        thread.quitSafely()
    }
}
