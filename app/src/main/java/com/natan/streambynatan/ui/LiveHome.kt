package com.natan.streambynatan.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.OverlayMedia
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.data.ThemeImage
import com.natan.streambynatan.features.Filters
import com.natan.streambynatan.features.I18n
import com.natan.streambynatan.features.PreLiveCheck
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveMode
import com.natan.streambynatan.live.LiveService
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Halaman Live: sebelum live (tata letak sederhana) dan saat live (kamera penuh layar seperti TikTok). */
@Composable
fun LiveScreen(activity: MainActivity, openAccount: (String) -> Unit, openSettings: (String?) -> Unit) {
    val stats by LiveBus.stats.collectAsState()
    if (stats.isLive) LiveDashboard(activity, openAccount, openSettings) else GoLivePanel(activity, openAccount)
}

// =========================================================================== sebelum live
@Composable
private fun GoLivePanel(activity: MainActivity, openAccount: (String) -> Unit) {
    val settings by Graph.settings.state.collectAsState()
    val str by I18n.strings.collectAsState()
    val creator by Graph.social.isCreator.collectAsState()
    val unread by Graph.social.unreadFeedback.collectAsState()
    var mode by remember { mutableStateOf(LiveMode.CAMERA) }
    var practice by remember { mutableStateOf(false) }
    var internalAudio by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var showYt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScreenColumn {
        // 1) baris atas: judul + Kotak saran (khusus pembuat) + Akun
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STREAM BY NATAN", style = MaterialTheme.typography.labelMedium, color = Neon.Cyan)
                Text(str["ready"] ?: "Siap live?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            if (creator) { TopIconButton(Icons.Filled.Inbox, str["inbox"] ?: "Kotak saran", unread) { openAccount("inbox") }; Spacer(Modifier.width(8.dp)) }
            TopIconButton(Icons.Filled.AccountCircle, str["account"] ?: "Akun") { openAccount("menu") }
        }

        // 2) platform tujuan: tinggal nyalakan / matikan
        SectionTitle(str["platforms"] ?: "Platform tujuan")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val ready = Graph.secure.get(SecureStore.YOUTUBE_KEY).isNotBlank()
                FilterChipButton("YouTube" + if (!ready) " (belum ada key)" else "", settings.enableYouTube) { Graph.settings.update { it.copy(enableYouTube = !it.enableYouTube) } }
            }
            item {
                val ready = Graph.secure.get(SecureStore.TIKTOK_KEY).isNotBlank() && settings.tiktokServer.isNotBlank()
                FilterChipButton("TikTok" + if (!ready) " (belum ada key)" else "", settings.enableTikTok) { Graph.settings.update { it.copy(enableTikTok = !it.enableTikTok) } }
            }
            settings.extraPlatforms.forEach { p ->
                item {
                    val ready = Graph.secure.get("key_${p.id}").isNotBlank() && p.server.isNotBlank()
                    FilterChipButton(p.name + if (!ready) " (belum diatur)" else "", p.enabled) {
                        Graph.settings.update { s -> s.copy(extraPlatforms = s.extraPlatforms.map { if (it.id == p.id) it.copy(enabled = !it.enabled) else it }) }
                    }
                }
            }
        }
        val count = (if (settings.enableYouTube) 1 else 0) + (if (settings.enableTikTok) 1 else 0) + settings.extraPlatforms.count { it.enabled }
        if (count >= 3) Text("$count platform sekaligus butuh internet unggah sekitar ${count * 3} Mbps. Coba Periksa sebelum live.", color = Neon.Amber)

        // tampilan siaran: tegak atau mendatar
        SectionTitle("Tampilan siaran")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChipButton("Tegak 9:16", settings.video.portrait) { Graph.settings.update { it.copy(video = it.video.copy(portrait = true)) } } }
            item { FilterChipButton("Mendatar 16:9", !settings.video.portrait) { Graph.settings.update { it.copy(video = it.video.copy(portrait = false)) } } }
        }
        if (!settings.video.portrait) {
            Text("Putar HP ke posisi mendatar saat live. Cocok untuk YouTube.", color = Neon.Muted)
            if (settings.enableTikTok) GlassCard {
                Text("Tampilan di TikTok", style = MaterialTheme.typography.titleMedium)
                ChoiceRow("", listOf("native" to "Mendatar asli (seperti TikTok Live Studio)", "frame" to "Bingkai tegak (judul + latar buram)"), settings.layout.tiktokLandscape) { v ->
                    Graph.settings.update { it.copy(layout = it.layout.copy(tiktokLandscape = v)) }
                }
                if (settings.layout.tiktokLandscape == "native") {
                    Text("Penonton TikTok melihat video mendatar di tengah layar dan bisa memutar HP-nya untuk menonton layar penuh, sama seperti siaran dari TikTok Live Studio di laptop.", color = Neon.Muted)
                } else {
                    Text("Satu siaran dipakai untuk semua platform, jadi YouTube juga akan menerima bingkai tegak. Pilih ini kalau live ke TikTok saja, atau kalau tidak masalah YouTube ikut tegak. Hanya untuk sumber Kamera HP.", color = Neon.Amber)
                    Field("Judul di atas video (kosong = judul YouTube)", settings.layout.frameTitle) { v -> Graph.settings.update { it.copy(layout = it.layout.copy(frameTitle = v.take(80))) } }
                    SwitchRow("Tampilkan judul", settings.layout.frameShowTitle) { v -> Graph.settings.update { it.copy(layout = it.layout.copy(frameShowTitle = v)) } }
                    ChoiceRow("Latar", listOf("blur" to "Video diburamkan", "dark" to "Gelap bergradasi"), settings.layout.frameBackground) { v ->
                        Graph.settings.update { it.copy(layout = it.layout.copy(frameBackground = v)) }
                    }
                }
            }
        }

        // 3) sumber video & profil: masing-masing satu baris
        SectionTitle(str["source"] ?: "Sumber video")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LiveMode.CAMERA to "Kamera HP", LiveMode.SCREEN to "Layar", LiveMode.AUDIO_ONLY to "Suara saja", LiveMode.USB to "Webcam USB").forEach { (m, label) ->
                item { FilterChipButton(label, mode == m) { mode = m } }
            }
        }
        SectionTitle(str["profile"] ?: "Profil live")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.profiles.forEach { p -> item { FilterChipButton(p.name, settings.activeProfileId == p.id) { Graph.settings.activateProfile(p.id) } } }
        }

        // 4) tombol besar Mulai live
        val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.04f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
        NeonButton(if (practice) (str["start_practice"] ?: "MULAI LATIHAN") else (str["start_live"] ?: "MULAI LIVE"),
            Modifier.scale(pulse).heightIn(min = 64.dp), brush = if (practice) Neon.brandH else Neon.live, icon = Icons.Filled.PlayArrow) {
            val landscape = !settings.video.portrait
            // putar layar dulu supaya kamera menyesuaikan posisi mendatar
            if (landscape) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val yt = settings.ytBroadcast
            val makeYt = yt.autoCreate && settings.enableYouTube && !practice && activity.youtubeConnected.value && yt.title.isNotBlank()
            scope.launch {
                if (makeYt) {
                    activity.message.value = "Membuat siaran YouTube…"
                    createYouTubeBroadcast(activity, yt)?.let { activity.message.value = "$it Live tetap dimulai dengan stream key terakhir." }
                }
                if (landscape) delay(700)
                activity.startLive(mode, practice, internalAudio || mode == LiveMode.SCREEN)
                // kalau live batal dimulai (izin ditolak), kembalikan layar tegak
                delay(20_000)
                if (!LiveBus.stats.value.isLive) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        // 5) opsi lainnya (dilipat)
        GhostButton((str["more_options"] ?: "Opsi lainnya") + if (more) " (tutup)" else "", icon = if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore) { more = !more }
        if (more) {
            GlassCard {
                SwitchRow("Mode latihan", practice, "Tidak disiarkan, hanya direkam untuk didengar ulang") { practice = it }
                if (mode != LiveMode.SCREEN) SwitchRow("Sertakan audio internal", internalAudio, "Suara aplikasi lain, tamu, dan Saweria ikut tersiar (butuh izin rekam layar)") { internalAudio = it }
                ChoiceRow("Kualitas video", listOf("480" to "480p", "720" to "720p", "1080" to "1080p", "auto" to "Otomatis"), settings.quality) { q -> Graph.settings.update { it.copy(quality = q) } }
            }
            GuestPanel(activity)
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
                GhostButton("Siaran YouTube", Modifier.weight(1f), Icons.Filled.SmartDisplay) { showYt = true }
            }
            if (settings.enableYouTube) {
                GhostButton("Cek monetisasi di YouTube Studio", icon = Icons.Filled.MonetizationOn) { openMonetizationCheck(activity) }
                Text("Pastikan monetisasi siaran menyala sebelum live (Edit > Monetisasi > Aktif). Hanya untuk kanal yang sudah dimonetisasi.", color = Neon.Muted)
            }
            GhostButton("Tempel key TikTok dari clipboard", icon = Icons.Filled.ContentPaste) {
                val t = activity.clipboardText()
                if (t.isNullOrBlank()) activity.message.value = "Clipboard kosong."
                else { Graph.secure.put(SecureStore.TIKTOK_KEY, t); activity.message.value = "Key TikTok disimpan." }
            }
            checkResult?.let { r ->
                GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text("Hasil pemeriksaan", style = MaterialTheme.typography.titleMedium)
                    Text(r, color = Neon.Muted)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
    if (showYt) YouTubeBroadcastDialog(activity) { showYt = false }
}

// =========================================================================== saat live
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveDashboard(activity: MainActivity, openAccount: (String) -> Unit, openSettings: (String?) -> Unit) {
    val st by LiveBus.stats.collectAsState()
    val feed by LiveBus.feed.collectAsState()
    val note by LiveBus.hostNote.collectAsState()
    val s by Graph.settings.state.collectAsState()
    val str by I18n.strings.collectAsState()
    val engine = LiveService.engine
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    // siaran mendatar: layar ikut mendatar selama live, kembali tegak setelah selesai
    val landscapeLive = remember { !Graph.settings.current.video.portrait }
    DisposableEffect(landscapeLive) {
        activity.requestedOrientation = if (landscapeLive) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // pratinjau siaran penuh layar
        AndroidView(
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Pratinjau siaran" },
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { LiveService.engine?.attachPreview(this@apply) }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                        override fun surfaceDestroyed(h: SurfaceHolder) { LiveService.engine?.detachPreview() }
                    })
                }
            },
        )

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // baris status atas
            val sec = ((now - st.startedAt) / 1000).coerceAtLeast(0)
            val healthColor = when (st.health) { "merah" -> Neon.Red; "kuning" -> Neon.Amber; else -> Neon.Lime }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x99000000)).padding(10.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "${if (st.practice) "Latihan" else "Live"} ${sec / 60} menit. ${st.viewers} penonton, ${st.likes} like. Koneksi ${st.health}." +
                            (if (st.battery >= 0) " Baterai ${st.battery} persen." else "") + if (st.paused) " Layar dijeda." else ""
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(true); Spacer(Modifier.width(6.dp))
                Text(if (st.practice) "LATIHAN" else "LIVE", color = Neon.Pink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(String.format("%02d:%02d", sec / 60, sec % 60), color = Color.White)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp)); Text(" ${st.viewers}", color = Color.White)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Favorite, null, tint = Neon.Pink, modifier = Modifier.size(18.dp)); Text(" ${st.likes}", color = Color.White)
                Spacer(Modifier.weight(1f))
                if (st.battery >= 0) Text("${st.battery}%  ", color = if (st.battery <= s.batteryWarnPercent) Neon.Red else Color.White)
                Box(Modifier.size(14.dp).clip(CircleShape).background(healthColor))
            }
            // catatan / teleprompter (kalau mode pembaca layar mati)
            if (note.isNotBlank() && !s.a11y.voiceAids) {
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.headlineSmall, color = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(Modifier.weight(1f))
            // komentar mengambang di kiri bawah
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    feed.takeLast(6).forEach { line ->
                        Text(line, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * s.commentDisplay.hostTextScale),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x77000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                // tombol cepat di kanan
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QuickButton(if (st.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (st.micOn) "Matikan mikrofon" else "Nyalakan mikrofon", !st.micOn) { engine?.setMic(!st.micOn) }
                    QuickButton(Icons.Filled.Cameraswitch, "Ganti kamera depan atau belakang") { engine?.switchFrontBack(st.activeCamera != "depan") }
                    if (s.features.pauseButton) QuickButton(if (st.paused) Icons.Filled.PlayCircle else Icons.Filled.PauseCircle, if (st.paused) "Lanjutkan layar" else "Jeda layar", st.paused) { engine?.setPaused(!st.paused) }
                    if (s.features.touchLock) QuickButton(Icons.Filled.Lock, "Kunci sentuhan") { locked = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            // bar bawah seperti TikTok
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().clip(RoundedCornerShape(22.dp)).background(Color(0xCC070A14)).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val gw by Graph.guests.waiting.collectAsState()
                val msgs by LiveBus.guestMessages.collectAsState()
                BarButton(Icons.Filled.Groups, (str["guests"] ?: "Tamu") + if (gw.isNotEmpty()) " (${gw.size})" else "") { sheet = "tamu" }
                BarButton(Icons.Filled.Handyman, str["tools"] ?: "Alat") { sheet = "alat" }
                BarButton(Icons.Filled.Share, str["share"] ?: "Bagikan") { sheet = "bagikan" }
                BarButton(Icons.Filled.AutoAwesome, str["enhance"] ?: "Sempurnakan") { sheet = "sempurnakan" }
                BarButton(Icons.Filled.MoreHoriz, str["more"] ?: "Lainnya") { sheet = "lainnya" }
                BarButton(Icons.Filled.StopCircle, str["end_live"] ?: "Akhiri", tint = Neon.Pink) { confirmStop = true }
                if (msgs.isNotEmpty()) Unit
            }
        }

        sheet?.let { name ->
            SheetPanel(when (name) {
                "tamu" -> str["guests"] ?: "Tamu"; "alat" -> str["tools"] ?: "Alat"; "bagikan" -> str["share"] ?: "Bagikan"
                "sempurnakan" -> str["enhance"] ?: "Sempurnakan"; else -> str["more"] ?: "Lainnya"
            }, onClose = { sheet = null }) {
                when (name) {
                    "tamu" -> GuestPanel(activity)
                    "alat" -> ToolsSheet(activity)
                    "bagikan" -> ShareSheet(activity)
                    "sempurnakan" -> EnhanceSheet(activity)
                    else -> MoreSheet(activity, openAccount = { sheet = null; openAccount(it) }, openSettings = { sheet = null; openSettings(it) })
                }
            }
        }

        // kunci sentuhan: semua sentuhan diabaikan, buka dengan tekan lama
        if (locked) Box(
            Modifier.fillMaxSize().background(Color(0x33000000))
                .combinedClickable(onClick = {}, onLongClick = { locked = false }, onLongClickLabel = "Buka kunci sentuhan")
                .semantics { contentDescription = "Layar dikunci. Tekan lama untuk membuka kunci."; onLongClick("Buka kunci") { locked = false; true } },
            contentAlignment = Alignment.Center,
        ) {
            Text("🔒 Layar dikunci\nTekan lama untuk membuka", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xAA000000)).padding(20.dp))
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Akhiri live?") },
        text = { Text("Siaran ke semua platform akan dihentikan, lalu rekaman dipotong otomatis.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; activity.stopLive() }) { Text("Ya, akhiri") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Batal") } },
    )
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (active) Neon.Pink else Color(0x99000000))
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Panel bawah (seperti lembar menu TikTok), bisa digulir. */
@Composable
private fun SheetPanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClickLabel = "Tutup $title", onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Neon.Deep)
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                GhostButton("Tutup", icon = Icons.Filled.Close, onClick = onClose)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// =========================================================================== Alat
@Composable
private fun ToolsSheet(activity: MainActivity) {
    val engine = LiveService.engine
    val brain = runCatching { engine?.brain }.getOrNull()
    val extras = runCatching { brain?.extras }.getOrNull()
    val s by Graph.settings.state.collectAsState()
    var showPoll by remember { mutableStateOf(false) }
    var prize by remember { mutableStateOf(s.giveaway.prize) }

    SectionTitle("Kamera")
    val names = remember { engine?.cameraNames().orEmpty() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { n -> item { FilterChipButton(n, engine?.currentCamera?.let { n.contains(it) } == true) { if (engine?.switchTo(n) != true) activity.message.value = "Kamera $n tidak tersedia." } } }
    }
    GhostButton("Tambah kamera dari HP lain", icon = Icons.Filled.AddAPhoto) {
        val gm = Graph.guests
        if (!gm.started && !gm.start(s.guest)) { activity.message.value = "Firebase belum diatur."; return@GhostButton }
        shareText(activity, "Jadikan HP ini kamera tambahan live saya: ${gm.link(s.guest, "camera")}", "Tambah kamera")
    }
    Text("Perintah suara tetap bisa dipakai, misalnya: pindah ke kamera belakang.", color = Neon.Muted)

    SectionTitle("Pertanyaan (${brain?.questions?.size ?: 0})")
    brain?.currentQuestion?.let { q -> Text("Sedang dijawab: ${q.name}: ${q.text}") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("Berikutnya", Icons.Filled.SkipNext, Modifier.weight(1f)) { brain?.nextQuestion() }
        ActionTile("Ulangi", Icons.Filled.Replay, Modifier.weight(1f)) { brain?.repeatQuestion() }
        ActionTile(if (brain?.holding == true) "Lanjut" else "Tahan", Icons.Filled.PauseCircle, Modifier.weight(1f)) { brain?.toggleHold() }
        ActionTile("Lewati", Icons.Filled.FastForward, Modifier.weight(1f)) { brain?.skipQuestion() }
    }

    SectionTitle("Giveaway")
    Field("Hadiah (opsional)", prize) { prize = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Mulai giveaway", Modifier.weight(1f), Icons.Filled.Redeem) { extras?.startGiveaway(prize) }
        GhostButton("Undi sekarang", Modifier.weight(1f), Icons.Filled.Casino) { extras?.drawWinner() }
    }
    extras?.giveaway?.let { Text("Peserta: ${it.entrants.size} · kata kunci ${it.keyword}", color = Neon.Muted) }

    SectionTitle("Kuis & polling")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Kuis berikutnya", Modifier.weight(1f), Icons.Filled.Quiz) { extras?.nextQuiz() }
        GhostButton("Papan skor", Modifier.weight(1f), Icons.Filled.Leaderboard) { extras?.showQuizScores() }
    }
    GhostButton("Polling", icon = Icons.Filled.Poll) { showPoll = true }

    SectionTitle("Lagu permintaan (${extras?.songQueue?.size ?: 0})")
    extras?.songQueue?.take(5)?.forEachIndexed { i, r -> Text("${i + 1}. ${r.song} · dari ${r.name}") }
    GhostButton("Lagu berikutnya", icon = Icons.Filled.QueueMusic) { extras?.nextSong() }

    SectionTitle("Susunan acara")
    val ri = extras?.rundownIndex ?: -1
    Text(if (ri in s.rundown.indices) "Sekarang: ${s.rundown[ri].title}" else if (s.rundown.isEmpty()) "Belum diisi (Pengaturan, Alat live)" else "Belum dimulai", color = Neon.Muted)
    GhostButton("Acara berikutnya", icon = Icons.Filled.Schedule) { extras?.nextRundown() }

    SectionTitle("Lainnya")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("QR Saweria", Icons.Filled.QrCode2, Modifier.weight(1f)) { brain?.showQr(true) }
        ActionTile("Pendukung teratas", Icons.Filled.EmojiEvents, Modifier.weight(1f)) { brain?.topSupporters() }
        ActionTile("Info donasi", Icons.Filled.Savings, Modifier.weight(1f)) { brain?.showOffering(true) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("Ringkasan komentar", Icons.Filled.Summarize, Modifier.weight(1f)) { brain?.missedSummary() }
        ActionTile("Tampilkan jawaban", Icons.Filled.QuestionAnswer, Modifier.weight(1f)) { brain?.showFaqAnswer() }
        ActionTile("Tutup tampilan", Icons.Filled.HideImage, Modifier.weight(1f)) { brain?.hideOverlays() }
    }
    SwitchRow("Pengumuman otomatis", s.features.announcements, "Pesan diulang otomatis setiap beberapa menit") { v -> Graph.settings.update { it.copy(features = it.features.copy(announcements = v)) } }
    SwitchRow("Target live", s.features.targets, "Target like ${s.targets.likes}, koin ${s.targets.coins}, penonton ${s.targets.viewers}") { v -> Graph.settings.update { it.copy(features = it.features.copy(targets = v)) } }
    if (showPoll) PollDialog({ q, opts -> brain?.startPoll(q, opts) }) { showPoll = false }
}

// =========================================================================== Bagikan
@Composable
private fun ShareSheet(activity: MainActivity) {
    val brain = runCatching { LiveService.engine?.brain }.getOrNull()
    val p by Graph.social.profile.collectAsState()
    val links = remember { brain?.liveLinks().orEmpty() + if (p.username.isNotBlank()) mapOf("profil" to p.shareUrl) else emptyMap() }
    val text = remember(links) {
        "Saya sedang live sekarang, yuk gabung!\n" + links.entries.joinToString("\n") { (k, v) -> "${k.replaceFirstChar { it.uppercase() }}: $v" }
    }
    if (links.isEmpty()) Text("Isi ID video YouTube atau username TikTok supaya link live bisa dibagikan.", color = Neon.Muted)
    links.forEach { (k, v) -> Text("${k.replaceFirstChar { it.uppercase() }}: $v", color = Neon.Cyan) }
    GhostButton("Salin link", icon = Icons.Filled.ContentCopy) {
        activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("link live", text))
        activity.message.value = "Link disalin."
    }
    val apps = listOf(
        "WhatsApp" to "com.whatsapp", "WhatsApp Bisnis" to "com.whatsapp.w4b", "Telegram" to "org.telegram.messenger",
        "Facebook" to "com.facebook.katana", "Messenger" to "com.facebook.orca", "Instagram" to "com.instagram.android",
        "X (Twitter)" to "com.twitter.android", "Threads" to "com.instagram.barcelona", "TikTok" to "com.zhiliaoapp.musically",
        "LINE" to "jp.naver.line.android", "Discord" to "com.discord", "Gmail" to "com.google.android.gm",
    )
    apps.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, pkg) ->
                GhostButton(label, Modifier.weight(1f), Icons.Filled.Send) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg)
                    runCatching { activity.startActivity(i) }.onFailure { activity.message.value = "$label belum terpasang." }
                }
            }
        }
    }
    GhostButton("SMS", icon = Icons.Filled.Sms) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)) }
    }
    NeonButton("Aplikasi lain…", icon = Icons.Filled.Share) { shareText(activity, text, "Bagikan link live") }
}

// =========================================================================== Sempurnakan
@Composable
private fun EnhanceSheet(activity: MainActivity) {
    val s by Graph.settings.state.collectAsState()
    val engine = LiveService.engine
    val extras = runCatching { engine?.brain?.extras }.getOrNull()
    fun upd(block: (com.natan.streambynatan.data.AppSettings) -> com.natan.streambynatan.data.AppSettings) = Graph.settings.update(block)

    // ---------------- filter
    SectionTitle("Filter")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filters.presets.forEach { p -> item { FilterChipButton(p.label, s.filter.preset == p.id) { upd { it.copy(filter = it.filter.copy(preset = p.id)) }; engine?.applyFilters() } } }
    }
    SliderRow("Kecerahan", s.filter.brightness, 0.5f..1.6f) { v -> upd { it.copy(filter = it.filter.copy(brightness = v)) }; engine?.applyFilters() }
    SliderRow("Kontras", s.filter.contrast, 0.5f..1.8f) { v -> upd { it.copy(filter = it.filter.copy(contrast = v)) }; engine?.applyFilters() }
    SliderRow("Kepekatan warna", s.filter.saturation, 0f..2f) { v -> upd { it.copy(filter = it.filter.copy(saturation = v)) }; engine?.applyFilters() }
    SwitchRow("Kulit halus", s.filter.smoothSkin) { v -> upd { it.copy(filter = it.filter.copy(smoothSkin = v)) }; engine?.applyFilters() }

    // ---------------- latar belakang & impor gambar
    SectionTitle("Latar belakang")
    ChoiceRow("Jenis latar", listOf("blur" to "Buram", "image" to "Gambar", "video" to "Video"), s.background.type) { t -> upd { it.copy(background = it.background.copy(type = t)) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Gambar dari galeri", Modifier.weight(1f), Icons.Filled.Image) { activity.pick(arrayOf("image/*")) { u -> upd { it.copy(background = it.background.copy(type = "image", path = u.toString())) } } }
        GhostButton("Video dari galeri", Modifier.weight(1f), Icons.Filled.VideoLibrary) { activity.pick(arrayOf("video/*")) { u -> upd { it.copy(background = it.background.copy(type = "video", path = u.toString())) } } }
    }
    SwitchRow("Ganti latar belakang", s.features.backgroundReplace, "Berlaku mulai live berikutnya") { v -> upd { it.copy(features = it.features.copy(backgroundReplace = v)) } }
    GhostButton("Impor gambar ke layar siaran", icon = Icons.Filled.AddPhotoAlternate) { activity.pick(arrayOf("image/*")) { u -> extras?.showImageOnce(u) } }

    // ---------------- overlay
    SectionTitle("Overlay gambar & video")
    s.overlays.forEach { o ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("${if (o.type == "video") "Video" else "Gambar"}: ${o.name}", Modifier.weight(1f), if (o.type == "video") Icons.Filled.Movie else Icons.Filled.Image) { extras?.showOverlay(o) }
            GhostButton("Hapus", icon = Icons.Filled.Delete) { upd { it.copy(overlays = it.overlays.filterNot { x -> x.id == o.id }) } }
        }
    }
    var ovSize by remember { mutableStateOf("medium") }
    var ovPos by remember { mutableStateOf("top_right") }
    ChoiceRow("Ukuran overlay baru", listOf("small" to "Kecil", "medium" to "Sedang", "full" to "Penuh"), ovSize) { ovSize = it }
    ChoiceRow("Posisi", listOf("top_left" to "Kiri atas", "top_right" to "Kanan atas", "center" to "Tengah", "bottom_left" to "Kiri bawah", "bottom_right" to "Kanan bawah"), ovPos) { ovPos = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Tambah gambar", Modifier.weight(1f), Icons.Filled.AddPhotoAlternate) {
            activity.pick(arrayOf("image/*")) { u -> upd { it.copy(overlays = it.overlays + OverlayMedia(UUID.randomUUID().toString().take(6), "Gambar ${it.overlays.size + 1}", u.toString(), "image", ovSize, ovPos)) } }
        }
        GhostButton("Tambah video", Modifier.weight(1f), Icons.Filled.VideoCall) {
            activity.pick(arrayOf("video/*")) { u -> upd { it.copy(overlays = it.overlays + OverlayMedia(UUID.randomUUID().toString().take(6), "Video ${it.overlays.size + 1}", u.toString(), "video", ovSize, ovPos)) } }
        }
    }

    // ---------------- tema
    SectionTitle("Tema (ucapkan kata TEMA)")
    SwitchRow("Tema otomatis lewat suara", s.features.themes, "Ucapkan kalimat apa saja yang ada kata ${s.themeTrigger}") { v -> upd { it.copy(features = it.features.copy(themes = v)) } }
    s.themes.forEach { t ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Tampilkan: ${t.title}", Modifier.weight(1f), Icons.Filled.Wallpaper) { extras?.showTheme(t) }
            GhostButton("Hapus", icon = Icons.Filled.Delete) { upd { it.copy(themes = it.themes.filterNot { x -> x.id == t.id }) } }
        }
    }
    var themeTitle by remember { mutableStateOf("") }
    Field("Judul tema baru (misal: persahabatan)", themeTitle) { themeTitle = it }
    GhostButton("Pilih gambar tema dari galeri", icon = Icons.Filled.AddPhotoAlternate) {
        if (themeTitle.isBlank()) { activity.message.value = "Isi judul tema dulu."; return@GhostButton }
        val title = themeTitle
        activity.pick(arrayOf("image/*")) { u -> upd { it.copy(themes = it.themes + ThemeImage(UUID.randomUUID().toString().take(6), title, u.toString()), features = it.features.copy(themes = true)) } }
        themeTitle = ""
    }

    // ---------------- teks layar
    SectionTitle("Teks layar")
    SwitchRow("Tampilkan teks layar", s.screenText.enabled) { v -> upd { it.copy(screenText = it.screenText.copy(enabled = v)) } }
    Field("Teks atau judul", s.screenText.text, singleLine = false, minLines = 2) { v -> upd { it.copy(screenText = it.screenText.copy(text = v, enabled = v.isNotBlank() || it.screenText.enabled)) } }
    ChoiceRow("Posisi teks", listOf("top" to "Atas", "center" to "Tengah", "bottom" to "Bawah"), s.screenText.position) { v -> upd { it.copy(screenText = it.screenText.copy(position = v)) } }
    SliderRow("Ukuran teks", s.screenText.size, 0.5f..2.5f) { v -> upd { it.copy(screenText = it.screenText.copy(size = v)) } }
    ColorRow("Warna teks", s.screenText.color) { v -> upd { it.copy(screenText = it.screenText.copy(color = v)) } }
    SwitchRow("Latar gelap di belakang teks", s.screenText.background) { v -> upd { it.copy(screenText = it.screenText.copy(background = v)) } }

    // ---------------- efek suara
    SectionTitle("Efek suara")
    val labels = Graph.sounds.effects.associate { it.id to it.label } + s.customSounds.associate { it.id to it.name }
    val ids = (s.floatingSfx + s.customSounds.map { it.id } + Graph.sounds.effects.take(24).map { it.id }).distinct()
    ids.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { id -> ActionTile(labels[id] ?: id, Icons.Filled.Campaign, Modifier.weight(1f)) { Graph.sounds.play(id, s.giftSfx.volume, s.customSounds) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---------------- mikrofon & pengubah suara
    SectionTitle("Efek mikrofon")
    fun mic(block: (com.natan.streambynatan.data.MicFx) -> com.natan.streambynatan.data.MicFx) { upd { it.copy(micFx = block(it.micFx)) }; engine?.updateMicFx() }
    SwitchRow("Peredam bising AI (RNNoise)", s.micFx.aiNoiseSuppression) { v -> mic { it.copy(aiNoiseSuppression = v) } }
    SwitchRow("Peredam bising kuat", s.micFx.strongNoiseGate) { v -> mic { it.copy(strongNoiseGate = v) } }
    SwitchRow("Penyeimbang volume", s.micFx.volumeBalancer) { v -> mic { it.copy(volumeBalancer = v) } }
    SwitchRow("Gema ruangan", s.micFx.reverb) { v -> mic { it.copy(reverb = v) } }
    SectionTitle("Pengubah suara")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("none" to "Asli", "robot" to "Robot", "high" to "Tinggi", "chipmunk" to "Cempreng", "deep" to "Berat", "giant" to "Raksasa", "echo" to "Gema", "phone" to "Telepon").forEach { (id, label) ->
            item { FilterChipButton(label, s.micFx.voiceChanger == id) { mic { it.copy(voiceChanger = id) } } }
        }
    }

    // ---------------- musik latar
    SectionTitle("Musik latar")
    val playing = Graph.sounds.musicPlaying
    NeonButton(if (playing) "Hentikan musik" else "Putar musik", icon = if (playing) Icons.Filled.Stop else Icons.Filled.MusicNote) {
        if (Graph.sounds.musicPlaying) Graph.sounds.stopMusic() else Graph.sounds.startMusic(s.music.trackId, s.music.customPath, s.music.loop)
    }
    SliderRow("Volume musik", s.music.volume, 0f..1f, { "${(it * 100).toInt()}%" }) { v -> upd { it.copy(music = it.music.copy(volume = v)) }; Graph.mixer.musicGain = v }
    GhostButton(if (s.music.customPath.isNotBlank()) "Lagu dari galeri: ${s.music.customName.ifBlank { "dipilih" }}" else "Impor audio dari galeri", icon = Icons.Filled.LibraryMusic) {
        activity.pick(arrayOf("audio/*", "video/*")) { u ->
            val name = runCatching { activity.contentResolver.query(u, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull().orEmpty()
            upd { it.copy(music = it.music.copy(customPath = u.toString(), customName = name)) }
            if (Graph.sounds.musicPlaying) Graph.sounds.startMusic("", u.toString(), s.music.loop)
        }
    }
    if (s.music.customPath.isNotBlank()) GhostButton("Pakai musik bawaan lagi", icon = Icons.Filled.Undo) { upd { it.copy(music = it.music.copy(customPath = "", customName = "")) } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Graph.sounds.musicTracks.forEach { t ->
            item {
                FilterChipButton(t.label, s.music.customPath.isBlank() && s.music.trackId == t.id) {
                    upd { it.copy(music = it.music.copy(trackId = t.id, customPath = "")) }
                    if (Graph.sounds.musicPlaying) Graph.sounds.startMusic(t.id, "", s.music.loop)
                }
            }
        }
    }
}

// =========================================================================== Lainnya
@Composable
private fun MoreSheet(activity: MainActivity, openAccount: (String) -> Unit, openSettings: (String?) -> Unit) {
    val s by Graph.settings.state.collectAsState()
    val engine = LiveService.engine
    fun feat(block: (com.natan.streambynatan.data.FeatureToggles) -> com.natan.streambynatan.data.FeatureToggles) = Graph.settings.update { it.copy(features = block(it.features)) }

    SectionTitle("Live")
    SwitchRow("Kunci sentuhan", s.features.touchLock, "Tombol gembok di layar live") { v -> feat { it.copy(touchLock = v) } }
    SwitchRow("Tombol jeda layar", s.features.pauseButton) { v -> feat { it.copy(pauseButton = v) } }
    SwitchRow("Rekam siaran ke HP", s.video.recordWhileLive, "Bawaan menyala, matikan kalau memori hampir penuh (berlaku live berikutnya)") { v -> Graph.settings.update { it.copy(video = it.video.copy(recordWhileLive = v)) } }
    ChoiceRow("Kualitas video (berlaku live berikutnya)", listOf("480" to "480p", "720" to "720p", "1080" to "1080p", "auto" to "Otomatis"), s.quality) { q -> Graph.settings.update { it.copy(quality = q) } }
    SwitchRow("Jangan Ganggu otomatis", s.features.doNotDisturb) { v -> feat { it.copy(doNotDisturb = v) } }
    SwitchRow("Layar tetap menyala", s.features.keepScreenOn) { v -> feat { it.copy(keepScreenOn = v) } }
    SwitchRow("Subtitle otomatis", s.features.subtitles) { v -> feat { it.copy(subtitles = v) } }
    SwitchRow("Perintah suara", s.features.voiceCommands) { v -> feat { it.copy(voiceCommands = v) } }
    SwitchRow("Papan nama", s.features.nameplate, "Nama dan keterangan tampil di siaran") { v -> feat { it.copy(nameplate = v) } }

    if (s.enableYouTube) {
        SectionTitle("YouTube")
        GhostButton("Cek monetisasi di YouTube Studio", icon = Icons.Filled.MonetizationOn) { openMonetizationCheck(activity) }
    }
    if (s.enableYouTube && s.youtubeVideoId.isNotBlank()) {
        val scope = rememberCoroutineScope()
        GhostButton("Jeda iklan sekarang (kanal dimonetisasi)", icon = Icons.Filled.SmartDisplay) {
            scope.launch {
                runCatching { Graph.ytApi.insertAdBreak(Graph.settings.current.youtubeVideoId, 30) }
                    .onSuccess { activity.message.value = "Jeda iklan 30 detik dikirim ke YouTube." }
                    .onFailure { activity.message.value = "Jeda iklan gagal: ${it.message?.take(120)}" }
            }
        }
    }
    if (engine?.frameMode == true) {
        SectionTitle("Bingkai TikTok")
        var t by remember { mutableStateOf(s.layout.frameTitle.ifBlank { s.ytBroadcast.title }) }
        Field("Judul di atas video", t) { t = it.take(80) }
        GhostButton("Pakai judul ini") {
            Graph.settings.update { it.copy(layout = it.layout.copy(frameTitle = t)) }
            engine.setFrameTitle(t, Graph.settings.current.layout.frameShowTitle)
        }
    }

    SectionTitle("Musik latar")
    SwitchRow("Musik mengecil saat host bicara", s.features.musicDucking, "Musik tidak hilang, hanya mengecil, lalu kembali normal") { v -> feat { it.copy(musicDucking = v) }; engine?.updateMicFx() }
    SliderRow("Tingkat musik saat bicara", s.music.duckLevel, 0.05f..0.9f, { "${(it * 100).toInt()}%" }) { v -> Graph.settings.update { it.copy(music = it.music.copy(duckLevel = v)) }; engine?.updateMicFx() }

    SectionTitle("Pengaturan komentar")
    SwitchRow("Bacakan komentar (TTS)", s.features.readComments) { v -> feat { it.copy(readComments = v) } }
    SwitchRow("Filter kata kasar", s.features.profanityFilter) { v -> feat { it.copy(profanityFilter = v) } }
    SwitchRow("Terjemahkan komentar asing", s.features.translate) { v -> feat { it.copy(translate = v) } }
    SwitchRow("Tampilkan komentar di layar siaran", s.commentDisplay.onStream) { v -> Graph.settings.update { it.copy(commentDisplay = it.commentDisplay.copy(onStream = v)) } }
    SliderRow("Ukuran komentar di layarmu", s.commentDisplay.hostTextScale, 0.7f..2f) { v -> Graph.settings.update { it.copy(commentDisplay = it.commentDisplay.copy(hostTextScale = v)) } }
    SliderRow("Kecepatan TTS", s.tts.rate, 0.5f..2f) { v -> Graph.settings.update { it.copy(tts = it.tts.copy(rate = v)) }; Graph.tts.config = Graph.settings.current.tts }
    var showMute by remember { mutableStateOf(false) }
    GhostButton("Mute penonton", icon = Icons.Filled.VoiceOverOff) { showMute = true }
    if (showMute) {
        val brain = runCatching { engine?.brain }.getOrNull()
        val names = brain?.recentCommenters?.toList().orEmpty()
        if (names.isEmpty()) Text("Belum ada yang berkomentar.", color = Neon.Muted)
        names.forEach { n -> GhostButton("Mute $n") { brain?.muteViewer(n) } }
    }

    SectionTitle("Penanda & bantuan")
    SwitchRow("Getaran & suara penanda", s.features.cues) { v -> feat { it.copy(cues = v) } }
    SwitchRow("Peringatan baterai & suhu", s.features.batteryWarning) { v -> feat { it.copy(batteryWarning = v) } }
    SwitchRow("Pengingat minum & istirahat", s.features.drinkReminder, "Setiap ${s.drinkReminderMinutes} menit") { v -> feat { it.copy(drinkReminder = v) } }
    SwitchRow("Peringatan live sepi", s.features.quietWarning, "Kalau ${s.quietWarnMinutes} menit tidak ada komentar") { v -> feat { it.copy(quietWarning = v) } }
    SwitchRow("Balas komentar YouTube pakai suara", s.features.voiceReply, "Ucapkan: ${s.voiceReplyTrigger} nama pesan") { v -> feat { it.copy(voiceReply = v) } }
    SwitchRow("Mode pembaca layar (tunanetra)", s.a11y.voiceAids) { v -> Graph.settings.update { it.copy(a11y = it.a11y.copy(voiceAids = v, chosen = true)) } }

    SectionTitle("Platform tujuan")
    val st by LiveBus.stats.collectAsState()
    Text("YouTube: ${st.youtubeState} · TikTok: ${st.tiktokState}")
    s.extraPlatforms.filter { it.enabled }.forEach { p -> Text("${p.name}: ${st.extraStates[p.id] ?: "Mati"}") }

    SectionTitle("Lainnya")
    NavRow("Moderator", "Tambah moderator lewat username atau link", Icons.Filled.AdminPanelSettings) { openAccount("moderators") }
    NavRow("Getaran & suara per kejadian", "Atur pola getar, efek suara, dan peringatan suara", Icons.Filled.Vibration) { openSettings("cues") }
    NavRow("Pengaturan lengkap", "Semua pengaturan aplikasi", Icons.Filled.Settings) { openSettings(null) }
}
