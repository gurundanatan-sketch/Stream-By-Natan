package com.natan.streambynatan.live

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.natan.streambynatan.data.LocalStore
import com.natan.streambynatan.data.Moment
import com.natan.streambynatan.data.RecordingEntry
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Rekaman live di dalam aplikasi + penanda momen.
 * Setiap pertanyaan yang dijawab (dari dibacakan sampai tombol "Pertanyaan berikutnya"),
 * dan setiap poin khotbah, otomatis menjadi satu video pendek dengan judul sesuai pertanyaan/poin.
 * Tidak dibatasi durasi.
 */
class RecordingManager(private val context: Context, private val store: LocalStore) {
    var currentFile: File? = null
        private set
    private var recordStart = 0L
    private val moments = ArrayList<Moment>()
    private var openMoment: Pair<String, Long>? = null
    private var openKind = "question"
    var sessionId = ""
        private set

    fun newFile(): File {
        sessionId = UUID.randomUUID().toString().take(8)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val f = File(store.recordingsDir, "live_${stamp}_$sessionId.mp4")
        currentFile = f
        moments.clear(); openMoment = null
        return f
    }

    fun onRecordStarted() { recordStart = System.currentTimeMillis() }

    private fun now() = System.currentTimeMillis() - recordStart

    /** Mulai momen baru (pertanyaan dibacakan / poin dimulai). Momen sebelumnya ditutup. */
    fun beginMoment(title: String, kind: String) {
        if (recordStart == 0L) return
        endMoment()
        openMoment = title to now()
        openKind = kind
    }

    fun endMoment() {
        val (title, start) = openMoment ?: return
        val end = now()
        if (end - start > 1500) moments += Moment(title, start, end, openKind)
        openMoment = null
    }

    fun momentsCount() = moments.size

    /** Dipanggil setelah live selesai: simpan rekaman utama, lalu potong otomatis. */
    fun finish(clipsEnabled: Boolean, onProgress: (String) -> Unit): Int {
        endMoment()
        val f = currentFile ?: return 0
        recordStart = 0L
        if (!f.exists() || f.length() < 10_000) return 0
        val parent = RecordingEntry(sessionId, "Live " + SimpleDateFormat("d MMM yyyy HH:mm", Locale("id")).format(Date(f.lastModified())),
            f.absolutePath, System.currentTimeMillis(), durationOf(f))
        val entries = mutableListOf(parent)
        if (clipsEnabled && moments.isNotEmpty()) {
            val dir = File(f.parentFile, "clips_$sessionId").apply { mkdirs() }
            moments.forEachIndexed { i, m ->
                val name = safeTitle(m.title).ifBlank { "Momen ${i + 1}" }
                val out = File(dir, "${i + 1}. $name.mp4")
                onProgress("Memotong ${i + 1} dari ${moments.size}: $name")
                // FFmpeg dulu; kalau gagal, cadangan MediaMuxer bawaan Android
                if (FFmpeg.clip(f, out, m.startMs, m.endMs) || Clipper.clip(f, out, m.startMs, m.endMs)) {
                    entries += RecordingEntry(UUID.randomUUID().toString(), name, out.absolutePath, System.currentTimeMillis(), m.endMs - m.startMs, sessionId, "clip")
                }
            }
        }
        store.addRecordings(entries)
        return entries.size - 1
    }

    fun addTranscript(text: String) {
        if (text.isBlank()) return
        val f = File(store.recordingsDir, "transkrip_$sessionId.txt")
        f.writeText(text)
        store.addRecordings(listOf(RecordingEntry(UUID.randomUUID().toString(), "Transkrip live", f.absolutePath, System.currentTimeMillis(), 0, sessionId, "transcript")))
    }

    private fun durationOf(f: File): Long = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(f.absolutePath)
        var d = 0L
        for (i in 0 until ex.trackCount) d = maxOf(d, ex.getTrackFormat(i).getLong(MediaFormat.KEY_DURATION) / 1000)
        ex.release(); d
    }.getOrDefault(0L)

    companion object {
        fun safeTitle(t: String) = t.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").replace(Regex("\\s+"), " ").trim().take(80)

        /** Menyalin file ke folder Download (atau Movies) supaya bisa dibagikan. */
        fun exportToDownloads(context: Context, file: File): Boolean = runCatching {
            val mime = if (file.extension == "txt") "text/plain" else "video/mp4"
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Stream By Natan")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Stream By Natan").apply { mkdirs() }
                file.copyTo(File(dir, file.name), overwrite = true)
            }
            true
        }.getOrDefault(false)
    }
}

/** Cadangan pemotong video (MediaMuxer) kalau FFmpeg gagal. */
object Clipper {
    fun clip(src: File, dst: File, startMs: Long, endMs: Long): Boolean = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(src.absolutePath)
        val muxer = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val map = HashMap<Int, Int>()
        var maxSize = 1 shl 20
        var videoTrack = -1
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                ex.selectTrack(i)
                map[i] = muxer.addTrack(f)
                if (mime.startsWith("video/")) videoTrack = i
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = maxOf(maxSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
        }
        muxer.start()
        val buf = ByteBuffer.allocate(maxSize)
        val info = MediaCodec.BufferInfo()
        ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var firstTs = -1L
        while (true) {
            info.size = ex.readSampleData(buf, 0)
            if (info.size < 0) break
            val t = ex.sampleTime
            if (t > endMs * 1000) break
            val track = ex.sampleTrackIndex
            if (firstTs < 0) firstTs = t
            info.presentationTimeUs = (t - firstTs).coerceAtLeast(0)
            info.offset = 0
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            map[track]?.let { muxer.writeSampleData(it, buf, info) }
            ex.advance()
        }
        muxer.stop(); muxer.release(); ex.release()
        videoTrack >= 0 && dst.length() > 1000
    }.getOrElse { dst.delete(); false }
}
