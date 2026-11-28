package com.natan.streambynatan.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.natan.streambynatan.features.SystemHelpers
import com.natan.streambynatan.features.Updater
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.LiveSummary
import com.natan.streambynatan.live.RecordingManager
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoginScreen(activity: MainActivity, onDone: () -> Unit) {
    var email by remember { mutableStateOf(activity.pendingLoginEmail.value) }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        Spacer(Modifier.height(30.dp))
        Text("STREAM BY NATAN", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
        ScreenTitle("Masuk", "Masuk dengan email atau akun Google untuk mulai memakai aplikasi.")
        JoinAsGuestCard(activity)
        if (!Graph.firebase.available) {
            GlassCard {
                Text("Firebase belum diatur di versi ini (google-services.json belum ada), jadi login belum bisa dipakai.", color = Neon.Amber)
                NeonButton("Lanjut tanpa akun") { onDone() }
            }
            return@ScreenColumn
        }
        // Beralih akun Google: langsung buka pemilih akun
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (activity.pendingGoogleLogin.value) {
                activity.pendingGoogleLogin.value = false
                busy = true; error = Graph.firebase.signInWithGoogle(activity); busy = false; if (error == null) onDone()
            }
        }
        GlassCard {
            NeonButton(if (busy) "Memproses…" else "Masuk dengan Google", enabled = !busy, icon = Icons.Filled.AccountCircle) {
                busy = true
                scope.launch { error = Graph.firebase.signInWithGoogle(activity); busy = false; if (error == null) onDone() }
            }
        }
        SectionTitle("Atau dengan email")
        GlassCard {
            Field("Email", email) { email = it.trim() }
            Field("Kata sandi (minimal 6 huruf)", pass, secret = true) { pass = it }
            error?.let { Text(it, color = Neon.Red, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
            NeonButton(if (busy) "Memproses…" else "Masuk dengan email", enabled = !busy && email.isNotBlank() && pass.length >= 6) {
                busy = true
                scope.launch { error = Graph.firebase.signIn(email, pass); busy = false; if (error == null) onDone() }
            }
            GhostButton("Daftar akun baru dengan email") {
                busy = true
                scope.launch { error = Graph.firebase.register(email, pass); busy = false; if (error == null) onDone() }
            }
            GhostButton("Lupa kata sandi") { scope.launch { error = Graph.firebase.resetPassword(email) ?: "Email atur ulang sudah dikirim." } }
        }
    }
}

/** Langkah pertama: berikan semua izin aplikasi, baru login. */
@Composable
fun PermissionScreen(activity: MainActivity, onDone: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    val basic = remember(tick) { activity.basicPermissionsGranted() }
    val overlay = remember(tick) { SystemHelpers.canDrawOverlays(activity) }
    val battery = remember(tick) { SystemHelpers.isIgnoringBattery(activity) }
    val dnd = remember(tick) { SystemHelpers.hasDndAccess(activity) }
    val install = remember(tick) { Updater.canInstall(activity) }
    val media = remember(tick) { activity.mediaPermissionsGranted() }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); tick++ } }
    ScreenColumn {
        Spacer(Modifier.height(30.dp))
        Text("STREAM BY NATAN", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
        ScreenTitle("Izin aplikasi", "Berikan izin berikut supaya semua fitur live bisa berjalan.")
        GlassCard {
            PermissionRow("Kamera, mikrofon, dan notifikasi", basic) { activity.askBasicPermissions() }
            PermissionRow("Jendela mengambang (untuk berbagi layar)", overlay) { SystemHelpers.requestOverlay(activity) }
            PermissionRow("Bebas penghemat baterai (live tidak terputus)", battery) { SystemHelpers.requestIgnoreBattery(activity) }
            PermissionRow("Akses Jangan Ganggu", dnd) { SystemHelpers.requestDnd(activity) }
            PermissionRow("Pasang aplikasi tidak dikenal (pembaruan otomatis)", install) { Updater.openInstallPermission(activity) }
            PermissionRow("Foto dan video, musik dan audio (latar, overlay, tema, musik dari galeri)", media) { activity.askMediaPermissions() }
        }
        NeonButton(if (basic) "Lanjut" else "Berikan izin dulu", enabled = basic) { onDone() }
        Text("Kamera dan mikrofon wajib. Izin lainnya bisa diberikan nanti di Pengaturan > Sistem.", color = Neon.Muted)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, request: () -> Unit) {
    if (granted) Text("✓ $label: diizinkan", style = MaterialTheme.typography.bodyLarge, color = Neon.Lime,
        modifier = Modifier.semantics { contentDescription = "$label, sudah diizinkan" })
    else GhostButton("Izinkan: $label", Modifier.fillMaxWidth(), Icons.Filled.Security, request)
}

/** Pustaka rekaman di dalam aplikasi: video penuh, potongan per pertanyaan/poin (judul otomatis), transkrip. */
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(Graph.local.recordings()) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }
    val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id"))

    ScreenColumn {
        ScreenTitle("Rekaman", "Potongan video diberi judul sesuai pertanyaan")
        if (items.isEmpty()) GlassCard { Text("Belum ada rekaman. Rekaman muncul di sini setelah live selesai.", color = Neon.Muted) }
        val parents = items.filter { it.kind == "full" }.sortedByDescending { it.createdAt }
        val orphans = items.filter { it.kind != "full" && parents.none { p -> p.id == it.parentId } }
        (parents + orphans).forEach { p ->
            GlassCard {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text("${fmt.format(Date(p.createdAt))} · ${p.durationMs / 60000} menit", color = Neon.Muted)
                RecordingActions(p.path, playing == p.id, {
                    player.value?.release(); player.value = null
                    if (playing == p.id) playing = null else {
                        playing = p.id
                        player.value = MediaPlayer().apply {
                            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                            setDataSource(p.path); prepare(); start()
                            setOnCompletionListener { playing = null }
                        }
                    }
                }, {
                    Graph.local.deleteRecording(p.id); items = Graph.local.recordings()
                })
                items.filter { it.parentId == p.id }.forEach { c ->
                    SectionTitle(if (c.kind == "transcript") "Transkrip" else "Potongan · ${c.durationMs / 1000} detik")
                    Text(c.title, style = MaterialTheme.typography.bodyLarge)
                    RecordingActions(c.path, playing == c.id, {
                        player.value?.release(); player.value = null
                        if (playing == c.id) playing = null else if (c.kind != "transcript") {
                            playing = c.id
                            player.value = MediaPlayer().apply { setDataSource(c.path); prepare(); start(); setOnCompletionListener { playing = null } }
                        } else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(c.path))
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    }, { Graph.local.deleteRecording(c.id); items = Graph.local.recordings() })
                }
            }
        }
    }
}

@Composable
private fun RecordingActions(path: String, isPlaying: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var msg by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(if (isPlaying) "Berhenti" else "Putar", Modifier.weight(1f), if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, onPlay)
        GhostButton("Unduh", Modifier.weight(1f), Icons.Filled.Download) {
            msg = if (RecordingManager.exportToDownloads(context, File(path))) "Tersimpan di folder Download/Stream By Natan" else "Gagal menyimpan"
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Bagikan", Modifier.weight(1f), Icons.Filled.Share) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (path.endsWith(".txt")) "text/plain" else "video/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Bagikan"))
        }
        GhostButton("Hapus", Modifier.weight(1f), Icons.Filled.Delete, onDelete)
    }
    if (msg.isNotBlank()) Text(msg, color = Neon.Lime)
}

/** Ringkasan setelah live & perkembangan dari live ke live. */
@Composable
fun ProgressScreen() {
    val list = remember { Graph.local.summaries().filter { !it.practice } }
    val fmt = SimpleDateFormat("EEEE, d MMM HH:mm", Locale("id"))
    ScreenColumn {
        ScreenTitle("Progres", "Ringkasan tiap live dan perbandingannya")
        if (list.isEmpty()) { GlassCard { Text("Belum ada data live.", color = Neon.Muted) }; return@ScreenColumn }
        val last = list.last()
        val prev = list.getOrNull(list.size - 2)
        GlassCard {
            Text("Live terakhir", style = MaterialTheme.typography.titleMedium)
            Text(summaryText(last))
            prev?.let { p ->
                val change = if (p.peakViewers > 0) ((last.peakViewers - p.peakViewers) * 100 / p.peakViewers) else 0
                Text(if (change >= 0) "Penonton naik $change% dibanding live sebelumnya." else "Penonton turun ${-change}% dibanding live sebelumnya.", color = if (change >= 0) Neon.Lime else Neon.Amber)
            }
        }
        // hari & jam terbaik
        val byDay = list.groupBy { SimpleDateFormat("EEEE", Locale("id")).format(Date(it.startedAt)) }
            .mapValues { (_, v) -> v.map { it.peakViewers }.average() }
        val byHour = list.groupBy { SimpleDateFormat("HH", Locale.US).format(Date(it.startedAt)).toInt() }
            .mapValues { (_, v) -> v.map { it.peakViewers }.average() }
        GlassCard {
            Text("Waktu terbaik", style = MaterialTheme.typography.titleMedium)
            byDay.maxByOrNull { it.value }?.let { Text("Hari paling ramai: ${it.key} (rata-rata ${it.value.toInt()} penonton)") }
            byHour.maxByOrNull { it.value }?.let { Text("Jam paling ramai: pukul ${it.key}.00") }
        }
        SectionTitle("Riwayat")
        list.reversed().take(30).forEach { s ->
            GlassCard {
                Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.titleMedium)
                Text(summaryText(s), color = Neon.Muted)
            }
        }
    }
}

private fun summaryText(s: LiveSummary): String {
    val min = (s.endedAt - s.startedAt) / 60000
    return "Durasi $min menit · puncak ${s.peakViewers} penonton · ${s.totalLikes} like · ${s.totalCoins} koin · " +
        "${s.comments} komentar · ${s.questions} pertanyaan · ${s.follows} follow · ${s.subscribes} subscribe · ${s.shares} share · " +
        "${s.newViewers} penonton baru" + (if (s.donations > 0) " · donasi Rp${s.donations}" else "") +
        (if (s.topSupporters.isNotEmpty()) "\nPendukung: " + s.topSupporters.joinToString(", ") else "")
}


/** Untuk tamu yang diundang: tempel link atau kode undangan. Gratis, tanpa akun. */
@Composable
fun JoinAsGuestCard(activity: MainActivity) {
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    GlassCard {
        SectionTitle("Diundang jadi tamu live?")
        Text("Tempel link atau kode undangan dari host. Gratis, tidak perlu akun.", color = Neon.Muted)
        Field("Link atau kode undangan", code) { code = it; err = null }
        err?.let { Text(it, color = Neon.Red, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        GhostButton("Tempel dari clipboard", icon = Icons.Filled.ContentPaste) { activity.clipboardText()?.let { code = it } }
        NeonButton("Gabung sebagai tamu", icon = Icons.Filled.GroupAdd, enabled = code.isNotBlank()) {
            val inv = MainActivity.parseInvite(code)
            if (inv == null) err = "Link atau kode undangan tidak dikenali." else activity.guestInvite.value = inv
        }
    }
}
