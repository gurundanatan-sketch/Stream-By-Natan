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

/** Palet futuristik: hitam angkasa, neon sian, ungu, dan merah muda. */
object Neon {
    val Void = Color(0xFF070A14)
    val Deep = Color(0xFF0B1022)
    val Glass = Color(0x1AFFFFFF)
    val GlassStrong = Color(0x29FFFFFF)
    val Line = Color(0x33FFFFFF)
    val Cyan = Color(0xFF22E5FF)
    val Violet = Color(0xFF8B5CFF)
    val Pink = Color(0xFFFF3DA8)
    val Lime = Color(0xFF7CFFB2)
    val Amber = Color(0xFFFFC86B)
    val Red = Color(0xFFFF4D6D)
    val Text = Color(0xFFF2F5FF)
    val Muted = Color(0xFFA6B0CF)
    val brand = Brush.linearGradient(listOf(Cyan, Violet, Pink))
    val brandH = Brush.horizontalGradient(listOf(Cyan, Violet, Pink))
    val live = Brush.horizontalGradient(listOf(Pink, Red))
}

private val scheme = darkColorScheme(
    primary = Neon.Cyan, onPrimary = Neon.Void,
    secondary = Neon.Violet, onSecondary = Color.White,
    tertiary = Neon.Pink,
    background = Neon.Void, onBackground = Neon.Text,
    surface = Neon.Deep, onSurface = Neon.Text,
    surfaceVariant = Color(0xFF151C36), onSurfaceVariant = Neon.Muted,
    outline = Neon.Line, error = Neon.Red,
)

private val display = FontFamily.SansSerif

private val type = Typography(
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = display, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = display, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.2.sp),
)

@Composable
fun StreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
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
            fun blob(c: Color, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.32f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
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
