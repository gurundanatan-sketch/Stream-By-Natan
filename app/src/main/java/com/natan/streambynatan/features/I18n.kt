package com.natan.streambynatan.features

import android.content.Context
import com.natan.streambynatan.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pilihan bahasa aplikasi.
 * - Bahasa Indonesia: bawaan.
 * - Inggris, Jawa, Sunda: ditulis langsung (bahasa daerah sebaiknya dicek penutur asli).
 * - Bahasa dunia lain: diterjemahkan otomatis di HP lewat ML Kit (sekali unduh model, lalu tersimpan).
 * - Bahasa daerah lain: ditambahkan bertahap.
 */
object I18n {
    data class Language(val code: String, val name: String, val native: String, val available: Boolean = true)

    val languages: List<Language> = listOf(
        Language("id", "Indonesia", "Bahasa Indonesia"),
        Language("en", "Inggris", "English"),
        Language("jv", "Jawa", "Basa Jawa"),
        Language("su", "Sunda", "Basa Sunda"),
        Language("ms", "Melayu", "Bahasa Melayu"),
        Language("ar", "Arab", "العربية"),
        Language("zh", "Mandarin", "中文"),
        Language("ja", "Jepang", "日本語"),
        Language("ko", "Korea", "한국어"),
        Language("hi", "Hindi", "हिन्दी"),
        Language("es", "Spanyol", "Español"),
        Language("pt", "Portugis", "Português"),
        Language("fr", "Prancis", "Français"),
        Language("de", "Jerman", "Deutsch"),
        Language("ru", "Rusia", "Русский"),
        Language("th", "Thai", "ไทย"),
        Language("vi", "Vietnam", "Tiếng Việt"),
        Language("tr", "Turki", "Türkçe"),
        Language("tl", "Tagalog", "Tagalog"),
        Language("it", "Italia", "Italiano"),
        Language("nl", "Belanda", "Nederlands"),
        Language("ban", "Bali", "Basa Bali", available = false),
        Language("min", "Minang", "Baso Minang", available = false),
        Language("bbc", "Batak", "Hata Batak", available = false),
        Language("bug", "Bugis", "Basa Ugi", available = false),
        Language("mad", "Madura", "Basa Madhura", available = false),
        Language("bjn", "Banjar", "Bahasa Banjar", available = false),
    )

    /** Teks utama aplikasi (kunci -> Indonesia). */
    private val base: Map<String, String> = linkedMapOf(
        "nav_live" to "Live", "nav_rec" to "Rekaman", "nav_progress" to "Progres", "nav_settings" to "Pengaturan",
        "ready" to "Siap live?", "platforms" to "Platform tujuan", "source" to "Sumber video", "profile" to "Profil live",
        "start_live" to "MULAI LIVE", "start_practice" to "MULAI LATIHAN", "more_options" to "Opsi lainnya",
        "account" to "Akun", "edit_profile" to "Edit profil", "share_profile" to "Bagikan profil",
        "feedback" to "Kirim saran & masukan", "inbox" to "Kotak saran", "about" to "Tentang aplikasi",
        "privacy" to "Kebijakan privasi", "language" to "Pilihan bahasa", "display" to "Tampilan",
        "restricted" to "Izin setelan terbatas", "logout" to "Keluar", "switch" to "Beralih akun", "delete" to "Hapus akun",
        "guests" to "Tamu", "tools" to "Alat", "share" to "Bagikan", "enhance" to "Sempurnakan", "more" to "Lainnya",
        "end_live" to "Akhiri live", "comments" to "Komentar", "following" to "Mengikuti", "schedule" to "Jadwal live",
        "moderators" to "Moderator", "creator_keys" to "Kunci khusus pembuat",
    )

    private val en = mapOf(
        "nav_live" to "Live", "nav_rec" to "Recordings", "nav_progress" to "Progress", "nav_settings" to "Settings",
        "ready" to "Ready to go live?", "platforms" to "Destinations", "source" to "Video source", "profile" to "Live profile",
        "start_live" to "GO LIVE", "start_practice" to "START PRACTICE", "more_options" to "More options",
        "account" to "Account", "edit_profile" to "Edit profile", "share_profile" to "Share profile",
        "feedback" to "Send feedback", "inbox" to "Feedback inbox", "about" to "About the app",
        "privacy" to "Privacy policy", "language" to "Language", "display" to "Display",
        "restricted" to "Restricted settings", "logout" to "Sign out", "switch" to "Switch account", "delete" to "Delete account",
        "guests" to "Guests", "tools" to "Tools", "share" to "Share", "enhance" to "Enhance", "more" to "More",
        "end_live" to "End live", "comments" to "Comments", "following" to "Following", "schedule" to "Live schedule",
        "moderators" to "Moderators", "creator_keys" to "Creator keys",
    )

    private val jv = mapOf(
        "nav_live" to "Live", "nav_rec" to "Rekaman", "nav_progress" to "Kemajuan", "nav_settings" to "Setelan",
        "ready" to "Siap live?", "platforms" to "Platform tujuan", "source" to "Sumber video", "profile" to "Profil live",
        "start_live" to "WIWIT LIVE", "start_practice" to "WIWIT LATIHAN", "more_options" to "Pilihan liyane",
        "account" to "Akun", "edit_profile" to "Ganti profil", "share_profile" to "Bagekake profil",
        "feedback" to "Kirim saran lan pamrayoga", "inbox" to "Kothak saran", "about" to "Babagan aplikasi",
        "privacy" to "Kawicaksanan privasi", "language" to "Pilihan basa", "display" to "Tampilan",
        "restricted" to "Ijin setelan winates", "logout" to "Metu", "switch" to "Ganti akun", "delete" to "Busak akun",
        "guests" to "Tamu", "tools" to "Piranti", "share" to "Bagekake", "enhance" to "Sampurnakake", "more" to "Liyane",
        "end_live" to "Pungkasi live", "comments" to "Komentar", "following" to "Ngetutake", "schedule" to "Jadwal live",
        "moderators" to "Moderator", "creator_keys" to "Kunci khusus panggawe",
    )

    private val su = mapOf(
        "nav_live" to "Live", "nav_rec" to "Rékaman", "nav_progress" to "Kamajuan", "nav_settings" to "Setélan",
        "ready" to "Siap live?", "platforms" to "Platform tujuan", "source" to "Sumber vidéo", "profile" to "Profil live",
        "start_live" to "MIMITIAN LIVE", "start_practice" to "MIMITIAN LATIHAN", "more_options" to "Pilihan séjén",
        "account" to "Akun", "edit_profile" to "Robah profil", "share_profile" to "Bagikeun profil",
        "feedback" to "Kirim saran jeung masukan", "inbox" to "Kotak saran", "about" to "Ngeunaan aplikasi",
        "privacy" to "Kawijakan privasi", "language" to "Pilihan basa", "display" to "Tampilan",
        "restricted" to "Idin setélan kawates", "logout" to "Kaluar", "switch" to "Ganti akun", "delete" to "Hapus akun",
        "guests" to "Sémah", "tools" to "Alat", "share" to "Bagikeun", "enhance" to "Sampurnakeun", "more" to "Séjénna",
        "end_live" to "Tungtungan live", "comments" to "Koméntar", "following" to "Nuturkeun", "schedule" to "Jadwal live",
        "moderators" to "Moderator", "creator_keys" to "Konci husus nu nyieun",
    )

    private val _strings = MutableStateFlow<Map<String, String>>(base)
    val strings: StateFlow<Map<String, String>> = _strings

    fun t(key: String): String = _strings.value[key] ?: base[key] ?: key

    /** Menerapkan bahasa. Bahasa dunia diterjemahkan otomatis lalu disimpan supaya tidak mengunduh ulang. */
    fun apply(context: Context, code: String) {
        when (code) {
            "id" -> { _strings.value = base; return }
            "en" -> { _strings.value = base + en; return }
            "jv" -> { _strings.value = base + jv; return }
            "su" -> { _strings.value = base + su; return }
        }
        val prefs = context.getSharedPreferences("i18n_$code", Context.MODE_PRIVATE)
        val cached = base.keys.associateWith { prefs.getString(it, null) }.filterValues { it != null }.mapValues { it.value!! }
        _strings.value = base + en + cached
        if (cached.size == base.size) return
        CoroutineScope(Dispatchers.IO).launch {
            val out = HashMap<String, String>()
            for ((k, v) in en) {
                val tr = runCatching { Graph.translator.translate(v, code)?.first }.getOrNull() ?: continue
                out[k] = tr
            }
            if (out.isNotEmpty()) {
                prefs.edit().apply { out.forEach { (k, v) -> putString(k, v) } }.apply()
                _strings.value = base + en + out
            }
        }
    }
}
