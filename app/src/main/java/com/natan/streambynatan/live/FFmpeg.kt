package com.natan.streambynatan.live

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

/**
 * FFmpeg di dalam aplikasi:
 * - memotong rekaman live per pertanyaan / per poin khotbah,
 * - mengambil suara saja dari file video (dan memotongnya) untuk efek suara sendiri.
 */
object FFmpeg {
    private fun sec(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

    private fun run(args: Array<String>): Boolean = runCatching {
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(args).returnCode)
    }.getOrDefault(false)

    /** Potong video tanpa encode ulang (cepat & kualitas utuh). */
    fun clip(src: File, dst: File, startMs: Long, endMs: Long): Boolean {
        val ok = run(arrayOf(
            "-y", "-ss", sec(startMs), "-i", src.absolutePath, "-t", sec((endMs - startMs).coerceAtLeast(500)),
            "-c", "copy", "-map", "0", "-avoid_negative_ts", "make_zero", "-movflags", "+faststart", dst.absolutePath,
        ))
        return ok && dst.length() > 1000
    }

    /** Ambil suara dari file audio/video (bisa dipotong), simpan sebagai WAV di cache aplikasi. */
    fun extractAudio(context: Context, uri: Uri, startMs: Long, endMs: Long, out: File): Boolean {
        val input = runCatching { FFmpegKitConfig.getSafParameterForRead(context, uri) }.getOrNull() ?: return false
        val args = mutableListOf("-y")
        if (startMs > 0) args += listOf("-ss", sec(startMs))
        args += listOf("-i", input)
        if (endMs > startMs) args += listOf("-t", sec(endMs - startMs))
        args += listOf("-vn", "-ac", "2", "-ar", "44100", "-c:a", "pcm_s16le", out.absolutePath)
        return run(args.toTypedArray()) && out.length() > 100
    }
}
