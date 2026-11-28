package com.natan.streambynatan.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Pencampur suara digital. Semua suara yang harus TERDENGAR PENONTON (TTS terima kasih, efek suara,
 * musik latar, pengumuman) dicampur langsung ke suara siaran di [mixInto].
 * Suara Jieshuo tidak pernah ikut, karena tidak lewat sini dan tidak direkam.
 */
class PcmMixer(val sampleRate: Int = 44100, val channels: Int = 2) {

    private class Voice(val data: ShortArray, val gain: Float, var pos: Int = 0)

    private val effects = CopyOnWriteArrayList<Voice>()
    private val speechQueue = ArrayBlockingQueue<Pair<ShortArray, Float>>(64)
    @Volatile private var speech: Voice? = null
    @Volatile var music: MusicStream? = null
    @Volatile var musicGain: Float = 0.25f
    /** Pengali musik saat host berbicara (1 = normal). Diatur oleh StudioAudioEffect. */
    @Volatile var duckGain: Float = 1f
    @Volatile var masterGain: Float = 1f
    @Volatile var active: Boolean = false
    var monitor: MonitorPlayer? = null

    /** Efek suara: boleh tumpang tindih. */
    fun playEffect(pcm: ShortArray, gain: Float = 1f) {
        if (effects.size > 12) effects.removeAt(0)
        effects.add(Voice(pcm, gain))
    }

    /** Ucapan TTS: diputar bergantian, tidak bertumpuk. */
    fun queueSpeech(pcm: ShortArray, gain: Float = 1f) {
        if (!speechQueue.offer(pcm to gain)) { speechQueue.poll(); speechQueue.offer(pcm to gain) }
    }

    fun clearSpeech() { speechQueue.clear(); speech = null }

    val isSpeaking: Boolean get() = speech != null || speechQueue.isNotEmpty()

    /** Dipanggil dari utas audio siaran. [buf] berisi suara mikrofon (stereo interleaved). */
    fun mixInto(buf: ShortArray, count: Int) {
        if (!active) return
        val fx = FloatArray(count)
        var any = false
        // ucapan
        var sp = speech
        if (sp == null) speechQueue.poll()?.let { sp = Voice(it.first, it.second); speech = sp }
        sp?.let { v ->
            var i = 0
            while (i < count && v.pos < v.data.size) { fx[i] += v.data[v.pos++] * v.gain; i++ }
            if (v.pos >= v.data.size) speech = null
            any = true
        }
        // efek
        for (v in effects) {
            var i = 0
            while (i < count && v.pos < v.data.size) { fx[i] += v.data[v.pos++] * v.gain; i++ }
            if (v.pos >= v.data.size) effects.remove(v)
            any = true
        }
        // musik
        music?.let { m ->
            val tmp = ShortArray(count)
            val got = m.read(tmp, count)
            val gMusic = musicGain * duckGain
            for (i in 0 until got) fx[i] += tmp[i] * gMusic
            if (got > 0) any = true
        }
        if (!any) return
        for (i in 0 until count) {
            val f = fx[i] * masterGain
            buf[i] = AudioDecoder.clip(buf[i] + f)
        }
        monitor?.push(fx)
    }
}

/** Musik latar yang didekode bertahap (tidak memenuhi memori). */
class MusicStream(
    private val context: Context,
    private val src: AudioDecoder.Src,
    private val rate: Int,
    private val ch: Int,
    private val loop: Boolean,
) {
    private val ring = ArrayBlockingQueue<ShortArray>(32)
    private var current: ShortArray? = null
    private var curPos = 0
    @Volatile private var stopped = false
    private val thread = Thread({
        do {
            runCatching {
                AudioDecoder.stream(context, src, rate, ch, 0, 0, { stopped }) { pcm ->
                    while (!stopped && !ring.offer(pcm, 200, TimeUnit.MILLISECONDS)) { /* tunggu */ }
                    !stopped
                }
            }
        } while (loop && !stopped)
    }, "MusicStream")

    fun start(): MusicStream { thread.start(); return this }
    fun stop() { stopped = true; ring.clear() }

    fun read(out: ShortArray, count: Int): Int {
        var filled = 0
        while (filled < count) {
            val c = current ?: ring.poll() ?: break
            current = c
            val n = minOf(count - filled, c.size - curPos)
            System.arraycopy(c, curPos, out, filled, n)
            filled += n; curPos += n
            if (curPos >= c.size) { current = null; curPos = 0 }
        }
        return filled
    }
}

/**
 * Pemantau: memutar suara campuran (TTS/efek/musik) ke earphone kamu dengan jenis "asisten",
 * yang TIDAK ikut terekam tangkapan audio internal, jadi tidak terdengar dobel oleh penonton.
 */
class MonitorPlayer(sampleRate: Int, private val channels: Int) {
    private val queue = ArrayBlockingQueue<ShortArray>(24)
    @Volatile private var running = true
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate,
            if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT) * 4)
        .build()

    private val thread = Thread({
        track.play()
        while (running) {
            val b = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            track.write(b, 0, b.size)
        }
        runCatching { track.stop() }
        track.release()
    }, "Monitor").apply { start() }

    fun push(fx: FloatArray) {
        val s = ShortArray(fx.size) { AudioDecoder.clip(fx[it]) }
        queue.offer(s)
    }

    fun release() { running = false }
}
