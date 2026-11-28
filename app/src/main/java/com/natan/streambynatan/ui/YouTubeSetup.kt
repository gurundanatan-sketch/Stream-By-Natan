package com.natan.streambynatan.ui

import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.SmartDisplay
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.natan.streambynatan.chat.BroadcastSettings
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.data.YtBroadcastPrefs
import com.natan.streambynatan.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Membuat siaran YouTube dari pengaturan tersimpan: judul, deskripsi, thumbnail, visibilitas, audiens,
 * kategori, tag, bahasa, lokasi, latensi, dan jeda iklan otomatis. Stream key diambil otomatis.
 * Mengembalikan null kalau berhasil, atau pesan error.
 */
suspend fun createYouTubeBroadcast(activity: MainActivity, p: YtBroadcastPrefs, scheduleMinutes: Int = 0): String? = runCatching {
    if (p.title.isBlank()) return "Judul siaran YouTube masih kosong."
    val thumb = p.thumbnailUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    val bytes = thumb?.let { u -> runCatching { activity.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }
    val mime = thumb?.let { runCatching { activity.contentResolver.getType(it) }.getOrNull() } ?: "image/jpeg"
    // nama tempat -> titik lokasi (tanpa izin lokasi)
    val point = if (p.location.isBlank()) null else withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(activity, Locale("id")).getFromLocationName(p.location, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
    val r = Graph.ytApi.createBroadcast(BroadcastSettings(
        title = p.title.trim().take(100), description = p.description.take(5000), privacy = p.privacy, madeForKids = p.madeForKids,
        scheduledStart = if (scheduleMinutes > 0) System.currentTimeMillis() + scheduleMinutes * 60_000L else 0,
        categoryId = p.categoryId, tags = p.tags, latency = p.latency, enableDvr = p.enableDvr,
        thumbnail = bytes, thumbnailMime = mime, language = p.language,
        latitude = point?.first, longitude = point?.second, locationName = p.location,
        autoAds = p.autoAds, adIntervalSecs = p.adIntervalMinutes * 60,
    ))
    Graph.secure.put(SecureStore.YOUTUBE_KEY, r.key)
    Graph.settings.update { it.copy(youtubeServer = r.server, youtubeVideoId = r.videoId, enableYouTube = true) }
    val extra = buildList {
        addAll(r.notes)
        if (p.location.isNotBlank() && point == null) add("Lokasi \"${p.location}\" tidak ditemukan")
    }
    activity.message.value = "Siaran YouTube dibuat. Stream key diambil otomatis." + if (extra.isNotEmpty()) " Catatan: " + extra.joinToString("; ") + "." else ""
    null
}.getOrElse { "Gagal membuat siaran YouTube: ${it.message?.take(160)}" }

/**
 * Membuka YouTube Studio untuk memeriksa / menyalakan monetisasi siaran.
 * Kalau siaran sudah dibuat dari aplikasi, langsung ke halaman siaran itu; kalau belum, ke Live Control Room.
 */
fun openMonetizationCheck(activity: MainActivity) {
    val id = Graph.settings.current.youtubeVideoId
    val url = if (id.isNotBlank()) "https://studio.youtube.com/video/$id/livestreaming" else "https://studio.youtube.com/channel/UC/livestreaming"
    runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }
        .onSuccess { activity.message.value = "Di YouTube Studio: Edit > Monetisasi > Aktif, lalu pilih waktu iklan Otomatis." }
        .onFailure { activity.message.value = "Tidak bisa membuka YouTube Studio. Buka studio.youtube.com di browser." }
}

/** Halaman pengaturan siaran YouTube (layar penuh). Semua isian disimpan untuk live berikutnya. */
@Composable
fun YouTubeBroadcastDialog(activity: MainActivity, onClose: () -> Unit) {
    val s by Graph.settings.state.collectAsState()
    val p = s.ytBroadcast
    fun set(f: (YtBroadcastPrefs) -> YtBroadcastPrefs) = Graph.settings.update { it.copy(ytBroadcast = f(it.ytBroadcast)) }
    var scheduleMinutes by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val connected by activity.youtubeConnected

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Neon.Void)) {
            ScreenColumn {
                GhostButton("Kembali", icon = Icons.Filled.ArrowBack, onClick = onClose)
                ScreenTitle("Siaran YouTube", "Semua isian disimpan untuk live berikutnya")
                if (!connected) GlassCard {
                    Text("Hubungkan akun Google dulu (sekali saja) supaya aplikasi bisa membuat siaran dan mengambil stream key.", color = Neon.Muted)
                    GhostButton("Hubungkan YouTube", icon = Icons.Filled.Link) { activity.connectYouTube() }
                }
                GlassCard {
                    SectionTitle("Detail")
                    Field("Judul (wajib, maks. 100 huruf)", p.title) { v -> set { it.copy(title = v.take(100)) } }
                    Field("Deskripsi", p.description, singleLine = false, minLines = 4) { v -> set { it.copy(description = v) } }
                    if (p.thumbnailUri.isBlank()) GhostButton("Pilih thumbnail dari galeri", icon = Icons.Filled.Image) {
                        activity.pick(arrayOf("image/*")) { u -> set { it.copy(thumbnailUri = u.toString()) } }
                    } else {
                        Text("Thumbnail sudah dipilih.", color = Neon.Lime)
                        GhostButton("Ganti thumbnail", icon = Icons.Filled.Image) { activity.pick(arrayOf("image/*")) { u -> set { it.copy(thumbnailUri = u.toString()) } } }
                        GhostButton("Hapus thumbnail", icon = Icons.Filled.Delete) { set { it.copy(thumbnailUri = "") } }
                    }
                    Text("Thumbnail butuh kanal yang sudah verifikasi nomor HP. Ukuran disarankan 1280×720, maks. 2 MB.", color = Neon.Muted)
                }
                GlassCard {
                    SectionTitle("Visibilitas & audiens")
                    ChoiceRow("Visibilitas", listOf("public" to "Publik", "unlisted" to "Tidak publik", "private" to "Pribadi"), p.privacy) { v -> set { it.copy(privacy = v) } }
                    ChoiceRow("Audiens", listOf("no" to "Bukan untuk anak-anak", "yes" to "Dibuat untuk anak-anak"), if (p.madeForKids) "yes" else "no") { v -> set { it.copy(madeForKids = v == "yes") } }
                    if (p.madeForKids) Text("Konten untuk anak-anak: komentar live dan iklan yang dipersonalisasi dimatikan oleh YouTube.", color = Neon.Amber)
                }
                GlassCard {
                    SectionTitle("Monetisasi")
                    Text("Hanya berlaku kalau kanal sudah masuk Program Partner YouTube. Iklan mengikuti setelan monetisasi bawaan kanal di YouTube Studio.", color = Neon.Muted)
                    SwitchRow("Jeda iklan otomatis selama live", p.autoAds, "YouTube menyisipkan iklan secara berkala") { v -> set { it.copy(autoAds = v) } }
                    if (p.autoAds) NumberField("Setiap (menit, minimal 5)", p.adIntervalMinutes) { v -> set { it.copy(adIntervalMinutes = v.coerceAtLeast(5)) } }
                    Text("Saat live, jeda iklan manual ada di menu Lainnya.", color = Neon.Muted)
                    GhostButton("Cek monetisasi di YouTube Studio", icon = Icons.Filled.MonetizationOn) { openMonetizationCheck(activity) }
                }
                GlassCard {
                    SectionTitle("Kategori, tag, bahasa & lokasi")
                    ChoiceRow("Kategori", listOf(
                        "22" to "Orang & blog", "29" to "Nirlaba & aktivisme", "27" to "Pendidikan", "10" to "Musik",
                        "24" to "Hiburan", "20" to "Game", "26" to "Gaya & tips", "28" to "Sains & teknologi", "25" to "Berita & politik", "17" to "Olahraga",
                    ), p.categoryId) { v -> set { it.copy(categoryId = v) } }
                    ListEditor("Tag", p.tags, "satu per baris") { v -> set { it.copy(tags = v) } }
                    ChoiceRow("Bahasa", listOf("id" to "Indonesia", "en" to "Inggris", "jv" to "Jawa", "su" to "Sunda", "ms" to "Melayu"), p.language) { v -> set { it.copy(language = v) } }
                    Field("Lokasi (nama kota/tempat, contoh: Manado)", p.location) { v -> set { it.copy(location = v) } }
                }
                GlassCard {
                    SectionTitle("Siaran")
                    ChoiceRow("Latensi", listOf("normal" to "Normal (kualitas terbaik)", "low" to "Rendah", "ultraLow" to "Sangat rendah (paling cepat balas komentar)"), p.latency) { v -> set { it.copy(latency = v) } }
                    SwitchRow("Penonton bisa memutar mundur (DVR)", p.enableDvr) { v -> set { it.copy(enableDvr = v) } }
                    NumberField("Jadwalkan (menit dari sekarang, 0 = langsung)", scheduleMinutes) { scheduleMinutes = it }
                    SwitchRow("Buat siaran baru otomatis setiap Mulai live", p.autoCreate, "Memakai pengaturan di halaman ini, tanpa membuka halaman ini lagi") { v -> set { it.copy(autoCreate = v) } }
                }
                error?.let { Text(it, color = Neon.Red) }
                NeonButton(if (busy) "Membuat siaran…" else "Buat siaran YouTube sekarang", enabled = !busy && connected && p.title.isNotBlank(), icon = Icons.Filled.SmartDisplay) {
                    busy = true; error = null
                    scope.launch {
                        error = createYouTubeBroadcast(activity, Graph.settings.current.ytBroadcast, scheduleMinutes)
                        busy = false
                        if (error == null) onClose()
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
