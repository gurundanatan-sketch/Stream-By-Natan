package com.natan.streambynatan.audio

import com.natan.streambynatan.data.MicFx
import com.pedro.encoder.input.audio.CustomAudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Diproses di setiap potongan suara siaran (mikrofon + audio internal):
 * 1. Efek mikrofon: peredam bising kuat (noise gate), penyeimbang volume, gema ringan.
 * 2. Salinan suara mono 16 kHz untuk pengenal suara (subtitle, perintah suara, ayat, naskah).
 * 3. Pencampuran suara aplikasi yang harus didengar penonton (TTS, efek suara, musik).
 */
class StudioAudioEffect(
    private val mixer: PcmMixer,
    private val sampleRate: Int,
    private val channels: Int,
) : CustomAudioEffect() {

    @Volatile var fx: MicFx = MicFx()
    @Volatile var speechTap: ((ShortArray) -> Unit)? = null
    @Volatile var levelListener: ((Float) -> Unit)? = null
    /** Musik mengecil otomatis saat host berbicara. */
    @Volatile var ducking: Boolean = false
    @Volatile var duckLevel: Float = 0.35f
    private var duckHold = 0
    private val changer = VoiceChanger(sampleRate, channels)

    // penyeimbang volume
    private var gain = 1f
    // gerbang bising
    private var gateEnv = 0f
    private var gateGain = 1f
    // gema (comb + allpass sederhana)
    private val combLens = intArrayOf(1557, 1617, 1491, 1422).map { it * sampleRate / 44100 }
    private val combs = combLens.map { FloatArray(it) }
    private val combIdx = IntArray(combs.size)
    private val combFb = 0.78f
    // pengubah laju ke 16 kHz untuk pengenal suara
    private val tapResampler = AudioDecoder.Resampler()
    private var levelCounter = 0
    private var rnnoise: RnnoiseProcessor? = null

    override fun process(pcmBuffer: ByteArray): ByteArray {
        val count = pcmBuffer.size / 2
        if (count == 0) return pcmBuffer
        val sb = ByteBuffer.wrap(pcmBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val s = ShortArray(count)
        sb.get(s)
        val f = fx

        // 1) gerbang bising kuat
        if (f.strongNoiseGate) {
            val threshold = 600f
            for (i in 0 until count) {
                val a = abs(s[i].toFloat())
                gateEnv = if (a > gateEnv) a else gateEnv * 0.9995f
                val target = if (gateEnv > threshold) 1f else 0.08f
                gateGain += (target - gateGain) * 0.002f
                s[i] = (s[i] * gateGain).toInt().toShort()
            }
        }
        // 1b) peredam bising AI (RNNoise)
        if (f.aiNoiseSuppression) {
            val rp = rnnoise ?: RnnoiseProcessor(sampleRate, channels).also { rnnoise = it }
            if (rp.available) rp.process(s)
        }
        // 2) penyeimbang volume (AGC lembut)
        if (f.volumeBalancer) {
            var peak = 1f
            for (v in s) peak = max(peak, abs(v.toFloat()))
            val desired = (12000f / peak).coerceIn(0.5f, 4f)
            gain += (desired - gain) * 0.05f
            for (i in 0 until count) s[i] = AudioDecoder.clip(s[i] * gain)
        }
        // 3) gema ringan seperti di gedung gereja
        if (f.reverb) {
            val wet = f.reverbAmount.coerceIn(0f, 0.8f)
            var i = 0
            while (i < count) {
                val dry = s[i].toFloat()
                var acc = 0f
                for (c in combs.indices) {
                    val buf = combs[c]
                    val idx = combIdx[c]
                    val out = buf[idx]
                    buf[idx] = dry + out * combFb
                    combIdx[c] = (idx + 1) % buf.size
                    acc += out
                }
                val v = AudioDecoder.clip(dry + acc * 0.25f * wet)
                s[i] = v
                if (channels == 2 && i + 1 < count) s[i + 1] = AudioDecoder.clip(s[i + 1] + acc * 0.25f * wet)
                i += channels
            }
        }
        // 4) salinan untuk pengenal suara (sebelum musik/TTS dicampur, supaya hanya suaramu yang dikenali)
        speechTap?.let { tap -> runCatching { tap(tapResampler.convert(s, sampleRate, channels, 16000, 1)) } }
        // 4b) pengubah suara (setelah pengenal suara, supaya perintah suara tetap dikenali)
        if (f.voiceChanger != "none") changer.process(s, count, f.voiceChanger)
        // 4c) musik mengecil saat host berbicara (tidak hilang)
        if (ducking) {
            var peak = 0f
            for (v in s) peak = max(peak, abs(v.toFloat()))
            if (peak > 2500f) duckHold = sampleRate * channels / 2 // tahan 0,5 detik setelah suara terakhir
            else duckHold = (duckHold - count).coerceAtLeast(0)
            val target = if (duckHold > 0) duckLevel.coerceIn(0.05f, 1f) else 1f
            val speed = if (target < mixer.duckGain) 0.35f else 0.06f
            mixer.duckGain += (target - mixer.duckGain) * speed
        } else if (mixer.duckGain != 1f) mixer.duckGain = 1f
        // level untuk indikator
        if (++levelCounter % 5 == 0) levelListener?.let { l ->
            var peak = 0f
            for (v in s) peak = max(peak, abs(v.toFloat()))
            l(peak / 32768f)
        }
        // 5) campur suara aplikasi untuk penonton
        mixer.mixInto(s, count)

        val out = ByteArray(pcmBuffer.size)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(s)
        return out
    }
}


/**
 * Pengubah suara langsung di HP (tanpa internet, tanpa API key).
 * Nada naik/turun memakai dua kepala baca pada saluran tunda dengan pudar silang.
 */
class VoiceChanger(private val sampleRate: Int, private val channels: Int) {
    private val size = 4096
    private val buf = Array(2) { FloatArray(size) }
    private var write = 0
    private var phase = 0f
    private var ringPhase = 0.0
    private val echo = Array(2) { FloatArray(sampleRate / 4) }
    private var echoIdx = 0
    private var lp = FloatArray(2)
    private var hp = FloatArray(2)

    fun process(s: ShortArray, count: Int, mode: String) {
        val ratio = when (mode) { "high" -> 1.35f; "chipmunk" -> 1.7f; "deep" -> 0.75f; "giant" -> 0.6f; else -> 1f }
        val window = 1600f
        var i = 0
        while (i < count) {
            for (ch in 0 until channels) {
                if (i + ch >= count) break
                val x = s[i + ch].toFloat()
                val c = ch.coerceAtMost(1)
                var y = x
                when (mode) {
                    "robot" -> {
                        y = x * Math.sin(ringPhase).toFloat()
                    }
                    "echo" -> {
                        val e = echo[c]
                        val d = e[echoIdx]
                        y = x + d * 0.55f
                        e[echoIdx] = x + d * 0.45f
                    }
                    "phone" -> {
                        // saringan pita sempit seperti suara telepon
                        lp[c] += (x - lp[c]) * 0.35f
                        hp[c] = lp[c] - (lp[c] - hp[c]) * 0.0f
                        y = (lp[c] - hpPrev(c, lp[c])) * 1.6f
                    }
                    "high", "chipmunk", "deep", "giant" -> {
                        val b = buf[c]
                        b[write] = x
                        val d1 = phase
                        val d2 = (phase + window / 2) % window
                        val r1 = read(b, write - d1)
                        val r2 = read(b, write - d2)
                        val g1 = 1f - Math.abs(d1 - window / 2) / (window / 2)
                        val g2 = 1f - g1
                        y = r1 * g1 + r2 * g2
                    }
                }
                s[i + ch] = AudioDecoder.clip(y)
            }
            write = (write + 1) % size
            phase = (phase + (1f - ratio)).let { if (it < 0) it + window else if (it >= window) it - window else it }
            ringPhase += 2 * Math.PI * 55.0 / sampleRate
            if (ringPhase > 2 * Math.PI) ringPhase -= 2 * Math.PI
            echoIdx = (echoIdx + 1) % echo[0].size
            i += channels
        }
    }

    private val hpState = FloatArray(2)
    private fun hpPrev(c: Int, v: Float): Float { hpState[c] += (v - hpState[c]) * 0.04f; return hpState[c] }

    private fun read(b: FloatArray, pos: Float): Float {
        var p = pos
        while (p < 0) p += size
        val i0 = p.toInt() % size
        val i1 = (i0 + 1) % size
        val f = p - p.toInt()
        return b[i0] * (1 - f) + b[i1] * f
    }
}
