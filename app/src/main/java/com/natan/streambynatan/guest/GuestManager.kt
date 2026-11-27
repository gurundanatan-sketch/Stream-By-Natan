package com.natan.streambynatan.guest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.natan.streambynatan.data.GuestConfig
import com.natan.streambynatan.live.LiveBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.AudioTrack
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
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tamu live & kamera tambahan dari HP lain lewat WebRTC.
 * - Banyak orang bisa bergabung ke RUANG TUNGGU, antre berdasarkan siapa yang datang lebih dulu.
 * - Host memilih memasukkan 1 orang atau lebih.
 * - Host bisa membisukan mikrofon tamu (tamu bisa menyalakannya lagi sendiri) dan mengeluarkan tamu.
 * - Tamu bisa berbagi layar dari browser.
 * Firebase (Firestore) hanya jadi perantara (signaling).
 */
class GuestManager(private val context: Context) {
    data class Remote(
        val id: String,
        val name: String,
        val mode: String,              // guest | camera
        val joinedAt: Long,
        @Volatile var status: String = "waiting", // waiting | admitted | onstage
        @Volatile var frame: Bitmap? = null,
        @Volatile var micOn: Boolean = true,
        @Volatile var sharingScreen: Boolean = false,
        @Volatile var level: Float = 0f,
        @Volatile var lastSpokeAt: Long = 0L,
    )

    val remotes = ConcurrentHashMap<String, Remote>()
    private val _waiting = MutableStateFlow<List<Remote>>(emptyList())
    /** Ruang tunggu, urut siapa yang datang lebih dulu. */
    val waiting: StateFlow<List<Remote>> = _waiting
    private val _stage = MutableStateFlow<List<Remote>>(emptyList())
    /** Tamu yang sudah naik ke siaran. */
    val stage: StateFlow<List<Remote>> = _stage

    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val registrations = ArrayList<ListenerRegistration>()
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var localAudio: AudioTrack? = null
    private var cfg = GuestConfig()
    private val main = Handler(Looper.getMainLooper())
    var roomId: String = ""
        private set
    var onRemotesChanged: (() -> Unit)? = null
    var onFrame: ((Remote) -> Unit)? = null
    @Volatile var started = false; private set

    private val db: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private fun peersCol() = db?.collection("rooms")?.document(roomId)?.collection("peers")

    fun link(cfg: GuestConfig, mode: String): String {
        val base = cfg.webBaseUrl.trimEnd('/').ifBlank { "https://PROYEK-FIREBASE.web.app" }
        return "$base/guest/?room=$roomId&mode=$mode"
    }

    fun start(config: GuestConfig): Boolean {
        val store = db ?: run { LiveBus.feed("Tamu: Firebase belum diatur."); return false }
        cfg = config
        if (started) return true
        if (factory == null) init()
        roomId = roomId.ifBlank { UUID.randomUUID().toString().take(8) }
        store.collection("rooms").document(roomId).set(mapOf("createdAt" to System.currentTimeMillis(), "open" to true))
        registrations += store.collection("rooms").document(roomId).collection("peers")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { ch ->
                    val d = ch.document
                    if (ch.type == DocumentChange.Type.REMOVED) { drop(d.id, notify = false); return@forEach }
                    val status = d.getString("status") ?: "waiting"
                    if (status == "left" || status == "kicked") { drop(d.id, notify = false); return@forEach }
                    val r = remotes.getOrPut(d.id) {
                        Remote(d.id, d.getString("name") ?: "Tamu", d.getString("mode") ?: "guest", d.getLong("joinedAt") ?: System.currentTimeMillis())
                            .also { if (it.mode == "guest") LiveBus.announce("${it.name} masuk ruang tunggu") }
                    }
                    r.micOn = d.getBoolean("micOn") ?: true
                    r.sharingScreen = d.getBoolean("screen") ?: false
                    if (r.status == "waiting" && status == "admitted") r.status = "admitted"
                    // kamera tambahan dari HP sendiri langsung diterima
                    if (r.mode == "camera" && r.status == "waiting") admit(listOf(r.id))
                    val offer = d.get("offer") as? Map<*, *>
                    if (offer != null && r.status != "waiting" && !peers.containsKey(d.id) && d.get("answer") == null) {
                        accept(d.id, offer["sdp"] as? String ?: return@forEach)
                    }
                }
                publish()
            }
        started = true
        main.post(statsTick)
        return true
    }

    private fun publish() {
        _waiting.value = remotes.values.filter { it.status == "waiting" && it.mode == "guest" }.sortedBy { it.joinedAt }
        _stage.value = remotes.values.filter { it.status != "waiting" && it.mode == "guest" }.sortedBy { it.joinedAt }
        onRemotesChanged?.invoke()
    }

    /** Memasukkan satu atau beberapa tamu dari ruang tunggu (urutan antrean tetap dihormati). */
    fun admit(ids: List<String>) {
        ids.mapNotNull { remotes[it] }.sortedBy { it.joinedAt }.forEach { r ->
            r.status = "admitted"
            peersCol()?.document(r.id)?.update("status", "admitted")
        }
        publish()
    }

    fun admitNext(count: Int = 1) = admit(_waiting.value.take(count).map { it.id })

    /** Membisukan mikrofon tamu. Tamu tetap bisa menyalakannya lagi sendiri. */
    fun muteGuest(id: String) {
        peersCol()?.document(id)?.update(mapOf("hostMuteAt" to System.currentTimeMillis(), "micOn" to false))
        remotes[id]?.micOn = false
        LiveBus.announce("Mikrofon ${remotes[id]?.name ?: "tamu"} dibisukan")
        publish()
    }

    /** Mengeluarkan tamu dari siaran (atau dari ruang tunggu). */
    fun kick(id: String) {
        val name = remotes[id]?.name ?: "Tamu"
        peersCol()?.document(id)?.update("status", "kicked")
        drop(id)
        LiveBus.announce("$name dikeluarkan")
    }

    private fun init() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val e = EglBase.create(); egl = e
        // Suara tamu diputar sebagai "media" supaya ikut tertangkap audio internal dan tersiar.
        val adm = JavaAudioDeviceModule.builder(context)
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
            .createPeerConnectionFactory()
        val f = factory!!
        localAudio = f.createAudioTrack("host-audio", f.createAudioSource(MediaConstraints()))
    }

    private fun iceServers(): List<PeerConnection.IceServer> {
        val list = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        if (cfg.turnUrl.isNotBlank()) list += PeerConnection.IceServer.builder(cfg.turnUrl).setUsername(cfg.turnUser).setPassword(cfg.turnPass).createIceServer()
        return list
    }

    private fun accept(peerId: String, offerSdp: String) {
        val f = factory ?: return
        val peerDoc = peersCol()?.document(peerId) ?: return
        val remote = remotes[peerId] ?: return
        val rtc = PeerConnection.RTCConfiguration(iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val pc = f.createPeerConnection(rtc, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                peerDoc.collection("hostCandidates").add(mapOf("candidate" to c.sdp, "sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex))
            }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.addSink(FrameGrabber(remote))
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                LiveBus.feed("${remote.name}: ${newState.name.lowercase()}")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> { remote.status = "onstage"; main.post { publish() } }
                    PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> main.post { drop(peerId) }
                    else -> Unit
                }
            }
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
        peers[peerId] = pc
        if (remote.mode == "guest") localAudio?.let { pc.addTrack(it, listOf("host")) }
        pc.setRemoteDescription(object : SimpleSdp() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SimpleSdp() {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        pc.setLocalDescription(SimpleSdp(), sdp)
                        peerDoc.update("answer", mapOf("type" to "answer", "sdp" to sdp.description))
                    }
                }, MediaConstraints())
            }
        }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        registrations += peerDoc.collection("guestCandidates").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch ->
                val d = ch.document
                pc.addIceCandidate(IceCandidate(d.getString("sdpMid"), (d.getLong("sdpMLineIndex") ?: 0L).toInt(), d.getString("candidate") ?: return@forEach))
            }
        }
        LiveBus.announce("${remote.name} naik sebagai ${if (remote.mode == "camera") "kamera tambahan" else "tamu"}")
    }

    /** Membaca tingkat suara tiap tamu (untuk tahu siapa yang sedang berbicara). */
    private val statsTick = object : Runnable {
        override fun run() {
            if (!started) return
            for ((id, pc) in peers) {
                val r = remotes[id] ?: continue
                runCatching {
                    pc.getStats { report ->
                        var lvl = 0.0
                        for (st in report.statsMap.values) {
                            if (st.type == "inbound-rtp" && st.members["kind"] == "audio") {
                                lvl = maxOf(lvl, (st.members["audioLevel"] as? Number)?.toDouble() ?: 0.0)
                            }
                        }
                        r.level = lvl.toFloat()
                        if (lvl > 0.04 && r.micOn) r.lastSpokeAt = System.currentTimeMillis()
                    }
                }
            }
            main.postDelayed(this, 300)
        }
    }

    fun drop(peerId: String, notify: Boolean = true) {
        peers.remove(peerId)?.let { runCatching { it.close() } }
        remotes.remove(peerId)
        if (notify) publish() else main.post { publish() }
    }

    fun stop() {
        started = false
        registrations.forEach { it.remove() }
        registrations.clear()
        peers.keys.toList().forEach { drop(it) }
        remotes.clear()
        publish()
        if (roomId.isNotBlank()) db?.collection("rooms")?.document(roomId)?.update("open", false)
    }

    /** Mengambil gambar dari video tamu (sekitar 12 fps) untuk ditempel ke siaran. */
    private inner class FrameGrabber(private val remote: Remote) : VideoSink {
        private var last = 0L
        override fun onFrame(frame: VideoFrame) {
            val now = System.currentTimeMillis()
            if (now - last < 80) return
            last = now
            runCatching {
                val i420 = frame.buffer.toI420() ?: return
                try {
                    val step = if (i420.width > 960) 2 else 1
                    val w = i420.width / step; val h = i420.height / step
                    val px = IntArray(w * h)
                    val y = i420.dataY; val u = i420.dataU; val v = i420.dataV
                    for (row in 0 until h) {
                        val sy = row * step
                        for (col in 0 until w) {
                            val sx = col * step
                            val yy = (y.get(sy * i420.strideY + sx).toInt() and 0xFF) - 16
                            val uu = (u.get((sy / 2) * i420.strideU + sx / 2).toInt() and 0xFF) - 128
                            val vv = (v.get((sy / 2) * i420.strideV + sx / 2).toInt() and 0xFF) - 128
                            val c = 1.164f * yy
                            val r = (c + 1.596f * vv).toInt().coerceIn(0, 255)
                            val g = (c - 0.392f * uu - 0.813f * vv).toInt().coerceIn(0, 255)
                            val b = (c + 2.017f * uu).toInt().coerceIn(0, 255)
                            px[row * w + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    var bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                    if (frame.rotation != 0) {
                        bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, Matrix().apply { postRotate(frame.rotation.toFloat()) }, true)
                    }
                    remote.frame = bmp
                    onFrame?.invoke(remote)
                } finally { i420.release() }
            }
        }
    }

    open class SimpleSdp : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { LiveBus.feed("WebRTC: $error") }
        override fun onSetFailure(error: String?) { LiveBus.feed("WebRTC: $error") }
    }
}
