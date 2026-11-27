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
fun LiveScreen(activity: MainActivity) {
    val stats by LiveBus.stats.collectAsState()
    if (stats.isLive) LiveDashboard(activity) else GoLivePanel(activity)
}

@Composable
private fun GoLivePanel(activity: MainActivity) {
    val settings by Graph.settings.state.collectAsState()
    var mode by remember { mutableStateOf(LiveMode.CAMERA) }
    var practice by remember { mutableStateOf(false) }
    var internalAudio by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var showYt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STREAM BY NATAN", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
                ScreenTitle("Siap live?", "YouTube + TikTok sekaligus")
            }
        }

        SectionTitle("Profil live")
        ChoiceRow("Pilih profil", settings.profiles.map { it.id to it.name }, settings.activeProfileId) { Graph.settings.activateProfile(it) }

        SectionTitle("Sumber video")
        val modes = listOf(
            Triple(LiveMode.CAMERA, Icons.Filled.Videocam, "Kamera HP"),
            Triple(LiveMode.SCREEN, Icons.Filled.ScreenShare, "Bagikan layar"),
            Triple(LiveMode.AUDIO_ONLY, Icons.Filled.GraphicEq, "Suara saja"),
            Triple(LiveMode.USB, Icons.Filled.Usb, "Webcam USB"),
        )
        modes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (m, icon, label) ->
                    ActionTile(label, icon, Modifier.weight(1f).semantics { stateDescription = if (mode == m) "dipilih" else "" }, active = mode == m) { mode = m }
                }
            }
        }
        GlassCard {
            SwitchRow("Mode latihan", practice, "Tidak disiarkan, hanya direkam untuk didengar ulang") { practice = it }
            if (mode != LiveMode.SCREEN) SwitchRow("Sertakan audio internal", internalAudio, "Suara aplikasi lain, tamu, dan Saweria ikut tersiar (butuh izin rekam layar)") { internalAudio = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformBadge("YouTube", settings.enableYouTube && Graph.secure.get(SecureStore.YOUTUBE_KEY).isNotBlank(), Modifier.weight(1f))
                PlatformBadge("TikTok", settings.enableTikTok && Graph.secure.get(SecureStore.TIKTOK_KEY).isNotBlank(), Modifier.weight(1f))
            }
        }

        // Tombol GO LIVE dengan denyut cahaya
        val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.04f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
        NeonButton(if (practice) "MULAI LATIHAN" else "MULAI LIVE", Modifier.scale(pulse), brush = if (practice) Neon.brandH else Neon.live, icon = Icons.Filled.PlayArrow) {
            activity.startLive(mode, practice, internalAudio || mode == LiveMode.SCREEN)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(if (checking) "Memeriksa…" else "Periksa sebelum live", Modifier.weight(1f), Icons.Filled.FactCheck) {
                checking = true
                scope.launch {
                    val r = PreLiveCheck.run(activity, Graph.settings.current)
                    checkResult = r.lines.joinToString("\n")
                    Graph.tts.whisper(r.spoken)
                    checking = false
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Tempel key TikTok", Modifier.weight(1f), Icons.Filled.ContentPaste) {
                val t = activity.clipboardText()
                if (t.isNullOrBlank()) activity.message.value = "Clipboard kosong."
                else { Graph.secure.put(SecureStore.TIKTOK_KEY, t); activity.message.value = "Key TikTok disimpan." }
            }
            GhostButton("Siaran YouTube", Modifier.weight(1f), Icons.Filled.SmartDisplay) { showYt = true }
        }
        checkResult?.let { r ->
            GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("Hasil pemeriksaan", style = MaterialTheme.typography.titleMedium)
                Text(r, color = Neon.Muted)
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showYt) YouTubeBroadcastDialog(activity) { showYt = false }
}

@Composable
private fun PlatformBadge(name: String, ready: Boolean, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Neon.Glass).border(1.dp, if (ready) Neon.Lime else Neon.Line, RoundedCornerShape(16.dp))
            .padding(12.dp).semantics(mergeDescendants = true) { contentDescription = "$name ${if (ready) "siap" else "belum diatur"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ready) Neon.Lime else Neon.Red))
        Spacer(Modifier.width(8.dp))
        Text("$name ${if (ready) "siap" else "belum"}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LiveDashboard(activity: MainActivity) {
    val st by LiveBus.stats.collectAsState()
    val feed by LiveBus.feed.collectAsState()
    val engine = LiveService.engine
    val brain = runCatching { engine?.brain }.getOrNull()
    var showPoll by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var showMute by remember { mutableStateOf(false) }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(true)
            Spacer(Modifier.width(10.dp))
            val minutes = ((System.currentTimeMillis() - st.startedAt) / 60000).toInt()
            Text(if (st.practice) "LATIHAN · $minutes menit" else "LIVE · $minutes menit", style = MaterialTheme.typography.headlineSmall, color = Neon.Pink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("Penonton", "${st.viewers}", Modifier.weight(1f))
            StatPill("Like", "${st.likes}", Modifier.weight(1f), Neon.Pink)
            StatPill("Koin", "${st.coins}", Modifier.weight(1f), Neon.Amber)
        }
        GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text("YouTube: ${st.youtubeState} · komentar ${st.youtubeChat}", style = MaterialTheme.typography.bodyMedium)
            Text("TikTok: ${st.tiktokState} · komentar ${st.tiktokChat}", style = MaterialTheme.typography.bodyMedium)
            Text("Internet: ${st.network} · ${st.bitrateKbps} kbps · ${if (st.recording) "merekam" else "tidak merekam"}", style = MaterialTheme.typography.bodyMedium, color = Neon.Muted)
        }

        SectionTitle("Antrean pertanyaan (${brain?.questions?.size ?: 0})")
        brain?.currentQuestion?.let { q -> GlassCard { Text("Sedang dijawab: ${q.name} — ${q.text}") } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Pertanyaan berikutnya", Icons.Filled.SkipNext, Modifier.weight(1f)) { brain?.nextQuestion() }
            ActionTile("Ulangi", Icons.Filled.Replay, Modifier.weight(1f)) { brain?.repeatQuestion() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(if (brain?.holding == true) "Lanjutkan" else "Tahan", Icons.Filled.PauseCircle, Modifier.weight(1f), active = brain?.holding == true) { brain?.toggleHold() }
            ActionTile("Lewati", Icons.Filled.FastForward, Modifier.weight(1f)) { brain?.skipQuestion() }
        }

        SectionTitle("Kendali")
        val tiles = listOf(
            Triple(if (st.micOn) "Mikrofon menyala" else "Mikrofon mati", Icons.Filled.Mic, st.micOn) to { engine?.setMic(!st.micOn) },
            Triple(if (st.shareAudioOn) "Bagikan audio menyala" else "Bagikan audio mati", Icons.Filled.SurroundSound, st.shareAudioOn) to { engine?.setShareAudio(!st.shareAudioOn) },
            Triple("Ganti kamera", Icons.Filled.Cameraswitch, false) to { engine?.switchFrontBack(st.activeCamera != "depan") },
            Triple("Ringkasan komentar", Icons.Filled.Summarize, false) to { brain?.missedSummary() },
            Triple("Bacakan doa", Icons.Filled.VolunteerActivism, false) to { brain?.readNextPrayer() },
            Triple("Kode QR Saweria", Icons.Filled.QrCode2, false) to { brain?.showQr(true) },
            Triple("Persembahan", Icons.Filled.Savings, false) to { brain?.showOffering(true) },
            Triple("Tampilkan jawaban", Icons.Filled.QuestionAnswer, false) to { brain?.showFaqAnswer() },
            Triple("Pendukung teratas", Icons.Filled.EmojiEvents, false) to { brain?.topSupporters() },
            Triple("Polling", Icons.Filled.Poll, false) to { showPoll = true },
            Triple(if (Graph.sounds.musicPlaying) "Hentikan musik" else "Putar musik", Icons.Filled.MusicNote, Graph.sounds.musicPlaying) to {
                val s = Graph.settings.current
                if (Graph.sounds.musicPlaying) Graph.sounds.stopMusic() else Graph.sounds.startMusic(s.music.trackId, s.music.customPath, true)
            },
            Triple("Poin berikutnya", Icons.Filled.FormatListNumbered, false) to { brain?.nextPoint() },
            Triple("Kalimat naskah berikutnya", Icons.Filled.Notes, false) to { brain?.nextSentence() },
            Triple("Tutup tampilan", Icons.Filled.HideImage, false) to { brain?.hideOverlays() },
            Triple("Mute penonton", Icons.Filled.VoiceOverOff, false) to { showMute = true },
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (t, action) -> ActionTile(t.first, t.second, Modifier.weight(1f), active = t.third) { action() } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (Graph.settings.current.features.guests) GuestPanel(activity)

        SectionTitle("Efek suara")
        val s = Graph.settings.current
        val labels = Graph.sounds.effects.associate { it.id to it.label } + s.customSounds.associate { it.id to it.name }
        s.floatingSfx.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { id -> ActionTile(labels[id] ?: id, Icons.Filled.Campaign, Modifier.weight(1f)) { Graph.sounds.play(id, s.giftSfx.volume, s.customSounds) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        SectionTitle("Komentar & kejadian")
        GlassCard {
            if (feed.isEmpty()) Text("Belum ada komentar.", color = Neon.Muted)
            feed.takeLast(40).reversed().forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp))
            }
        }

        NeonButton("AKHIRI LIVE", brush = Neon.live, icon = Icons.Filled.StopCircle) { confirmStop = true }
        Spacer(Modifier.height(30.dp))
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Akhiri live?") },
        text = { Text("Siaran ke YouTube dan TikTok akan dihentikan, lalu rekaman dipotong otomatis.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; activity.stopLive() }) { Text("Ya, akhiri") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Batal") } },
    )
    if (showMute) AlertDialog(
        onDismissRequest = { showMute = false },
        title = { Text("Mute penonton") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = brain?.recentCommenters?.toList().orEmpty()
                if (names.isEmpty()) Text("Belum ada yang berkomentar.")
                names.forEach { n -> GhostButton("Mute $n", Modifier.fillMaxWidth()) { brain?.muteViewer(n) } }
            }
        },
        confirmButton = { TextButton(onClick = { showMute = false }) { Text("Selesai") } },
    )
    if (showPoll) PollDialog({ q, opts -> brain?.startPoll(q, opts) }) { showPoll = false }
}

@Composable
private fun PollDialog(onStart: (String, List<String>) -> Unit, onClose: () -> Unit) {
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
