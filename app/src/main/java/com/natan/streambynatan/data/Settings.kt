package com.natan.streambynatan.data

import kotlinx.serialization.Serializable

/**
 * Semua pengaturan aplikasi (kecuali rahasia seperti stream key, yang disimpan terenkripsi di [SecureStore]).
 * Semuanya bisa diatur dari layar Pengaturan, dan dicadangkan ke Firebase (fitur cadangan pengaturan).
 */
@Serializable
data class AppSettings(
    val youtubeServer: String = "rtmp://a.rtmp.youtube.com/live2",
    val tiktokServer: String = "",
    val enableYouTube: Boolean = true,
    val enableTikTok: Boolean = true,
    val tiktokUsername: String = "",
    val youtubeVideoId: String = "",
    val youtubeChannelUrl: String = "",
    val video: VideoConfig = VideoConfig(),
    val features: FeatureToggles = FeatureToggles(),
    val tts: TtsConfig = TtsConfig(),
    val templates: Templates = Templates(),
    val abbreviations: Map<String, String> = Defaults.abbreviations,
    val questionWords: List<String> = Defaults.questionWords,
    val profanity: List<String> = Defaults.profanity,
    val mutedViewers: List<String> = emptyList(),
    val targets: Targets = Targets(),
    val announcements: List<Announcement> = Defaults.announcements,
    val faq: List<FaqItem> = Defaults.faq,
    val faqCommandPhrases: List<String> = Defaults.faqCommands,
    val voiceCommands: Map<String, List<String>> = Defaults.voiceCommands,
    val profiles: List<LiveProfile> = Defaults.profiles,
    val activeProfileId: String = "santai",
    val notes: List<NoteScript> = emptyList(),
    val activeNoteId: String = "",
    val lyrics: List<LyricSong> = emptyList(),
    val activeLyricId: String = "",
    val prayerTriggers: List<String> = listOf("doa", "mohon doa", "tolong doakan", "minta doa", "doakan"),
    val offering: OfferingConfig = OfferingConfig(),
    val saweria: SaweriaConfig = SaweriaConfig(),
    val intro: IntroConfig = IntroConfig(),
    val transition: TransitionConfig = TransitionConfig(),
    val timer: TimerConfig = TimerConfig(),
    val ticker: TickerConfig = TickerConfig(),
    val overlayText: String = "",
    val subtitles: SubtitleConfig = SubtitleConfig(),
    val translate: TranslateConfig = TranslateConfig(),
    val micFx: MicFx = MicFx(),
    val music: MusicConfig = MusicConfig(),
    val giftSfx: GiftSfxConfig = GiftSfxConfig(),
    val customSounds: List<CustomSound> = emptyList(),
    val floatingSfx: List<String> = listOf("sfx_applause_synth", "sfx_ting", "sfx_drumroll", "sfx_success"),
    val background: BackgroundConfig = BackgroundConfig(),
    val audioOnlyImage: String = "",
    val cameraNames: Map<String, String> = mapOf("depan" to "kamera depan", "belakang" to "kamera belakang"),
    val shareMessage: String = "Saya sedang live sekarang, yuk gabung! {youtube} {tiktok}",
    val schedules: List<ScheduleItem> = emptyList(),
    val verseCard: CardStyle = CardStyle(),
    val qrStyle: QrStyle = QrStyle(),
    val faqCard: CardStyle = CardStyle(bgColor = 0xFF101830.toInt()),
    val guest: GuestConfig = GuestConfig(),
    val pollDurationSec: Int = 60,
    val silenceToHideVerseMs: Int = 2500,
    val updateCheckOnStart: Boolean = true,
    // ---------------- tambahan versi 2
    val onboardingDone: Boolean = false,
    val a11y: A11yConfig = A11yConfig(),
    val language: String = "id",
    val display: DisplayConfig = DisplayConfig(),
    /** Platform tambahan selain YouTube & TikTok (Facebook, server RTMP lain). Stream key disimpan terenkripsi. */
    val extraPlatforms: List<PlatformTarget> = Defaults.extraPlatforms,
    /** Kualitas video: 480 | 720 | 1080 | auto */
    val quality: String = "720",
    /** Pengaturan siaran YouTube terakhir (diisi otomatis saat membuat siaran berikutnya). */
    val ytBroadcast: YtBroadcastPrefs = YtBroadcastPrefs(),
    /** Tampilan siaran mendatar untuk TikTok. */
    val layout: LayoutConfig = LayoutConfig(),
    val filter: FilterConfig = FilterConfig(),
    val screenText: ScreenTextConfig = ScreenTextConfig(),
    val nameplate: NameplateConfig = NameplateConfig(),
    val commentDisplay: CommentDisplayConfig = CommentDisplayConfig(),
    val cues: Map<String, CueConfig> = Defaults.cues,
    val giveaway: GiveawayConfig = GiveawayConfig(),
    val quiz: List<QuizItem> = emptyList(),
    val songRequestKeyword: String = "!lagu",
    val quietWarnMinutes: Int = 3,
    val drinkReminderMinutes: Int = 45,
    val batteryWarnPercent: Int = 20,
    val temperatureWarnC: Int = 42,
    val rundown: List<RundownItem> = emptyList(),
    val themes: List<ThemeImage> = emptyList(),
    val themeTrigger: String = "tema",
    val themeDurationSec: Int = 12,
    val pause: PauseConfig = PauseConfig(),
    val overlays: List<OverlayMedia> = emptyList(),
    val voiceReplyTrigger: String = "balas",
)

@Serializable
data class A11yConfig(
    /** true = semua bantuan suara aktif (bisikan catatan, pengumuman). false = tampil sebagai teks di layar. */
    val voiceAids: Boolean = false,
    val chosen: Boolean = false,
)

@Serializable
data class DisplayConfig(
    val theme: String = "dark", // dark | light
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
)

@Serializable
data class YtBroadcastPrefs(
    val title: String = "",
    val description: String = "",
    /** public | unlisted | private */
    val privacy: String = "public",
    val madeForKids: Boolean = false,
    val categoryId: String = "22",
    val tags: List<String> = emptyList(),
    /** normal | low | ultraLow */
    val latency: String = "low",
    val enableDvr: Boolean = true,
    /** Bahasa judul & audio (kode ISO, misal id, en). */
    val language: String = "id",
    /** Nama tempat (dicari otomatis jadi titik lokasi). */
    val location: String = "",
    val thumbnailUri: String = "",
    /** Buat siaran baru otomatis dengan pengaturan ini setiap tombol Mulai live ditekan. */
    val autoCreate: Boolean = false,
    /** Jeda iklan otomatis selama live (hanya untuk kanal yang sudah dimonetisasi). */
    val autoAds: Boolean = false,
    val adIntervalMinutes: Int = 15,
)

@Serializable
data class LayoutConfig(
    /** Kalau siaran mendatar dan TikTok menyala: native = 16:9 asli (seperti TikTok Live Studio), frame = bingkai tegak. */
    val tiktokLandscape: String = "native",
    /** Judul di bagian atas bingkai tegak. */
    val frameTitle: String = "",
    val frameShowTitle: Boolean = true,
    /** blur = latar video diburamkan, dark = latar gelap bergradasi */
    val frameBackground: String = "blur",
)

@Serializable
data class PlatformTarget(
    val id: String,
    val name: String,
    val server: String,
    val enabled: Boolean = false,
)

@Serializable
data class FilterConfig(
    /** Filter siaran host (lihat Filters.presets). */
    val preset: String = "normal",
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val smoothSkin: Boolean = false,
)

@Serializable
data class ScreenTextConfig(
    val enabled: Boolean = false,
    val text: String = "",
    val position: String = "top", // top | center | bottom
    val size: Float = 1f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val background: Boolean = true,
)

@Serializable
data class NameplateConfig(
    val enabled: Boolean = false,
    val name: String = "",
    val role: String = "Host",
    val position: String = "bottom_left", // bottom_left | top_left | top_right
    /** 0 = tampil terus, selain itu tampil N detik di awal live lalu hilang. */
    val showSeconds: Int = 0,
    val guestPlates: Boolean = true,
)

@Serializable
data class CommentDisplayConfig(
    /** Komentar ikut tampil di layar siaran. */
    val onStream: Boolean = false,
    /** Ukuran tulisan komentar di layar host. */
    val hostTextScale: Float = 1f,
    /** Letak kolom komentar di layar host: bottom | top */
    val hostPosition: String = "bottom",
)

@Serializable
data class CueConfig(
    val vibrate: Boolean = true,
    /** Pola getar: short | double | long | triple */
    val pattern: String = "short",
    val sound: String = "",
    val speak: Boolean = false,
)

@Serializable
data class GiveawayConfig(
    val keyword: String = "!ikut",
    val durationSec: Int = 120,
    val prize: String = "",
)

@Serializable
data class QuizItem(val question: String, val answer: String)

@Serializable
data class RundownItem(val title: String, val minutes: Int)

@Serializable
data class ThemeImage(val id: String, val title: String, val imagePath: String)

@Serializable
data class PauseConfig(
    val text: String = "Sebentar ya, live akan segera kembali",
    val imagePath: String = "",
    val muteMic: Boolean = true,
)

@Serializable
data class OverlayMedia(
    val id: String,
    val name: String,
    val uri: String,
    val type: String = "image", // image | video
    val size: String = "medium", // small | medium | full
    val position: String = "top_right",
)

@Serializable
data class VideoConfig(
    val width: Int = 720,
    val height: Int = 1280,
    val fps: Int = 30,
    val bitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val portrait: Boolean = true,
    val adaptiveBitrate: Boolean = true,
    val recordWhileLive: Boolean = true,
    val autoReconnect: Boolean = true,
)

/** Saklar semua fitur. Bisa diubah langsung atau lewat profil live. */
@Serializable
data class FeatureToggles(
    val readComments: Boolean = true,
    val commentsToBroadcast: Boolean = true,
    val questionQueue: Boolean = true,
    val thanksGift: Boolean = true,
    val thanksFollow: Boolean = true,
    val thanksShare: Boolean = true,
    val thanksSubscribe: Boolean = true,
    val loyalGreeting: Boolean = true,
    val newViewerGreeting: Boolean = true,
    val profanityFilter: Boolean = true,
    val translate: Boolean = false,
    val polls: Boolean = true,
    val targets: Boolean = true,
    val liveTimer: Boolean = true,
    val ticker: Boolean = false,
    val overlayText: Boolean = false,
    val subtitles: Boolean = false,
    val announcements: Boolean = false,
    val missedSummary: Boolean = true,
    val leaderboard: Boolean = true,
    val giftSfx: Boolean = true,
    val notesWhisper: Boolean = false,
    val scriptFollow: Boolean = true,
    val verseFromScript: Boolean = false,
    val verseFromVoice: Boolean = false,
    val prayerList: Boolean = false,
    val backgroundReplace: Boolean = false,
    val lyrics: Boolean = false,
    val offering: Boolean = false,
    val saweria: Boolean = false,
    val intro: Boolean = false,
    val outro: Boolean = false,
    val cameraAssistant: Boolean = true,
    val transcript: Boolean = false,
    val voiceCommands: Boolean = true,
    val audioOnly: Boolean = false,
    val autoLight: Boolean = false,
    val shareLink: Boolean = false,
    val faq: Boolean = true,
    val momentClips: Boolean = true,
    val doNotDisturb: Boolean = true,
    val keepScreenOn: Boolean = true,
    val music: Boolean = false,
    val guests: Boolean = false,
    val progressStats: Boolean = true,
    // ---------------- tambahan versi 2 (semua opsional)
    val giveaway: Boolean = false,
    val quiz: Boolean = false,
    val songRequests: Boolean = false,
    val quietWarning: Boolean = false,
    val drinkReminder: Boolean = false,
    val batteryWarning: Boolean = true,
    val voiceReply: Boolean = false,
    val pauseButton: Boolean = true,
    val rundown: Boolean = false,
    val practiceReport: Boolean = false,
    val themes: Boolean = false,
    val nameplate: Boolean = false,
    val cues: Boolean = true,
    val touchLock: Boolean = true,
    /** Suara musik latar mengecil otomatis saat host berbicara. */
    val musicDucking: Boolean = false,
    val guestClips: Boolean = true,
)

@Serializable
data class TtsConfig(
    val enginePackage: String = "com.google.android.tts",
    val language: String = "id-ID",
    val voiceName: String = "",
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val whisperRate: Float = 1.0f,
    val scriptRate: Float = 0.7f,
    val monitorPublicTts: Boolean = true,
)

@Serializable
data class Templates(
    val gift: String = "Terima kasih {nama} untuk {jumlah} {hadiah}, senilai {koin} koin!",
    val follow: String = "Terima kasih {nama} sudah follow!",
    val subscribe: String = "Terima kasih {nama} sudah subscribe!",
    val share: String = "Terima kasih {nama} sudah membagikan live ini!",
    val superChat: String = "Terima kasih {nama} untuk Super Chat {jumlah}!",
    val member: String = "Selamat bergabung jadi member, {nama}!",
    val saweria: String = "Terima kasih {nama} untuk dukungan {jumlah} lewat Saweria! {pesan}",
    val loyal: String = "Halo {nama}, sudah {jumlah} kali datang. Terima kasih setia menemani!",
    val newViewer: String = "Selamat datang, {nama}, makasih sudah mampir!",
    val comment: String = "{nama} bilang: {pesan}",
    val question: String = "Pertanyaan dari {nama}: {pesan}",
    val targetReached: String = "Hore! Target {target} sudah tercapai!",
)

@Serializable
data class Targets(
    val likes: Int = 1000,
    val coins: Int = 500,
    val viewers: Int = 50,
    val combined: Boolean = true,
)

@Serializable
data class Announcement(
    val text: String,
    val everyMinutes: Int = 15,
    val mode: String = "both", // tts | screen | both
    val enabled: Boolean = true,
)

@Serializable
data class FaqItem(
    val keywords: List<String>,
    val answer: String,
    val imagePath: String = "",
    val mode: String = "both", // tts | image | both
)

@Serializable
data class LiveProfile(
    val id: String,
    val name: String,
    val features: FeatureToggles,
    val noteId: String = "",
    val backgroundPath: String = "",
    val introTemplate: String = "",
)

@Serializable
data class NoteScript(
    val id: String,
    val title: String,
    /** Naskah lengkap. Baris diawali "# " = judul poin. Ayat ditulis "[Yohanes 3:16] isi ayat". */
    val content: String,
)

@Serializable
data class LyricSong(val id: String, val title: String, val lines: String)

@Serializable
data class OfferingConfig(
    val imagePath: String = "",
    val text: String = "Persembahan / donasi",
    val durationSec: Int = 30,
    val size: String = "medium", // small | medium | full
    val position: String = "top_right",
)

@Serializable
data class SaweriaConfig(
    val pageLink: String = "",
    val showQrOnCommand: Boolean = true,
)

@Serializable
data class QrStyle(
    val title: String = "Dukung lewat Saweria",
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    val fgColor: Int = 0xFF000000.toInt(),
    val textColor: Int = 0xFF111111.toInt(),
    val size: String = "medium",
    val position: String = "bottom_right",
    val durationSec: Int = 300,
)

@Serializable
data class CardStyle(
    val bgColor: Int = 0xFF0B1022.toInt(),
    val bgColor2: Int = 0xFF2A1250.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFF22E5FF.toInt(),
    val fontScale: Float = 1.0f,
    val bgImage: String = "",
)

@Serializable
data class IntroConfig(
    val introTemplate: String = "ibadah",
    val outroTemplate: String = "ibadah",
    val title: String = "Live segera dimulai",
    val subtitle: String = "",
    val outroTitle: String = "Terima kasih sudah menonton",
    val outroSubtitle: String = "Sampai jumpa di live berikutnya",
    val durationSec: Int = 10,
    val countdown: Boolean = true,
    val musicId: String = "bgm_cinematic_pad",
)

@Serializable
data class TransitionConfig(
    val style: String = "fade",
    val durationMs: Int = 500,
)

@Serializable
data class TimerConfig(
    val reminderMinutes: Int = 30,
    val limitMinutes: Int = 0,
    val warnBeforeMinutes: Int = 10,
)

@Serializable
data class TickerConfig(
    val text: String = "Selamat datang di live Stream By Natan",
    val speed: Float = 1.0f,
)

@Serializable
data class SubtitleConfig(
    val textSize: Float = 1.0f,
    val showOnTop: Boolean = false,
)

@Serializable
data class TranslateConfig(
    val targetLanguage: String = "id",
    val readTranslated: Boolean = true,
)

@Serializable
data class MicFx(
    val noiseSuppressor: Boolean = true,
    val strongNoiseGate: Boolean = false,
    val aiNoiseSuppression: Boolean = false,
    val reverb: Boolean = false,
    val reverbAmount: Float = 0.25f,
    val volumeBalancer: Boolean = true,
    val micVolume: Float = 1.0f,
    val internalVolume: Float = 1.0f,
    /** Pengubah suara: none | robot | high | deep | chipmunk | giant | echo | phone */
    val voiceChanger: String = "none",
)

@Serializable
data class MusicConfig(
    val trackId: String = "",
    val customPath: String = "",
    val volume: Float = 0.25f,
    val loop: Boolean = true,
    /** Tingkat musik saat host berbicara (0..1 dari volume normal). Musik mengecil, tidak hilang. */
    val duckLevel: Float = 0.35f,
    val customName: String = "",
)

@Serializable
data class GiftSfxConfig(
    val smallSound: String = "sfx_ting",
    val bigSound: String = "sfx_applause_synth",
    val followSound: String = "sfx_notif",
    val shareSound: String = "sfx_pop",
    val bigThresholdCoins: Int = 100,
    val volume: Float = 0.8f,
)

@Serializable
data class CustomSound(
    val id: String,
    val name: String,
    val uri: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
)

@Serializable
data class BackgroundConfig(
    val type: String = "blur", // blur | image | video
    val path: String = "",
)

@Serializable
data class ScheduleItem(
    val id: String,
    val title: String,
    val epochMillis: Long,
    val remindMinutesBefore: Int = 15,
    val repeatWeekly: Boolean = false,
)

@Serializable
data class GuestConfig(
    val webBaseUrl: String = "",
    val turnUrl: String = "",
    val turnUser: String = "",
    val turnPass: String = "",
)
