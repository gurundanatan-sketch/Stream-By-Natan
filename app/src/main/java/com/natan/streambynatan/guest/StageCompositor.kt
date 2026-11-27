package com.natan.streambynatan.guest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Menyusun layar host + tamu di siaran:
 * - Tidak ada yang bicara, atau 2 orang lebih bicara bersamaan -> ukuran layar semua SAMA.
 * - Hanya satu orang yang bicara -> layar orang itu otomatis PENUH LAYAR.
 * Ada jeda singkat supaya layar tidak berkedip saat orang berhenti sebentar.
 */
class StageCompositor(private val width: Int = 720, private val height: Int = 1280) {
    private data class Person(val id: String, val name: String, val frame: Bitmap?, val speaking: Boolean, val micOn: Boolean, val screen: Boolean)

    @Volatile var hostName = "Host"
    @Volatile var hostLevel = 0f
        set(v) { field = v; if (v > 0.08f) hostSpokeAt = System.currentTimeMillis() }
    @Volatile var hostMicOn = true
    private var hostSpokeAt = 0L
    private var focusId: String? = null
    private var focusSince = 0L
    private val buffers = arrayOf(
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
    )
    private var bufIndex = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = 30f }

    /** Menggambar satu bingkai gabungan. Mengembalikan bitmap untuk dikirim ke siaran. */
    fun render(hostFrame: Bitmap?, guests: List<GuestManager.Remote>): Bitmap {
        val now = System.currentTimeMillis()
        val people = buildList {
            add(Person("host", hostName, hostFrame, now - hostSpokeAt < 700 && hostMicOn, hostMicOn, false))
            guests.filter { it.status == "onstage" }.forEach {
                add(Person(it.id, it.name, it.frame, now - it.lastSpokeAt < 700 && it.micOn, it.micOn, it.sharingScreen))
            }
        }
        val speaking = people.filter { it.speaking }
        val wanted: String? = when {
            people.size == 1 -> "host"
            speaking.size == 1 -> speaking.first().id
            else -> null // semua sama besar
        }
        // jeda 1,2 detik sebelum berganti tata letak
        if (wanted != focusId && now - focusSince > 1200) { focusId = wanted; focusSince = now }
        val out = buffers[bufIndex]; bufIndex = 1 - bufIndex
        val c = Canvas(out)
        c.drawColor(0xFF070A14.toInt())
        val focus = people.firstOrNull { it.id == focusId }
        if (focus != null) {
            drawTile(c, focus, RectF(0f, 0f, width.toFloat(), height.toFloat()), fill = !focus.screen, showLabel = people.size > 1)
        } else {
            val n = people.size
            val cols = if (n <= 2) 1 else 2
            val rows = ceil(n / cols.toFloat()).toInt()
            val tw = width / cols.toFloat(); val th = height / rows.toFloat()
            people.forEachIndexed { i, p ->
                val col = i % cols; val row = i / cols
                // baris terakhir yang ganjil diletakkan di tengah
                val offset = if (row == rows - 1 && n % cols != 0) (width - tw * (n % cols)) / 2f else 0f
                drawTile(c, p, RectF(offset + col * tw + 3, row * th + 3, offset + (col + 1) * tw - 3, (row + 1) * th - 3), fill = !p.screen, showLabel = true)
            }
        }
        return out
    }

    private fun drawTile(c: Canvas, p: Person, r: RectF, fill: Boolean, showLabel: Boolean) {
        val b = p.frame
        c.save()
        c.clipRect(r)
        if (b != null && !b.isRecycled) {
            val s = if (fill) max(r.width() / b.width, r.height() / b.height) else minOf(r.width() / b.width, r.height() / b.height)
            val dw = b.width * s; val dh = b.height * s
            c.drawBitmap(b, null, RectF(r.centerX() - dw / 2, r.centerY() - dh / 2, r.centerX() + dw / 2, r.centerY() + dh / 2), paint)
        } else {
            paint.color = 0xFF151C36.toInt(); c.drawRect(r, paint)
            label.textSize = r.width() * 0.12f
            val initial = p.name.take(1).uppercase()
            c.drawText(initial, r.centerX() - label.measureText(initial) / 2, r.centerY() + label.textSize / 3, label)
        }
        if (p.speaking) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = 0xFF22E5FF.toInt()
            c.drawRect(r, paint); paint.style = Paint.Style.FILL
        }
        if (showLabel) {
            label.textSize = max(22f, sqrt(r.width() * r.height()) * 0.045f)
            val text = p.name + if (!p.micOn) "  (mic mati)" else ""
            val tw = label.measureText(text)
            paint.color = 0xAA000000.toInt()
            c.drawRoundRect(RectF(r.left + 12, r.bottom - label.textSize * 1.8f, r.left + 36 + tw, r.bottom - 12), 14f, 14f, paint)
            c.drawText(text, r.left + 24, r.bottom - label.textSize * 0.6f - 8, label)
        }
        c.restore()
    }
}
