package com.natan.streambynatan.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.AppSettings
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.features.BackgroundReplacer
import com.natan.streambynatan.features.CameraAssistant
import com.natan.streambynatan.features.LiveBrain
import com.natan.streambynatan.overlay.CinematicItem
import com.natan.streambynatan.overlay.StageHost
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ViewSurfaceFilterRender
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.extrasources.CameraUvcSource
import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.TakePhotoCallback
import com.natan.streambynatan.audio.MonitorPlayer
import com.natan.streambynatan.audio.StudioAudioEffect

enum class LiveMode(val label: String) {
    CAMERA("Kamera"), SCREEN("Bagikan layar"), AUDIO_ONLY("Suara saja"), USB("Webcam USB")
}

/**
 * Mesin siaran: satu encoder, dikirim sekaligus ke YouTube dan TikTok (RootEncoder MultiStream),
 * dengan bitrate adaptif, sambung ulang otomatis, rekaman ke HP, lapisan grafis, dan pencampur audio.
 */
class LiveEngine(private val context: Context, private val owner: LifecycleOwner) {
    private val g = Graph
    private val main = Handler(Looper.getMainLooper())
    private val settings: AppSettings get() = g.settings.current

    private lateinit var stream: MultiStream
    val stageHost = StageHost(context)
    private val stageFilter = ViewSurfaceFilterRender()
    private var brightness: BrightnessFilterRender? = null
    private var audioSource: AudioSource? = null
    private var effect: StudioAudioEffect? = null
    private var camera2: Camera2Source? = null
    private var canvasSource: CanvasVideoSource? = null
    private var bgReplacer: BackgroundReplacer? = null
    private var projection: MediaProjection? = null
    private var monitor: MonitorPlayer? = null
    var mode = LiveMode.CAMERA; private set
    var practice = false; private set
    lateinit var brain: LiveBrain; private set
    private val assistant = CameraAssistant { g.tts.whisper(it) }
    private val compositor = com.natan.streambynatan.guest.StageCompositor()
    private var ytIndex = -1
    private var ttIndex = -1
    private var stopping = false
    private var activeCameraName = "depan"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wasOffline = false

    private val bitrateAdapter = BitrateAdapter { br -> runCatching { stream.setVideoBitrateOnFly(br) } }

    private inner class Checker(val label: String, val isYt: Boolean) : ConnectChecker {
        private fun state(s: String) = LiveBus.stats { if (isYt) it.copy(youtubeState = s) else it.copy(tiktokState = s) }
        override fun onConnectionStarted(url: String) = state("Menyambung…")
        override fun onConnectionSuccess() { state("Live"); LiveBus.announce("$label tersambung, kamu sudah live") }
        override fun onConnectionFailed(reason: String) {
            val idx = if (isYt) ytIndex else ttIndex
            val client = runCatching { stream.getStreamClient(MultiType.RTMP, idx) }.getOrNull()
            if (!stopping && settings.video.autoReconnect && client?.reTry(5000, reason) == true) {
                state("Menyambung ulang…")
                g.tts.whisper("Koneksi $label bermasalah, menyambung ulang.")
            } else {
                state("Gagal: $reason")
                if (!stopping) g.tts.whisper("$label gagal tersambung.")
                main.post { runCatching { stream.stopStream(MultiType.RTMP, idx) } }
            }
        }
        override fun onNewBitrate(bitrate: Long) {
            LiveBus.stats { it.copy(bitrateKbps = bitrate / 1000) }
            if (settings.video.adaptiveBitrate) {
                val idx = if (isYt) ytIndex else ttIndex
                val congested = runCatching { stream.getStreamClient(MultiType.RTMP, idx).hasCongestion(20f) }.getOrDefault(false)
                bitrateAdapter.adaptBitrate(bitrate, congested)
            }
        }
        override fun onDisconnect() = state("Terputus")
        override fun onAuthError() = state("Stream key salah")
        override fun onAuthSuccess() = state("Masuk")
    }

    private val ytChecker = Checker("YouTube", true)
    private val ttChecker = Checker("TikTok", false)

    /** Menyiapkan semua sumber & encoder. [proj] wajib untuk mode layar / audio internal. */
    fun prepare(mode: LiveMode, practice: Boolean, proj: MediaProjection?) {
        this.mode = mode; this.practice = practice; this.projection = proj
        val s = settings
        val video = when {
            mode == LiveMode.SCREEN && proj != null -> ScreenSource(context, proj)
            mode == LiveMode.USB -> CameraUvcSource()
            mode == LiveMode.AUDIO_ONLY || s.features.audioOnly -> CanvasVideoSource(loadImage(s.audioOnlyImage) ?: defaultCover()).also { canvasSource = it }
            s.features.backgroundReplace || s.features.guests -> CanvasVideoSource().also { canvasSource = it }
            else -> Camera2Source(context).also { camera2 = it }
        }
        // Mikrofon + audio internal (Android 10+) kalau ada izin rekam layar, selain itu mikrofon saja.
        val audio: AudioSource = if (proj != null && Build.VERSION.SDK_INT >= 29) MixAudioSource(proj) else MicrophoneSource()
        audioSource = audio
        stream = MultiStream(context, arrayOf<ConnectChecker>(ytChecker, ttChecker), null, null, null, video, audio)
        ytIndex = 0; ttIndex = 1
        for (i in 0..1) runCatching {
            stream.getStreamClient(MultiType.RTMP, i).setReTries(10)
        }

        val v = s.video
        val long = maxOf(v.width, v.height); val short = minOf(v.width, v.height)
        val rotation = if (v.portrait) 90 else 0
        val okV = stream.prepareVideo(long, short, v.bitrateKbps * 1000, v.fps, 2, rotation)
        val okA = stream.prepareAudio(44100, true, v.audioBitrateKbps * 1000, echoCanceler = false, noiseSuppressor = s.micFx.noiseSuppressor)
        if (!okV || !okA) throw IllegalStateException("HP ini tidak mendukung pengaturan video/audio tersebut. Coba turunkan resolusi.")
        bitrateAdapter.setMaxBitrate(v.bitrateKbps * 1000)

        // Pencampur audio untuk TTS/efek/musik ke siaran
        val fx = StudioAudioEffect(g.mixer, 44100, 2).also {
            it.fx = s.micFx
            it.speechTap = { pcm -> g.speech.feed(pcm) }
            it.levelListener = { lvl -> compositor.hostLevel = lvl; levelForUi?.invoke(lvl) }
        }
        effect = fx
        when (audio) {
            is MixAudioSource -> { audio.setAudioEffect(fx); audio.microphoneVolume = s.micFx.micVolume; audio.internalVolume = s.micFx.internalVolume }
            is MicrophoneSource -> { audio.setAudioEffect(fx); audio.microphoneVolume = s.micFx.micVolume }
        }
        if (s.tts.monitorPublicTts) { monitor = MonitorPlayer(44100, 2); g.mixer.monitor = monitor }
        g.mixer.musicGain = s.music.volume

        // Lapisan grafis siaran
        stageFilter.setScale(100f, 100f)
        stageFilter.setPosition(0f, 0f)
        stream.getGlInterface().addFilter(stageFilter)
        stageHost.layoutParams = ViewGroup.LayoutParams(short, long)
        stageFilter.setView(stageHost)
        if (s.features.autoLight) {
            brightness = BrightnessFilterRender().also { stream.getGlInterface().addFilter(it) }
            assistant.onBrightness = { lum -> brightness?.setBrightness((1f + (0.45f - lum) * 1.2f).coerceIn(0.7f, 1.6f)) }
        }
        brain = LiveBrain(context, this, stageHost)
    }

    fun start() {
        val s = settings
        stopping = false
        g.mixer.active = true
        // Rekam ke HP (selalu untuk mode latihan, supaya bisa didengar ulang)
        if (s.video.recordWhileLive || practice) {
            val f = g.recordings.newFile()
            runCatching {
                stream.startRecord(f.absolutePath, null, RecordController.Listener { status ->
                    if (status == RecordController.Status.RECORDING) g.recordings.onRecordStarted()
                    LiveBus.stats { it.copy(recording = status == RecordController.Status.RECORDING || status == RecordController.Status.STARTED || status == RecordController.Status.RESUMED) }
                })
            }.onFailure { LiveBus.feed("Rekaman gagal dimulai: ${it.message}") }
        }
        if (!practice) {
            val ytKey = g.secure.get(SecureStore.YOUTUBE_KEY)
            val ttKey = g.secure.get(SecureStore.TIKTOK_KEY)
            if (s.enableYouTube && ytKey.isNotBlank()) stream.startStream(MultiType.RTMP, ytIndex, join(s.youtubeServer, ytKey))
            else LiveBus.stats { it.copy(youtubeState = if (s.enableYouTube) "Stream key kosong" else "Mati") }
            if (s.enableTikTok && ttKey.isNotBlank() && s.tiktokServer.isNotBlank()) stream.startStream(MultiType.RTMP, ttIndex, join(s.tiktokServer, ttKey))
            else LiveBus.stats { it.copy(tiktokState = if (s.enableTikTok) "Server/key TikTok kosong" else "Mati") }
        }
        // Kamera depan sebagai bawaan
        camera2?.let { cam -> main.postDelayed({ if (cam.getCameraFacing() == CameraHelper.Facing.BACK) runCatching { cam.switchCamera() } }, 600) }
        if (mode == LiveMode.CAMERA && canvasSource != null && (s.features.backgroundReplace || s.features.guests)) {
            compositor.hostName = g.firebase.displayName ?: "Host"
            bgReplacer = BackgroundReplacer(context, replace = s.features.backgroundReplace) { bmp -> onHostFrame(bmp) }.also { it.start(owner, s.background) }
        }
        LiveBus.stats { LiveStats(isLive = true, practice = practice, startedAt = System.currentTimeMillis(),
            youtubeState = it.youtubeState, tiktokState = it.tiktokState, screenSharing = mode == LiveMode.SCREEN,
            micOn = true, shareAudioOn = audioSource is MixAudioSource) }
        if (s.features.intro) playCinematic(intro = true) {}
        watchNetwork()
        brain.start()
        main.postDelayed(assistantTick, 5000)
    }

    private fun join(server: String, key: String) = server.trimEnd('/') + "/" + key.trim()

    private val assistantTick = object : Runnable {
        override fun run() {
            if (!stream.isStreaming && !stream.isRecording) return
            val s = settings
            val wantPos = s.features.cameraAssistant && (mode == LiveMode.CAMERA || mode == LiveMode.USB)
            if ((wantPos || s.features.autoLight) && stageHost.stage.showingIds().none { it.startsWith("full") }) {
                stream.getGlInterface().takePhoto(TakePhotoCallback { bmp -> runCatching { assistant.analyze(bmp, wantPos, s.features.autoLight) } })
            }
            main.postDelayed(this, 6000)
        }
    }

    fun playCinematic(intro: Boolean, done: () -> Unit) {
        val c = settings.intro
        val ms = c.durationSec * 1000L
        stageHost.stage.show("full_cinematic", CinematicItem(
            if (intro) c.introTemplate else c.outroTemplate,
            if (intro) c.title else c.outroTitle,
            if (intro) c.subtitle else c.outroSubtitle, ms, intro && c.countdown), ms)
        val hadMusic = g.sounds.musicPlaying
        if (c.musicId.isNotBlank() && !hadMusic) g.sounds.startMusic(c.musicId, "", loop = true)
        main.postDelayed({ if (!hadMusic) g.sounds.stopMusic(); done() }, ms + 300)
    }

    /** Akhiri live: tampilkan layar penutup dulu kalau diaktifkan. */
    fun requestStop(onStopped: () -> Unit) {
        if (stopping) return
        stopping = true
        if (settings.features.outro && !practice) playCinematic(intro = false) { stopNow(onStopped) } else stopNow(onStopped)
    }

    private fun stopNow(onStopped: () -> Unit) {
        main.removeCallbacks(assistantTick)
        brain.stop()
        bgReplacer?.stop()
        runCatching { stream.stopStream(MultiType.RTMP, ytIndex) }
        runCatching { stream.stopStream(MultiType.RTMP, ttIndex) }
        val wasRecording = stream.isRecording
        runCatching { if (wasRecording) stream.stopRecord() }
        runCatching { stream.release() }
        g.mixer.active = false
        g.mixer.clearSpeech()
        g.sounds.stopMusic()
        g.mixer.monitor = null
        monitor?.release()
        networkCallback?.let { runCatching { (context.getSystemService(ConnectivityManager::class.java)).unregisterNetworkCallback(it) } }
        runCatching { projection?.stop() }
        stageHost.removeSaweria()
        LiveBus.stats { it.copy(isLive = false, youtubeState = "Mati", tiktokState = "Mati", recording = false) }
        Thread {
            val clips = g.recordings.finish(settings.features.momentClips) { LiveBus.feed(it) }
            brain.transcriptText().let { g.recordings.addTranscript(it) }
            if (wasRecording) {
                val msg = if (clips > 0) "Selesai, $clips video siap." else "Rekaman tersimpan."
                main.post { g.tts.whisper(msg); LiveBus.announce(msg) }
            }
            main.post(onStopped)
        }.start()
    }

    // ---------------------------------------------------------------- kendali
    fun setMic(on: Boolean) {
        val vol = if (on) settings.micFx.micVolume else 0f
        when (val a = audioSource) {
            is MixAudioSource -> a.microphoneVolume = vol
            is MicrophoneSource -> a.microphoneVolume = vol
        }
        compositor.hostMicOn = on
        LiveBus.stats { it.copy(micOn = on) }
    }

    /** Bagikan audio (suara aplikasi lain/internal) nyala/mati. */
    fun setShareAudio(on: Boolean) {
        (audioSource as? MixAudioSource)?.internalVolume = if (on) settings.micFx.internalVolume else 0f
        LiveBus.stats { it.copy(shareAudioOn = on && audioSource is MixAudioSource) }
    }

    fun updateMicFx() { effect?.fx = settings.micFx }

    fun switchFrontBack(front: Boolean? = null) {
        val cam = camera2
        if (cam != null) {
            val isFront = cam.getCameraFacing() == CameraHelper.Facing.FRONT
            if (front == null || front != isFront) runCatching { cam.switchCamera() }
        } else bgReplacer?.let { r ->
            r.front = front ?: !r.front
            r.stop(); r.start(owner, settings.background)
        }
        activeCameraName = if (front ?: true) "depan" else "belakang"
        LiveBus.stats { it.copy(activeCamera = activeCameraName) }
    }

    /** Pindah ke kamera bernama (kamera depan/belakang, webcam USB, atau HP lain lewat link). */
    fun switchTo(name: String): Boolean {
        val n = name.lowercase().trim()
        val names = settings.cameraNames
        when {
            names["depan"]?.lowercase()?.contains(n) == true || n.contains("depan") -> { toCamera2(); switchFrontBack(true); return true }
            names["belakang"]?.lowercase()?.contains(n) == true || n.contains("belakang") -> { toCamera2(); switchFrontBack(false); return true }
            n.contains("usb") || n.contains("webcam") -> { change(CameraUvcSource()); activeCameraName = "usb"; return true }
        }
        val remote = g.guests.remotes.values.firstOrNull { it.mode == "camera" && (it.name.lowercase().contains(n) || n.contains(it.name.lowercase())) }
            ?: return false
        val src = CanvasVideoSource()
        change(src)
        g.guests.onFrame = { r -> if (r.id == remote.id) r.frame?.let { src.submit(it) } }
        activeCameraName = remote.name
        LiveBus.stats { it.copy(activeCamera = remote.name) }
        return true
    }

    private fun toCamera2() {
        if (stream.videoSource is Camera2Source) return
        change(Camera2Source(context).also { camera2 = it })
    }

    private fun change(src: VideoSource) {
        runCatching { stream.changeVideoSource(src) }.onFailure { LiveBus.feed("Gagal ganti kamera: ${it.message}") }
        if (src !is Camera2Source) camera2 = null
    }

    /** Hentikan berbagi layar: kembali ke kamera, live tetap jalan. */
    fun stopScreenShare() {
        if (mode != LiveMode.SCREEN) return
        mode = LiveMode.CAMERA
        toCamera2()
        LiveBus.stats { it.copy(screenSharing = false) }
    }

    fun cameraNames(): List<String> = buildList {
        addAll(settings.cameraNames.values)
        add("webcam usb")
        g.guests.remotes.values.filter { it.mode == "camera" }.forEach { add(it.name) }
    }

    val isRunning: Boolean get() = this::stream.isInitialized && (stream.isStreaming || stream.isRecording)

    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (cm.activeNetwork == null) {
                    wasOffline = true
                    LiveBus.stats { it.copy(network = "Terputus") }
                    g.tts.whisper("Peringatan, koneksi internet terputus.")
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val kbps = caps.linkUpstreamBandwidthKbps
                val label = when { kbps <= 0 -> "Tidak diketahui"; kbps < settings.video.bitrateKbps -> "Lemah"; else -> "Baik" }
                LiveBus.stats { it.copy(network = label) }
                if (wasOffline && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    wasOffline = false
                    g.tts.whisper("Koneksi kembali.")
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    private fun loadImage(path: String): Bitmap? = runCatching {
        if (path.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun defaultCover(): Bitmap {
        val b = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        CinematicItem("malam", "Sedang siaran suara", "Stream By Natan", 1, false).draw(c, 720, 1280, 4000)
        return b
    }

    /** Tamu disusun bersama host (ukuran sama / pembicara penuh layar) hanya di mode kamera. */
    val composesGuests: Boolean get() = mode == LiveMode.CAMERA && bgReplacer != null

    private fun onHostFrame(bmp: Bitmap) {
        val src = canvasSource ?: return
        val onStage = g.guests.stage.value
        if (settings.features.guests && onStage.any { it.status == "onstage" }) src.submit(compositor.render(bmp, onStage))
        else src.submit(bmp)
    }

    @Volatile private var levelForUi: ((Float) -> Unit)? = null
    fun micLevelListener(l: ((Float) -> Unit)?) { levelForUi = l }
    fun overlayBackgroundColor(): Int = Color.TRANSPARENT
}
