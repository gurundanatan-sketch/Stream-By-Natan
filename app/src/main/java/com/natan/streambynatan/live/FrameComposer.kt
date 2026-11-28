package com.natan.streambynatan.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Bingkai tegak untuk TikTok dari siaran mendatar (gaya TikTok Live Studio):
 * gambar 16:9 di tengah, latar video yang diburamkan (atau gelap bergradasi) di atas & bawah,
 * judul di bagian atas, dan ruang di bawah untuk kolom komentar TikTok.
 */
class FrameComposer(private val outW: Int = 720, private val outH: Int = 1280) {
    @Volatile var title: String = ""
    @Volatile var showTitle: Boolean = true
    /** blur | dark */
    @Volatile var background: String = "blur"

    private val buffers = arrayOf(
        Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888),
    )
    private var index = 0
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x55FFFFFF }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = outW * 0.058f
        setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt())
    }
    private var tiny: Bitmap? = null
    private var cachedTitle = ""
    private var titleLayout: StaticLayout? = null

    /** Menyusun satu bingkai tegak dari gambar mendatar. */
    fun compose(scene: Bitmap): Bitmap {
        val out = buffers[index]; index = 1 - index
        val c = Canvas(out)
        // area video 16:9 di tengah, sedikit di atas tengah supaya komentar TikTok tidak menutupinya
        val vh = outW * 9f / 16f
        val top = outH * 0.30f
        val video = RectF(0f, top, outW.toFloat(), top + vh)

        if (background == "blur") {
            // buram murah: kecilkan sangat kecil lalu besarkan lagi dengan penghalusan
            val tw = 36; val th = (36f * scene.height / scene.width).toInt().coerceAtLeast(8)
            val t = tiny?.takeIf { it.width == tw && it.height == th } ?: Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also { tiny = it }
            Canvas(t).drawBitmap(scene, null, RectF(0f, 0f, tw.toFloat(), th.toFloat()), smooth)
            val s = maxOf(outW / tw.toFloat(), outH / th.toFloat())
            val dw = tw * s; val dh = th * s
            c.drawBitmap(t, null, RectF((outW - dw) / 2, (outH - dh) / 2, (outW + dw) / 2, (outH + dh) / 2), smooth)
            shade.shader = null; shade.color = 0x66000000
            c.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), shade)
        } else {
            shade.shader = LinearGradient(0f, 0f, 0f, outH.toFloat(), intArrayOf(0xFF0B1022.toInt(), 0xFF1A0F3D.toInt(), 0xFF070A14.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), shade)
        }

        // gambar utama (pas lebar, tanpa terpotong)
        val s = minOf(video.width() / scene.width, video.height() / scene.height)
        val dw = scene.width * s; val dh = scene.height * s
        val dst = RectF(video.centerX() - dw / 2, video.centerY() - dh / 2, video.centerX() + dw / 2, video.centerY() + dh / 2)
        c.drawBitmap(scene, null, dst, smooth)
        c.drawRect(dst, border)

        // judul di atas video
        val t = title.trim()
        if (showTitle && t.isNotEmpty()) {
            if (t != cachedTitle || titleLayout == null) {
                cachedTitle = t
                titleLayout = StaticLayout.Builder.obtain(t, 0, t.length, titlePaint, (outW * 0.86f).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            }
            val l = titleLayout!!
            c.save()
            c.translate(outW * 0.07f, (top - l.height) - outH * 0.03f)
            l.draw(c)
            c.restore()
        }
        return out
    }
}
