package com.natan.streambynatan.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEngine
import com.natan.streambynatan.ui.MainActivity

/**
 * Jendela mengambang saat berbagi layar. Layar yang dibagikan tetap penuh;
 * yang mengambang hanya tombol kecil di tepi layar, semuanya berlabel untuk pembaca layar:
 * Bagikan audio, Mikrofon, Hentikan berbagi, plus panel tambahan (antrean pertanyaan, efek suara, dll).
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingControls(private val context: Context, private val engine: LiveEngine, private val owner: LifecycleOwner) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var panel: View? = null
    private var bubble: View? = null
    private var shareBtn: Button? = null
    private var micBtn: Button? = null

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    private fun params(w: Int = WindowManager.LayoutParams.WRAP_CONTENT, h: Int = WindowManager.LayoutParams.WRAP_CONTENT) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun button(label: String, desc: String = label, onClick: (Button) -> Unit): Button = Button(context).apply {
        text = label
        contentDescription = desc
        isAllCaps = false
        textSize = 13f
        minHeight = dp(52); minWidth = dp(52)
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xE60B1022.toInt()); setStroke(dp(2), 0xFF22E5FF.toInt()) }
        setOnClickListener { onClick(this) }
    }

    fun show() {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        shareBtn = button("Audio", "Bagikan audio, menyala") { b ->
            val on = !LiveBus.stats.value.shareAudioOn
            engine.setShareAudio(on)
            b.contentDescription = "Bagikan audio, ${if (on) "menyala" else "mati"}"
            b.alpha = if (on) 1f else 0.5f
            announce(b, if (on) "Bagikan audio menyala" else "Bagikan audio mati")
        }
        micBtn = button("Mic", "Mikrofon, menyala") { b ->
            val on = !LiveBus.stats.value.micOn
            engine.setMic(on)
            b.contentDescription = "Mikrofon, ${if (on) "menyala" else "mati"}"
            b.alpha = if (on) 1f else 0.5f
            announce(b, if (on) "Mikrofon menyala" else "Mikrofon mati")
        }
        val stopShare = button("Stop", "Hentikan berbagi, kembali ke aplikasi") {
            engine.stopScreenShare()
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        val more = button("☰", "Panel lainnya: pertanyaan, efek suara, kode QR") { togglePanel() }
        listOf(shareBtn!!, micBtn!!, stopShare, more).forEach {
            col.addView(it, LinearLayout.LayoutParams(dp(60), dp(56)).apply { bottomMargin = dp(6) })
        }
        val p = params().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 0; y = 0 }
        makeDraggable(col, p)
        runCatching { wm.addView(col, p) }
        root = col
    }

    private fun announce(v: View, text: String) {
        v.announceForAccessibility(text)
        v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    private fun togglePanel() {
        panel?.let { runCatching { wm.removeView(it) }; panel = null; return }
        val brain = engine.brain
        val s = Graph.settings.current
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        fun row(vararg buttons: Button) {
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.forEach { r.addView(it, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(4) }) }
            list.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        }
        row(button("Pertanyaan berikutnya") { brain.nextQuestion() }, button("Ulangi") { brain.repeatQuestion() })
        row(button("Tahan") { brain.toggleHold() }, button("Lewati") { brain.skipQuestion() })
        row(button("Ringkasan komentar") { brain.missedSummary() }, button("Bacakan doa") { brain.readNextPrayer() })
        row(button("Kode QR") { brain.showQr(true) }, button("Tutup QR") { brain.showQr(false) })
        row(button("Persembahan") { brain.showOffering(true) }, button("Tampilkan jawaban") { brain.showFaqAnswer() })
        row(button("Pendukung teratas") { brain.topSupporters() }, button("Facecam") { toggleFacecam() })
        // Pintasan efek suara
        val sfxRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = Graph.sounds.effects.associate { it.id to it.label } + s.customSounds.associate { it.id to it.name }
        s.floatingSfx.forEach { id ->
            sfxRow.addView(button(labels[id] ?: id, "Efek suara ${labels[id] ?: id}") { Graph.sounds.play(id, s.giftSfx.volume, s.customSounds) },
                LinearLayout.LayoutParams(dp(110), dp(56)).apply { marginEnd = dp(4) })
        }
        list.addView(HorizontalScrollView(context).apply { addView(sfxRow) })
        list.addView(button("Tutup panel") { togglePanel() })
        val scroll = ScrollView(context).apply {
            addView(list)
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xF0070A14.toInt()); setStroke(dp(2), 0xFF8B5CFF.toInt()) }
        }
        val p = params(dp(300), dp(420)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; x = dp(8) }
        runCatching { wm.addView(scroll, p) }
        panel = scroll
    }

    /** Facecam di gelembung mengambang (ikut terlihat di layar yang dibagikan). */
    private fun toggleFacecam() {
        bubble?.let { runCatching { wm.removeView(it) }; bubble = null; ProcessCameraProvider.getInstance(context).get().unbindAll(); return }
        val pv = PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            contentDescription = "Facecam"
            clipToOutline = true
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF000000.toInt()) }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        val size = dp(130)
        val p = params(size, size).apply { gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(80) }
        makeDraggable(pv, p)
        runCatching { wm.addView(pv, p) }
        bubble = pv
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
            runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun makeDraggable(v: View, p: WindowManager.LayoutParams) {
        var sx = 0f; var sy = 0f; var px = 0; var py = 0; var moved = false
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = p.x; py = p.y; moved = false; false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                    if (moved) {
                        p.x = if (p.gravity and Gravity.END == Gravity.END) px - dx else px + dx
                        p.y = py + dy
                        runCatching { wm.updateViewLayout(view, p) }
                    }
                    moved
                }
                else -> moved
            }
        }
    }

    fun hide() {
        listOfNotNull(root, panel, bubble).forEach { runCatching { wm.removeView(it) } }
        root = null; panel = null
        if (bubble != null) runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        bubble = null
    }
}
