package com.natan.streambynatan.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/**
 * Mengubah file audio ATAU video (suara diambil saja) menjadi PCM 16-bit.
 * Dipakai untuk efek suara, musik, suara TTS, dan efek suara dari video pilihan sendiri (bisa dipotong).
 */
object AudioDecoder {

    sealed class Src {
        data class Raw(val resId: Int) : Src()
        data class FileSrc(val file: File) : Src()
        data class UriSrc(val uri: Uri) : Src()
    }

    fun open(context: Context, src: Src): MediaExtractor {
        val ex = MediaExtractor()
        when (src) {
            is Src.Raw -> context.resources.openRawResourceFd(src.resId).use { ex.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            is Src.FileSrc -> ex.setDataSource(src.file.absolutePath)
            is Src.UriSrc -> ex.setDataSource(context, src.uri, null)
        }
        return ex
    }

    fun durationMs(context: Context, src: Src): Long = runCatching {
        val ex = open(context, src)
        try {
            val i = audioTrackIndex(ex)
            if (i < 0) 0L else ex.getTrackFormat(i).getLong(MediaFormat.KEY_DURATION) / 1000
        } finally { ex.release() }
    }.getOrDefault(0L)

    private fun audioTrackIndex(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        return -1
    }

    /** Dekode seluruh (atau sebagian) file ke PCM interleaved sesuai [outRate] dan [outChannels]. */
    fun decode(
        context: Context, src: Src, outRate: Int, outChannels: Int,
        startMs: Long = 0, endMs: Long = 0, maxSeconds: Int = 600,
    ): ShortArray {
        val chunks = ArrayList<ShortArray>()
        var total = 0
        stream(context, src, outRate, outChannels, startMs, endMs, { false }) { pcm ->
            chunks.add(pcm); total += pcm.size
            total < outRate * outChannels * maxSeconds
        }
        val out = ShortArray(total)
        var p = 0
        for (c in chunks) { System.arraycopy(c, 0, out, p, c.size); p += c.size }
        return out
    }

    /**
     * Dekode bertahap. [onPcm] dipanggil untuk setiap potongan; kembalikan false untuk berhenti.
     * [shouldStop] diperiksa terus supaya musik bisa dihentikan kapan saja.
     */
    fun stream(
        context: Context, src: Src, outRate: Int, outChannels: Int,
        startMs: Long, endMs: Long, shouldStop: () -> Boolean, onPcm: (ShortArray) -> Boolean,
    ) {
        val ex = open(context, src)
        val track = audioTrackIndex(ex)
        if (track < 0) { ex.release(); return }
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run { ex.release(); return }
        if (startMs > 0) ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        var inRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var inCh = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val resampler = Resampler()
        try {
            while (!outputDone && !shouldStop()) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        val t = ex.sampleTime
                        if (size < 0 || (endMs > 0 && t > endMs * 1000)) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, t, 0)
                            ex.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        inRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        inCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (info.size > 0 && (startMs <= 0 || info.presentationTimeUs >= startMs * 1000)) {
                            val ob = codec.getOutputBuffer(outIndex)!!
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val raw = ShortArray(sb.remaining()); sb.get(raw)
                            val conv = resampler.convert(raw, inRate, inCh, outRate, outChannels)
                            if (conv.isNotEmpty() && !onPcm(conv)) outputDone = true
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
    }

    /** Pengubah laju sampel & jumlah kanal sederhana (interpolasi linear). */
    class Resampler {
        private var frac = 0.0
        private var last = FloatArray(2)

        fun convert(input: ShortArray, inRate: Int, inCh: Int, outRate: Int, outCh: Int): ShortArray {
            val ch = inCh.coerceAtLeast(1)
            val frames = input.size / ch
            if (frames == 0) return ShortArray(0)
            // indeks 0 = sampel terakhir blok sebelumnya, supaya sambungan antarblok mulus
            val n = frames + 1
            val l = FloatArray(n); val r = FloatArray(n)
            l[0] = last[0]; r[0] = last[1]
            for (i in 0 until frames) {
                val a = input[i * ch].toFloat()
                val b = if (ch > 1) input[i * ch + 1].toFloat() else a
                l[i + 1] = a; r[i + 1] = b
            }
            val step = inRate.toDouble() / outRate
            val out = ShortArray((((n - frac) / step).toInt() + 2) * outCh)
            var count = 0
            var pos = frac
            while (pos.toInt() + 1 < n) {
                val i0 = pos.toInt()
                val t = (pos - i0).toFloat()
                val lv = l[i0] + (l[i0 + 1] - l[i0]) * t
                val rv = r[i0] + (r[i0 + 1] - r[i0]) * t
                if (outCh == 1) out[count++] = clip((lv + rv) / 2f)
                else { out[count++] = clip(lv); out[count++] = clip(rv) }
                pos += step
            }
            frac = pos - frames
            last[0] = l[n - 1]; last[1] = r[n - 1]
            return out.copyOf(count)
        }
    }

    fun clip(v: Float): Short = v.coerceIn(-32768f, 32767f).toInt().toShort()
}
