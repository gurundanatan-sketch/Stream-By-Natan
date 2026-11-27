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
)

@Serializable
data class MusicConfig(
    val trackId: String = "",
    val customPath: String = "",
    val volume: Float = 0.25f,
    val loop: Boolean = true,
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
