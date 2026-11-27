package com.natan.streambynatan.audio

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.natan.streambynatan.data.CustomSound
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import com.natan.streambynatan.data.AppJson
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Serializable
data class SoundItem(
    val id: String,
    val label: String,
    val mood: String = "",
    val source: String = "",
    val license: String = "",
    val author: String = "",
    val credit: String = "",
)

/** Kumpulan efek suara (200 dari Commons + buatan sendiri) dan musik latar (20 suasana + lagu sendiri). */
class SoundBoard(private val context: Context, private val mixer: PcmMixer) {
    private val cache = ConcurrentHashMap<String, ShortArray>()
    private val worker = Executors.newSingleThreadExecutor()

    val effects: List<SoundItem> by lazy { loadCatalog("sfx_catalog.json") }
    val musicTracks: List<SoundItem> by lazy { loadCatalog("music_catalog.json") }

    private fun loadCatalog(name: String): List<SoundItem> = runCatching {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        AppJson.decodeFromString(ListSerializer(SoundItem.serializer()), text)
    }.getOrDefault(emptyList()).filter { rawId(it.id) != 0 }

    @SuppressLint("DiscouragedApi")
    fun rawId(name: String): Int = context.resources.getIdentifier(name, "raw", context.packageName)

    fun play(id: String, gain: Float = 1f, custom: List<CustomSound> = emptyList()) {
        worker.execute {
            val pcm = cache[id] ?: load(id, custom)?.also { cache[id] = it }
            pcm?.let { mixer.playEffect(it, gain) }
        }
    }

    private fun load(id: String, custom: List<CustomSound>): ShortArray? = runCatching {
        val c = custom.firstOrNull { it.id == id }
        if (c != null) {
            // Efek suara dari file audio ATAU video: FFmpeg mengambil suaranya saja & memotongnya
            // (misal detik ke-3 sampai ke-6). Hasilnya disimpan di cache aplikasi, tidak masuk galeri.
            val wav = java.io.File(context.cacheDir, "sfx_${c.id}_${c.startMs}_${c.endMs}.wav")
            if (wav.exists() || com.natan.streambynatan.live.FFmpeg.extractAudio(context, Uri.parse(c.uri), c.startMs, c.endMs, wav)) {
                return AudioDecoder.decode(context, AudioDecoder.Src.FileSrc(wav), mixer.sampleRate, mixer.channels, maxSeconds = 30)
            }
            return AudioDecoder.decode(context, AudioDecoder.Src.UriSrc(Uri.parse(c.uri)), mixer.sampleRate, mixer.channels, c.startMs, c.endMs, maxSeconds = 30)
        }
        val res = rawId(id); if (res == 0) return null
        AudioDecoder.decode(context, AudioDecoder.Src.Raw(res), mixer.sampleRate, mixer.channels, maxSeconds = 30)
    }.getOrNull()

    fun invalidate(id: String) { cache.remove(id) }

    fun startMusic(trackId: String, customPath: String, loop: Boolean): Boolean {
        stopMusic()
        val src = when {
            customPath.isNotBlank() -> AudioDecoder.Src.UriSrc(Uri.parse(customPath))
            trackId.isNotBlank() && rawId(trackId) != 0 -> AudioDecoder.Src.Raw(rawId(trackId))
            else -> musicTracks.firstOrNull()?.let { AudioDecoder.Src.Raw(rawId(it.id)) } ?: return false
        }
        mixer.music = MusicStream(context, src, mixer.sampleRate, mixer.channels, loop).start()
        return true
    }

    fun stopMusic() {
        mixer.music?.stop()
        mixer.music = null
    }

    val musicPlaying: Boolean get() = mixer.music != null
}
