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
