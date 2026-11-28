package com.natan.streambynatan.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Palet futuristik: hitam angkasa, neon sian, ungu, dan merah muda. Bisa terang / kontras tinggi (menu Tampilan). */
object Neon {
    var Void = Color(0xFF070A14); private set
    var Deep = Color(0xFF0B1022); private set
    var Glass = Color(0x1AFFFFFF); private set
    var GlassStrong = Color(0x29FFFFFF); private set
    var Line = Color(0x33FFFFFF); private set
    var Cyan = Color(0xFF22E5FF); private set
    var Violet = Color(0xFF8B5CFF); private set
    var Pink = Color(0xFFFF3DA8); private set
    var Lime = Color(0xFF7CFFB2); private set
    var Amber = Color(0xFFFFC86B); private set
    var Red = Color(0xFFFF4D6D); private set
    var Text = Color(0xFFF2F5FF); private set
    var Muted = Color(0xFFA6B0CF); private set
    var isLight = false; private set
    var textScale = 1f; private set
    val brand get() = Brush.linearGradient(listOf(Cyan, Violet, Pink))
    val brandH get() = Brush.horizontalGradient(listOf(Cyan, Violet, Pink))
    val live get() = Brush.horizontalGradient(listOf(Pink, Red))

    /** Menerapkan pilihan Tampilan (dipanggil sebelum layar digambar). */
    fun apply(d: com.natan.streambynatan.data.DisplayConfig) {
        isLight = d.theme == "light"
        textScale = if (d.largeText) 1.22f else 1f
        if (isLight) {
            Void = Color(0xFFF4F6FF); Deep = Color(0xFFFFFFFF)
            Glass = Color(0x14000000); GlassStrong = Color(0x1F000000); Line = Color(0x33000000)
            Cyan = Color(0xFF006C80); Violet = Color(0xFF5B2FD6); Pink = Color(0xFFC2185B)
            Lime = Color(0xFF1B7F4B); Amber = Color(0xFF9A5B00); Red = Color(0xFFC62828)
            Text = Color(0xFF0B1022); Muted = Color(0xFF3F4766)
        } else {
            Void = Color(0xFF070A14); Deep = Color(0xFF0B1022)
            Glass = Color(0x1AFFFFFF); GlassStrong = Color(0x29FFFFFF); Line = Color(0x33FFFFFF)
            Cyan = Color(0xFF22E5FF); Violet = Color(0xFF8B5CFF); Pink = Color(0xFFFF3DA8)
            Lime = Color(0xFF7CFFB2); Amber = Color(0xFFFFC86B); Red = Color(0xFFFF4D6D)
            Text = Color(0xFFF2F5FF); Muted = Color(0xFFA6B0CF)
        }
        if (d.highContrast) {
            Muted = Text
            Line = if (isLight) Color(0xFF000000) else Color(0xFFFFFFFF)
            Glass = if (isLight) Color(0x22000000) else Color(0x33FFFFFF)
            GlassStrong = if (isLight) Color(0x33000000) else Color(0x44FFFFFF)
            if (!isLight) { Void = Color(0xFF000000); Deep = Color(0xFF000000) }
        }
    }
}

private val display = FontFamily.SansSerif

private fun type(k: Float) = Typography(
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = (34 * k).sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = (26 * k).sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (21 * k).sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (19 * k).sp),
    titleMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = (17 * k).sp),
    bodyLarge = TextStyle(fontFamily = display, fontSize = (17 * k).sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = display, fontSize = (15 * k).sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (16 * k).sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = (13 * k).sp, letterSpacing = 1.2.sp),
)

@Composable
fun StreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = type(Neon.textScale), content = content)
}

/** Latar aurora bergerak pelan (dekorasi, diabaikan pembaca layar). */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(modifier.fillMaxSize().background(Neon.Void)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (w < 2f || h < 2f) return@Canvas   // ukuran belum siap: jangan gambar (gradien radius 0 bikin error)
            fun blob(c: Color, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.32f), Color.Transparent), Offset(cx, cy), r.coerceAtLeast(1f)), r.coerceAtLeast(1f), Offset(cx, cy))
            blob(Neon.Violet, w * (0.2f + 0.12f * cos(t)), h * (0.18f + 0.05f * sin(t)), w * 0.8f)
            blob(Neon.Cyan, w * (0.85f + 0.1f * sin(t * 1.3f)), h * (0.42f + 0.06f * cos(t)), w * 0.7f)
            blob(Neon.Pink, w * (0.35f + 0.15f * sin(t * 0.7f)), h * (0.92f + 0.04f * cos(t * 1.1f)), w * 0.75f)
            // garis kisi tipis
            val step = 64f
            var y = 0f
            while (y < h) { drawLine(Color(0x0DFFFFFF), Offset(0f, y), Offset(w, y), 1f); y += step }
        }
        content()
    }
}
