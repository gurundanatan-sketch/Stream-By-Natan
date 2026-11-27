package com.natan.streambynatan.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.natan.streambynatan.data.CardStyle
import com.natan.streambynatan.data.QrStyle
import kotlin.math.min

/** Ukuran & posisi gambar: kecil di pojok, sedang (seperempat layar), atau penuh layar. */
fun placeRect(size: String, position: String, w: Int, h: Int, aspect: Float): RectF {
    if (size == "full") return RectF(0f, 0f, w.toFloat(), h.toFloat())
    val boxW = if (size == "small") w * 0.28f else w * 0.5f
    val boxH = boxW / aspect.coerceAtLeast(0.1f)
    val m = w * 0.04f
    val left = when {
        position.endsWith("left") -> m
        position.endsWith("right") -> w - boxW - m
        else -> (w - boxW) / 2f
    }
    val top = when {
        position.startsWith("top") -> h * 0.12f
        position.startsWith("bottom") -> h - boxH - h * 0.18f
        else -> (h - boxH) / 2f
    }
    return RectF(left, top, left + boxW, top + boxH)
}

/** Kartu teks penuh layar: ayat, kutipan, jawaban FAQ, hasil polling, pendukung teratas. */
class CardItem(
    private val body: String,
    private val footer: String,
    private val style: CardStyle,
    private val bgImage: Bitmap? = null,
    private val header: String = "",
) : StageView.Item(fullScreen = true) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@CardItem.style.textColor; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@CardItem.style.accentColor; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@CardItem.style.accentColor; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.15f
    }

    override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
        if (bgImage != null && !bgImage.isRecycled) {
            c.drawBitmap(bgImage, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
            bg.shader = null; bg.color = 0x88000000.toInt()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        } else {
            bg.shader = StageView.gradient(style, w, h)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            bg.shader = null
            // cahaya lembut bergerak
            val t = (elapsedMs % 8000) / 8000f
            bg.color = Color.argb(40, Color.red(style.accentColor), Color.green(style.accentColor), Color.blue(style.accentColor))
            c.drawCircle(w * (0.2f + 0.6f * t), h * 0.25f, w * 0.45f, bg)
        }
        val tw = (w * 0.84f).toInt()
        var y = h * 0.12f
        if (header.isNotBlank()) {
            headPaint.textSize = w * 0.04f
            val hl = StageView.layout(header.uppercase(), headPaint, tw, Layout.Alignment.ALIGN_CENTER)
            c.save(); c.translate(w * 0.08f, y); hl.draw(c); c.restore()
            y += hl.height + h * 0.03f
        }
        footPaint.textSize = w * 0.05f * style.fontScale
        val fl = StageView.layout(footer, footPaint, tw, Layout.Alignment.ALIGN_CENTER)
        val maxH = (h * 0.82f - y - fl.height).toInt()
        val bl = StageView.fitLayout(body, textPaint, tw, maxH, w * 0.075f * style.fontScale, w * 0.03f)
        val top = y + (maxH - bl.height) / 2f
        c.save(); c.translate(w * 0.08f, top); bl.draw(c); c.restore()
        bg.color = style.accentColor
        c.drawRect(w * 0.4f, top + bl.height + h * 0.025f, w * 0.6f, top + bl.height + h * 0.025f + 4f, bg)
        c.save(); c.translate(w * 0.08f, top + bl.height + h * 0.045f); fl.draw(c); c.restore()
    }
}

/** Gambar (info persembahan/QRIS, jawaban berupa gambar, latar suara-saja). */
class ImageItem(
    @Volatile var bitmap: Bitmap?,
    private val size: String,
    private val position: String,
    private val caption: String = "",
) : StageView.Item(fullScreen = size == "full") {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
        val b = bitmap ?: return
        if (b.isRecycled) return
        val r = placeRect(size, position, w, h, b.width / b.height.toFloat())
        if (size == "full") {
            p.color = Color.BLACK; c.drawRect(r, p)
            val s = min(w / b.width.toFloat(), h / b.height.toFloat())
            val dw = b.width * s; val dh = b.height * s
            c.drawBitmap(b, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p)
        } else {
            p.color = 0xCCFFFFFF.toInt()
            c.drawRoundRect(RectF(r.left - 6, r.top - 6, r.right + 6, r.bottom + 6), 22f, 22f, p)
            c.drawBitmap(b, null, r, p)
        }
        if (caption.isNotBlank()) {
            cap.textSize = w * 0.035f
            val l = StageView.layout(caption, cap, r.width().toInt(), Layout.Alignment.ALIGN_CENTER, 3)
            p.color = 0xAA000000.toInt()
            val top = if (size == "full") h - l.height - h * 0.08f else r.bottom + 10
            c.drawRoundRect(RectF(r.left, top - 6, r.right, top + l.height + 6), 14f, 14f, p)
            c.save(); c.translate(r.left, top); l.draw(c); c.restore()
        }
    }
}

/** Kode QR Saweria. Latar putih supaya mudah dipindai, judul di atas, alamat link di bawah. */
class QrItem(private val link: String, private val style: QrStyle) : StageView.Item(fullScreen = style.size == "full") {
    private val qr: Bitmap = makeQr(link, 600, style.fgColor, style.bgColor)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = this@QrItem.style.textColor; typeface = Typeface.DEFAULT_BOLD }

    override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
        if (style.size == "full") {
            p.color = style.bgColor
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            val s = w * 0.72f
            val left = (w - s) / 2; val top = (h - s) / 2
            t.textSize = w * 0.065f
            val tl = StageView.layout(style.title, t, (w * 0.9f).toInt(), Layout.Alignment.ALIGN_CENTER)
            c.save(); c.translate(w * 0.05f, top - tl.height - h * 0.04f); tl.draw(c); c.restore()
            c.drawBitmap(qr, null, RectF(left, top, left + s, top + s), null)
            t.textSize = w * 0.045f
            val ll = StageView.layout(link.removePrefix("https://"), t, (w * 0.9f).toInt(), Layout.Alignment.ALIGN_CENTER)
            c.save(); c.translate(w * 0.05f, top + s + h * 0.03f); ll.draw(c); c.restore()
        } else {
            val r = placeRect(style.size, style.position, w, h, 0.82f)
            p.color = style.bgColor
            c.drawRoundRect(r, 24f, 24f, p)
            val pad = r.width() * 0.08f
            t.textSize = r.width() * 0.08f
            val tl = StageView.layout(style.title, t, (r.width() - pad * 2).toInt(), Layout.Alignment.ALIGN_CENTER, 2)
            c.save(); c.translate(r.left + pad, r.top + pad * 0.6f); tl.draw(c); c.restore()
            val s = r.width() - pad * 2
            val qt = r.top + pad * 0.6f + tl.height + 6
            c.drawBitmap(qr, null, RectF(r.left + pad, qt, r.left + pad + s, qt + s), null)
            t.textSize = r.width() * 0.06f
            val ll = StageView.layout(link.removePrefix("https://"), t, (r.width() - pad).toInt(), Layout.Alignment.ALIGN_CENTER, 1)
            c.save(); c.translate(r.left + pad / 2, minOf(qt + s + 4, r.bottom - ll.height - 4)); ll.draw(c); c.restore()
        }
    }

    companion object {
        fun makeQr(text: String, size: Int, fg: Int, bg: Int): Bitmap {
            val m = QRCodeWriter().encode(text.ifBlank { " " }, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val px = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m.get(x, y)) fg else bg
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}

/** Spanduk pengumuman di bagian atas layar. */
class BannerItem(private val text: String) : StageView.Item() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
        t.textSize = w * 0.045f
        val l = StageView.layout(text, t, (w * 0.82f).toInt(), Layout.Alignment.ALIGN_CENTER, 4)
        val top = h * 0.16f
        p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), 0f, 0xEE8B5CFF.toInt(), 0xEEFF3DA8.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(w * 0.05f, top - 20, w * 0.95f, top + l.height + 20), 30f, 30f, p)
        p.shader = null
        c.save(); c.translate(w * 0.09f, top); l.draw(c); c.restore()
    }
}

/** Layar pembuka/penutup sinematik dari template. */
class CinematicItem(
    private val template: String,
    private val title: String,
    private val subtitle: String,
    private val totalMs: Long,
    private val countdown: Boolean,
) : StageView.Item(fullScreen = true) {
    private val painter = Cinematics(template)
    override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
        painter.draw(c, w, h, elapsedMs, title, subtitle, if (countdown) ((totalMs - elapsedMs) / 1000 + 1).toInt().coerceAtLeast(0) else -1)
    }
}
