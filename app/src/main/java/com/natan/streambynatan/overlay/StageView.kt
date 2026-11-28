package com.natan.streambynatan.overlay

import android.annotation.SuppressLint
import android.content.Context
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
import android.view.View
import com.natan.streambynatan.data.CardStyle
import com.natan.streambynatan.data.TransitionConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Lapisan grafis yang ditempel ke video SEBELUM dikirim ke YouTube & TikTok
 * (dirender ke siaran lewat ViewSurfaceFilterRender milik RootEncoder).
 * Isinya: teks overlay, teks berjalan, subtitle (paling bawah), kartu ayat, kode QR, info persembahan,
 * jawaban FAQ, pengumuman, layar pembuka/penutup sinematik, kotak tamu, notifikasi Saweria.
 */
@SuppressLint("ViewConstructor")
class StageView(context: Context) : View(context) {

    abstract class Item(val fullScreen: Boolean = false) {
        abstract fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long)
    }

    private class Entry(val item: Item, val shownAt: Long, var hideAt: Long, var exitStart: Long = 0L, val z: Int)

    private val entries = ConcurrentHashMap<String, Entry>()
    @Volatile var transition = TransitionConfig()
    @Volatile var tickerText: String = ""
    @Volatile var tickerSpeed: Float = 1f
    @Volatile var overlayText: String = ""
    @Volatile var subtitle: String = ""
    @Volatile var subtitleScale: Float = 1f
    /** Bitmap tamu (WebRTC) yang ditampilkan sebagai kotak kecil. */
    val guestTiles = ConcurrentHashMap<String, Pair<String, Bitmap>>()
    /** Teks layar bebas dari host (Sempurnakan > Teks layar). */
    @Volatile var screenText: com.natan.streambynatan.data.ScreenTextConfig? = null
    /** Papan nama: (nama, keterangan), posisi bottom_left | top_left | top_right. */
    @Volatile var nameplate: Pair<String, String>? = null
    @Volatile var nameplatePosition: String = "bottom_left"
    /** Komentar yang ikut tampil di layar siaran: (nama, isi, waktu). */
    val streamComments = java.util.concurrent.ConcurrentLinkedDeque<Triple<String, String, Long>>()
    fun pushComment(name: String, text: String) {
        streamComments.addLast(Triple(name, text.take(120), System.currentTimeMillis()))
        while (streamComments.size > 4) streamComments.pollFirst()
    }
    private var tickerX = Float.NaN
    private var zCounter = 0
    var onChange: ((List<String>) -> Unit)? = null

    private val tickerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val overlayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    init { setBackgroundColor(Color.TRANSPARENT) }

    /** Menampilkan item. [durationMs] 0 = tampil sampai [hide] dipanggil. */
    fun show(id: String, item: Item, durationMs: Long = 0) {
        val now = System.currentTimeMillis()
        entries[id] = Entry(item, now, if (durationMs > 0) now + durationMs else 0L, 0L, zCounter++)
        onChange?.invoke(entries.keys.toList())
    }

    fun hide(id: String) {
        entries[id]?.let { if (it.exitStart == 0L) it.exitStart = System.currentTimeMillis() }
    }

    fun hideAll() { entries.keys.forEach { hide(it) } }

    fun isShowing(id: String) = entries[id]?.let { it.exitStart == 0L } ?: false

    fun showingIds(): List<String> = entries.filter { it.value.exitStart == 0L }.keys.toList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width; val h = height
        if (w == 0 || h == 0) { postInvalidateDelayed(50); return }
        val now = System.currentTimeMillis()
        val dur = transition.durationMs.coerceAtLeast(1).toLong()

        drawGuests(canvas, w, h)
        drawOverlayText(canvas, w, h)
        drawScreenText(canvas, w, h)
        drawComments(canvas, w, h)
        drawTicker(canvas, w, h)
        drawNameplate(canvas, w, h)
        drawSubtitle(canvas, w, h)

        val sorted = entries.entries.sortedBy { it.value.z }
        for ((id, e) in sorted) {
            if (e.hideAt in 1..now && e.exitStart == 0L) e.exitStart = now
            val enter = min(1f, (now - e.shownAt).toFloat() / dur)
            val exit = if (e.exitStart > 0) min(1f, (now - e.exitStart).toFloat() / dur) else 0f
            if (exit >= 1f) { entries.remove(id); onChange?.invoke(entries.keys.toList()); continue }
            val p = Transitions.ease(enter) * (1f - Transitions.ease(exit))
            val save = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (255 * Transitions.alphaFor(transition.style, p)).toInt())
            Transitions.apply(canvas, transition.style, p, w, h)
            e.item.draw(canvas, w, h, now - e.shownAt)
            canvas.restoreToCount(save)
        }
        postInvalidateDelayed(33)
    }

    private fun drawOverlayText(c: Canvas, w: Int, h: Int) {
        val text = overlayText.takeIf { it.isNotBlank() } ?: return
        overlayPaint.textSize = w * 0.042f
        val layout = layout(text, overlayPaint, (w * 0.86f).toInt(), Layout.Alignment.ALIGN_CENTER)
        val top = h * 0.05f
        boxPaint.color = 0x99000000.toInt()
        c.drawRoundRect(RectF(w * 0.05f, top - 12, w * 0.95f, top + layout.height + 12), 24f, 24f, boxPaint)
        c.save(); c.translate(w * 0.07f, top); layout.draw(c); c.restore()
    }

    private val plateName = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val plateRole = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22E5FF.toInt(); typeface = Typeface.DEFAULT }
    private val commentName = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22E5FF.toInt(); typeface = Typeface.DEFAULT_BOLD }
    private val commentText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private fun drawScreenText(c: Canvas, w: Int, h: Int) {
        val st = screenText ?: return
        if (!st.enabled || st.text.isBlank()) return
        overlayPaint.color = st.color
        overlayPaint.textSize = w * 0.05f * st.size.coerceIn(0.5f, 2.5f)
        val layout = layout(st.text, overlayPaint, (w * 0.86f).toInt(), Layout.Alignment.ALIGN_CENTER, 4)
        val top = when (st.position) { "center" -> (h - layout.height) / 2f; "bottom" -> h * 0.62f; else -> h * 0.13f }
        if (st.background) {
            boxPaint.color = 0x99000000.toInt()
            c.drawRoundRect(RectF(w * 0.05f, top - 14, w * 0.95f, top + layout.height + 14), 26f, 26f, boxPaint)
        }
        c.save(); c.translate(w * 0.07f, top); layout.draw(c); c.restore()
        overlayPaint.color = Color.WHITE
    }

    /** Komentar penonton di sisi kiri layar siaran (maksimal 4, hilang sendiri setelah 12 detik). */
    private fun drawComments(c: Canvas, w: Int, h: Int) {
        if (streamComments.isEmpty()) return
        val now = System.currentTimeMillis()
        streamComments.removeIf { now - it.third > 12_000 }
        commentName.textSize = w * 0.03f; commentText.textSize = w * 0.032f
        var y = h * 0.58f
        for ((name, text, _) in streamComments) {
            val l = layout(text, commentText, (w * 0.58f).toInt(), Layout.Alignment.ALIGN_NORMAL, 2)
            val boxH = commentName.textSize * 1.3f + l.height + 16
            boxPaint.color = 0x88000000.toInt()
            c.drawRoundRect(RectF(w * 0.03f, y, w * 0.65f, y + boxH), 18f, 18f, boxPaint)
            c.drawText(name, w * 0.05f, y + commentName.textSize * 1.15f, commentName)
            c.save(); c.translate(w * 0.05f, y + commentName.textSize * 1.4f); l.draw(c); c.restore()
            y += boxH + 10
        }
    }

    /** Papan nama (misal "Natan · Host"), di kiri bawah DI ATAS teks berjalan, atau di posisi lain. */
    private fun drawNameplate(c: Canvas, w: Int, h: Int) {
        val (name, role) = nameplate ?: return
        if (name.isBlank()) return
        plateName.textSize = w * 0.048f; plateRole.textSize = w * 0.032f
        val tw = maxOf(plateName.measureText(name), plateRole.measureText(role)) + w * 0.08f
        val bh = plateName.textSize * 1.3f + (if (role.isNotBlank()) plateRole.textSize * 1.4f else 0f) + 20
        val tickerReserve = if (tickerText.isNotBlank()) w * 0.04f * 1.8f + 10 else 0f
        val subReserve = if (subtitle.isNotBlank()) h * 0.13f else h * 0.03f
        val (left, top) = when (nameplatePosition) {
            "top_left" -> w * 0.04f to h * 0.1f
            "top_right" -> (w * 0.96f - tw) to h * 0.1f
            else -> w * 0.04f to (h - subReserve - tickerReserve - bh - 16)
        }
        boxPaint.shader = LinearGradient(left, 0f, left + tw, 0f, 0xE622E5FF.toInt(), 0xE68B5CFF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(left, top, left + 10, top + bh, boxPaint)
        boxPaint.shader = null
        boxPaint.color = 0xCC070A14.toInt()
        c.drawRect(left + 10, top, left + tw, top + bh, boxPaint)
        c.drawText(name, left + w * 0.04f, top + plateName.textSize * 1.15f, plateName)
        if (role.isNotBlank()) c.drawText(role, left + w * 0.04f, top + plateName.textSize * 1.3f + plateRole.textSize * 1.15f, plateRole)
    }

    /** Teks berjalan, diletakkan sedikit DI ATAS subtitle supaya tidak bertumpuk. */
    private fun drawTicker(c: Canvas, w: Int, h: Int) {
        val text = tickerText.takeIf { it.isNotBlank() } ?: return
        tickerPaint.textSize = w * 0.04f
        val barH = tickerPaint.textSize * 1.8f
        val subReserve = if (subtitle.isNotBlank()) h * 0.13f else h * 0.02f
        val bottom = h - subReserve
        boxPaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0xCC22E5FF.toInt(), 0xCC8B5CFF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, bottom - barH, w.toFloat(), bottom, boxPaint)
        boxPaint.shader = null
        val tw = tickerPaint.measureText(text)
        if (tickerX.isNaN() || tickerX < -tw) tickerX = w.toFloat()
        tickerX -= 3f * tickerSpeed * (w / 720f)
        c.drawText(text, tickerX, bottom - barH * 0.32f, tickerPaint)
    }

    /** Subtitle otomatis di bagian PALING BAWAH video. */
    private fun drawSubtitle(c: Canvas, w: Int, h: Int) {
        val text = subtitle.takeIf { it.isNotBlank() } ?: return
        subPaint.textSize = w * 0.045f * subtitleScale
        val layout = layout(text.takeLast(140), subPaint, (w * 0.9f).toInt(), Layout.Alignment.ALIGN_CENTER, maxLines = 2)
        val bottom = h - h * 0.015f
        val top = bottom - layout.height - 16
        boxPaint.color = 0xB3000000.toInt()
        c.drawRoundRect(RectF(w * 0.03f, top - 8, w * 0.97f, bottom), 18f, 18f, boxPaint)
        c.save(); c.translate(w * 0.05f, top); layout.draw(c); c.restore()
    }

    private fun drawGuests(c: Canvas, w: Int, h: Int) {
        if (guestTiles.isEmpty()) return
        val tileW = w * 0.32f
        var y = h * 0.14f
        for ((_, pair) in guestTiles) {
            val (name, bmp) = pair
            if (bmp.isRecycled) continue
            val tileH = tileW * bmp.height / max(1, bmp.width)
            val r = RectF(w - tileW - w * 0.03f, y, w - w * 0.03f, y + tileH)
            boxPaint.color = 0xFF22E5FF.toInt()
            c.drawRoundRect(RectF(r.left - 4, r.top - 4, r.right + 4, r.bottom + 4), 20f, 20f, boxPaint)
            c.drawBitmap(bmp, null, r, null)
            tickerPaint.textSize = w * 0.03f
            boxPaint.color = 0xAA000000.toInt()
            c.drawRect(r.left, r.bottom - tickerPaint.textSize * 1.5f, r.right, r.bottom, boxPaint)
            c.drawText(name, r.left + 10, r.bottom - tickerPaint.textSize * 0.4f, tickerPaint)
            y += tileH + 24
        }
    }

    companion object {
        fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment, maxLines: Int = 40): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
                .setAlignment(align)
                .setLineSpacing(0f, 1.12f)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

        /** Mengecilkan huruf sampai teks muat di kotak. */
        fun fitLayout(text: String, paint: TextPaint, width: Int, maxHeight: Int, startSize: Float, minSize: Float): StaticLayout {
            var size = startSize
            var l: StaticLayout
            do {
                paint.textSize = size
                l = layout(text, paint, width, Layout.Alignment.ALIGN_CENTER)
                size *= 0.92f
            } while (l.height > maxHeight && size > minSize)
            return l
        }

        fun gradient(style: CardStyle, w: Int, h: Int): LinearGradient =
            LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), style.bgColor, style.bgColor2, Shader.TileMode.CLAMP)
    }
}
