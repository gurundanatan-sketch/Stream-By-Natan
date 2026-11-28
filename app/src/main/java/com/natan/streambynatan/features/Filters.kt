package com.natan.streambynatan.features

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * Filter video untuk host dan tamu. Semuanya diproses langsung di HP (tanpa API key, tanpa internet).
 *
 * - Host: filter GPU milik RootEncoder (dibuat lewat nama kelas, jadi aman kalau ada yang tidak tersedia),
 *   ditambah penyesuaian dasar (kecerahan, kontras, saturasi) dan kulit halus.
 * - Tamu: matriks warna yang diterapkan saat video tamu digambar ke siaran (juga di pratinjau browser).
 *
 * Menambah filter baru cukup dengan menambah baris di [presets].
 */
object Filters {
    data class Preset(
        val id: String,
        val label: String,
        /** Kelas filter GPU RootEncoder (opsional, untuk host). */
        val gpu: List<String> = emptyList(),
        /** Matriks warna (untuk tamu, dan cadangan host). */
        val matrix: ColorMatrix? = null,
        /** CSS filter untuk pratinjau tamu di browser. */
        val css: String = "none",
    )

    private fun sat(v: Float) = ColorMatrix().apply { setSaturation(v) }
    private fun scale(r: Float, g: Float, b: Float, offR: Float = 0f, offG: Float = 0f, offB: Float = 0f) = ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, offR,
        0f, g, 0f, 0f, offG,
        0f, 0f, b, 0f, offB,
        0f, 0f, 0f, 1f, 0f,
    ))
    private fun contrast(c: Float, bright: Float = 0f): ColorMatrix {
        val t = (1f - c) * 128f + bright
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }
    private fun chain(vararg m: ColorMatrix) = ColorMatrix().apply { m.forEach { postConcat(it) } }
    private val sepia = ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    private val invert = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))

    val presets: List<Preset> = listOf(
        Preset("normal", "Normal", matrix = null, css = "none"),
        Preset("cerah", "Cerah", listOf("BrightnessFilterRender"), contrast(1.05f, 18f), "brightness(1.12) contrast(1.05)"),
        Preset("hangat", "Hangat", listOf("TemperatureFilterRender"), scale(1.1f, 1.0f, 0.85f, 8f, 4f, 0f), "sepia(0.2) saturate(1.2)"),
        Preset("sejuk", "Sejuk", listOf("TemperatureFilterRender"), scale(0.9f, 1.0f, 1.15f, 0f, 2f, 10f), "hue-rotate(-10deg) saturate(1.1)"),
        Preset("sinematik", "Sinematik", listOf("ContrastFilterRender", "SaturationFilterRender"), chain(contrast(1.18f), sat(0.85f), scale(1.0f, 1.02f, 1.06f)), "contrast(1.18) saturate(0.85)"),
        Preset("vintage", "Vintage", listOf("Image70sFilterRender"), chain(sepia, contrast(0.92f, 10f)), "sepia(0.55) contrast(0.92)"),
        Preset("hitamputih", "Hitam putih", listOf("GreyScaleFilterRender"), sat(0f), "grayscale(1)"),
        Preset("sepia", "Sepia", listOf("SepiaFilterRender"), sepia, "sepia(1)"),
        Preset("cerah_warna", "Warna cerah", listOf("SaturationFilterRender"), sat(1.45f), "saturate(1.45)"),
        Preset("pastel", "Pastel", emptyList(), chain(sat(0.7f), contrast(0.88f, 22f)), "saturate(0.7) contrast(0.88) brightness(1.08)"),
        Preset("dramatis", "Dramatis", listOf("ContrastFilterRender"), chain(contrast(1.35f, -10f), sat(1.1f)), "contrast(1.35) saturate(1.1)"),
        Preset("pudar", "Pudar", emptyList(), chain(contrast(0.8f, 20f), sat(0.75f)), "contrast(0.8) saturate(0.75) brightness(1.06)"),
        Preset("senja", "Senja", listOf("TemperatureFilterRender"), scale(1.15f, 0.95f, 0.8f, 12f, 0f, 0f), "sepia(0.35) saturate(1.3) hue-rotate(-12deg)"),
        Preset("malam", "Malam", emptyList(), chain(scale(0.8f, 0.9f, 1.15f), contrast(1.1f, -12f)), "brightness(0.85) hue-rotate(15deg)"),
        Preset("retro", "Retro", listOf("LamoishFilterRender"), chain(scale(1.08f, 1.0f, 0.82f, 6f, 6f, 0f), contrast(0.95f)), "sepia(0.3) contrast(0.95) saturate(1.2)"),
        Preset("klasik", "Klasik", listOf("EarlyBirdFilterRender"), chain(sepia, sat(1.4f)), "sepia(0.4) saturate(1.3)"),
        Preset("emas", "Emas", listOf("MoneyFilterRender"), scale(1.15f, 1.05f, 0.75f, 10f, 6f, 0f), "sepia(0.45) saturate(1.5)"),
        Preset("negatif", "Negatif", listOf("NegativeFilterRender"), invert, "invert(1)"),
        Preset("kartun", "Kartun", listOf("CartoonFilterRender"), chain(sat(1.6f), contrast(1.25f)), "saturate(1.6) contrast(1.25)"),
        Preset("tajam", "Tajam", listOf("SharpnessFilterRender"), contrast(1.12f), "contrast(1.12)"),
        Preset("duotone", "Duotone", listOf("DuotoneFilterRender"), chain(sat(0f), scale(1.0f, 0.75f, 1.2f)), "grayscale(1) sepia(0.4) hue-rotate(200deg)"),
    )

    fun byId(id: String): Preset = presets.firstOrNull { it.id == id } ?: presets.first()

    /** ColorFilter untuk menggambar tamu dengan filter pilihannya. */
    fun colorFilter(id: String): ColorMatrixColorFilter? = byId(id).matrix?.let { ColorMatrixColorFilter(it) }

    /** Matriks penyesuaian dasar (kecerahan, kontras, saturasi). */
    fun adjust(brightness: Float, contrast: Float, saturation: Float): ColorMatrix =
        chain(sat(saturation), contrast(contrast, (brightness - 1f) * 120f))

    /** Membuat filter GPU RootEncoder berdasarkan nama kelas (null kalau tidak tersedia di versi library ini). */
    fun gpu(className: String): Any? = runCatching {
        Class.forName("com.pedro.encoder.input.gl.render.filters.$className").getDeclaredConstructor().newInstance()
    }.getOrNull()

    /** Memanggil setter float (misal setBrightness) lewat refleksi, aman kalau tidak ada. */
    fun setFloat(target: Any, method: String, value: Float) {
        runCatching { target.javaClass.getMethod(method, java.lang.Float.TYPE).invoke(target, value) }
    }
}
