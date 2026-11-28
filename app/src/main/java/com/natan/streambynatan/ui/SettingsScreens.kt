package com.natan.streambynatan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.*
import com.natan.streambynatan.features.Scheduler
import com.natan.streambynatan.features.SystemHelpers
import com.natan.streambynatan.features.Updater
import com.natan.streambynatan.overlay.Cinematics
import com.natan.streambynatan.overlay.Transitions
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.launch
import java.util.UUID

private data class Page(val id: String, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val pages = listOf(
    Page("keys", "Stream key & server", "YouTube, TikTok, komentar (tanpa API key TikTok)", Icons.Filled.Key),
    Page("video", "Video & koneksi", "Resolusi, bitrate adaptif, sambung ulang, rekam", Icons.Filled.HighQuality),
    Page("features", "Semua fitur", "Nyalakan atau matikan setiap fitur", Icons.Filled.ToggleOn),
    Page("profiles", "Profil live", "Ngobrol santai, Presentasi, Catatan live, Live music, dll", Icons.Filled.Badge),
    Page("tts", "Suara TTS", "Suara, kecepatan, volume (terpisah dari Jieshuo)", Icons.Filled.RecordVoiceOver),
    Page("templates", "Kalimat terima kasih", "Template {nama}, {hadiah}, {jumlah}, {koin}", Icons.Filled.FormatQuote),
    Page("comments", "Komentar & pertanyaan", "Kata tanya, singkatan, filter kata kasar, mute, terjemahan", Icons.Filled.Forum),
    Page("targets", "Target & timer", "Target like/koin/penonton, pengingat waktu", Icons.Filled.Flag),
    Page("tools", "Alat live", "Giveaway, kuis, request lagu, rundown, pengingat, papan nama, jeda", Icons.Filled.Build),
    Page("cues", "Getar, suara & TTS per kejadian", "Atur tanda untuk gift, tamu, koneksi putus, dll", Icons.Filled.Vibration),
    Page("screen", "Teks di layar", "Teks overlay, teks berjalan, subtitle, pengumuman", Icons.Filled.Subtitles),
    Page("faq", "Jawaban otomatis (FAQ)", "Pertanyaan yang sering ditanyakan", Icons.Filled.LiveHelp),
    Page("voice", "Perintah suara", "Kalimat pemicu yang bisa diatur", Icons.Filled.KeyboardVoice),
    Page("notes", "Catatan live & lirik", "Catatan poin, ayat, lirik Live music", Icons.Filled.MenuBook),
    Page("visual", "Tampilan & transisi", "Kartu ayat, kode QR, persembahan, Saweria, pembuka/penutup", Icons.Filled.AutoAwesome),
    Page("sound", "Mikrofon, musik & efek suara", "Peredam bising, gema, musik latar, suara gift", Icons.Filled.Equalizer),
    Page("camera", "Kamera & tamu", "Ganti latar, suara saja, nama kamera, tamu lewat link", Icons.Filled.Groups),
    Page("schedule", "Jadwal live", "Pengingat sebelum live", Icons.Filled.Event),
    Page("account", "Akun & cadangan", "Login, cadangan ke Firebase", Icons.Filled.CloudSync),
    Page("system", "Sistem & pembaruan", "Izin, baterai, Jangan Ganggu, versi baru", Icons.Filled.SystemUpdate),
)

@Composable
fun SettingsHome(open: (String) -> Unit) {
    ScreenColumn {
        ScreenTitle("Pengaturan", "Semua bisa diatur sendiri")
        pages.forEach { p -> NavRow(p.title, p.subtitle, p.icon) { open(p.id) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsPage(activity: MainActivity, id: String, back: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    val up: ((AppSettings) -> AppSettings) -> Unit = { Graph.settings.update(it) }
    val page = pages.first { it.id == id }
    ScreenColumn {
        GhostButton("Kembali", icon = Icons.Filled.ArrowBack, onClick = back)
        ScreenTitle(page.title, page.subtitle)
        when (id) {
            "keys" -> KeysPage(activity, s, up)
            "video" -> VideoPage(s, up)
            "features" -> FeaturesPage(s.features) { f -> up { it.copy(features = f) } }
            "profiles" -> ProfilesPage(s, up)
            "tts" -> TtsPage(s, up)
            "templates" -> TemplatesPage(s, up)
            "comments" -> CommentsPage(s, up)
            "targets" -> TargetsPage(s, up)
            "tools" -> LiveToolsPage(activity, s, up)
            "cues" -> CuesPage(s, up)
            "screen" -> ScreenTextPage(s, up)
            "faq" -> FaqPage(activity, s, up)
            "voice" -> VoicePage(s, up)
            "notes" -> NotesPage(s, up)
            "visual" -> VisualPage(activity, s, up)
            "sound" -> SoundPage(activity, s, up)
            "camera" -> CameraPage(activity, s, up)
            "schedule" -> SchedulePage(activity, s, up)
            "account" -> AccountPage(activity)
            "system" -> SystemPage(activity, s, up)
        }
        Spacer(Modifier.height(40.dp))
    }
}

private typealias Up = ((AppSettings) -> AppSettings) -> Unit

@Composable
private fun KeysPage(activity: MainActivity, s: AppSettings, up: Up) {
    var ytKey by remember { mutableStateOf(Graph.secure.get(SecureStore.YOUTUBE_KEY)) }
    var ttKey by remember { mutableStateOf(Graph.secure.get(SecureStore.TIKTOK_KEY)) }
    var ytApi by remember { mutableStateOf(Graph.secure.get(SecureStore.YOUTUBE_API_KEY)) }
    var ytServer by remember { mutableStateOf(s.youtubeServer) }
    var ttServer by remember { mutableStateOf(s.tiktokServer) }
    GlassCard {
        SectionTitle("YouTube")
        SwitchRow("Live ke YouTube", s.enableYouTube) { v -> up { it.copy(enableYouTube = v) } }
        Field("Server YouTube", ytServer) { ytServer = it }
        Field("Stream key YouTube (tetap sama setiap live)", ytKey, secret = true) { ytKey = it }
        val connected by activity.youtubeConnected
        GhostButton(if (connected) "YouTube terhubung" else "Hubungkan YouTube (login Google)", icon = Icons.Filled.Link) { activity.connectYouTube() }
    }
    GlassCard {
        SectionTitle("TikTok")
        SwitchRow("Live ke TikTok", s.enableTikTok) { v -> up { it.copy(enableTikTok = v) } }
        Field("Server TikTok", ttServer) { ttServer = it }
        Field("Stream key TikTok (berganti setiap live)", ttKey, secret = true) { ttKey = it }
        GhostButton("Tempel key TikTok", icon = Icons.Filled.ContentPaste) { activity.clipboardText()?.let { ttKey = it } }
    }
    SectionTitle("Platform tambahan")
    val extraKeys = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { s.extraPlatforms.forEach { put(it.id, Graph.secure.get("key_" + it.id)) } } }
    s.extraPlatforms.forEach { p ->
        fun setP(f: (PlatformTarget) -> PlatformTarget) = up { st -> st.copy(extraPlatforms = st.extraPlatforms.map { if (it.id == p.id) f(it) else it }) }
        GlassCard {
            SwitchRow("Live ke ${p.name}", p.enabled) { v -> setP { it.copy(enabled = v) } }
            if (p.id.startsWith("custom")) Field("Nama platform", p.name) { v -> setP { it.copy(name = v) } }
            Field("Server RTMP/RTMPS", p.server) { v -> setP { it.copy(server = v.trim()) } }
            Field("Stream key ${p.name}", extraKeys[p.id] ?: "", secret = true) { extraKeys[p.id] = it }
            GhostButton("Tempel key", icon = Icons.Filled.ContentPaste) { activity.clipboardText()?.let { extraKeys[p.id] = it } }
        }
    }
    NeonButton("Simpan", icon = Icons.Filled.Save) {
        extraKeys.forEach { (id, k) -> Graph.secure.put("key_" + id, k.trim()) }
        Graph.secure.put(SecureStore.YOUTUBE_KEY, ytKey.trim())
        Graph.secure.put(SecureStore.TIKTOK_KEY, ttKey.trim())
        up { it.copy(youtubeServer = ytServer.trim(), tiktokServer = ttServer.trim()) }
        activity.message.value = "Tersimpan (terenkripsi di HP)."
    }
    GlassCard {
        SectionTitle("Komentar & kejadian")
        Field("Username TikTok (tanpa @)", s.tiktokUsername) { v -> up { it.copy(tiktokUsername = v.trim()) } }
        Text("Komentar, hadiah, ikuti, bagikan & penonton TikTok dibaca langsung dari halaman live TikTok kamu. Tidak perlu API key.", color = Neon.Muted)
        GhostButton("Buka halaman TikTok (login / verifikasi)", icon = Icons.Filled.OpenInBrowser) {
            activity.startActivity(android.content.Intent(activity, com.natan.streambynatan.chat.TikTokWebActivity::class.java))
        }
        GhostButton("Sambung ulang komentar TikTok", icon = Icons.Filled.Refresh) { Graph.tiktok.reload() }
        Field("ID video YouTube live (opsional)", s.youtubeVideoId) { v -> up { it.copy(youtubeVideoId = v.trim()) } }
        Field("API key YouTube Data (opsional kalau sudah login Google)", ytApi, secret = true) { ytApi = it; Graph.secure.put(SecureStore.YOUTUBE_API_KEY, it.trim()) }
        Field("Link kanal YouTube (untuk bagikan link)", s.youtubeChannelUrl) { v -> up { it.copy(youtubeChannelUrl = v.trim()) } }
    }
}

@Composable
private fun VideoPage(s: AppSettings, up: Up) {
    val v = s.video
    fun set(f: (VideoConfig) -> VideoConfig) = up { it.copy(video = f(it.video)) }
    GlassCard {
        ChoiceRow("Resolusi", listOf("480" to "480p hemat", "720" to "720p", "1080" to "1080p"), minOf(v.width, v.height).toString()) { r ->
            val short = r.toInt(); val long = short * 16 / 9
            set { it.copy(width = short, height = long) }
        }
        ChoiceRow("Frame per detik", listOf("24" to "24", "30" to "30", "60" to "60"), v.fps.toString()) { f -> set { it.copy(fps = f.toInt()) } }
        SliderRow("Bitrate video", v.bitrateKbps.toFloat(), 600f..8000f, { "${it.toInt()} kbps" }) { b -> set { it.copy(bitrateKbps = b.toInt()) } }
        ChoiceRow("Bitrate audio", listOf("96" to "96 kbps", "128" to "128 kbps", "192" to "192 kbps"), v.audioBitrateKbps.toString()) { a -> set { it.copy(audioBitrateKbps = a.toInt()) } }
        SwitchRow("Tegak (portrait)", v.portrait, "Paling cocok untuk TikTok") { p -> set { it.copy(portrait = p) } }
        SwitchRow("Bitrate adaptif", v.adaptiveBitrate, "Turun otomatis saat internet lemah") { p -> set { it.copy(adaptiveBitrate = p) } }
        SwitchRow("Sambung ulang otomatis", v.autoReconnect) { p -> set { it.copy(autoReconnect = p) } }
        SwitchRow("Rekam live ke HP", v.recordWhileLive, "Diperlukan untuk potongan video per pertanyaan") { p -> set { it.copy(recordWhileLive = p) } }
    }
}

private val featureLabels: List<Triple<String, String, (FeatureToggles) -> Boolean>> = listOf(
    Triple("readComments", "Bacakan komentar", { it.readComments }),
    Triple("commentsToBroadcast", "Bacaan komentar ikut tersiar", { it.commentsToBroadcast }),
    Triple("questionQueue", "Antrean pertanyaan", { it.questionQueue }),
    Triple("thanksGift", "Terima kasih gift", { it.thanksGift }),
    Triple("thanksFollow", "Terima kasih follow", { it.thanksFollow }),
    Triple("thanksShare", "Terima kasih share", { it.thanksShare }),
    Triple("thanksSubscribe", "Terima kasih subscribe", { it.thanksSubscribe }),
    Triple("loyalGreeting", "Sapaan penonton setia", { it.loyalGreeting }),
    Triple("newViewerGreeting", "Sapaan penonton baru", { it.newViewerGreeting }),
    Triple("profanityFilter", "Filter kata kasar", { it.profanityFilter }),
    Triple("translate", "Terjemahan komentar", { it.translate }),
    Triple("polls", "Polling lewat komentar", { it.polls }),
    Triple("targets", "Target live", { it.targets }),
    Triple("liveTimer", "Penghitung waktu live", { it.liveTimer }),
    Triple("ticker", "Teks berjalan", { it.ticker }),
    Triple("overlayText", "Teks overlay", { it.overlayText }),
    Triple("subtitles", "Subtitle otomatis", { it.subtitles }),
    Triple("announcements", "Pengumuman otomatis", { it.announcements }),
    Triple("missedSummary", "Ringkasan komentar terlewat", { it.missedSummary }),
    Triple("leaderboard", "Papan peringkat pendukung", { it.leaderboard }),
    Triple("giftSfx", "Efek suara otomatis untuk gift", { it.giftSfx }),
    Triple("notesWhisper", "Naskah dibisikkan pelan", { it.notesWhisper }),
    Triple("verseFromScript", "Ayat otomatis dari naskah", { it.verseFromScript }),
    Triple("verseFromVoice", "Ayat otomatis dari suara", { it.verseFromVoice }),
    Triple("prayerList", "Daftar permohonan doa", { it.prayerList }),
    Triple("backgroundReplace", "Pengganti latar belakang", { it.backgroundReplace }),
    Triple("lyrics", "Lirik Live music di layar", { it.lyrics }),
    Triple("offering", "Info persembahan", { it.offering }),
    Triple("saweria", "Saweria", { it.saweria }),
    Triple("intro", "Layar pembuka", { it.intro }),
    Triple("outro", "Layar penutup", { it.outro }),
    Triple("cameraAssistant", "Asisten posisi kamera", { it.cameraAssistant }),
    Triple("autoLight", "Penyesuai cahaya otomatis", { it.autoLight }),
    Triple("transcript", "Transkrip live", { it.transcript }),
    Triple("voiceCommands", "Perintah suara", { it.voiceCommands }),
    Triple("audioOnly", "Mode suara saja", { it.audioOnly }),
    Triple("shareLink", "Bagikan link live otomatis", { it.shareLink }),
    Triple("faq", "Jawaban otomatis (FAQ)", { it.faq }),
    Triple("momentClips", "Potong video per pertanyaan/poin", { it.momentClips }),
    Triple("music", "Musik latar saat mulai", { it.music }),
    Triple("guests", "Tamu & kamera tambahan", { it.guests }),
    Triple("doNotDisturb", "Jangan Ganggu saat live", { it.doNotDisturb }),
    Triple("keepScreenOn", "Layar tetap menyala", { it.keepScreenOn }),
    Triple("progressStats", "Perkembangan antar-live", { it.progressStats }),
    Triple("giveaway", "Giveaway lewat komentar", { it.giveaway }),
    Triple("quiz", "Kuis lewat komentar", { it.quiz }),
    Triple("songRequests", "Request lagu dari penonton", { it.songRequests }),
    Triple("quietWarning", "Peringatan saat live sepi", { it.quietWarning }),
    Triple("drinkReminder", "Pengingat minum", { it.drinkReminder }),
    Triple("batteryWarning", "Peringatan baterai & suhu HP", { it.batteryWarning }),
    Triple("voiceReply", "Balas komentar YouTube dengan suara", { it.voiceReply }),
    Triple("pauseButton", "Tombol jeda layar", { it.pauseButton }),
    Triple("rundown", "Rundown acara", { it.rundown }),
    Triple("practiceReport", "Laporan latihan Catatan live", { it.practiceReport }),
    Triple("themes", "TEMA: gambar layar penuh lewat suara", { it.themes }),
    Triple("nameplate", "Papan nama di layar", { it.nameplate }),
    Triple("cues", "Getar, suara & TTS per kejadian", { it.cues }),
    Triple("touchLock", "Kunci sentuh saat live", { it.touchLock }),
    Triple("musicDucking", "Musik mengecil saat host bicara", { it.musicDucking }),
    Triple("guestClips", "Potong rekaman per tamu", { it.guestClips }),
)

/** Mengubah satu saklar berdasarkan nama (lewat JSON supaya ringkas). */
private fun toggle(f: FeatureToggles, key: String, value: Boolean): FeatureToggles {
    val obj = AppJson.encodeToJsonElement(FeatureToggles.serializer(), f) as kotlinx.serialization.json.JsonObject
    val next = kotlinx.serialization.json.JsonObject(obj + (key to kotlinx.serialization.json.JsonPrimitive(value)))
    return AppJson.decodeFromJsonElement(FeatureToggles.serializer(), next)
}

@Composable
fun FeaturesPage(f: FeatureToggles, onChange: (FeatureToggles) -> Unit) {
    GlassCard {
        featureLabels.forEach { (key, label, get) -> SwitchRow(label, get(f)) { onChange(toggle(f, key, it)) } }
    }
}

@Composable
private fun ProfilesPage(s: AppSettings, up: Up) {
    var name by remember { mutableStateOf("") }
    s.profiles.forEach { p ->
        GlassCard {
            Text(p.name + if (p.id == s.activeProfileId) " (aktif)" else "", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Aktifkan", Modifier.weight(1f)) { Graph.settings.activateProfile(p.id) }
                GhostButton("Simpan fitur sekarang", Modifier.weight(1f)) {
                    up { st -> st.copy(profiles = st.profiles.map { if (it.id == p.id) it.copy(features = st.features, noteId = st.activeNoteId) else it }) }
                }
            }
            ChoiceRow("Template pembuka profil ini", listOf("" to "Bawaan") + Cinematics.templates, p.introTemplate) { t ->
                up { st -> st.copy(profiles = st.profiles.map { if (it.id == p.id) it.copy(introTemplate = t) else it }) }
            }
            if (s.profiles.size > 1) GhostButton("Hapus profil") { up { st -> st.copy(profiles = st.profiles.filterNot { it.id == p.id }) } }
        }
    }
    GlassCard {
        Field("Nama profil baru", name) { name = it }
        NeonButton("Tambah profil dari fitur sekarang") {
            if (name.isNotBlank()) up { st -> st.copy(profiles = st.profiles + LiveProfile(UUID.randomUUID().toString().take(6), name, st.features)) }
            name = ""
        }
    }
}

@Composable
private fun TtsPage(s: AppSettings, up: Up) {
    val t = s.tts
    fun set(f: (TtsConfig) -> TtsConfig) = up { val n = it.copy(tts = f(it.tts)); Graph.tts.config = n.tts; n }
    GlassCard {
        val voices = remember { Graph.tts.voices() }
        if (voices.isNotEmpty()) ChoiceRow("Suara", listOf("" to "Bawaan") + voices.map { it to it.substringAfter("-").take(24) }, t.voiceName) { v -> set { it.copy(voiceName = v) } }
        SliderRow("Kecepatan", t.rate, 0.5f..2f) { v -> set { it.copy(rate = v) } }
        SliderRow("Nada", t.pitch, 0.5f..2f) { v -> set { it.copy(pitch = v) } }
        SliderRow("Volume", t.volume, 0.1f..1f) { v -> set { it.copy(volume = v) } }
        SliderRow("Kecepatan bisikan", t.whisperRate, 0.5f..2f) { v -> set { it.copy(whisperRate = v) } }
        SliderRow("Kecepatan baca Catatan live", t.scriptRate, 0.4f..1.2f) { v -> set { it.copy(scriptRate = v) } }
        SwitchRow("Dengarkan TTS & efek suara di earphone", t.monitorPublicTts, "Tidak terdengar dobel oleh penonton") { v -> set { it.copy(monitorPublicTts = v) } }
        GhostButton("Coba suara") { Graph.tts.whisper("Halo, ini suara Stream By Natan.") }
    }
}

@Composable
private fun TemplatesPage(s: AppSettings, up: Up) {
    val t = s.templates
    fun set(f: (Templates) -> Templates) = up { it.copy(templates = f(it.templates)) }
    GlassCard {
        Text("Kata pengganti: {nama} {hadiah} {jumlah} {koin} {pesan} {target}", color = Neon.Muted)
        Field("Gift", t.gift, singleLine = false) { v -> set { it.copy(gift = v) } }
        Field("Follow", t.follow, singleLine = false) { v -> set { it.copy(follow = v) } }
        Field("Subscribe", t.subscribe, singleLine = false) { v -> set { it.copy(subscribe = v) } }
        Field("Share", t.share, singleLine = false) { v -> set { it.copy(share = v) } }
        Field("Super Chat", t.superChat, singleLine = false) { v -> set { it.copy(superChat = v) } }
        Field("Member YouTube", t.member, singleLine = false) { v -> set { it.copy(member = v) } }
        Field("Saweria", t.saweria, singleLine = false) { v -> set { it.copy(saweria = v) } }
        Field("Penonton setia", t.loyal, singleLine = false) { v -> set { it.copy(loyal = v) } }
        Field("Penonton baru", t.newViewer, singleLine = false) { v -> set { it.copy(newViewer = v) } }
        Field("Cara membaca komentar", t.comment, singleLine = false) { v -> set { it.copy(comment = v) } }
        Field("Cara membaca pertanyaan", t.question, singleLine = false) { v -> set { it.copy(question = v) } }
        Field("Target tercapai", t.targetReached, singleLine = false) { v -> set { it.copy(targetReached = v) } }
    }
}

@Composable
private fun CommentsPage(s: AppSettings, up: Up) {
    GlassCard { ListEditor("Kata tanya & pemicu pertanyaan", s.questionWords) { v -> up { it.copy(questionWords = v) } } }
    GlassCard { MapEditor("Singkatan yang dipanjangkan", s.abbreviations) { v -> up { it.copy(abbreviations = v) } } }
    GlassCard { ListEditor("Kata kasar yang disensor", s.profanity) { v -> up { it.copy(profanity = v) } } }
    GlassCard { ListEditor("Penonton yang di-mute (nama/ID)", s.mutedViewers) { v -> up { it.copy(mutedViewers = v) } } }
    GlassCard { ListEditor("Kata pemicu permohonan doa", s.prayerTriggers) { v -> up { it.copy(prayerTriggers = v) } } }
    GlassCard {
        ChoiceRow("Terjemahkan ke", listOf("id" to "Indonesia", "en" to "Inggris"), s.translate.targetLanguage) { v -> up { it.copy(translate = it.translate.copy(targetLanguage = v)) } }
        SwitchRow("Bacakan hasil terjemahan", s.translate.readTranslated) { v -> up { it.copy(translate = it.translate.copy(readTranslated = v)) } }
        NumberField("Lama polling (detik)", s.pollDurationSec) { v -> up { it.copy(pollDurationSec = v) } }
    }
}

@Composable
private fun TargetsPage(s: AppSettings, up: Up) {
    GlassCard {
        NumberField("Target like", s.targets.likes) { v -> up { it.copy(targets = it.targets.copy(likes = v)) } }
        NumberField("Target koin", s.targets.coins) { v -> up { it.copy(targets = it.targets.copy(coins = v)) } }
        NumberField("Target penonton", s.targets.viewers) { v -> up { it.copy(targets = it.targets.copy(viewers = v)) } }
        SwitchRow("Hitung juga total gabungan", s.targets.combined) { v -> up { it.copy(targets = it.targets.copy(combined = v)) } }
    }
    GlassCard {
        NumberField("Pengingat setiap (menit)", s.timer.reminderMinutes) { v -> up { it.copy(timer = it.timer.copy(reminderMinutes = v)) } }
        NumberField("Batas waktu live (menit, 0 = tanpa batas)", s.timer.limitMinutes) { v -> up { it.copy(timer = it.timer.copy(limitMinutes = v)) } }
        NumberField("Peringatan sebelum habis (menit)", s.timer.warnBeforeMinutes) { v -> up { it.copy(timer = it.timer.copy(warnBeforeMinutes = v)) } }
    }
}

@Composable
private fun ScreenTextPage(s: AppSettings, up: Up) {
    GlassCard {
        Field("Teks overlay (atas layar)", s.overlayText, singleLine = false) { v -> up { it.copy(overlayText = v) } }
        Field("Teks berjalan", s.ticker.text, singleLine = false) { v -> up { it.copy(ticker = it.ticker.copy(text = v)) } }
        SliderRow("Kecepatan teks berjalan", s.ticker.speed, 0.3f..3f) { v -> up { it.copy(ticker = it.ticker.copy(speed = v)) } }
        SliderRow("Ukuran subtitle", s.subtitles.textSize, 0.6f..1.8f) { v -> up { it.copy(subtitles = it.subtitles.copy(textSize = v)) } }
    }
    SectionTitle("Pengumuman otomatis")
    s.announcements.forEachIndexed { i, a ->
        GlassCard {
            Field("Kalimat", a.text, singleLine = false) { v -> up { st -> st.copy(announcements = st.announcements.mapIndexed { j, x -> if (j == i) x.copy(text = v) else x }) } }
            NumberField("Setiap (menit)", a.everyMinutes) { v -> up { st -> st.copy(announcements = st.announcements.mapIndexed { j, x -> if (j == i) x.copy(everyMinutes = v) else x }) } }
            ChoiceRow("Cara", listOf("tts" to "Dibacakan", "screen" to "Di layar", "both" to "Keduanya"), a.mode) { v -> up { st -> st.copy(announcements = st.announcements.mapIndexed { j, x -> if (j == i) x.copy(mode = v) else x }) } }
            GhostButton("Hapus") { up { st -> st.copy(announcements = st.announcements.filterIndexed { j, _ -> j != i }) } }
        }
    }
    GhostButton("Tambah pengumuman", icon = Icons.Filled.Add) { up { it.copy(announcements = it.announcements + Announcement("Pengumuman baru")) } }
}

@Composable
private fun FaqPage(activity: MainActivity, s: AppSettings, up: Up) {
    s.faq.forEachIndexed { i, f ->
        fun set(n: FaqItem) = up { st -> st.copy(faq = st.faq.mapIndexed { j, x -> if (j == i) n else x }) }
        GlassCard {
            ListEditor("Kata kunci pertanyaan", f.keywords) { set(f.copy(keywords = it)) }
            Field("Jawaban", f.answer, singleLine = false) { set(f.copy(answer = it)) }
            ChoiceRow("Cara menjawab", listOf("tts" to "Dibacakan", "image" to "Gambar", "both" to "Keduanya"), f.mode) { set(f.copy(mode = it)) }
            GhostButton(if (f.imagePath.isBlank()) "Pilih gambar jawaban" else "Gambar dipilih", icon = Icons.Filled.Image) {
                activity.pick(arrayOf("image/*")) { u -> set(f.copy(imagePath = u.toString())) }
            }
            GhostButton("Hapus") { up { st -> st.copy(faq = st.faq.filterIndexed { j, _ -> j != i }) } }
        }
    }
    GhostButton("Tambah pertanyaan", icon = Icons.Filled.Add) { up { it.copy(faq = it.faq + FaqItem(listOf("kata kunci"), "Jawaban")) } }
    GlassCard { ListEditor("Kalimat perintah untuk menampilkan jawaban", s.faqCommandPhrases) { v -> up { it.copy(faqCommandPhrases = v) } } }
}

private val actionLabels = mapOf(
    "next_question" to "Pertanyaan berikutnya", "repeat_question" to "Ulangi pertanyaan", "hold_question" to "Tahan",
    "skip_question" to "Lewati", "missed_summary" to "Ringkasan komentar", "top_supporters" to "Pendukung teratas",
    "read_prayers" to "Bacakan doa", "show_qr" to "Tampilkan kode QR", "hide_qr" to "Sembunyikan kode QR",
    "show_offering" to "Tampilkan persembahan", "hide_offering" to "Sembunyikan persembahan", "show_answer" to "Tampilkan jawaban",
    "hide_overlay" to "Tutup tampilan", "next_point" to "Poin berikutnya", "camera_front" to "Kamera depan",
    "camera_back" to "Kamera belakang", "switch_camera" to "Pindah ke kamera (diikuti nama kamera)", "mic_off" to "Matikan mikrofon",
    "mic_on" to "Nyalakan mikrofon", "poll_result" to "Hasil polling", "live_time" to "Durasi live", "music_toggle" to "Putar/hentikan musik",
)

@Composable
private fun VoicePage(s: AppSettings, up: Up) {
    s.voiceCommands.forEach { (action, phrases) ->
        GlassCard {
            ListEditor(actionLabels[action] ?: action, phrases) { v -> up { st -> st.copy(voiceCommands = LinkedHashMap(st.voiceCommands).apply { put(action, v) }) } }
        }
    }
}

@Composable
private fun NotesPage(s: AppSettings, up: Up) {
    GlassCard {
        Text("Tulis naskah lengkap. Baris diawali # menjadi judul poin. Ayat ditulis: [Yohanes 3:16] Karena begitu besar kasih Allah…", color = Neon.Muted)
    }
    s.notes.forEach { n ->
        GlassCard {
            Field("Judul", n.title) { v -> up { st -> st.copy(notes = st.notes.map { if (it.id == n.id) it.copy(title = v) else it }) } }
            Field("Naskah / catatan poin", n.content, singleLine = false, minLines = 8) { v -> up { st -> st.copy(notes = st.notes.map { if (it.id == n.id) it.copy(content = v) else it }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(if (s.activeNoteId == n.id) "Dipakai" else "Pakai", Modifier.weight(1f)) { up { it.copy(activeNoteId = n.id) } }
                GhostButton("Hapus", Modifier.weight(1f)) { up { st -> st.copy(notes = st.notes.filterNot { it.id == n.id }) } }
            }
        }
    }
    GhostButton("Tambah naskah", icon = Icons.Filled.Add) { up { it.copy(notes = it.notes + NoteScript(UUID.randomUUID().toString().take(6), "Naskah baru", "# Poin 1\n")) } }
    SectionTitle("Lirik Live music")
    s.lyrics.forEach { l ->
        GlassCard {
            Field("Judul lagu", l.title) { v -> up { st -> st.copy(lyrics = st.lyrics.map { if (it.id == l.id) it.copy(title = v) else it }) } }
            Field("Lirik (satu baris per baris)", l.lines, singleLine = false, minLines = 6) { v -> up { st -> st.copy(lyrics = st.lyrics.map { if (it.id == l.id) it.copy(lines = v) else it }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(if (s.activeLyricId == l.id) "Dipakai" else "Pakai", Modifier.weight(1f)) { up { it.copy(activeLyricId = l.id) } }
                GhostButton("Hapus", Modifier.weight(1f)) { up { st -> st.copy(lyrics = st.lyrics.filterNot { it.id == l.id }) } }
            }
        }
    }
    GhostButton("Tambah lagu", icon = Icons.Filled.Add) { up { it.copy(lyrics = it.lyrics + LyricSong(UUID.randomUUID().toString().take(6), "Lagu baru", "")) } }
}

private val sizes = listOf("small" to "Kecil di pojok", "medium" to "Sedang", "full" to "Penuh layar")
private val positions = listOf("top_left" to "Kiri atas", "top_right" to "Kanan atas", "center" to "Tengah", "bottom_left" to "Kiri bawah", "bottom_right" to "Kanan bawah")

@Composable
private fun VisualPage(activity: MainActivity, s: AppSettings, up: Up) {
    GlassCard {
        SectionTitle("Transisi")
        ChoiceRow("Gaya transisi", Transitions.styles, s.transition.style) { v -> up { it.copy(transition = it.transition.copy(style = v)) } }
        SliderRow("Kecepatan transisi", s.transition.durationMs.toFloat(), 150f..2000f, { "${it.toInt()} ms" }) { v -> up { it.copy(transition = it.transition.copy(durationMs = v.toInt())) } }
    }
    GlassCard {
        SectionTitle("Kartu ayat")
        ColorRow("Warna latar 1", s.verseCard.bgColor) { v -> up { it.copy(verseCard = it.verseCard.copy(bgColor = v)) } }
        ColorRow("Warna latar 2", s.verseCard.bgColor2) { v -> up { it.copy(verseCard = it.verseCard.copy(bgColor2 = v)) } }
        ColorRow("Warna huruf", s.verseCard.textColor) { v -> up { it.copy(verseCard = it.verseCard.copy(textColor = v)) } }
        ColorRow("Warna aksen", s.verseCard.accentColor) { v -> up { it.copy(verseCard = it.verseCard.copy(accentColor = v)) } }
        SliderRow("Ukuran huruf", s.verseCard.fontScale, 0.6f..1.6f) { v -> up { it.copy(verseCard = it.verseCard.copy(fontScale = v)) } }
        GhostButton(if (s.verseCard.bgImage.isBlank()) "Pakai foto sendiri sebagai latar" else "Foto latar dipilih (ketuk untuk ganti)", icon = Icons.Filled.Image) {
            activity.pick(arrayOf("image/*")) { u -> up { it.copy(verseCard = it.verseCard.copy(bgImage = u.toString())) } }
        }
        if (s.verseCard.bgImage.isNotBlank()) GhostButton("Hapus foto latar") { up { it.copy(verseCard = it.verseCard.copy(bgImage = "")) } }
        NumberField("Sembunyikan ayat dari suara setelah diam (milidetik)", s.silenceToHideVerseMs) { v -> up { it.copy(silenceToHideVerseMs = v) } }
    }
    GlassCard {
        SectionTitle("Saweria")
        var overlay by remember { mutableStateOf(Graph.secure.get(SecureStore.SAWERIA_OVERLAY)) }
        Field("Link halaman Saweria (saweria.co/namamu)", s.saweria.pageLink) { v -> up { it.copy(saweria = it.saweria.copy(pageLink = v.trim())) } }
        Field("Link overlay Saweria (rahasia)", overlay, secret = true) { overlay = it; Graph.secure.put(SecureStore.SAWERIA_OVERLAY, it.trim()) }
        Field("Tulisan di atas kode QR", s.qrStyle.title) { v -> up { it.copy(qrStyle = it.qrStyle.copy(title = v)) } }
        ColorRow("Warna latar QR", s.qrStyle.bgColor) { v -> up { it.copy(qrStyle = it.qrStyle.copy(bgColor = v)) } }
        ColorRow("Warna kode QR", s.qrStyle.fgColor) { v -> up { it.copy(qrStyle = it.qrStyle.copy(fgColor = v)) } }
        ColorRow("Warna tulisan QR", s.qrStyle.textColor) { v -> up { it.copy(qrStyle = it.qrStyle.copy(textColor = v)) } }
        ChoiceRow("Ukuran QR", sizes, s.qrStyle.size) { v -> up { it.copy(qrStyle = it.qrStyle.copy(size = v)) } }
        ChoiceRow("Posisi QR", positions, s.qrStyle.position) { v -> up { it.copy(qrStyle = it.qrStyle.copy(position = v)) } }
        NumberField("Lama tampil QR (detik)", s.qrStyle.durationSec) { v -> up { it.copy(qrStyle = it.qrStyle.copy(durationSec = v)) } }
    }
    GlassCard {
        SectionTitle("Info persembahan / donasi")
        GhostButton(if (s.offering.imagePath.isBlank()) "Pilih gambar QRIS / rekening" else "Gambar dipilih", icon = Icons.Filled.Image) {
            activity.pick(arrayOf("image/*")) { u -> up { it.copy(offering = it.offering.copy(imagePath = u.toString())) } }
        }
        Field("Keterangan", s.offering.text, singleLine = false) { v -> up { it.copy(offering = it.offering.copy(text = v)) } }
        ChoiceRow("Ukuran", sizes, s.offering.size) { v -> up { it.copy(offering = it.offering.copy(size = v)) } }
        ChoiceRow("Posisi", positions, s.offering.position) { v -> up { it.copy(offering = it.offering.copy(position = v)) } }
        NumberField("Lama tampil (detik)", s.offering.durationSec) { v -> up { it.copy(offering = it.offering.copy(durationSec = v)) } }
    }
    GlassCard {
        SectionTitle("Layar pembuka & penutup sinematik")
        ChoiceRow("Template pembuka", Cinematics.templates, s.intro.introTemplate) { v -> up { it.copy(intro = it.intro.copy(introTemplate = v)) } }
        Field("Judul pembuka", s.intro.title) { v -> up { it.copy(intro = it.intro.copy(title = v)) } }
        Field("Subjudul pembuka", s.intro.subtitle) { v -> up { it.copy(intro = it.intro.copy(subtitle = v)) } }
        SwitchRow("Hitung mundur", s.intro.countdown) { v -> up { it.copy(intro = it.intro.copy(countdown = v)) } }
        ChoiceRow("Template penutup", Cinematics.templates, s.intro.outroTemplate) { v -> up { it.copy(intro = it.intro.copy(outroTemplate = v)) } }
        Field("Judul penutup", s.intro.outroTitle) { v -> up { it.copy(intro = it.intro.copy(outroTitle = v)) } }
        Field("Subjudul penutup", s.intro.outroSubtitle) { v -> up { it.copy(intro = it.intro.copy(outroSubtitle = v)) } }
        SliderRow("Lama tampil", s.intro.durationSec.toFloat(), 3f..30f, { "${it.toInt()} detik" }) { v -> up { it.copy(intro = it.intro.copy(durationSec = v.toInt())) } }
        val music = Graph.sounds.musicTracks
        if (music.isNotEmpty()) ChoiceRow("Musik pembuka/penutup", music.map { it.id to it.label }, s.intro.musicId) { v -> up { it.copy(intro = it.intro.copy(musicId = v)) } }
    }
}

@Composable
private fun SoundPage(activity: MainActivity, s: AppSettings, up: Up) {
    val fx = s.micFx
    fun setFx(f: (MicFx) -> MicFx) = up { it.copy(micFx = f(it.micFx)) }
    GlassCard {
        SectionTitle("Mikrofon")
        SwitchRow("Peredam bising (bawaan Android)", fx.noiseSuppressor, "Berlaku saat live berikutnya") { v -> setFx { it.copy(noiseSuppressor = v) } }
        SwitchRow("Peredam bising kuat", fx.strongNoiseGate, "Meredam kipas, kendaraan, keramaian saat kamu diam") { v -> setFx { it.copy(strongNoiseGate = v) } }
        SwitchRow("Peredam bising AI (RNNoise)", fx.aiNoiseSuppression, "Paling kuat: meredam kipas, kendaraan, keramaian, suaramu tetap jelas") { v -> setFx { it.copy(aiNoiseSuppression = v) } }
        SwitchRow("Penyeimbang volume", fx.volumeBalancer, "Suara tidak naik turun") { v -> setFx { it.copy(volumeBalancer = v) } }
        SwitchRow("Gema ringan (seperti di gedung gereja)", fx.reverb) { v -> setFx { it.copy(reverb = v) } }
        if (fx.reverb) SliderRow("Kekuatan gema", fx.reverbAmount, 0.05f..0.8f) { v -> setFx { it.copy(reverbAmount = v) } }
        SliderRow("Volume mikrofon", fx.micVolume, 0f..2f) { v -> setFx { it.copy(micVolume = v) } }
        SliderRow("Volume audio internal", fx.internalVolume, 0f..2f) { v -> setFx { it.copy(internalVolume = v) } }
    }
    GlassCard {
        SectionTitle("Musik latar")
        val tracks = Graph.sounds.musicTracks
        ChoiceRow("Pilih musik", tracks.map { it.id to "${it.label} (${it.mood})" }, s.music.trackId) { v -> up { it.copy(music = it.music.copy(trackId = v, customPath = "")) } }
        GhostButton(if (s.music.customPath.isBlank()) "Pakai lagu sendiri dari HP" else "Lagu sendiri dipilih", icon = Icons.Filled.LibraryMusic) {
            activity.pick(arrayOf("audio/*")) { u -> up { it.copy(music = it.music.copy(customPath = u.toString())) } }
        }
        SliderRow("Volume musik", s.music.volume, 0f..1f) { v -> up { it.copy(music = it.music.copy(volume = v)) }; Graph.mixer.musicGain = v }
        SwitchRow("Ulangi terus", s.music.loop) { v -> up { it.copy(music = it.music.copy(loop = v)) } }
        Text("Musik dari Incompetech wajib mencantumkan kredit Kevin MacLeod (lihat Sistem > Kredit).", color = Neon.Muted)
    }
    val allSounds = Graph.sounds.effects.map { it.id to it.label } + s.customSounds.map { it.id to "${it.name} (punyaku)" }
    GlassCard {
        SectionTitle("Efek suara otomatis untuk gift")
        ChoiceRow("Gift kecil", allSounds, s.giftSfx.smallSound) { v -> up { it.copy(giftSfx = it.giftSfx.copy(smallSound = v)) } }
        ChoiceRow("Gift besar", allSounds, s.giftSfx.bigSound) { v -> up { it.copy(giftSfx = it.giftSfx.copy(bigSound = v)) } }
        ChoiceRow("Follow / subscribe", allSounds, s.giftSfx.followSound) { v -> up { it.copy(giftSfx = it.giftSfx.copy(followSound = v)) } }
        ChoiceRow("Share", allSounds, s.giftSfx.shareSound) { v -> up { it.copy(giftSfx = it.giftSfx.copy(shareSound = v)) } }
        NumberField("Batas gift besar (koin)", s.giftSfx.bigThresholdCoins) { v -> up { it.copy(giftSfx = it.giftSfx.copy(bigThresholdCoins = v)) } }
        SliderRow("Volume efek suara", s.giftSfx.volume, 0f..1.5f) { v -> up { it.copy(giftSfx = it.giftSfx.copy(volume = v)) } }
    }
    GlassCard {
        SectionTitle("Efek suara sendiri (dari file audio atau video)")
        s.customSounds.forEach { c ->
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            NumberField("Mulai (milidetik)", c.startMs.toInt()) { v -> up { st -> st.copy(customSounds = st.customSounds.map { if (it.id == c.id) it.copy(startMs = v.toLong()) else it }) }; Graph.sounds.invalidate(c.id) }
            NumberField("Selesai (milidetik, 0 = sampai habis)", c.endMs.toInt()) { v -> up { st -> st.copy(customSounds = st.customSounds.map { if (it.id == c.id) it.copy(endMs = v.toLong()) else it }) }; Graph.sounds.invalidate(c.id) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Coba", Modifier.weight(1f)) { Graph.mixer.active = true; Graph.sounds.play(c.id, 1f, s.customSounds) }
                GhostButton("Hapus", Modifier.weight(1f)) { up { st -> st.copy(customSounds = st.customSounds.filterNot { it.id == c.id }, floatingSfx = st.floatingSfx - c.id) } }
            }
        }
        GhostButton("Tambah dari file audio/video", icon = Icons.Filled.Add) {
            activity.pick(arrayOf("audio/*", "video/*")) { u ->
                val name = u.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Suara saya"
                up { it.copy(customSounds = it.customSounds + CustomSound("my_" + UUID.randomUUID().toString().take(6), name, u.toString())) }
            }
        }
    }
    GlassCard {
        SectionTitle("Pintasan efek suara (jendela mengambang & layar live)")
        allSounds.forEach { (sid, label) ->
            SwitchRow(label, sid in s.floatingSfx) { on -> up { it.copy(floatingSfx = if (on) (it.floatingSfx + sid).distinct() else it.floatingSfx - sid) } }
        }
    }
}

@Composable
private fun CameraPage(activity: MainActivity, s: AppSettings, up: Up) {
    GlassCard {
        SectionTitle("Pengganti latar belakang")
        ChoiceRow("Jenis latar", listOf("blur" to "Efek buram", "image" to "Gambar", "video" to "Video"), s.background.type) { v -> up { it.copy(background = it.background.copy(type = v)) } }
        GhostButton(if (s.background.path.isBlank()) "Pilih gambar/video latar" else "Latar dipilih", icon = Icons.Filled.Wallpaper) {
            activity.pick(arrayOf("image/*", "video/*")) { u ->
                val isVideo = activity.contentResolver.getType(u)?.startsWith("video") == true
                up { it.copy(background = BackgroundConfig(if (isVideo) "video" else "image", u.toString())) }
            }
        }
        Text("Fitur ini cukup berat: HP bisa lebih cepat panas dan baterai lebih boros.", color = Neon.Muted)
    }
    GlassCard {
        SectionTitle("Mode suara saja")
        GhostButton(if (s.audioOnlyImage.isBlank()) "Pilih gambar (misal salib / foto profil)" else "Gambar dipilih", icon = Icons.Filled.Image) {
            activity.pick(arrayOf("image/*")) { u -> up { it.copy(audioOnlyImage = u.toString()) } }
        }
    }
    GlassCard {
        SectionTitle("Nama kamera (untuk perintah suara)")
        Field("Kamera depan", s.cameraNames["depan"] ?: "") { v -> up { it.copy(cameraNames = it.cameraNames + ("depan" to v)) } }
        Field("Kamera belakang", s.cameraNames["belakang"] ?: "") { v -> up { it.copy(cameraNames = it.cameraNames + ("belakang" to v)) } }
        Text("HP lain yang tersambung memakai nama yang diisi di HP itu, misal \"kamera mimbar\". Ucapkan \"pindah ke kamera mimbar\".", color = Neon.Muted)
    }
    GlassCard {
        SectionTitle("Tamu & kamera tambahan (WebRTC)")
        Field("Alamat halaman tamu (Firebase Hosting)", s.guest.webBaseUrl) { v -> up { it.copy(guest = it.guest.copy(webBaseUrl = v.trim())) } }
        Field("Server TURN (opsional)", s.guest.turnUrl) { v -> up { it.copy(guest = it.guest.copy(turnUrl = v.trim())) } }
        Field("Nama pengguna TURN", s.guest.turnUser) { v -> up { it.copy(guest = it.guest.copy(turnUser = v)) } }
        Field("Kata sandi TURN", s.guest.turnPass, secret = true) { v -> up { it.copy(guest = it.guest.copy(turnPass = v)) } }
        var link by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Link tamu", Modifier.weight(1f)) { if (Graph.guests.start(s.guest)) link = Graph.guests.link(s.guest, "guest") else activity.message.value = "Firebase belum diatur." }
            GhostButton("Link kamera HP lain", Modifier.weight(1f)) { if (Graph.guests.start(s.guest)) link = Graph.guests.link(s.guest, "camera") else activity.message.value = "Firebase belum diatur." }
        }
        if (link.isNotBlank()) {
            Text(link, color = Neon.Cyan)
            val bmp = remember(link) { com.natan.streambynatan.overlay.QrItem.makeQr(link, 480, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()) }
            androidx.compose.foundation.Image(bmp.asImageBitmap(), "Kode QR untuk bergabung", Modifier.height(220.dp))
            GhostButton("Bagikan link", icon = Icons.Filled.Share) {
                activity.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, link), "Bagikan"))
            }
        }
        Text("Webcam USB: colok lewat kabel OTG, lalu pilih sumber \"Webcam USB\". HP lain dengan mode USB \"Webcam\" juga terbaca sebagai webcam.", color = Neon.Muted)
    }
}

@Composable
private fun SchedulePage(activity: MainActivity, s: AppSettings, up: Up) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0) }
    var hour by remember { mutableStateOf(19) }
    var minute by remember { mutableStateOf(0) }
    var before by remember { mutableStateOf(15) }
    var weekly by remember { mutableStateOf(true) }
    val fmt = java.text.SimpleDateFormat("EEEE d MMM HH:mm", java.util.Locale("id"))
    s.schedules.forEach { sc ->
        GlassCard {
            Text(sc.title, style = MaterialTheme.typography.titleMedium)
            Text(fmt.format(java.util.Date(sc.epochMillis)) + (if (sc.repeatWeekly) " · setiap minggu" else "") + " · ingatkan ${sc.remindMinutesBefore} menit sebelumnya", color = Neon.Muted)
            GhostButton("Hapus") { up { st -> st.copy(schedules = st.schedules.filterNot { it.id == sc.id }) } }
        }
    }
    GlassCard {
        Field("Judul live", title) { title = it }
        NumberField("Berapa hari lagi (0 = hari ini)", days) { days = it }
        NumberField("Jam (0-23)", hour) { hour = it.coerceIn(0, 23) }
        NumberField("Menit (0-59)", minute) { minute = it.coerceIn(0, 59) }
        NumberField("Ingatkan berapa menit sebelumnya", before) { before = it }
        SwitchRow("Ulangi setiap minggu", weekly) { weekly = it }
        NeonButton("Tambah jadwal", icon = Icons.Filled.AlarmAdd) {
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, days); set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute); set(java.util.Calendar.SECOND, 0)
            }
            val item = ScheduleItem(UUID.randomUUID().toString().take(6), title.ifBlank { "Live" }, cal.timeInMillis, before, weekly)
            up { it.copy(schedules = it.schedules + item) }
            Scheduler.scheduleAll(activity, Graph.settings.current.schedules)
            activity.message.value = "Jadwal disimpan."
        }
    }
}

@Composable
private fun AccountPage(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    GlassCard {
        if (!Graph.firebase.available) Text("Firebase belum diatur (google-services.json). Lihat panduan di README.", color = Neon.Amber)
        else Text(Graph.firebase.email?.let { "Masuk sebagai $it" } ?: "Belum masuk", style = MaterialTheme.typography.titleMedium)
        NeonButton("Cadangkan pengaturan ke Firebase", icon = Icons.Filled.CloudUpload) {
            scope.launch { status = Graph.firebase.backupSettings(Graph.settings.exportJson()) ?: "Cadangan tersimpan." }
        }
        GhostButton("Pulihkan dari Firebase", icon = Icons.Filled.CloudDownload) {
            scope.launch {
                val json = Graph.firebase.restoreSettings()
                status = if (json != null && Graph.settings.importJson(json)) "Pengaturan dipulihkan." else "Tidak ada cadangan."
            }
        }
        if (Graph.firebase.uid != null) GhostButton("Keluar dari akun", icon = Icons.Filled.Logout) { Graph.firebase.signOut(); status = "Sudah keluar. Buka ulang aplikasi untuk masuk lagi." }
        if (status.isNotBlank()) Text(status, color = Neon.Lime)
        Text("Stream key dan API key tidak ikut dicadangkan, tetap aman di HP.", color = Neon.Muted)
    }
}

@Composable
private fun SystemPage(activity: MainActivity, s: AppSettings, up: Up) {
    val scope = rememberCoroutineScope()
    GlassCard {
        SectionTitle("Izin")
        GhostButton("Izin kamera, mikrofon, notifikasi", icon = Icons.Filled.Security) { activity.askBasicPermissions() }
        GhostButton(if (SystemHelpers.canDrawOverlays(activity)) "Jendela mengambang: diizinkan" else "Izinkan jendela mengambang", icon = Icons.Filled.PictureInPicture) { SystemHelpers.requestOverlay(activity) }
        GhostButton(if (SystemHelpers.isIgnoringBattery(activity)) "Pengecualian baterai: aktif" else "Kecualikan dari penghemat baterai", icon = Icons.Filled.BatteryChargingFull) { SystemHelpers.requestIgnoreBattery(activity) }
        GhostButton(if (SystemHelpers.hasDndAccess(activity)) "Akses Jangan Ganggu: diizinkan" else "Izinkan akses Jangan Ganggu", icon = Icons.Filled.DoNotDisturbOn) { SystemHelpers.requestDnd(activity) }
        GhostButton(if (Updater.canInstall(activity)) "Pasang aplikasi tidak dikenal: diizinkan" else "Izinkan pasang aplikasi tidak dikenal", icon = Icons.Filled.InstallMobile) { Updater.openInstallPermission(activity) }
    }
    GlassCard {
        SectionTitle("Pembaruan")
        Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Neon.Muted)
        SwitchRow("Cek versi baru saat aplikasi dibuka", s.updateCheckOnStart) { v -> up { it.copy(updateCheckOnStart = v) } }
        NeonButton("Cek pembaruan sekarang", icon = Icons.Filled.SystemUpdate) {
            scope.launch {
                val r = Updater.check()
                if (r == null) activity.message.value = "Sudah versi terbaru." else activity.updateAvailable.value = r
            }
        }
    }
    GlassCard {
        SectionTitle("Kredit musik & efek suara")
        val credits = remember { runCatching { activity.assets.open("credits.txt").bufferedReader().readText() }.getOrDefault("") }
        Text(credits.take(4000), style = MaterialTheme.typography.bodyMedium, color = Neon.Muted)
    }
}


@Composable
private fun LiveToolsPage(activity: MainActivity, s: AppSettings, up: Up) {
    GlassCard {
        SectionTitle("Giveaway")
        SwitchRow("Aktifkan giveaway", s.features.giveaway) { v -> up { it.copy(features = it.features.copy(giveaway = v)) } }
        Field("Kata ikut (penonton menulis ini di komentar)", s.giveaway.keyword) { v -> up { it.copy(giveaway = it.giveaway.copy(keyword = v.trim())) } }
        NumberField("Lama pendaftaran (detik)", s.giveaway.durationSec) { v -> up { it.copy(giveaway = it.giveaway.copy(durationSec = v)) } }
        Field("Hadiah", s.giveaway.prize) { v -> up { it.copy(giveaway = it.giveaway.copy(prize = v)) } }
    }
    GlassCard {
        SectionTitle("Kuis")
        SwitchRow("Aktifkan kuis", s.features.quiz) { v -> up { it.copy(features = it.features.copy(quiz = v)) } }
        Text("Tulis satu soal per baris: pertanyaan | jawaban", color = Neon.Muted)
        ListEditor("Soal kuis", s.quiz.map { it.question + " | " + it.answer }) { lines ->
            up { it.copy(quiz = lines.mapNotNull { l -> val parts = l.split("|"); if (parts.size >= 2 && parts[0].isNotBlank()) QuizItem(parts[0].trim(), parts[1].trim()) else null }) }
        }
    }
    GlassCard {
        SectionTitle("Request lagu")
        SwitchRow("Terima request lagu", s.features.songRequests) { v -> up { it.copy(features = it.features.copy(songRequests = v)) } }
        Field("Kata pemicu request", s.songRequestKeyword) { v -> up { it.copy(songRequestKeyword = v.trim()) } }
    }
    GlassCard {
        SectionTitle("Rundown acara")
        SwitchRow("Aktifkan rundown", s.features.rundown) { v -> up { it.copy(features = it.features.copy(rundown = v)) } }
        Text("Satu bagian per baris: judul | menit", color = Neon.Muted)
        ListEditor("Bagian acara", s.rundown.map { it.title + " | " + it.minutes }) { lines ->
            up { it.copy(rundown = lines.mapNotNull { l -> val parts = l.split("|"); val t = parts[0].trim(); if (t.isBlank()) null else RundownItem(t, parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 5) }) }
        }
    }
    GlassCard {
        SectionTitle("Pengingat & peringatan")
        SwitchRow("Pengingat minum", s.features.drinkReminder) { v -> up { it.copy(features = it.features.copy(drinkReminder = v)) } }
        NumberField("Ingatkan minum setiap (menit)", s.drinkReminderMinutes) { v -> up { it.copy(drinkReminderMinutes = v) } }
        SwitchRow("Peringatan live sepi", s.features.quietWarning) { v -> up { it.copy(features = it.features.copy(quietWarning = v)) } }
        NumberField("Dianggap sepi setelah (menit tanpa komentar)", s.quietWarnMinutes) { v -> up { it.copy(quietWarnMinutes = v) } }
        SwitchRow("Peringatan baterai & suhu", s.features.batteryWarning) { v -> up { it.copy(features = it.features.copy(batteryWarning = v)) } }
        NumberField("Peringatan baterai di bawah (%)", s.batteryWarnPercent) { v -> up { it.copy(batteryWarnPercent = v) } }
        NumberField("Peringatan suhu di atas (°C)", s.temperatureWarnC) { v -> up { it.copy(temperatureWarnC = v) } }
    }
    GlassCard {
        SectionTitle("Balas komentar YouTube dengan suara")
        SwitchRow("Aktifkan", s.features.voiceReply, "Ucapkan kata pemicu lalu isi balasan, contoh: balas terima kasih semuanya") { v -> up { it.copy(features = it.features.copy(voiceReply = v)) } }
        Field("Kata pemicu", s.voiceReplyTrigger) { v -> up { it.copy(voiceReplyTrigger = v.trim()) } }
    }
    GlassCard {
        SectionTitle("Papan nama")
        val n = s.nameplate
        fun setN(f: (NameplateConfig) -> NameplateConfig) = up { it.copy(nameplate = f(it.nameplate)) }
        SwitchRow("Tampilkan papan nama", n.enabled) { v -> setN { it.copy(enabled = v) }; up { it.copy(features = it.features.copy(nameplate = v)) } }
        Field("Nama", n.name) { v -> setN { it.copy(name = v) } }
        Field("Peran / keterangan", n.role) { v -> setN { it.copy(role = v) } }
        ChoiceRow("Letak", listOf("bottom_left" to "Kiri bawah", "top_left" to "Kiri atas", "top_right" to "Kanan atas"), n.position) { v -> setN { it.copy(position = v) } }
        NumberField("Tampil berapa detik di awal (0 = terus)", n.showSeconds) { v -> setN { it.copy(showSeconds = v) } }
        SwitchRow("Papan nama untuk tamu juga", n.guestPlates) { v -> setN { it.copy(guestPlates = v) } }
    }
    GlassCard {
        SectionTitle("Jeda layar")
        SwitchRow("Tombol jeda di layar live", s.features.pauseButton) { v -> up { it.copy(features = it.features.copy(pauseButton = v)) } }
        Field("Tulisan saat jeda", s.pause.text, singleLine = false) { v -> up { it.copy(pause = it.pause.copy(text = v)) } }
        SwitchRow("Matikan mikrofon saat jeda", s.pause.muteMic) { v -> up { it.copy(pause = it.pause.copy(muteMic = v)) } }
        GhostButton(if (s.pause.imagePath.isBlank()) "Pilih gambar jeda dari galeri" else "Ganti gambar jeda", icon = Icons.Filled.Image) {
            activity.pick(arrayOf("image/*")) { u -> up { it.copy(pause = it.pause.copy(imagePath = u.toString())) } }
        }
        if (s.pause.imagePath.isNotBlank()) GhostButton("Hapus gambar jeda", icon = Icons.Filled.Delete) { up { it.copy(pause = it.pause.copy(imagePath = "")) } }
    }
    GlassCard {
        SectionTitle("TEMA")
        SwitchRow("Aktifkan TEMA", s.features.themes, "Saat kamu mengucapkan kata pemicu diikuti judul tema, gambarnya tampil layar penuh") { v -> up { it.copy(features = it.features.copy(themes = v)) } }
        Field("Kata pemicu", s.themeTrigger) { v -> up { it.copy(themeTrigger = v.trim()) } }
        NumberField("Lama tampil (detik)", s.themeDurationSec) { v -> up { it.copy(themeDurationSec = v) } }
        Text("Daftar tema diatur di Sempurnakan, bagian TEMA, saat live.", color = Neon.Muted)
    }
    GlassCard {
        SectionTitle("Lain-lain")
        SwitchRow("Laporan latihan Catatan live", s.features.practiceReport) { v -> up { it.copy(features = it.features.copy(practiceReport = v)) } }
        SwitchRow("Kunci sentuh saat live", s.features.touchLock, "Tekan lama untuk membuka kunci") { v -> up { it.copy(features = it.features.copy(touchLock = v)) } }
        SwitchRow("Potong rekaman per tamu", s.features.guestClips) { v -> up { it.copy(features = it.features.copy(guestClips = v)) } }
    }
}

@Composable
private fun CuesPage(s: AppSettings, up: Up) {
    GlassCard {
        SwitchRow("Aktifkan tanda per kejadian", s.features.cues, "Hanya kamu yang merasakan/mendengar, tidak ikut tersiar") { v -> up { it.copy(features = it.features.copy(cues = v)) } }
    }
    val sounds = remember { listOf("" to "Tanpa suara") + Graph.sounds.effects.map { it.id to it.label } }
    Defaults.cueLabels.forEach { (key, label) ->
        val c = s.cues[key] ?: CueConfig()
        fun setC(f: (CueConfig) -> CueConfig) = up { it.copy(cues = it.cues + (key to f(it.cues[key] ?: CueConfig()))) }
        GlassCard {
            SectionTitle(label)
            SwitchRow("Getar", c.vibrate) { v -> setC { it.copy(vibrate = v) } }
            if (c.vibrate) ChoiceRow("Pola getar", listOf("short" to "Pendek", "double" to "Dua kali", "long" to "Panjang", "triple" to "Tiga kali"), c.pattern) { v -> setC { it.copy(pattern = v) } }
            ChoiceRow("Suara", sounds, c.sound) { v -> setC { it.copy(sound = v) } }
            SwitchRow("Ucapkan dengan TTS", c.speak) { v -> setC { it.copy(speak = v) } }
        }
    }
}
