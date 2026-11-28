package com.natan.streambynatan.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.overlay.QrItem
import com.natan.streambynatan.ui.theme.Neon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Panel host: ruang tunggu (antre siapa yang datang duluan), masukkan 1 atau lebih, bisukan, keluarkan. */
@Composable
fun GuestPanel(activity: MainActivity) {
    val gm = Graph.guests
    val waiting by gm.waiting.collectAsState()
    val onStage by gm.stage.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showLink by remember { mutableStateOf(false) }
    val s = Graph.settings.current
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    SectionTitle("Tamu live")
    if (!gm.started) {
        Text("Ruang tamu bisa dibuka sebelum live, supaya tamu sudah antre lebih dulu.", color = Neon.Muted)
        GhostButton("Buka ruang tamu", icon = Icons.Filled.GroupAdd) {
            if (!gm.start(s.guest)) activity.message.value = "Firebase belum diatur, tamu belum bisa dipakai."
            else if (!s.features.guests) Graph.settings.update { it.copy(features = it.features.copy(guests = true)) }
        }
        return
    }
    GhostButton(if (showLink) "Sembunyikan link undangan" else "Link & kode QR undangan", icon = Icons.Filled.Link) { showLink = !showLink }
    if (showLink) GlassCard {
        val link = gm.link(s.guest, "guest")
        Text(link, color = Neon.Cyan)
        Text("Kode ruang: ${gm.roomId}", color = Neon.Muted)
        val bmp = remember(link) { QrItem.makeQr(link, 480, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()) }
        Image(bmp.asImageBitmap(), "Kode QR undangan tamu", Modifier.height(220.dp))
        GhostButton("Bagikan link undangan", icon = Icons.Filled.Share) {
            val text = "Gabung live saya sebagai tamu: $link\n\nLink ini membuka aplikasi Stream By Natan (gratis). Kalau belum punya, ada tombol Unduh aplikasi atau Masuk lewat browser."
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Bagikan"))
        }
        GhostButton("Bagikan link download aplikasi", icon = Icons.Filled.Share) {
            val dl = com.natan.streambynatan.BuildConfig.HOSTING_URL + "/"
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Unduh aplikasi Stream By Natan (gratis): $dl"), "Bagikan"))
        }
    }

    GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Ruang tunggu: ${waiting.size} orang", style = MaterialTheme.typography.titleMedium)
        if (waiting.isEmpty()) Text("Belum ada yang menunggu.", color = Neon.Muted)
        waiting.forEachIndexed { i, r ->
            SwitchRow("${i + 1}. ${r.name}", r.id in selected, "${r.viaLabel} · datang ${fmt.format(Date(r.joinedAt))}${if (r.mode == "camera") " · kamera tambahan" else ""}") { on ->
                selected = if (on) selected + r.id else selected - r.id
            }
        }
        if (waiting.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Masukkan berikutnya", Modifier.weight(1f), Icons.Filled.PersonAdd) { gm.admitNext(1) }
                GhostButton("Masukkan terpilih (${selected.size})", Modifier.weight(1f), Icons.Filled.GroupAdd) {
                    gm.admit(selected.toList()); selected = emptySet()
                }
            }
            GhostButton("Masukkan semua") { gm.admit(waiting.map { it.id }); selected = emptySet() }
            if (selected.isNotEmpty()) GhostButton("Tolak terpilih", icon = Icons.Filled.PersonRemove) {
                selected.forEach { gm.kick(it) }; selected = emptySet()
            }
        }
    }

    GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Di siaran: ${onStage.size} tamu", style = MaterialTheme.typography.titleMedium)
        onStage.forEach { r ->
            val state = when {
                r.status != "onstage" -> "menyambung…"
                r.sharingScreen -> "berbagi layar"
                else -> "kamera"
            }
            Text("${r.name} · ${r.viaLabel} · $state · mic ${if (r.micOn) "menyala" else "mati"}" +
                if (r.filter != "normal") " · filter ${com.natan.streambynatan.features.Filters.byId(r.filter).label}" else "", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Bisukan mic", Modifier.weight(1f), Icons.Filled.MicOff) { gm.muteGuest(r.id) }
                GhostButton("Keluarkan", Modifier.weight(1f), Icons.Filled.PersonRemove) { gm.kick(r.id) }
            }
        }
        Text("Saat hanya satu orang berbicara, layarnya otomatis penuh. Kalau tidak ada atau beberapa orang bicara bersamaan, ukuran layar sama.", color = Neon.Muted)
    }

    // Pesan dari tamu (ruang tunggu maupun yang sedang tampil), bisa dibalas
    val messages by com.natan.streambynatan.live.LiveBus.guestMessages.collectAsState()
    var replyTo by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Pesan tamu (${messages.size})", style = MaterialTheme.typography.titleMedium)
        if (messages.isEmpty()) Text("Belum ada pesan.", color = Neon.Muted)
        messages.takeLast(15).forEach { (peerId, name, text) ->
            Text("$name: $text", style = MaterialTheme.typography.bodyLarge)
            if (!name.startsWith("Kamu")) GhostButton("Balas $name", icon = Icons.Filled.Reply) { replyTo = peerId }
        }
        replyTo?.let { id ->
            Field("Balasan untuk ${gm.remotes[id]?.name ?: "tamu"}", reply) { reply = it.take(300) }
            GhostButton("Kirim balasan", icon = Icons.Filled.Send) { gm.reply(id, reply); reply = ""; replyTo = null }
        }
    }
}
