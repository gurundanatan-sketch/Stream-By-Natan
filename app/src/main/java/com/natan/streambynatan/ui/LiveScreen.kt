package com.natan.streambynatan.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.natan.streambynatan.chat.BroadcastSettings
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.features.PreLiveCheck
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveMode
import com.natan.streambynatan.live.LiveService
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.launch

@Composable
fun PollDialog(onStart: (String, List<String>) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var opts by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Polling lewat komentar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Pertanyaan", q) { q = it }
                ListEditor("Pilihan (A, B, C…)", opts) { opts = it }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(q, opts); onClose() }) { Text("Mulai") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } },
    )
}

/** Pengaturan siaran YouTube langsung dari aplikasi (judul, deskripsi, thumbnail, audiens, visibilitas, jadwal, dll). */
@Composable
fun YouTubeBroadcastDialog(activity: MainActivity, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("public") }
    var kids by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var category by remember { mutableStateOf("29") }
    var latency by remember { mutableStateOf("low") }
    var scheduleMinutes by remember { mutableStateOf(0) }
    var thumb by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val connected by activity.youtubeConnected

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Siaran YouTube") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connected) {
                    Text("Hubungkan akun Google dulu (sekali saja).", color = Neon.Muted)
                    GhostButton("Hubungkan YouTube", icon = Icons.Filled.Link) { activity.connectYouTube() }
                }
                Field("Judul", title) { title = it }
                Field("Deskripsi", desc, singleLine = false, minLines = 3) { desc = it }
                GhostButton(if (thumb == null) "Pilih thumbnail dari galeri" else "Thumbnail dipilih", icon = Icons.Filled.Image) {
                    activity.pick(arrayOf("image/*")) { thumb = it }
                }
                ChoiceRow("Visibilitas", listOf("public" to "Publik", "unlisted" to "Tidak publik", "private" to "Pribadi"), privacy) { privacy = it }
                SwitchRow("Konten untuk anak-anak", kids) { kids = it }
                ChoiceRow("Kategori", listOf("29" to "Nirlaba & aktivisme", "22" to "Orang & blog", "27" to "Pendidikan", "10" to "Musik", "24" to "Hiburan", "20" to "Game"), category) { category = it }
                ListEditor("Tag", tags) { tags = it }
                ChoiceRow("Latensi", listOf("normal" to "Normal", "low" to "Rendah", "ultraLow" to "Sangat rendah"), latency) { latency = it }
                NumberField("Jadwalkan (menit dari sekarang, 0 = sekarang)", scheduleMinutes) { scheduleMinutes = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && title.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    runCatching {
                        val bytes = thumb?.let { u -> activity.contentResolver.openInputStream(u)?.use { it.readBytes() } }
                        val mime = thumb?.let { activity.contentResolver.getType(it) } ?: "image/jpeg"
                        Graph.ytApi.createBroadcast(BroadcastSettings(title, desc, privacy, kids,
                            if (scheduleMinutes > 0) System.currentTimeMillis() + scheduleMinutes * 60_000L else 0,
                            category, tags, latency, true, bytes, mime))
                    }.onSuccess { (server, key, videoId) ->
                        Graph.secure.put(SecureStore.YOUTUBE_KEY, key)
                        Graph.settings.update { it.copy(youtubeServer = server, youtubeVideoId = videoId, enableYouTube = true) }
                        activity.message.value = "Siaran YouTube dibuat. Stream key diambil otomatis."
                        onClose()
                    }.onFailure { activity.message.value = "Gagal: ${it.message?.take(160)}" }
                    busy = false
                }
            }) { Text(if (busy) "Membuat…" else "Buat siaran") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}
