package com.natan.streambynatan.live

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Platform(val label: String) { TIKTOK("TikTok"), YOUTUBE("YouTube"), SAWERIA("Saweria"), APP("Aplikasi") }

/** Semua kejadian dari penonton, dari platform mana pun. */
sealed class LiveEvent {
    abstract val platform: Platform
    abstract val userId: String
    abstract val name: String
    val time: Long = System.currentTimeMillis()

    data class Comment(override val platform: Platform, override val userId: String, override val name: String, val text: String) : LiveEvent()
    data class Gift(override val platform: Platform, override val userId: String, override val name: String, val giftName: String, val count: Int, val coinsEach: Int) : LiveEvent() {
        val totalCoins: Int get() = count * coinsEach
    }
    data class Like(override val platform: Platform, override val userId: String, override val name: String, val count: Int, val total: Long) : LiveEvent()
    data class Follow(override val platform: Platform, override val userId: String, override val name: String) : LiveEvent()
    data class Subscribe(override val platform: Platform, override val userId: String, override val name: String) : LiveEvent()
    data class Share(override val platform: Platform, override val userId: String, override val name: String) : LiveEvent()
    data class Join(override val platform: Platform, override val userId: String, override val name: String) : LiveEvent()
    data class Viewers(override val platform: Platform, val count: Int) : LiveEvent() {
        override val userId: String = ""
        override val name: String = ""
    }
    data class SuperChat(override val platform: Platform, override val userId: String, override val name: String, val amountText: String, val text: String, val micros: Long) : LiveEvent()
    data class Member(override val platform: Platform, override val userId: String, override val name: String) : LiveEvent()
    data class Donation(override val platform: Platform, override val userId: String, override val name: String, val amount: Long, val amountText: String, val message: String) : LiveEvent()
}

/** Angka-angka live yang tampil di layar dan dipakai untuk target & ringkasan. */
data class LiveStats(
    val isLive: Boolean = false,
    val practice: Boolean = false,
    val startedAt: Long = 0,
    val youtubeState: String = "Mati",
    val tiktokState: String = "Mati",
    val tiktokChat: String = "Belum tersambung",
    val youtubeChat: String = "Belum tersambung",
    val bitrateKbps: Long = 0,
    val viewersTikTok: Int = 0,
    val viewersYouTube: Int = 0,
    val peakViewers: Int = 0,
    val likesTikTok: Long = 0,
    val likesYouTube: Long = 0,
    val coins: Long = 0,
    val donations: Long = 0,
    val comments: Int = 0,
    val questions: Int = 0,
    val follows: Int = 0,
    val shares: Int = 0,
    val subscribes: Int = 0,
    val newViewers: Int = 0,
    val micOn: Boolean = true,
    val shareAudioOn: Boolean = true,
    val recording: Boolean = false,
    val subtitle: String = "",
    val network: String = "Baik",
    val screenSharing: Boolean = false,
    val activeCamera: String = "depan",
) {
    val viewers: Int get() = viewersTikTok + viewersYouTube
    val likes: Long get() = likesTikTok + likesYouTube
}

object LiveBus {
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<LiveEvent> = _events
    fun emit(e: LiveEvent) { _events.tryEmit(e) }

    private val _stats = MutableStateFlow(LiveStats())
    val stats: StateFlow<LiveStats> = _stats
    fun stats(block: (LiveStats) -> LiveStats) = _stats.update(block)

    /** Log teks untuk layar live (bisa dibaca pembaca layar). */
    private val _feed = MutableStateFlow<List<String>>(emptyList())
    val feed: StateFlow<List<String>> = _feed
    fun feed(line: String) = _feed.update { (it + line).takeLast(200) }
    fun clearFeed() { _feed.value = emptyList() }

    /** Pengumuman untuk pembaca layar (Live Region). */
    private val _announce = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val announce: SharedFlow<String> = _announce
    fun announce(text: String) { _announce.tryEmit(text) }
}
