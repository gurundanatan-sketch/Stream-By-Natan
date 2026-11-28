package com.natan.streambynatan.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.natan.streambynatan.data.TtsConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dua jalur TTS (Google TTS, bahasa Indonesia), terpisah dari suara Jieshuo:
 * - [speakPublic]: disintesis ke file lalu dicampur ke siaran, jadi penonton mendengar.
 * - [whisper]: hanya untukmu (jenis audio aksesibilitas, tidak ikut tersiar).
 * Suara, kecepatan, dan volume diatur sendiri, tidak mengubah pengaturan Jieshuo.
 */
class TtsHub(private val context: Context, private val mixer: PcmMixer) {
    @Volatile var config: TtsConfig = TtsConfig()
        set(value) { field = value; applyConfig() }

    private var publicReady = false
    private var privateReady = false
    private val pendingPublic = ArrayList<String>()
    private val pendingPrivate = ArrayList<Triple<String, Float, (() -> Unit)?>>()
    private val files = ConcurrentHashMap<String, File>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val seq = AtomicInteger()
    private val decoder = Executors.newSingleThreadExecutor()
    private val dir = File(context.cacheDir, "tts").apply { mkdirs() }

    private val synth: TextToSpeech = TextToSpeech(context, { st ->
        publicReady = st == TextToSpeech.SUCCESS
        applyConfig()
        synchronized(pendingPublic) { pendingPublic.forEach { speakPublic(it) }; pendingPublic.clear() }
    }, config.enginePackage)

    private val priv: TextToSpeech = TextToSpeech(context, { st ->
        privateReady = st == TextToSpeech.SUCCESS
        applyConfig()
        synchronized(pendingPrivate) { pendingPrivate.forEach { whisper(it.first, it.second, it.third) }; pendingPrivate.clear() }
    }, config.enginePackage)

    init {
        synth.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                val f = id?.let { files.remove(it) } ?: return
                decoder.execute {
                    runCatching {
                        val pcm = AudioDecoder.decode(context, AudioDecoder.Src.FileSrc(f), mixer.sampleRate, mixer.channels)
                        mixer.queueSpeech(pcm, config.volume)
                    }
                    f.delete()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { id?.let { files.remove(it)?.delete() } }
        })
        priv.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { id?.let { callbacks.remove(it)?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { id?.let { callbacks.remove(it)?.invoke() } }
        })
        priv.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    private fun applyConfig() {
        val c = config
        val locale = Locale.forLanguageTag(c.language)
        for (t in listOf(synth, priv)) runCatching {
            t.language = locale
            t.setPitch(c.pitch)
            if (c.voiceName.isNotBlank()) t.voices?.firstOrNull { it.name == c.voiceName }?.let { t.voice = it }
        }
        runCatching { synth.setSpeechRate(c.rate) }
    }

    fun voices(): List<String> = runCatching {
        synth.voices?.filter { it.locale.language == "id" || it.locale.language == "in" }?.map { it.name }?.sorted()
    }.getOrNull() ?: emptyList()

    /** Penonton ikut mendengar (dicampur ke siaran). Kalau belum live, dibacakan biasa untukmu. */
    fun speakPublic(text: String) {
        if (text.isBlank()) return
        if (!publicReady) { synchronized(pendingPublic) { pendingPublic.add(text) }; return }
        if (!mixer.active) { whisper(text); return }
        val id = "p" + seq.incrementAndGet()
        val f = File(dir, "$id.wav")
        files[id] = f
        synth.setSpeechRate(config.rate)
        synth.synthesizeToFile(text, Bundle(), f, id)
    }

    /** Hanya kamu yang mendengar. [rate] null = kecepatan bisikan biasa. */
    fun whisper(text: String, rate: Float? = null, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        // Mode pembaca layar/tunanetra mati: bisikan tampil sebagai teks di layar host (tidak diucapkan).
        if (!com.natan.streambynatan.core.Graph.settings.current.a11y.voiceAids && !forceVoice) {
            com.natan.streambynatan.live.LiveBus.hostNote(text)
            com.natan.streambynatan.live.LiveBus.feed("📝 $text")
            onDone?.invoke()
            return
        }
        if (!privateReady) { synchronized(pendingPrivate) { pendingPrivate.add(Triple(text, rate ?: config.whisperRate, onDone)) }; return }
        val id = "w" + seq.incrementAndGet()
        onDone?.let { callbacks[id] = it }
        priv.setSpeechRate(rate ?: config.whisperRate)
        val b = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume) }
        priv.speak(text, TextToSpeech.QUEUE_ADD, b, id)
    }

    /** Bisikan yang tetap diucapkan walau mode tunanetra mati (misal peringatan suara yang dipilih pengguna). */
    @Volatile var forceVoice = false
    fun speakAlert(text: String) { forceVoice = true; try { whisper(text) } finally { forceVoice = false } }

    fun stopWhisper() { priv.stop(); callbacks.clear() }

    fun shutdown() {
        runCatching { synth.shutdown() }
        runCatching { priv.shutdown() }
        decoder.shutdownNow()
    }
}
