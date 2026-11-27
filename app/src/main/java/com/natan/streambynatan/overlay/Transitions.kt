package com.natan.streambynatan.overlay

import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Gaya transisi saat tampilan muncul/hilang. Semua bisa dipilih di pengaturan. */
object Transitions {
    val styles: List<Pair<String, String>> = listOf(
        "fade" to "Memudar",
        "slide_left" to "Bergeser dari kiri",
        "slide_right" to "Bergeser dari kanan",
        "slide_up" to "Bergeser dari bawah",
        "slide_down" to "Bergeser dari atas",
        "zoom_in" to "Membesar",
        "zoom_out" to "Mengecil",
        "rotate" to "Berputar",
        "curtain" to "Tirai terbuka",
        "bounce" to "Memantul",
        "flip" to "Membalik",
        "blinds" to "Kerai",
        "swing" to "Berayun",
        "pop" to "Meletup",
        "none" to "Tanpa transisi",
    )

    fun label(id: String) = styles.firstOrNull { it.first == id }?.second ?: id

    fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x).pow(3)
    }

    private fun bounce(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - abs(cos(x * PI * 2.5).toFloat()) * (1f - x)
    }

    fun alphaFor(style: String, p: Float): Float = when (style) {
        "none" -> if (p > 0.01f) 1f else 0f
        "curtain", "blinds" -> if (p > 0.01f) 1f else 0f
        else -> p.coerceIn(0f, 1f)
    }

    /** Mengubah posisi/ukuran kanvas sesuai kemajuan transisi [p] (0 = tersembunyi, 1 = tampil penuh). */
    fun apply(c: Canvas, style: String, p: Float, w: Int, h: Int) {
        val q = 1f - p
        val cx = w / 2f; val cy = h / 2f
        when (style) {
            "slide_left" -> c.translate(-w * q, 0f)
            "slide_right" -> c.translate(w * q, 0f)
            "slide_up" -> c.translate(0f, h * q)
            "slide_down" -> c.translate(0f, -h * q)
            "zoom_in" -> { val s = 0.3f + 0.7f * p; c.scale(s, s, cx, cy) }
            "zoom_out" -> { val s = 1.8f - 0.8f * p; c.scale(s, s, cx, cy) }
            "rotate" -> { c.rotate(-180f * q, cx, cy); val s = 0.5f + 0.5f * p; c.scale(s, s, cx, cy) }
            "curtain" -> { val half = w / 2f * p; c.clipRect(cx - half, 0f, cx + half, h.toFloat()) }
            "bounce" -> { val b = bounce(p); c.translate(0f, -h * 0.3f * (1f - b)); }
            "flip" -> c.scale(1f, maxOf(0.02f, p), cx, cy)
            "blinds" -> {
                val n = 8; val bh = h / n.toFloat()
                val path = android.graphics.Path()
                for (i in 0 until n) path.addRect(0f, i * bh, w.toFloat(), i * bh + bh * p, android.graphics.Path.Direction.CW)
                c.clipPath(path)
            }
            "swing" -> c.rotate(sin(q * PI.toFloat() * 3) * 25f * q, cx, 0f)
            "pop" -> { val s = if (p < 0.7f) p / 0.7f * 1.15f else 1.15f - (p - 0.7f) / 0.3f * 0.15f; c.scale(s, s, cx, cy) }
            else -> Unit
        }
    }
}
