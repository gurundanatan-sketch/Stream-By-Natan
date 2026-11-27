package com.natan.streambynatan.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import kotlin.math.max

/**
 * Sumber video yang digambar sendiri: dipakai untuk
 * - pengganti latar belakang (tubuhmu tetap terlihat, latar diganti),
 * - mode suara saja (gambar pilihanmu),
 * - kamera tambahan dari HP lain / tamu lewat link browser.
 * Gambar dikirim lewat [submit], lalu digambar ke video dengan fps yang sama.
 */
class CanvasVideoSource(private val placeholder: Bitmap? = null) : VideoSource() {
    private var surface: Surface? = null
    @Volatile private var frame: Bitmap? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    var outW = 0; private set
    var outH = 0; private set

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean = true

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        val rotate = rotation == 90 || rotation == 270
        outW = if (rotate) height else width
        outH = if (rotate) width else height
        surfaceTexture.setDefaultBufferSize(outW, outH)
        surface = Surface(surfaceTexture)
        running = true
        thread = Thread({
            val delay = 1000L / max(1, fps)
            while (running) {
                val start = System.currentTimeMillis()
                runCatching {
                    val s = surface ?: return@runCatching
                    val c: Canvas = s.lockCanvas(null)
                    try { drawFrame(c) } finally { s.unlockCanvasAndPost(c) }
                }
                val spent = System.currentTimeMillis() - start
                if (spent < delay) Thread.sleep(delay - spent)
            }
        }, "CanvasVideoSource").apply { start() }
    }

    private fun drawFrame(c: Canvas) {
        c.drawColor(Color.BLACK)
        val b = frame ?: placeholder ?: return
        if (b.isRecycled) return
        // isi penuh (crop tengah) supaya tidak ada garis hitam
        val s = max(outW / b.width.toFloat(), outH / b.height.toFloat())
        val dw = b.width * s; val dh = b.height * s
        c.drawBitmap(b, null, RectF((outW - dw) / 2, (outH - dh) / 2, (outW + dw) / 2, (outH + dh) / 2), paint)
    }

    fun submit(bitmap: Bitmap) { frame = bitmap }

    override fun stop() {
        running = false
        thread?.join(500)
        thread = null
        surface?.release()
        surface = null
    }

    override fun release() {}
    override fun isRunning(): Boolean = running

    override fun getOrientationConfig(): OrientationConfig {
        val isPortrait = (rotation == 90 || rotation == 270) != (height > width)
        return OrientationConfig(cameraOrientation = 0, isPortrait = isPortrait, forced = OrientationForced.NONE)
    }
}
