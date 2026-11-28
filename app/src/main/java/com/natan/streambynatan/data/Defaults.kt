package com.natan.streambynatan.data

/** Nilai bawaan yang bisa diubah pengguna. */
object Defaults {

    /** Singkatan yang dipanjangkan sebelum dibacakan TTS (bisa ditambah sendiri). */
    val abbreviations: Map<String, String> = linkedMapOf(
        "gmn" to "gimana", "gmna" to "gimana", "gimna" to "gimana", "bgmn" to "bagaimana",
        "knp" to "kenapa", "knpa" to "kenapa", "ngpn" to "ngapain", "ngp" to "ngapain",
        "kpn" to "kapan", "dmn" to "di mana", "dmna" to "di mana", "kmn" to "ke mana",
        "brp" to "berapa", "brpa" to "berapa", "sp" to "siapa", "sapa" to "siapa",
        "ap" to "apa", "apaan" to "apa", "tny" to "tanya", "nny" to "nanya",
        "emg" to "emang", "emng" to "emang", "bs" to "bisa", "gk" to "nggak", "ga" to "nggak",
        "gak" to "nggak", "ngga" to "nggak", "tdk" to "tidak", "blh" to "boleh", "yg" to "yang",
        "dgn" to "dengan", "utk" to "untuk", "krn" to "karena", "sm" to "sama", "jg" to "juga",
        "udh" to "udah", "sdh" to "sudah", "blm" to "belum", "lg" to "lagi", "aja" to "saja",
        "bgt" to "banget", "kak" to "kakak", "bang" to "abang", "trs" to "terus", "tp" to "tapi",
        "klo" to "kalau", "kl" to "kalau", "kalo" to "kalau", "dr" to "dari", "dpt" to "dapat",
        "org" to "orang", "sy" to "saya", "aq" to "aku", "gw" to "gue", "lu" to "lo", "pls" to "tolong",
        "mksh" to "makasih", "makasi" to "makasih", "trims" to "terima kasih", "thx" to "thanks",
        "wkwk" to "haha", "wkwkwk" to "haha", "btw" to "ngomong-ngomong", "otw" to "lagi jalan",
        "slmt" to "selamat", "tq" to "terima kasih", "ttg" to "tentang", "msh" to "masih",
        "bkn" to "bukan", "spt" to "seperti", "kyk" to "kayak", "kyak" to "kayak", "sblm" to "sebelum",
        "stlh" to "setelah", "skrg" to "sekarang", "skrang" to "sekarang", "bsk" to "besok",
        "kmrn" to "kemarin", "hr" to "hari", "mlm" to "malam", "pgi" to "pagi", "sore" to "sore",
        "tuhan" to "Tuhan", "yesus" to "Yesus", "gbu" to "Tuhan memberkati", "amin" to "amin",
    )

    /** Kata tanya baku dan tidak baku, plus pemicu "mau tanya". */
    val questionWords: List<String> = listOf(
        "apa", "apakah", "siapa", "siapakah", "kapan", "kapankah", "di mana", "dimana", "ke mana", "kemana",
        "dari mana", "darimana", "mengapa", "kenapa", "bagaimana", "gimana", "berapa", "berapakah", "yang mana",
        "manakah", "bolehkah", "bisakah", "apa iya", "masa sih", "emang", "emangnya", "ngapain", "kok",
        "tanya", "nanya", "mau tanya", "boleh tanya", "izin tanya", "pertanyaan", "bisa gak", "bisa nggak",
        "boleh gak", "boleh nggak", "gak sih", "kan ya", "ya kak", "gmn", "knp", "kpn", "dmn", "brp", "sp",
        "ap", "tny", "emg", "ngpn", "bs gk", "blh gk", "bs ga", "blh ga", "gmna", "knpa", "brpa",
    )

    val profanity: List<String> = listOf(
        "anjing", "anjir", "anjay", "bangsat", "babi", "bajingan", "kontol", "memek", "ngentot", "jancok",
        "jancuk", "goblok", "tolol", "bego", "kampret", "asu", "tai", "taik", "brengsek", "keparat",
        "sialan", "monyet", "pantek", "pukimak", "fuck", "shit", "bitch",
    )

    val announcements: List<Announcement> = listOf(
        Announcement("Jangan lupa follow dan subscribe ya!", 15, "both", true),
    )

    val faq: List<FaqItem> = listOf(
        FaqItem(listOf("kapan live", "jadwal live", "live lagi"), "Live rutin setiap hari Minggu malam, ditunggu ya!"),
    )

    val faqCommands: List<String> = listOf(
        "tampilkan jawaban", "munculkan jawaban", "tunjukkan jawabannya", "kasih lihat jawaban",
    )

    /** Aksi -> kalimat pemicu perintah suara (semua bisa diubah). */
    val voiceCommands: Map<String, List<String>> = linkedMapOf(
        "next_question" to listOf("pertanyaan berikutnya", "lanjut pertanyaan"),
        "repeat_question" to listOf("ulangi pertanyaan", "ulangi"),
        "hold_question" to listOf("tahan pertanyaan", "tahan"),
        "skip_question" to listOf("lewati pertanyaan", "lewati"),
        "missed_summary" to listOf("ringkasan komentar", "komentar yang terlewat"),
        "top_supporters" to listOf("siapa pendukung teratas", "pendukung teratas"),
        "read_prayers" to listOf("bacakan doa", "daftar doa"),
        "show_qr" to listOf("tampilkan kode qr", "munculkan kode qr"),
        "hide_qr" to listOf("sembunyikan kode qr", "tutup kode qr"),
        "show_offering" to listOf("tampilkan persembahan", "info persembahan"),
        "hide_offering" to listOf("sembunyikan persembahan"),
        "show_answer" to listOf("tampilkan jawaban"),
        "hide_overlay" to listOf("tutup tampilan", "kembali normal"),
        "next_point" to listOf("poin berikutnya", "lanjut poin"),
        "camera_front" to listOf("kamera depan"),
        "camera_back" to listOf("kamera belakang"),
        "switch_camera" to listOf("pindah ke"),
        "mic_off" to listOf("matikan mikrofon"),
        "mic_on" to listOf("nyalakan mikrofon"),
        "poll_result" to listOf("hasil polling", "umumkan hasil polling"),
        "live_time" to listOf("sudah berapa lama", "durasi live"),
        "music_toggle" to listOf("putar musik", "hentikan musik"),
        "pause_screen" to listOf("jeda layar", "tutup layar sebentar"),
        "resume_screen" to listOf("buka layar", "lanjutkan layar"),
        "giveaway_draw" to listOf("undi pemenang", "pilih pemenang"),
        "quiz_next" to listOf("kuis berikutnya", "pertanyaan kuis"),
        "next_song" to listOf("lagu berikutnya", "permintaan lagu"),
        "rundown_next" to listOf("acara berikutnya", "lanjut acara"),
    )

    /** Platform tambahan. Stream key masing-masing disimpan terenkripsi dengan nama "key_<id>". */
    val extraPlatforms: List<PlatformTarget> = listOf(
        PlatformTarget("facebook", "Facebook", "rtmps://live-api-s.facebook.com:443/rtmp/"),
        PlatformTarget("instagram", "Instagram", "rtmps://edgetee-upload.instagram.com:443/rtmp/"),
        PlatformTarget("custom1", "Server lain 1", ""),
        PlatformTarget("custom2", "Server lain 2", ""),
    )

    /** Penanda (getar / suara / ucapan) per kejadian. */
    val cues: Map<String, CueConfig> = linkedMapOf(
        "gift_big" to CueConfig(true, "double", "sfx_success", false),
        "guest_waiting" to CueConfig(true, "short", "sfx_notif", true),
        "guest_message" to CueConfig(true, "short", "", true),
        "connection_lost" to CueConfig(true, "long", "", true),
        "question_new" to CueConfig(false, "short", "", false),
        "follow" to CueConfig(false, "short", "", false),
        "battery_low" to CueConfig(true, "triple", "", true),
        "moderator" to CueConfig(true, "short", "", true),
    )

    val cueLabels: Map<String, String> = linkedMapOf(
        "gift_big" to "Hadiah besar", "guest_waiting" to "Tamu masuk ruang tunggu", "guest_message" to "Pesan dari tamu",
        "connection_lost" to "Koneksi putus", "question_new" to "Pertanyaan baru", "follow" to "Follow baru",
        "battery_low" to "Baterai / suhu HP", "moderator" to "Tindakan moderator",
    )

    private val allOn = FeatureToggles()

    val profiles: List<LiveProfile> = listOf(
        LiveProfile("santai", "Ngobrol santai", allOn.copy(notesWhisper = false, verseFromScript = false, lyrics = false)),
        LiveProfile("presentasi", "Presentasi", allOn.copy(readComments = false, newViewerGreeting = false, subtitles = true)),
        LiveProfile(
            "khotbah", "Catatan live",
            allOn.copy(notesWhisper = true, verseFromScript = true, prayerList = true, offering = true,
                subtitles = true, readComments = false, newViewerGreeting = false, intro = true, outro = true),
        ),
        LiveProfile("pujian", "Live music", allOn.copy(lyrics = true, readComments = false, music = false)),
    )
}
