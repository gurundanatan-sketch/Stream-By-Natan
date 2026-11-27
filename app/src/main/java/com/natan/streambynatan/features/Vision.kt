package com.natan.streambynatan.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.natan.streambynatan.data.BackgroundConfig
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Pengganti latar belakang: tubuhmu dipisahkan dari latar (ML Kit Selfie Segmentation),
 * lalu latarnya diganti gambar, video, atau efek buram. Kamu tetap terlihat utuh di depan.
 */
class BackgroundReplacer(private val context: Context, @Volatile var replace: Boolean = true, private val onFrame: (Bitmap) -> Unit) {
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build()
    )
    private val exec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    @Volatile private var bgImage: Bitmap? = null
    private var bgFrames: List<Bitmap> = emptyList()
    private var cfg = BackgroundConfig()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    @Volatile var front = true

    fun start(owner: LifecycleOwner, config: BackgroundConfig) {
        cfg = config
        loadBackground()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get(); provider = p
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(720, 1280), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(exec) { proxy ->
                try {
                    val raw = proxy.toBitmap()
                    val m = Matrix().apply {
                        postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                        if (front) postScale(-1f, 1f)
                    }
                    val frame = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    onFrame(composite(frame))
                } catch (_: Exception) {
                } finally { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun loadBackground() {
        bgImage = null; bgFrames = emptyList()
        if (cfg.path.isBlank()) return
        runCatching {
            val uri = Uri.parse(cfg.path)
            if (cfg.type == "video") {
                val r = MediaMetadataRetriever(); r.setDataSource(context, uri)
                val dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000).coerceAtMost(12_000)
                bgFrames = (0 until 48).mapNotNull { i ->
                    r.getFrameAtTime(dur * 1000 * i / 48, MediaMetadataRetriever.OPTION_CLOSEST)?.let { f ->
                        val s = 720f / max(f.width, f.height)
                        if (s < 1f) Bitmap.createScaledBitmap(f, (f.width * s).toInt(), (f.height * s).toInt(), true) else f
                    }
                }
                r.release()
            } else if (cfg.type == "image") {
                context.contentResolver.openInputStream(uri)?.use { bgImage = BitmapFactory.decodeStream(it) }
            }
        }
    }

    private var frameIndex = 0

    private fun composite(frame: Bitmap): Bitmap {
        if (!replace) return frame
        val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(frame, 0)))
        val w = frame.width; val h = frame.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val bg: Bitmap? = when (cfg.type) {
            "video" -> bgFrames.takeIf { it.isNotEmpty() }?.let { it[(frameIndex++ / 2) % it.size] }
            "image" -> bgImage
            else -> null
        }
        if (bg != null) {
            val s = max(w / bg.width.toFloat(), h / bg.height.toFloat())
            val dw = bg.width * s; val dh = bg.height * s
            c.drawBitmap(bg, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
        } else {
            // efek buram: kecilkan lalu besarkan lagi
            val small = Bitmap.createScaledBitmap(frame, max(1, w / 24), max(1, h / 24), true)
            c.drawBitmap(Bitmap.createScaledBitmap(small, w, h, true), 0f, 0f, paint)
        }
        val mw = mask.width; val mh = mask.height
        val buf = mask.buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        val person = IntArray(w * h)
        frame.getPixels(person, 0, w, 0, 0, w, h)
        val sx = mw / w.toFloat(); val sy = mh / h.toFloat()
        for (y in 0 until h) {
            val my = (y * sy).toInt().coerceAtMost(mh - 1)
            for (x in 0 until w) {
                val mx = (x * sx).toInt().coerceAtMost(mw - 1)
                val conf = buf.get(my * mw + mx)
                val a = ((conf - 0.3f) / 0.4f).coerceIn(0f, 1f)
                val p = person[y * w + x]
                person[y * w + x] = Color.argb((a * 255).toInt(), Color.red(p), Color.green(p), Color.blue(p))
            }
        }
        val fg = Bitmap.createBitmap(person, w, h, Bitmap.Config.ARGB_8888)
        c.drawBitmap(fg, 0f, 0f, paint)
        return out
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
    }
}

/**
 * Asisten posisi kamera & penyesuai cahaya: memeriksa gambar siaran beberapa detik sekali,
 * lalu membisikkan saran yang hanya kamu dengar.
 */
class CameraAssistant(private val whisper: (String) -> Unit) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
    )
    private var lastAdvice = ""
    private var lastAt = 0L
    private var goodCount = 0
    var onBrightness: ((Float) -> Unit)? = null

    fun analyze(bmp: Bitmap, positionHelp: Boolean, lightHelp: Boolean) {
        val small = Bitmap.createScaledBitmap(bmp, 270, 480, true)
        val lum = luminance(small)
        if (lightHelp) onBrightness?.invoke(lum)
        detector.process(InputImage.fromBitmap(small, 0)).addOnSuccessListener { faces ->
            val advice = when {
                lightHelp && lum < 0.18f -> "Ruangan agak gelap, coba nyalakan lampu atau hadap ke cahaya."
                lightHelp && lum > 0.85f -> "Cahaya terlalu terang, coba geser sedikit dari sumber cahaya."
                !positionHelp -> ""
                faces.isEmpty() -> "Wajahmu tidak terlihat di kamera."
                else -> {
                    val f = faces.maxBy { it.boundingBox.width() }.boundingBox
                    val cx = f.centerX() / 270f; val top = f.top / 480f; val size = f.width() / 270f
                    when {
                        cx < 0.33f -> "Wajahmu terlalu ke kiri, geser sedikit ke kanan."
                        cx > 0.67f -> "Wajahmu terlalu ke kanan, geser sedikit ke kiri."
                        top < 0.03f -> "Wajahmu terpotong di atas, mundurkan HP sedikit."
                        size > 0.75f -> "Terlalu dekat, mundurkan HP sedikit."
                        size < 0.18f -> "Terlalu jauh, dekatkan HP sedikit."
                        else -> "ok"
                    }
                }
            }
            val now = System.currentTimeMillis()
            if (advice == "ok") {
                goodCount++
                if (lastAdvice.isNotEmpty() && lastAdvice != "ok") { whisper("Posisi sudah pas."); lastAdvice = "ok" }
            } else if (advice.isNotEmpty() && (advice != lastAdvice || now - lastAt > 45_000)) {
                whisper(advice); lastAdvice = advice; lastAt = now; goodCount = 0
            }
        }
    }

    private fun luminance(b: Bitmap): Float {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        var sum = 0L
        var i = 0
        while (i < px.size) { val p = px[i]; sum += (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000; i += 7 }
        return sum / (px.size / 7f) / 255f
    }
}
