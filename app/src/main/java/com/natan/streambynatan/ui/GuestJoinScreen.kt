package com.natan.streambynatan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.guest.GuestClient
import com.natan.streambynatan.ui.theme.Neon
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * Layar TAMU di dalam aplikasi (gratis, tanpa perlu akun).
 * Dibuka dari link undangan host atau dari kode undangan.
 */
@Composable
fun GuestJoinScreen(activity: MainActivity, invite: GuestInvite, onClose: () -> Unit) {
    val gc = Graph.guestClient
    val ui by gc.ui.collectAsState()
    val isCamera = invite.mode == "camera"
    fun hasPerms() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }
    var perms by remember { mutableStateOf(hasPerms()) }
    var cameraReady by remember { mutableStateOf(false) }
    val prefs = remember { activity.getSharedPreferences("tamu", android.content.Context.MODE_PRIVATE) }
    // nama terakhir yang dipakai diingat, supaya tamu tidak perlu mengetik lagi
    var name by remember { mutableStateOf(prefs.getString(if (isCamera) "nama_kamera" else "nama", null) ?: Graph.firebase.displayName.orEmpty()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms = hasPerms() }

    LaunchedEffect(perms) {
        if (perms && !cameraReady) { runCatching { gc.startCamera(front = !isCamera) }; cameraReady = true }
    }

    val active = ui.phase == GuestClient.Phase.WAITING || ui.phase == GuestClient.Phase.CONNECTING || ui.phase == GuestClient.Phase.ONSTAGE
    fun close() { gc.reset(); onClose() }
    BackHandler { if (active) gc.leave() else close() }

    ScreenColumn {
        Spacer(Modifier.height(16.dp))
        Text("STREAM BY NATAN · TAMU", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
        ScreenTitle(
            if (isCamera) "Jadi kamera tambahan" else "Gabung sebagai tamu live",
            if (isCamera) "HP ini akan menjadi kamera tambahan untuk siaran host." else "Gratis. Kamera, mikrofon, dan berbagi layar dari HP-mu.",
        )

        if (!perms) {
            GlassCard {
                Text("Izinkan kamera dan mikrofon supaya host bisa melihat dan mendengarmu.")
                NeonButton("Izinkan kamera & mikrofon") {
                    askPerms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
            }
        } else if (cameraReady && gc.egl != null) {
            Box(
                Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(22.dp)).background(Neon.Deep)
                    .semantics { contentDescription = if (ui.sharing) "Pratinjau layar yang kamu bagikan" else "Pratinjau kamera kamu" },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(gc.egl!!.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            setMirror(!isCamera)
                            gc.setPreview(this)
                        }
                    },
                    update = { it.setMirror(ui.frontCamera && !ui.sharing) },
                    onRelease = { gc.setPreview(null); it.release() },
                )
            }
        }

        if (ui.status.isNotBlank()) {
            GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(ui.status, style = MaterialTheme.typography.titleMedium)
                if (ui.myName.isNotBlank()) Text("Tampil sebagai: ${ui.myName}", color = Neon.Cyan)
                if (ui.phase == GuestClient.Phase.WAITING && ui.queueSize > 0) {
                    Text("Antrean ke-${ui.queuePos} dari ${ui.queueSize}", color = Neon.Muted)
                }
            }
        }

        when (ui.phase) {
            GuestClient.Phase.IDLE -> GlassCard {
                Text("Kode ruang: ${invite.room}", color = Neon.Muted)
                Text(
                    if (isCamera) "Beri nama kamera ini supaya host tahu kamera mana yang tersambung."
                    else "Nama wajib diisi. Nama ini tampil di ruang tunggu host dan di layar siaran, jadi penonton juga tahu siapa kamu.",
                    color = Neon.Muted,
                )
                Field(if (isCamera) "Nama kamera (misal: kamera mimbar)" else "Nama kamu (wajib)", name) {
                    name = it.take(GuestClient.MAX_NAME); nameError = null
                }
                nameError?.let { Text(it, color = Neon.Red, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                NeonButton(if (isCamera) "Sambungkan kamera" else "Masuk ruang tunggu", enabled = perms, icon = Icons.Filled.Login) {
                    val clean = GuestClient.cleanName(name)
                    if (clean.length < 2) {
                        nameError = if (isCamera) "Isi nama kamera dulu, minimal 2 huruf." else "Isi nama kamu dulu, minimal 2 huruf."
                    } else {
                        prefs.edit().putString(if (isCamera) "nama_kamera" else "nama", clean).apply()
                        gc.join(invite.room, clean, invite.mode)
                    }
                }
                GhostButton("Batal") { close() }
            }
            GuestClient.Phase.WAITING, GuestClient.Phase.CONNECTING, GuestClient.Phase.ONSTAGE -> GlassCard {
                if (!isCamera) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (ui.micOn) "Matikan mikrofon" else "Nyalakan mikrofon", Modifier.weight(1f), if (ui.micOn) Icons.Filled.Mic else Icons.Filled.MicOff) {
                        gc.setMic(!ui.micOn)
                    }
                    GhostButton(if (ui.camOn) "Matikan kamera" else "Nyalakan kamera", Modifier.weight(1f), if (ui.camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff) {
                        gc.setCam(!ui.camOn)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Ganti kamera depan/belakang", Modifier.weight(1f), Icons.Filled.Cameraswitch) { gc.flipCamera() }
                    if (!isCamera) GhostButton(if (ui.sharing) "Hentikan berbagi layar" else "Bagikan layar", Modifier.weight(1f),
                        if (ui.sharing) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare) {
                        if (ui.sharing) gc.stopScreen() else activity.requestGuestScreenShare()
                    }
                }
                NeonButton("Keluar dari live", brush = Neon.live, icon = Icons.Filled.CallEnd) { gc.leave() }
            }
            GuestClient.Phase.KICKED, GuestClient.Phase.LEFT, GuestClient.Phase.ERROR -> GlassCard {
                NeonButton("Tutup") { close() }
            }
        }
    }
}
