package com.natan.streambynatan.audio

import cn.enaium.rnnoise.Rnnoise
import cn.enaium.rnnoise.createRnnoise

/**
 * Peredam bising kuat berbasis AI (RNNoise, offline). RNNoise bekerja di 48 kHz dengan bingkai 10 ms,
 * jadi suara siaran (44,1 kHz) diubah dulu ke 48 kHz, dibersihkan, lalu dikembalikan.
 * Suara TTS, efek suara, dan musik dari aplikasi dicampur SETELAH tahap ini, jadi tidak ikut diredam.
 */
class RnnoiseProcessor(private val sampleRate: Int, private val channels: Int) {
    private val rn: Rnnoise? = runCatching { createRnnoise() }.getOrNull()
    val available: Boolean get() = rn != null
    private val down = AudioDecoder.Resampler()
    private val up = AudioDecoder.Resampler()
    private var pending = FloatArray(0)
    private var outFifo = ShortArray(0)

    /** Memproses [s] (interleaved) di tempat. Ada jeda sekitar 10 ms. */
    fun process(s: ShortArray) {
        val r = rn ?: return
        val frame = r.frameSize
        // 1) ke mono 48 kHz
        val mono48 = down.convert(s, sampleRate, channels, 48000, 1)
        val joined = FloatArray(pending.size + mono48.size)
        System.arraycopy(pending, 0, joined, 0, pending.size)
        for (i in mono48.indices) joined[pending.size + i] = mono48[i] / 32768f
        // 2) bersihkan per bingkai 480 sampel
        var pos = 0
        val cleaned = ArrayList<ShortArray>()
        val input = FloatArray(frame); val output = FloatArray(frame)
        while (pos + frame <= joined.size) {
            System.arraycopy(joined, pos, input, 0, frame)
            r.processFrame(input, output)
            cleaned += ShortArray(frame) { AudioDecoder.clip(output[it] * 32768f) }
            pos += frame
        }
        pending = joined.copyOfRange(pos, joined.size)
        // 3) kembali ke laju & kanal siaran
        for (c in cleaned) {
            val back = up.convert(c, 48000, 1, sampleRate, channels)
            outFifo = outFifo + back
        }
        // 4) isi buffer keluaran dengan ukuran yang sama (awal live: hening sejenak)
        val n = minOf(s.size, outFifo.size)
        for (i in s.indices) s[i] = if (i >= s.size - n) outFifo[i - (s.size - n)] else 0
        outFifo = outFifo.copyOfRange(n, outFifo.size)
        if (outFifo.size > sampleRate * channels) outFifo = ShortArray(0) // jaga agar jeda tidak menumpuk
    }

    fun release() { runCatching { rn?.close() } }
}
