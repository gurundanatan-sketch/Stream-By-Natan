package com.natan.streambynatan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.core.Social
import com.natan.streambynatan.core.UserProfile
import com.natan.streambynatan.features.I18n
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Halaman-halaman menu Akun. [page] = nama halaman, [go] = pindah halaman, [onClose] = kembali ke halaman utama. */
@Composable
fun AccountScreen(activity: MainActivity, page: String, go: (String) -> Unit, onClose: () -> Unit, onSignedOut: () -> Unit) {
    when {
        page == "menu" -> AccountMenu(activity, go, onClose, onSignedOut)
        page == "edit" -> EditProfile(activity) { go("menu") }
        page == "feedback" -> FeedbackSend(activity) { go("menu") }
        page == "inbox" -> FeedbackInbox { go("menu") }
        page == "about" -> AboutApp(go) { go("menu") }
        page.startsWith("guide:") -> GuideDetail(page.removePrefix("guide:")) { go("about") }
        page == "creator" -> CreatorProfile { go("about") }
        page == "privacy" -> PrivacyPage(activity) { go("menu") }
        page == "language" -> LanguagePage(activity) { go("menu") }
        page == "display" -> DisplayPage(activity) { go("menu") }
        page == "restricted" -> RestrictedPage(activity) { go("menu") }
        page == "following" -> FollowingPage(activity) { go("menu") }
        page == "schedule" -> SchedulePage(activity) { go("menu") }
        page == "moderators" -> ModeratorsPage(activity) { go("menu") }
        page == "modhosts" -> ModHostsPage(go) { go("menu") }
        page.startsWith("modpanel:") -> ModeratorPanel(activity, page.removePrefix("modpanel:")) { go("modhosts") }
        page == "creatorkeys" -> CreatorKeysPage(activity) { go("menu") }
        page == "switch" -> SwitchAccountPage(activity, onSignedOut) { go("menu") }
        else -> AccountMenu(activity, go, onClose, onSignedOut)
    }
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton("Kembali", icon = Icons.Filled.ArrowBack, onClick = onBack)
    }
    ScreenTitle(title)
}

@Composable
fun ProfilePhoto(photo: String, name: String, sizeDp: Int = 72) {
    val bmp = remember(photo) { Social.dataUrlToBitmap(photo) }
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(Neon.Violet), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), "Foto profil $name", Modifier.size(sizeDp.dp), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase().ifBlank { "?" }, style = MaterialTheme.typography.headlineMedium, color = Neon.Text)
    }
}

fun shareText(activity: MainActivity, text: String, title: String = "Bagikan") {
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))
}

// ============================================================================ menu utama akun
@Composable
private fun AccountMenu(activity: MainActivity, go: (String) -> Unit, onClose: () -> Unit, onSignedOut: () -> Unit) {
    val p by Graph.social.profile.collectAsState()
    val creator by Graph.social.isCreator.collectAsState()
    val unread by Graph.social.unreadFeedback.collectAsState()
    val str by I18n.strings.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Graph.social.loadProfile() }

    ScreenColumn {
        BackRow(str["account"] ?: "Akun", onClose)
        GlassCard(Modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(p.photo, p.name.ifBlank { Graph.firebase.displayName ?: "" })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { Graph.firebase.displayName ?: "Pengguna" }, style = MaterialTheme.typography.titleLarge)
                    Text(if (p.username.isNotBlank()) "@${p.username}" else "Belum ada username", color = Neon.Muted)
                    Text(Graph.firebase.email.orEmpty(), color = Neon.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (p.bio.isNotBlank()) Text(p.bio)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(str["edit_profile"] ?: "Edit profil", Modifier.weight(1f), icon = Icons.Filled.Edit) { go("edit") }
                GhostButton(str["share_profile"] ?: "Bagikan profil", Modifier.weight(1f), Icons.Filled.Share) {
                    if (p.username.isBlank()) activity.message.value = "Buat username dulu di Edit profil."
                    else shareText(activity, "Lihat profil ${p.name} di Stream By Natan: ${p.shareUrl}", "Bagikan profil")
                }
            }
        }

        SectionTitle("Sosial")
        NavRow(str["following"] ?: "Mengikuti", "Kreator yang kamu ikuti & notifikasi live", Icons.Filled.Favorite) { go("following") }
        NavRow(str["schedule"] ?: "Jadwal live", "Umumkan jadwal, pengikut diingatkan", Icons.Filled.Event) { go("schedule") }
        NavRow(str["moderators"] ?: "Moderator", "Tambah moderator lewat username atau link", Icons.Filled.AdminPanelSettings) { go("moderators") }
        NavRow("Panel moderator", "Bantu kelola live orang yang mempercayaimu", Icons.Filled.SupervisorAccount) { go("modhosts") }

        if (creator) {
            SectionTitle("Khusus pembuat aplikasi")
            NavRow((str["inbox"] ?: "Kotak saran") + if (unread > 0) " ($unread baru)" else "", "Saran & masukan dari pengguna", Icons.Filled.Inbox) { go("inbox") }
            NavRow(str["creator_keys"] ?: "Kunci khusus pembuat", "Euler Stream & YouTube API untuk akun pembuat", Icons.Filled.Key) { go("creatorkeys") }
        }

        SectionTitle("Aplikasi")
        NavRow(str["feedback"] ?: "Kirim saran & masukan", "Untuk pengembangan aplikasi", Icons.Filled.RateReview) { go("feedback") }
        NavRow(str["about"] ?: "Tentang aplikasi", "Panduan semua fitur & tentang pembuat", Icons.Filled.Info) { go("about") }
        NavRow(str["privacy"] ?: "Kebijakan privasi", "Data apa yang dipakai dan untuk apa", Icons.Filled.PrivacyTip) { go("privacy") }
        NavRow(str["language"] ?: "Pilihan bahasa", I18n.languages.firstOrNull { it.code == Graph.settings.current.language }?.native ?: "Bahasa Indonesia", Icons.Filled.Language) { go("language") }
        NavRow(str["display"] ?: "Tampilan", "Tema gelap/terang, huruf besar, kontras tinggi, mode pembaca layar", Icons.Filled.Palette) { go("display") }
        NavRow(str["restricted"] ?: "Izin setelan terbatas", "Kalau ada izin yang tidak bisa dinyalakan", Icons.Filled.Lock) { go("restricted") }

        SectionTitle("Akun")
        NavRow(str["logout"] ?: "Keluar", "Keluar dari akun ini", Icons.Filled.Logout) { confirmLogout = true }
        NavRow(str["switch"] ?: "Beralih akun", "Pilih akun lain atau tambah akun", Icons.Filled.SwitchAccount) { go("switch") }
        NavRow(str["delete"] ?: "Hapus akun", "Hapus akun dan datanya secara permanen", Icons.Filled.DeleteForever) { confirmDelete = true }
        Text("Versi ${BuildConfig.VERSION_NAME}", color = Neon.Muted)
        Spacer(Modifier.height(30.dp))
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Yakin mau keluar?") },
        text = { Text("Kamu bisa masuk lagi kapan saja dengan akun ini.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; Graph.social.signOut(); onSignedOut() }) { Text("Ya") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Tidak") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("Hapus akun secara permanen?") },
        text = { Text("Profil, username, jadwal, daftar ikuti, cadangan pengaturan, dan akunmu akan dihapus dan tidak bisa dikembalikan. Rekaman di HP tidak ikut terhapus.") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val err = Graph.social.deleteAccount()
                    busy = false; confirmDelete = false
                    if (err == null) { activity.message.value = "Akun sudah dihapus."; onSignedOut() } else activity.message.value = err
                }
            }) { Text(if (busy) "Menghapus…" else "Ya, hapus") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Batal") } },
    )
}

// ============================================================================ edit profil
@Composable
private fun EditProfile(activity: MainActivity, onBack: () -> Unit) {
    val p by Graph.social.profile.collectAsState()
    var name by remember(p.uid) { mutableStateOf(p.name.ifBlank { Graph.firebase.displayName.orEmpty() }) }
    var username by remember(p.uid) { mutableStateOf(p.username) }
    var bio by remember(p.uid) { mutableStateOf(p.bio) }
    var photo by remember(p.uid) { mutableStateOf(p.photo) }
    val linkKeys = listOf("youtube" to "YouTube", "instagram" to "Instagram", "tiktok" to "TikTok", "facebook" to "Facebook", "website" to "Situs web", "lainnya" to "Tautan lain")
    var links by remember(p.uid) { mutableStateOf(p.links) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ScreenColumn {
        BackRow("Edit profil", onBack)
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(photo, name, 84)
                Spacer(Modifier.width(14.dp))
                GhostButton("Ganti foto profil", icon = Icons.Filled.PhotoCamera) {
                    activity.pick(arrayOf("image/*")) { uri -> Social.photoToDataUrl(activity, uri)?.let { photo = it } ?: run { activity.message.value = "Foto tidak bisa dibuka." } }
                }
            }
            Field("Nama", name) { name = it.take(40) }
            Field("Username (huruf kecil, angka, titik, garis bawah)", username) { username = Social.cleanUsername(it) }
            Text("Link profilmu: ${BuildConfig.HOSTING_URL}/@${username.ifBlank { "username" }}", color = Neon.Muted)
            Field("Bio", bio, singleLine = false, minLines = 3) { bio = it.take(300) }
        }
        GlassCard {
            SectionTitle("Tautan")
            linkKeys.forEach { (k, label) -> Field(label, links[k].orEmpty()) { v -> links = links + (k to v) } }
        }
        err?.let { Text(it, color = Neon.Red, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        NeonButton(if (busy) "Menyimpan…" else "Simpan profil", enabled = !busy, icon = Icons.Filled.Save) {
            busy = true; err = null
            scope.launch {
                err = Graph.social.saveProfile(name, username, bio, links, photo)
                busy = false
                if (err == null) { activity.message.value = "Profil disimpan."; onBack() }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ============================================================================ saran
@Composable
private fun FeedbackSend(activity: MainActivity, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("saran") }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        BackRow("Kirim saran & masukan", onBack)
        GlassCard {
            ChoiceRow("Jenis", listOf("saran" to "Saran", "masalah" to "Masalah / error", "pertanyaan" to "Pertanyaan", "lainnya" to "Lainnya"), kind) { kind = it }
            Field("Tulis saran atau masukanmu", text, singleLine = false, minLines = 5) { text = it.take(2000) }
            Text("Pesanmu dikirim ke pembuat aplikasi bersama nama, email, versi aplikasi, dan model HP.", color = Neon.Muted)
        }
        NeonButton(if (busy) "Mengirim…" else "Kirim", enabled = !busy && text.isNotBlank(), icon = Icons.Filled.Send) {
            busy = true
            scope.launch {
                val e = Graph.social.sendFeedback(text, kind)
                busy = false
                if (e == null) { activity.message.value = "Terima kasih! Saranmu sudah terkirim."; onBack() } else activity.message.value = e
            }
        }
    }
}

@Composable
private fun FeedbackInbox(onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Social.Feedback>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { list = Graph.social.feedbackList(); loading = false }
    val fmt = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale("id")) }
    ScreenColumn {
        BackRow("Kotak saran", onBack)
        if (loading) Text("Memuat…", color = Neon.Muted)
        else if (list.isEmpty()) Text("Belum ada saran masuk.", color = Neon.Muted)
        list.forEach { f ->
            GlassCard {
                Text((if (!f.read) "BARU · " else "") + f.kind.replaceFirstChar { it.uppercase() } + " dari " + f.name.ifBlank { "Pengguna" }, style = MaterialTheme.typography.titleMedium)
                Text(f.text)
                Text("${f.email} · versi ${f.version} · ${fmt.format(Date(f.at))}", color = Neon.Muted, style = MaterialTheme.typography.bodyMedium)
                if (!f.read) GhostButton("Tandai sudah dibaca", icon = Icons.Filled.DoneAll) {
                    Graph.social.markFeedbackRead(f.id); list = list.map { if (it.id == f.id) it.copy(read = true) else it }
                }
            }
        }
    }
}

// ============================================================================ tentang aplikasi & panduan
@Composable
private fun AboutApp(go: (String) -> Unit, onBack: () -> Unit) {
    ScreenColumn {
        BackRow("Tentang aplikasi", onBack)
        GlassCard {
            Text("Stream By Natan", style = MaterialTheme.typography.titleLarge)
            Text("Versi ${BuildConfig.VERSION_NAME}. Live ke banyak platform sekaligus dari HP, dengan fitur selengkap live dari laptop.", color = Neon.Muted)
        }
        NavRow("Tentang pembuat", "${Guides.CREATOR_NAME} dan tujuan dibuatnya aplikasi ini", Icons.Filled.Person) { go("creator") }
        SectionTitle("Tutorial awal")
        Guides.tutorial.forEach { g -> NavRow(g.title, "${g.steps.size} langkah", Icons.Filled.School) { go("guide:${g.id}") } }
        Guides.features.groupBy { it.group }.forEach { (group, items) ->
            SectionTitle("Panduan: $group")
            items.forEach { g -> NavRow(g.title, "${g.steps.size} langkah", Icons.Filled.MenuBook) { go("guide:${g.id}") } }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun GuideDetail(id: String, onBack: () -> Unit) {
    val g = Guides.all.firstOrNull { it.id == id }
    ScreenColumn {
        BackRow(g?.title ?: "Panduan", onBack)
        g?.steps?.forEachIndexed { i, (title, body) ->
            GlassCard {
                Text("Langkah ${i + 1}: $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun CreatorProfile(onBack: () -> Unit) {
    ScreenColumn {
        BackRow("Tentang pembuat", onBack)
        GlassCard {
            Text(Guides.CREATOR_NAME, style = MaterialTheme.typography.headlineSmall)
            Text(Guides.CREATOR_ORIGIN, color = Neon.Muted)
        }
        Guides.creatorProfile.forEach { (title, body) ->
            GlassCard {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PrivacyPage(activity: MainActivity, onBack: () -> Unit) {
    ScreenColumn {
        BackRow("Kebijakan privasi", onBack)
        listOf(
            "Data akun" to "Email, nama, dan foto profil untuk login dan profil publik.",
            "Profil publik" to "Nama, username, bio, tautan, status live, dan jadwal tampil di halaman profilmu.",
            "Stream key & API key" to "Disimpan terenkripsi di HP, tidak dikirim ke server kami.",
            "Rekaman" to "Disimpan di dalam aplikasi di HP-mu, tidak diunggah.",
            "Saran" to "Isi saran, email, versi aplikasi, dan model HP dikirim ke pembuat aplikasi.",
            "Hapus data" to "Hapus akun kapan saja dari menu Akun, Hapus akun.",
        ).forEach { (t, b) -> GlassCard { Text(t, style = MaterialTheme.typography.titleMedium); Text(b) } }
        NeonButton("Buka kebijakan privasi lengkap", icon = Icons.Filled.OpenInBrowser) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.HOSTING_URL + "/privasi/")))
        }
    }
}

@Composable
private fun LanguagePage(activity: MainActivity, onBack: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    ScreenColumn {
        BackRow("Pilihan bahasa", onBack)
        Text("Bahasa Indonesia adalah bahasa utama. Bahasa dunia diterjemahkan otomatis di HP (perlu internet sekali). Bahasa daerah lain menyusul.", color = Neon.Muted)
        I18n.languages.forEach { l ->
            val label = l.native + (if (l.native != l.name) " (${l.name})" else "") + if (!l.available) " · segera hadir" else ""
            FilterChipButton(label, s.language == l.code) {
                if (!l.available) { activity.message.value = "Bahasa ${l.name} sedang disiapkan."; return@FilterChipButton }
                Graph.settings.update { it.copy(language = l.code) }
                I18n.apply(activity, l.code)
                activity.message.value = "Bahasa: ${l.native}"
            }
        }
    }
}

@Composable
private fun DisplayPage(activity: MainActivity, onBack: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    fun set(d: com.natan.streambynatan.data.DisplayConfig) {
        Graph.settings.update { it.copy(display = d) }
        Neon.apply(d)
        activity.recreate()
    }
    ScreenColumn {
        BackRow("Tampilan", onBack)
        GlassCard {
            ChoiceRow("Tema", listOf("dark" to "Gelap", "light" to "Terang"), s.display.theme) { set(s.display.copy(theme = it)) }
            SwitchRow("Huruf besar", s.display.largeText, "Semua tulisan diperbesar") { set(s.display.copy(largeText = it)) }
            SwitchRow("Kontras tinggi", s.display.highContrast, "Warna lebih tegas, mudah dibaca") { set(s.display.copy(highContrast = it)) }
        }
        GlassCard {
            SwitchRow("Mode pembaca layar (tunanetra)", s.a11y.voiceAids, "Bisikan catatan dan peringatan diucapkan. Kalau mati, tampil sebagai tulisan.") { v ->
                Graph.settings.update { it.copy(a11y = it.a11y.copy(voiceAids = v, chosen = true)) }
            }
            SwitchRow("Bacakan komentar dengan TTS", s.features.readComments, "Pilihan terpisah, bisa dipakai siapa saja") { v ->
                Graph.settings.update { it.copy(features = it.features.copy(readComments = v)) }
            }
        }
    }
}

@Composable
private fun RestrictedPage(activity: MainActivity, onBack: () -> Unit) {
    ScreenColumn {
        BackRow("Izin setelan terbatas", onBack)
        GlassCard {
            Text("Kalau aplikasi dipasang dari luar Play Store, Android 13 ke atas bisa membatasi beberapa izin, misalnya akses Jangan Ganggu.")
            Text("Langkah 1: tekan tombol di bawah untuk membuka Info aplikasi.", style = MaterialTheme.typography.titleMedium)
            Text("Langkah 2: tekan titik tiga di kanan atas.", style = MaterialTheme.typography.titleMedium)
            Text("Langkah 3: pilih Izinkan setelan terbatas, lalu konfirmasi.", style = MaterialTheme.typography.titleMedium)
            Text("Setelah dipasang dari Play Store nanti, batasan ini hilang sendiri.", color = Neon.Muted)
        }
        NeonButton("Buka Info aplikasi", icon = Icons.Filled.Settings) {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName)))
        }
    }
}

// ============================================================================ ikuti kreator & jadwal
@Composable
private fun FollowingPage(activity: MainActivity, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var query by remember { mutableStateOf(activity.profileLink.value.orEmpty()) }
    var found by remember { mutableStateOf<UserProfile?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        list = Graph.social.following()
        if (activity.profileLink.value != null) { activity.profileLink.value = null; found = Graph.social.findByUsername(query) }
    }
    ScreenColumn {
        BackRow("Mengikuti", onBack)
        GlassCard {
            Field("Cari username kreator", query) { query = it }
            GhostButton("Cari", icon = Icons.Filled.Search) {
                scope.launch { found = Graph.social.findByUsername(query); if (found == null) activity.message.value = "Username tidak ditemukan." }
            }
            found?.let { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhoto(p.photo, p.name, 48); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(p.name); Text("@${p.username}", color = Neon.Muted) }
                }
                NeonButton("Ikuti ${p.name}", icon = Icons.Filled.PersonAdd) {
                    scope.launch {
                        val e = Graph.social.follow(p, true)
                        activity.message.value = e ?: "Kamu mengikuti ${p.name}. Kamu akan dapat notifikasi saat dia live."
                        list = Graph.social.following(); found = null
                    }
                }
            }
        }
        if (list.isEmpty()) Text("Belum mengikuti siapa pun.", color = Neon.Muted)
        list.forEach { p ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhoto(p.photo, p.name, 48); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.live) " · SEDANG LIVE" else "")
                        Text("@${p.username}", color = Neon.Muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Buka profil", Modifier.weight(1f), Icons.Filled.OpenInBrowser) { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.shareUrl))) }
                    GhostButton("Berhenti ikuti", Modifier.weight(1f), Icons.Filled.PersonRemove) {
                        scope.launch { Graph.social.follow(p, false); list = Graph.social.following() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulePage(activity: MainActivity, onBack: () -> Unit) {
    val p by Graph.social.profile.collectAsState()
    var items by remember(p.schedule) { mutableStateOf(p.schedule) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())) }
    var time by remember { mutableStateOf("19:00") }
    val fmt = remember { SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale("id")) }
    ScreenColumn {
        BackRow("Jadwal live", onBack)
        Text("Jadwal tampil di profilmu, dan pengikut mendapat pengingat sebelum live dimulai.", color = Neon.Muted)
        GlassCard {
            Field("Judul live", title) { title = it.take(80) }
            Field("Tanggal (hari-bulan-tahun, contoh 05-12-2026)", date) { date = it }
            Field("Jam (contoh 19:00)", time) { time = it }
            NeonButton("Tambah jadwal", icon = Icons.Filled.EventAvailable) {
                val at = runCatching { SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).parse("$date $time")?.time }.getOrNull()
                if (at == null || title.isBlank()) { activity.message.value = "Isi judul, tanggal, dan jam dengan benar."; return@NeonButton }
                items = (items + (title.trim() to at)).sortedBy { it.second }
                Graph.social.publishSchedule(items)
                Graph.firebase.saveSchedule(title, at)
                activity.message.value = "Jadwal ditambahkan."
                title = ""
            }
        }
        items.forEach { (t, at) ->
            GlassCard {
                Text(t, style = MaterialTheme.typography.titleMedium)
                Text(fmt.format(Date(at)), color = Neon.Muted)
                GhostButton("Hapus jadwal", icon = Icons.Filled.Delete) { items = items.filterNot { it.first == t && it.second == at }; Graph.social.publishSchedule(items) }
            }
        }
    }
}

// ============================================================================ moderator
@Composable
private fun ModeratorsPage(activity: MainActivity, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Social.Moderator>>(emptyList()) }
    var username by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { list = Graph.social.myModerators() }
    ScreenColumn {
        BackRow("Moderator", onBack)
        Text("Moderator bisa memasukkan, membisukan, mengeluarkan, dan membalas tamu, membisukan penonton, serta mengatur antrean pertanyaan saat kamu live.", color = Neon.Muted)
        GlassCard {
            SectionTitle("Tambah lewat username")
            Field("Username moderator", username) { username = it }
            NeonButton("Tambah moderator", icon = Icons.Filled.PersonAdd) {
                scope.launch {
                    val e = Graph.social.addModeratorByUsername(username)
                    activity.message.value = e ?: "Moderator ditambahkan."
                    if (e == null) { username = ""; list = Graph.social.myModerators() }
                }
            }
        }
        GlassCard {
            SectionTitle("Tambah lewat link")
            Text("Bagikan link ini. Orang yang membukanya cukup login lalu tekan Terima.", color = Neon.Muted)
            GhostButton("Bagikan link undangan moderator", icon = Icons.Filled.Share) {
                scope.launch {
                    val link = Graph.social.moderatorInviteLink()
                    if (link == null) activity.message.value = "Gagal membuat link."
                    else shareText(activity, "Kamu diundang jadi moderator live saya di Stream By Natan: $link", "Undang moderator")
                }
            }
        }
        if (list.isEmpty()) Text("Belum ada moderator.", color = Neon.Muted)
        list.forEach { m ->
            GlassCard {
                Text(m.name.ifBlank { "Moderator" } + if (m.username.isNotBlank()) " · @${m.username}" else "")
                GhostButton("Cabut status moderator", icon = Icons.Filled.PersonRemove) {
                    scope.launch { Graph.social.removeModerator(m.uid); list = Graph.social.myModerators() }
                }
            }
        }
    }
}

@Composable
private fun ModHostsPage(go: (String) -> Unit, onBack: () -> Unit) {
    var hosts by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { hosts = Graph.social.hostsIModerate(); loading = false }
    ScreenColumn {
        BackRow("Panel moderator", onBack)
        if (loading) Text("Memuat…", color = Neon.Muted)
        else if (hosts.isEmpty()) Text("Kamu belum menjadi moderator siapa pun.", color = Neon.Muted)
        hosts.forEach { h ->
            NavRow(h.name.ifBlank { "Host" } + if (h.live) " · SEDANG LIVE" else "", if (h.room.isNotBlank()) "Ruang tamu terbuka, ketuk untuk mengelola" else "Belum membuka ruang tamu", Icons.Filled.SupervisorAccount) {
                go("modpanel:${h.uid}")
            }
        }
    }
}

/** Panel moderator: kelola tamu (masukkan, bisukan, keluarkan, balas), bisukan penonton, atur antrean pertanyaan. */
@Composable
fun ModeratorPanel(activity: MainActivity, hostUid: String, onBack: () -> Unit) {
    var host by remember { mutableStateOf<UserProfile?>(null) }
    var room by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var peers by remember { mutableStateOf<List<Pair<String, Map<String, Any?>>>>(emptyList()) }
    var reply by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hostUid) { host = Graph.social.profileOf(hostUid) }
    val roomId = host?.room.orEmpty()
    DisposableEffect(roomId) {
        val db = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        val regs = if (db != null && roomId.isNotBlank()) listOf(
            db.collection("rooms").document(roomId).addSnapshotListener { d, _ -> room = d?.data.orEmpty() },
            db.collection("rooms").document(roomId).collection("peers").addSnapshotListener { snap, _ ->
                peers = snap?.documents?.map { it.id to it.data.orEmpty() }?.sortedBy { (it.second["joinedAt"] as? Number)?.toLong() ?: 0L }.orEmpty()
            },
        ) else emptyList()
        onDispose { regs.forEach { it.remove() } }
    }
    val peerCol = runCatching { FirebaseFirestore.getInstance().collection("rooms").document(roomId).collection("peers") }.getOrNull()

    ScreenColumn {
        BackRow("Moderator: ${host?.name ?: "…"}", onBack)
        if (roomId.isBlank()) { Text("Host belum membuka ruang tamu atau belum live.", color = Neon.Muted); return@ScreenColumn }
        GlassCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(if (room["live"] == true) "Host sedang live · ${room["viewers"] ?: 0} penonton" else "Host belum live", style = MaterialTheme.typography.titleMedium)
            (room["currentQuestion"] as? String)?.takeIf { it.isNotBlank() }?.let { Text("Sedang dijawab: $it") }
        }
        SectionTitle("Antrean pertanyaan")
        @Suppress("UNCHECKED_CAST") val qs = (room["questions"] as? List<String>).orEmpty()
        if (qs.isEmpty()) Text("Kosong.", color = Neon.Muted)
        qs.take(10).forEachIndexed { i, q -> Text("${i + 1}. $q") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Pertanyaan berikutnya", Modifier.weight(1f), Icons.Filled.SkipNext) { Graph.social.sendCommand(roomId, "next_question") }
            GhostButton("Lewati", Modifier.weight(1f), Icons.Filled.FastForward) { Graph.social.sendCommand(roomId, "skip_question") }
        }
        SectionTitle("Tamu")
        val waiting = peers.filter { it.second["status"] == "waiting" && it.second["mode"] != "camera" }
        val onStage = peers.filter { it.second["status"] == "admitted" || it.second["status"] == "onstage" }
        Text("Ruang tunggu: ${waiting.size} · Tampil: ${onStage.size}", color = Neon.Muted)
        (waiting + onStage).forEach { (id, d) ->
            val name = d["name"] as? String ?: "Tamu"
            val via = if (d["app"] == "web") "lewat browser" else "lewat aplikasi"
            GlassCard {
                Text("$name · $via · ${if (d["status"] == "waiting") "menunggu" else "tampil"}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d["status"] == "waiting") GhostButton("Masukkan", Modifier.weight(1f), Icons.Filled.Login) { peerCol?.document(id)?.update("status", "admitted") }
                    GhostButton("Bisukan", Modifier.weight(1f), Icons.Filled.MicOff) { peerCol?.document(id)?.update(mapOf("hostMuteAt" to System.currentTimeMillis(), "micOn" to false)) }
                    GhostButton("Keluarkan", Modifier.weight(1f), Icons.Filled.PersonRemove) { peerCol?.document(id)?.update("status", "kicked") }
                }
                GhostButton("Balas pesan", icon = Icons.Filled.Reply) { replyTo = id }
            }
        }
        replyTo?.let { id ->
            GlassCard {
                Field("Balasan untuk tamu", reply) { reply = it.take(300) }
                GhostButton("Kirim balasan", icon = Icons.Filled.Send) {
                    peerCol?.document(id)?.collection("messages")?.add(mapOf("from" to "mod", "name" to (Graph.firebase.displayName ?: "Moderator"), "text" to reply, "at" to System.currentTimeMillis()))
                    reply = ""; replyTo = null
                }
            }
        }
        SectionTitle("Penonton")
        @Suppress("UNCHECKED_CAST") val viewers = (room["commenters"] as? List<String>).orEmpty()
        if (viewers.isEmpty()) Text("Belum ada yang berkomentar.", color = Neon.Muted)
        viewers.forEach { n -> GhostButton("Bisukan $n") { Graph.social.sendCommand(roomId, "mute_viewer", mapOf("name" to n)); activity.message.value = "$n dibisukan." } }
        Spacer(Modifier.height(30.dp))
    }
}

// ============================================================================ kunci pembuat & beralih akun
@Composable
private fun CreatorKeysPage(activity: MainActivity, onBack: () -> Unit) {
    var euler by remember { mutableStateOf("") }
    var yt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ScreenColumn {
        BackRow("Kunci khusus pembuat", onBack)
        Text("Kunci disimpan di Firebase dan hanya bisa dibaca email pembuat aplikasi. Tidak ikut tertanam di APK. Akun pembuat otomatis memakai Euler Stream untuk TikTok dan YouTube API untuk komentar YouTube.", color = Neon.Muted)
        if (Graph.social.creatorNeedsVerification) GlassCard {
            Text("Email ini belum terverifikasi. Kunci baru bisa dibaca setelah email diverifikasi.", color = Neon.Amber)
            GhostButton("Kirim email verifikasi", icon = Icons.Filled.MarkEmailRead) {
                scope.launch { activity.message.value = Graph.social.sendVerification() ?: "Email verifikasi terkirim. Buka email lalu masuk lagi." }
            }
        }
        GlassCard {
            Field("API key Euler Stream (TikTok)", euler, secret = true) { euler = it.trim() }
            Field("API key YouTube Data API", yt, secret = true) { yt = it.trim() }
            Text("Kosongkan kolom yang tidak ingin diubah.", color = Neon.Muted)
        }
        NeonButton(if (busy) "Menyimpan…" else "Simpan kunci", enabled = !busy && (euler.isNotBlank() || yt.isNotBlank()), icon = Icons.Filled.Key) {
            busy = true
            scope.launch {
                val e = Graph.social.saveCreatorKeys(euler, yt)
                busy = false
                activity.message.value = e ?: "Kunci tersimpan untuk semua email pembuat."
                if (e == null) { euler = ""; yt = "" }
            }
        }
        GhostButton("Ambil ulang kunci dari Firebase", icon = Icons.Filled.Sync) { Graph.social.fetchCreatorKeys(); activity.message.value = "Kunci diperbarui." }
    }
}

@Composable
private fun SwitchAccountPage(activity: MainActivity, onSignedOut: () -> Unit, onBack: () -> Unit) {
    val accounts = remember { Graph.social.savedAccounts() }
    val current = Graph.firebase.email.orEmpty().lowercase()
    ScreenColumn {
        BackRow("Beralih akun", onBack)
        accounts.forEach { a ->
            val isCurrent = a.email == current
            GlassCard {
                Text(a.email + if (isCurrent) " · sedang dipakai" else "", style = MaterialTheme.typography.titleMedium)
                Text(if (a.google) "Akun Google" else "Akun email", color = Neon.Muted)
                if (!isCurrent) NeonButton("Pakai akun ini", icon = Icons.Filled.SwitchAccount) {
                    activity.pendingLoginEmail.value = if (a.google) "" else a.email
                    activity.pendingGoogleLogin.value = a.google
                    Graph.social.signOut(); onSignedOut()
                }
            }
        }
        NeonButton("Tambah akun", icon = Icons.Filled.PersonAdd) {
            activity.pendingLoginEmail.value = ""
            Graph.social.signOut(); onSignedOut()
        }
    }
}
