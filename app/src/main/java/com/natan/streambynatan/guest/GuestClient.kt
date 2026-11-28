package com.natan.streambynatan.guest

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Sisi TAMU di dalam aplikasi (gratis): kamera, mikrofon, dan berbagi layar HP.
 * Alurnya sama dengan halaman tamu di browser:
 * ruang tunggu (antre siapa yang duluan) → host memasukkan → tampil di siaran.
 * Host bisa membisukan mikrofon (tamu bisa menyalakannya lagi sendiri) dan mengeluarkan tamu.
 */
class GuestClient(private val context: Context) {

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val status: String = "",
        val queuePos: Int = 0,
        val queueSize: Int = 0,
        val micOn: Boolean = true,
        val camOn: Boolean = true,
        val sharing: Boolean = false,
        val frontCamera: Boolean = true,
        val myName: String = "",
        val filter: String = "normal",
        /** Percakapan dengan host: (dari, isi). */
        val messages: List<Pair<String, String>> = emptyList(),
    )
    enum class Phase { IDLE, WAITING, CONNECTING, ONSTAGE, KICKED, LEFT, ERROR }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui
    private val main = Handler(Looper.getMainLooper())

    private var factory: PeerConnectionFactory? = null
    var egl: EglBase? = null; private set
    private var pc: PeerConnection? = null
    private var videoSender: RtpSender? = null

    private var camCapturer: VideoCapturer? = null
    private var camHelper: SurfaceTextureHelper? = null
    private var camSource: VideoSource? = null
    private var camTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null

    private var screenCapturer: VideoCapturer? = null
    private var screenHelper: SurfaceTextureHelper? = null
    private var screenSource: VideoSource? = null
    private var screenTrack: VideoTrack? = null

    private var peerRef: DocumentReference? = null
    private val regs = ArrayList<ListenerRegistration>()
    private var lastHostMute = 0L
    private var mode = "guest"
    private var preview: VideoSink? = null
    private val addedHostCandidates = HashSet<String>()

    private val db: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private fun set(block: (Ui) -> Ui) = _ui.update(block)

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val e = EglBase.create(); egl = e
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    /** Menyalakan kamera untuk pratinjau (sebelum masuk ruang tunggu). */
    fun startCamera(front: Boolean = true) {
        ensureFactory()
        if (camTrack != null) return
        val f = factory ?: return
        val e = egl ?: return
        val en = Camera2Enumerator(context)
        val name = en.deviceNames.firstOrNull { en.isFrontFacing(it) == front } ?: en.deviceNames.firstOrNull() ?: return
        val cap = en.createCapturer(name, null)
        val helper = SurfaceTextureHelper.create("tamu-kamera", e.eglBaseContext)
        val src = f.createVideoSource(false)
        cap.initialize(helper, context, src.capturerObserver)
        cap.startCapture(720, 1280, 30)
        camCapturer = cap; camHelper = helper; camSource = src
        camTrack = f.createVideoTrack("tamu-video", src).also { t -> preview?.let { t.addSink(it) } }
        set { it.copy(frontCamera = front) }
    }

    /** Pratinjau di layar tamu (kamera atau layar yang sedang dibagikan). */
    fun setPreview(sink: VideoSink?) {
        val old = preview
        if (old != null) { camTrack?.removeSink(old); screenTrack?.removeSink(old) }
        preview = sink
        if (sink != null) (if (_ui.value.sharing) screenTrack else camTrack)?.addSink(sink)
    }

    /** Masuk ruang tunggu. */
    fun join(room: String, name: String, joinMode: String) {
        val store = db ?: run { set { it.copy(phase = Phase.ERROR, status = "Firebase belum diatur di versi aplikasi ini.") }; return }
        mode = if (joinMode == "camera") "camera" else "guest"
        startCamera(front = mode != "camera")
        val f = factory ?: return
        if (mode == "guest" && audioTrack == null) {
            val c = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            audioTrack = f.createAudioTrack("tamu-audio", f.createAudioSource(c))
        }
        val col = store.collection("rooms").document(room).collection("peers")
        val ref = col.document(); peerRef = ref
        val shown = cleanName(name).ifBlank { if (mode == "camera") "Kamera tambahan" else "Tamu" }
        set { it.copy(myName = shown) }
        ref.set(mapOf(
            "name" to shown,
            "mode" to mode, "status" to "waiting", "joinedAt" to System.currentTimeMillis(),
            "micOn" to (mode == "guest"), "screen" to false, "app" to "android", "filter" to _ui.value.filter,
        ))
        set { it.copy(phase = Phase.WAITING, status = if (mode == "camera") "Menyambung ke host…" else "Kamu di ruang tunggu. Tunggu host memasukkanmu.", micOn = mode == "guest") }
        GuestService.start(context, screen = false)
        if (mode == "guest") {
            regs += col.whereEqualTo("status", "waiting").addSnapshotListener { snap, _ ->
                val list = snap?.documents?.sortedBy { it.getLong("joinedAt") ?: 0L } ?: return@addSnapshotListener
                val pos = list.indexOfFirst { it.id == ref.id }
                if (pos >= 0) set { it.copy(queuePos = pos + 1, queueSize = list.size, status = "Kamu di ruang tunggu, antrean ke-${pos + 1} dari ${list.size}. Tunggu host memasukkanmu.") }
            }
        }
        regs += ref.addSnapshotListener { doc, _ -> doc?.let { onMyDoc(it) } }
        // balasan host / moderator
        regs += ref.collection("messages").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch ->
                val d = ch.document
                val from = d.getString("from") ?: return@forEach
                val label = if (from == "guest") "Kamu" else (if (from == "mod") "Moderator " else "Host ") + d.getString("name").orEmpty()
                set { it.copy(messages = (it.messages + (label to d.getString("text").orEmpty())).takeLast(50)) }
            }
        }
    }

    private fun onMyDoc(d: DocumentSnapshot) {
        val status = d.getString("status") ?: return
        if (status == "admitted" && pc == null) {
            set { it.copy(phase = Phase.CONNECTING, status = "Host memasukkanmu. Menyambung…") }
            connect()
        }
        if (status == "kicked") {
            set { it.copy(phase = Phase.KICKED, status = "Kamu dikeluarkan dari siaran oleh host.") }
            cleanup()
            return
        }
        val hm = d.getLong("hostMuteAt") ?: 0L
        if (hm != 0L && hm != lastHostMute) {
            val stale = lastHostMute == 0L && System.currentTimeMillis() - hm > 10_000
            lastHostMute = hm
            if (!stale) {
                setMic(false, report = false)
                set { it.copy(status = "Mikrofonmu dibisukan host. Kamu bisa menyalakannya lagi dengan tombol Nyalakan mikrofon.") }
            }
        }
        @Suppress("UNCHECKED_CAST")
        val answer = d.get("answer") as? Map<String, Any?>
        val p = pc
        if (answer != null && p != null && p.remoteDescription == null) {
            val sdp = answer["sdp"] as? String ?: return
            p.setRemoteDescription(GuestManager.SimpleSdp(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
    }

    private fun connect() {
        val f = factory ?: return
        val ref = peerRef ?: return
        val ice = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        val rtc = PeerConnection.RTCConfiguration(ice).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val p = f.createPeerConnection(rtc, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                ref.collection("guestCandidates").add(mapOf("candidate" to c.sdp, "sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex))
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> set {
                        it.copy(phase = Phase.ONSTAGE, status = if (mode == "camera") "Tersambung sebagai kamera tambahan." else "Kamu sudah tampil di siaran!")
                    }
                    PeerConnection.PeerConnectionState.FAILED -> set { it.copy(phase = Phase.ERROR, status = "Sambungan gagal. Keluar lalu gabung lagi.") }
                    else -> Unit
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) { /* suara host diputar otomatis */ }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: return
        pc = p
        audioTrack?.let { p.addTrack(it, listOf("tamu")) }
        val v = if (_ui.value.sharing) screenTrack else camTrack
        v?.let { videoSender = p.addTrack(it, listOf("tamu")) }
        regs += ref.collection("hostCandidates").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch ->
                val d = ch.document
                if (!addedHostCandidates.add(d.id)) return@forEach
                val sdp = d.getString("candidate") ?: return@forEach
                p.addIceCandidate(IceCandidate(d.getString("sdpMid"), (d.getLong("sdpMLineIndex") ?: 0L).toInt(), sdp))
            }
        }
        p.createOffer(object : GuestManager.SimpleSdp() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                p.setLocalDescription(GuestManager.SimpleSdp(), sdp)
                ref.update("offer", mapOf("type" to "offer", "sdp" to sdp.description))
            }
        }, MediaConstraints())
    }

    /** Kirim pesan ke host dari ruang tunggu (disaring kata kasar oleh host). */
    fun sendMessage(text: String) {
        val t = text.trim().take(300)
        if (t.isBlank()) return
        peerRef?.collection("messages")?.add(mapOf("from" to "guest", "name" to _ui.value.myName, "text" to t, "at" to System.currentTimeMillis()))
    }

    /** Filter video tamu (diterapkan host saat menggambar video tamu ke siaran). */
    fun setFilter(id: String) {
        peerRef?.update("filter", id)
        set { it.copy(filter = id, status = "Filter: " + com.natan.streambynatan.features.Filters.byId(id).label) }
    }

    fun setMic(on: Boolean, report: Boolean = true) {
        audioTrack?.setEnabled(on)
        peerRef?.update("micOn", on)
        set { it.copy(micOn = on, status = if (report) (if (on) "Mikrofon menyala." else "Mikrofon mati.") else it.status) }
    }

    fun setCam(on: Boolean) {
        camTrack?.setEnabled(on)
        set { it.copy(camOn = on, status = if (on) "Kamera menyala." else "Kamera mati.") }
    }

    fun flipCamera() {
        val cap = camCapturer as? CameraVideoCapturer ?: return
        cap.switchCamera(null)
        set { it.copy(frontCamera = !it.frontCamera, status = if (it.frontCamera) "Kamera belakang." else "Kamera depan.") }
    }

    /** Berbagi layar HP. Dipanggil setelah izin rekam layar diberikan dan layanan latar depan siap. */
    fun startScreen(permissionData: Intent) {
        ensureFactory()
        val f = factory ?: return
        val e = egl ?: return
        stopScreenInternal()
        val cap = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopScreen() } }
        })
        val helper = SurfaceTextureHelper.create("tamu-layar", e.eglBaseContext)
        val src = f.createVideoSource(true)
        cap.initialize(helper, context, src.capturerObserver)
        val dm = context.resources.displayMetrics
        val scale = minOf(1f, 720f / minOf(dm.widthPixels, dm.heightPixels))
        cap.startCapture((dm.widthPixels * scale).toInt(), (dm.heightPixels * scale).toInt(), 15)
        val track = f.createVideoTrack("tamu-layar", src)
        screenCapturer = cap; screenHelper = helper; screenSource = src; screenTrack = track
        preview?.let { camTrack?.removeSink(it); track.addSink(it) }
        videoSender?.setTrack(track, false)
        peerRef?.update("screen", true)
        set { it.copy(sharing = true, status = "Kamu sedang berbagi layar.") }
    }

    fun stopScreen() {
        if (screenTrack == null) return
        videoSender?.setTrack(camTrack, false)
        preview?.let { screenTrack?.removeSink(it); camTrack?.addSink(it) }
        stopScreenInternal()
        peerRef?.update("screen", false)
        GuestService.start(context, screen = false)
        set { it.copy(sharing = false, status = "Berbagi layar dihentikan.") }
    }

    private fun stopScreenInternal() {
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        runCatching { screenHelper?.dispose() }
        screenCapturer = null; screenTrack = null; screenSource = null; screenHelper = null
    }

    fun leave() {
        peerRef?.update("status", "left")
        set { it.copy(phase = Phase.LEFT, status = "Kamu sudah keluar. Terima kasih!") }
        cleanup()
    }

    /** Kembali ke keadaan awal (setelah keluar / dikeluarkan). */
    fun reset() { cleanup(); _ui.value = Ui() }

    private fun cleanup() {
        regs.forEach { it.remove() }; regs.clear()
        addedHostCandidates.clear()
        runCatching { pc?.close() }; pc = null; videoSender = null
        stopScreenInternal()
        preview?.let { camTrack?.removeSink(it) }
        runCatching { camCapturer?.stopCapture() }
        runCatching { camCapturer?.dispose() }
        runCatching { camTrack?.dispose() }
        runCatching { camSource?.dispose() }
        runCatching { camHelper?.dispose() }
        runCatching { audioTrack?.dispose() }
        camCapturer = null; camTrack = null; camSource = null; camHelper = null; audioTrack = null
        peerRef = null; lastHostMute = 0L
        GuestService.stop(context)
    }

    companion object {
        const val MAX_NAME = 30
        /** Merapikan nama tamu: tanpa spasi berlebih & karakter aneh, maksimal 30 huruf. */
        fun cleanName(raw: String): String =
            raw.replace(Regex("[\\p{Cntrl}<>]"), "").replace(Regex("\\s+"), " ").trim().take(MAX_NAME)
    }
}
