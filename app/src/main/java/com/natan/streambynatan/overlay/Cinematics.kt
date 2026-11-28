package com.natan.streambynatan.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Template pembuka & penutup sinematik buatan aplikasi sendiri:
 * animasi teks, efek cahaya, partikel, dan transisi. Tinggal pilih template dan isi judul.
 */
class Cinematics(private val template: String) {

    /** Animasi Lottie (efek cahaya & partikel) untuk tiap template. */
    private val lottie: com.airbnb.lottie.LottieDrawable? = runCatching {
        val file = when (template) {
            "ibadah", "paskah" -> "light_rays"
            "megah", "klasik" -> "light_sweep"
            "natal", "malam" -> "sparkles"
            "modern" -> "rings"
            else -> "glow_pulse"
        }
        val comp = com.airbnb.lottie.LottieCompositionFactory.fromAssetSync(com.natan.streambynatan.core.Graph.app, "lottie/$file.json").value
        comp?.let { c -> com.airbnb.lottie.LottieDrawable().apply { composition = c } }
    }.getOrNull()
    private val lottieExtra: com.airbnb.lottie.LottieDrawable? = runCatching {
        if (template !in setOf("megah", "ibadah", "klasik")) return@runCatching null
        val c = com.airbnb.lottie.LottieCompositionFactory.fromAssetSync(com.natan.streambynatan.core.Graph.app, "lottie/sparkles.json").value
        c?.let { com.airbnb.lottie.LottieDrawable().apply { composition = it } }
    }.getOrNull()

    private fun drawLottie(d: com.airbnb.lottie.LottieDrawable?, c: Canvas, w: Int, h: Int, ms: Long) {
        val l = d ?: return
        val comp = l.composition ?: return
        val dur = comp.duration.toLong().coerceAtLeast(1)
        l.progress = (ms % dur) / dur.toFloat()
        l.setBounds(0, 0, w, h)
        l.draw(c)
    }

    companion object {
        val templates: List<Pair<String, String>> = listOf(
            "ibadah" to "Ibadah",
            "santai" to "Santai",
            "megah" to "Megah",
            "natal" to "Natal",
            "paskah" to "Paskah",
            "malam" to "Malam (gelap & bintang)",
            "alam" to "Alam (langit & matahari)",
            "modern" to "Modern (garis bersih)",
            "klasik" to "Klasik (elegan & emas)",
        )
    }

    private data class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

    private val rnd = Random(template.hashCode())
    private val particles = List(90) { Particle(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat() * 2.5f, 0.3f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL) }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private data class Palette(val top: Int, val bottom: Int, val accent: Int, val text: Int, val serif: Boolean)

    private val palette = when (template) {
        "ibadah" -> Palette(0xFF1B1035.toInt(), 0xFF3A1F0B.toInt(), 0xFFFFC86B.toInt(), Color.WHITE, true)
        "santai" -> Palette(0xFF2B2D6E.toInt(), 0xFFE0709A.toInt(), 0xFFFFE3F1.toInt(), Color.WHITE, false)
        "megah" -> Palette(0xFF07070C.toInt(), 0xFF2A1A05.toInt(), 0xFFFFD36E.toInt(), 0xFFFFF4D6.toInt(), true)
        "natal" -> Palette(0xFF0A2A1C.toInt(), 0xFF3B0710.toInt(), 0xFFFFE08A.toInt(), Color.WHITE, true)
        "paskah" -> Palette(0xFF3B2A6B.toInt(), 0xFFFFB37A.toInt(), 0xFFFFF1C2.toInt(), Color.WHITE, true)
        "malam" -> Palette(0xFF02030A.toInt(), 0xFF0C1438.toInt(), 0xFFBFD4FF.toInt(), Color.WHITE, false)
        "alam" -> Palette(0xFF3F8EDB.toInt(), 0xFFFFD9A0.toInt(), 0xFFFFF7C9.toInt(), Color.WHITE, false)
        "modern" -> Palette(0xFF060914.toInt(), 0xFF101A3A.toInt(), 0xFF22E5FF.toInt(), Color.WHITE, false)
        else -> Palette(0xFF14100A.toInt(), 0xFF2C2112.toInt(), 0xFFD4AF37.toInt(), 0xFFF7EBD0.toInt(), true) // klasik
    }

    fun draw(c: Canvas, w: Int, h: Int, ms: Long, title: String, subtitle: String, countdown: Int) {
        val t = ms / 1000f
        val wf = w.toFloat(); val hf = h.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, hf, palette.top, palette.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        // Video latar bergerak (diunduh GitHub Actions dari Pexels/Pixabay), kalau ada
        CinematicVideos.frame(template, ms)?.let { f ->
            val sc = maxOf(wf / f.width, hf / f.height)
            val dw = f.width * sc; val dh = f.height * sc
            c.drawBitmap(f, null, RectF((wf - dw) / 2, (hf - dh) / 2, (wf + dw) / 2, (hf + dh) / 2), null)
            p.color = withAlpha(palette.top, 140)
            c.drawRect(0f, 0f, wf, hf, p)
        }
        drawLottie(lottie, c, w, h, ms)
        drawLottie(lottieExtra, c, w, h, ms)
        when (template) {
            "ibadah", "paskah" -> rays(c, w, h, t, if (template == "paskah") hf * 0.75f else hf * 0.3f)
            "santai" -> bokeh(c, w, h, t)
            "megah" -> { sweep(c, w, h, t); sparks(c, w, h, t, rising = true) }
            "natal" -> { snow(c, w, h, t); star(c, wf / 2, hf * 0.2f, wf * 0.06f, t) }
            "malam" -> { stars(c, w, h, t); moon(c, w, h) }
            "alam" -> { sun(c, w, h, t); clouds(c, w, h, t) }
            "modern" -> grid(c, w, h, t)
            else -> frame(c, w, h, t)
        }

        // Animasi teks: memudar + membesar + jarak huruf menyempit
        val appear = min(1f, max(0f, (t - 0.4f) / 1.4f))
        val e = 1f - (1f - appear) * (1f - appear) * (1f - appear)
        titlePaint.typeface = Typeface.create(if (palette.serif) Typeface.SERIF else Typeface.SANS_SERIF, Typeface.BOLD)
        titlePaint.color = palette.text
        titlePaint.alpha = (255 * e).toInt()
        titlePaint.letterSpacing = 0.35f * (1f - e) + 0.04f
        titlePaint.setShadowLayer(30f, 0f, 0f, palette.accent)
        val tl = StageView.fitLayout(title, titlePaint, (wf * 0.86f).toInt(), (hf * 0.3f).toInt(), wf * 0.09f, wf * 0.04f)
        val scale = 0.85f + 0.15f * e
        val top = hf * 0.42f - tl.height / 2f
        c.save(); c.scale(scale, scale, wf / 2, hf * 0.45f); c.translate(wf * 0.07f, top); tl.draw(c); c.restore()

        // garis cahaya di bawah judul
        p.color = palette.accent; p.alpha = (255 * e).toInt()
        val lineW = wf * 0.4f * e
        c.drawRect(wf / 2 - lineW / 2, top + tl.height + hf * 0.025f, wf / 2 + lineW / 2, top + tl.height + hf * 0.025f + 4f, p)

        if (subtitle.isNotBlank()) {
            val sAppear = min(1f, max(0f, (t - 1.4f) / 1.2f))
            subPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            subPaint.textSize = minOf(wf, hf) * 0.045f
            subPaint.color = palette.text; subPaint.alpha = (255 * sAppear).toInt()
            val sl = StageView.layout(subtitle, subPaint, (wf * 0.8f).toInt(), Layout.Alignment.ALIGN_CENTER, 3)
            c.save(); c.translate(wf * 0.1f, top + tl.height + hf * 0.05f + (1 - sAppear) * 30f); sl.draw(c); c.restore()
        }
        if (countdown >= 0) {
            subPaint.textSize = minOf(wf, hf) * 0.12f
            subPaint.color = palette.accent; subPaint.alpha = 220
            subPaint.typeface = Typeface.DEFAULT_BOLD
            val s = countdown.toString()
            c.drawText(s, wf / 2 - subPaint.measureText(s) / 2, hf * 0.85f, subPaint)
        }
        // vignette
        p.shader = RadialGradient(wf / 2, hf / 2, max(wf, hf) * 0.75f, Color.TRANSPARENT, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)
        p.shader = null
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun rays(c: Canvas, w: Int, h: Int, t: Float, cy: Float) {
        val cx = w / 2f
        for (i in 0 until 14) {
            val ang = (i / 14f) * 2 * PI.toFloat() + t * 0.08f
            val path = Path()
            path.moveTo(cx, cy)
            path.lineTo(cx + cos(ang - 0.06f) * h, cy + sin(ang - 0.06f) * h)
            path.lineTo(cx + cos(ang + 0.06f) * h, cy + sin(ang + 0.06f) * h)
            path.close()
            p.color = withAlpha(palette.accent, 26 + (14 * sin(t * 1.5f + i)).toInt())
            c.drawPath(path, p)
        }
        glow.color = withAlpha(palette.accent, 170)
        c.drawCircle(cx, cy, w * 0.16f, glow)
    }

    private fun bokeh(c: Canvas, w: Int, h: Int, t: Float) {
        for (pt in particles.take(30)) {
            val x = (pt.x * w + sin(t * pt.speed + pt.phase) * 40)
            val y = (pt.y * h + cos(t * pt.speed * 0.7f + pt.phase) * 40)
            p.color = withAlpha(palette.accent, 40)
            c.drawCircle(x, y, pt.r * w * 0.03f, p)
        }
    }

    private fun sweep(c: Canvas, w: Int, h: Int, t: Float) {
        val x = ((t * 0.35f) % 1.4f - 0.2f) * w
        p.shader = LinearGradient(x - w * 0.3f, 0f, x + w * 0.3f, h.toFloat(), Color.TRANSPARENT, withAlpha(palette.accent, 90), Shader.TileMode.MIRROR)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
    }

    private fun sparks(c: Canvas, w: Int, h: Int, t: Float, rising: Boolean) {
        for (pt in particles) {
            val prog = ((pt.y + t * 0.05f * pt.speed) % 1f)
            val y = if (rising) h * (1f - prog) else h * prog
            val x = pt.x * w + sin(t + pt.phase) * 12
            p.color = withAlpha(palette.accent, (200 * sin(prog * PI.toFloat())).toInt())
            c.drawCircle(x, y, pt.r * 1.6f, p)
        }
    }

    private fun snow(c: Canvas, w: Int, h: Int, t: Float) {
        for (pt in particles) {
            val y = ((pt.y + t * 0.06f * pt.speed) % 1f) * h
            val x = pt.x * w + sin(t * pt.speed + pt.phase) * 20
            p.color = withAlpha(Color.WHITE, 200)
            c.drawCircle(x, y, pt.r * 2.2f, p)
        }
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val path = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.42f
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            val x = cx + cos(a) * rr; val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        glow.color = withAlpha(palette.accent, (160 + 60 * sin(t * 2)).toInt())
        c.drawCircle(cx, cy, r * 1.4f, glow)
        p.color = palette.accent
        c.drawPath(path, p)
    }

    private fun stars(c: Canvas, w: Int, h: Int, t: Float) {
        for (pt in particles) {
            val a = (120 + 120 * sin(t * 2 * pt.speed + pt.phase)).toInt()
            p.color = withAlpha(Color.WHITE, a)
            c.drawCircle(pt.x * w, pt.y * h * 0.8f, pt.r, p)
        }
    }

    private fun moon(c: Canvas, w: Int, h: Int) {
        glow.color = withAlpha(palette.accent, 120)
        c.drawCircle(w * 0.78f, h * 0.16f, w * 0.1f, glow)
        p.color = withAlpha(0xFFF4F6FF.toInt(), 255)
        c.drawCircle(w * 0.78f, h * 0.16f, w * 0.07f, p)
    }

    private fun sun(c: Canvas, w: Int, h: Int, t: Float) {
        val cy = h * (0.75f - 0.05f * min(1f, t / 6f))
        glow.color = withAlpha(0xFFFFE08A.toInt(), 200)
        c.drawCircle(w / 2f, cy, w * 0.22f, glow)
        p.color = 0xFFFFF2C2.toInt()
        c.drawCircle(w / 2f, cy, w * 0.12f, p)
    }

    private fun clouds(c: Canvas, w: Int, h: Int, t: Float) {
        p.color = withAlpha(Color.WHITE, 150)
        for (i in 0 until 5) {
            val x = ((i * 0.27f + t * 0.015f * (i + 1)) % 1.3f - 0.15f) * w
            val y = h * (0.12f + i * 0.09f)
            c.drawOval(RectF(x, y, x + w * 0.35f, y + h * 0.045f), p)
            c.drawOval(RectF(x + w * 0.08f, y - h * 0.025f, x + w * 0.25f, y + h * 0.035f), p)
        }
    }

    private fun grid(c: Canvas, w: Int, h: Int, t: Float) {
        p.strokeWidth = 2f
        val off = (t * 40) % 60
        for (i in -1..(h / 60 + 1)) {
            p.color = withAlpha(palette.accent, 35)
            val y = i * 60 + off
            c.drawLine(0f, y, w.toFloat(), y, p)
        }
        for (i in 0..(w / 60)) {
            p.color = withAlpha(0xFF8B5CFF.toInt(), 30)
            c.drawLine(i * 60f, 0f, i * 60f, h.toFloat(), p)
        }
        p.color = palette.accent; p.strokeWidth = 5f
        val len = w * 0.5f * min(1f, t / 1.5f)
        c.drawLine(w * 0.06f, h * 0.2f, w * 0.06f + len, h * 0.2f, p)
        c.drawLine(w * 0.94f - len, h * 0.8f, w * 0.94f, h * 0.8f, p)
    }

    private fun frame(c: Canvas, w: Int, h: Int, t: Float) {
        val inset = w * 0.06f
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f
        p.color = withAlpha(palette.accent, (255 * min(1f, t / 1.2f)).toInt())
        c.drawRect(inset, inset * 2, w - inset, h - inset * 2, p)
        p.strokeWidth = 1.5f
        c.drawRect(inset * 1.4f, inset * 2.4f, w - inset * 1.4f, h - inset * 2.4f, p)
        p.style = Paint.Style.FILL
        for ((x, y) in listOf(inset to inset * 2, w - inset to inset * 2, inset to h - inset * 2, w - inset to h - inset * 2)) {
            c.drawCircle(x, y, 10f, p)
        }
        sparks(c, w, h, t, rising = true)
    }
}

/**
 * Video latar sinematik dari res/raw (cine_<template>.mp4). Bingkainya diambil sekali di latar belakang,
 * lalu diputar berulang. Kalau videonya tidak ada, template tetap memakai animasi buatan aplikasi.
 */
object CinematicVideos {
    private val frames = java.util.concurrent.ConcurrentHashMap<String, List<android.graphics.Bitmap>>()
    private val loading = java.util.Collections.synchronizedSet(HashSet<String>())

    @android.annotation.SuppressLint("DiscouragedApi")
    fun frame(template: String, ms: Long): android.graphics.Bitmap? {
        frames[template]?.let { list -> return if (list.isEmpty()) null else list[((ms / 83) % list.size).toInt()] }
        if (loading.add(template)) Thread {
            val ctx = com.natan.streambynatan.core.Graph.app
            val id = ctx.resources.getIdentifier("cine_$template", "raw", ctx.packageName)
            val list = if (id == 0) emptyList() else runCatching {
                val r = android.media.MediaMetadataRetriever()
                ctx.resources.openRawResourceFd(id).use { r.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                val dur = (r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 8000).coerceAtMost(10_000)
                val out = (0 until 72).mapNotNull { i ->
                    r.getFrameAtTime(dur * 1000 * i / 72, android.media.MediaMetadataRetriever.OPTION_CLOSEST)?.let { b ->
                        val sc = 480f / maxOf(b.width, b.height)
                        if (sc < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
                    }
                }
                r.release(); out
            }.getOrDefault(emptyList())
            frames[template] = list
        }.start()
        return null
    }
}
