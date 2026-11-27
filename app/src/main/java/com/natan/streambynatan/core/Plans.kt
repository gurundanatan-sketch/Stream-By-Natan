package com.natan.streambynatan.core

/**
 * Paket langganan. SEKARANG SEMUA GRATIS (masa promosi).
 * Setelah rilis di Play Store, fitur premium cukup ditandai di sini lalu dicek dengan Plans.allows(...).
 * Peran TAMU selalu gratis.
 */
enum class Feature { MULTISTREAM, GUESTS_HOST, GUEST_JOIN, CINEMATIC, AI_NOISE, RECORDING_CLIPS, TIKTOK_COMMENTS, YOUTUBE_TOOLS }

object Plans {
    /** true selama masa promosi: semua fitur terbuka untuk semua orang. */
    const val FREE_PROMO = true

    /** Fitur yang nanti masuk premium (belum dipakai selama FREE_PROMO). */
    private val premiumLater = setOf(Feature.CINEMATIC, Feature.AI_NOISE, Feature.RECORDING_CLIPS)

    /** Tempat cek status premium dari Play Billing nanti. */
    @Volatile var isPremium: Boolean = false

    fun allows(f: Feature): Boolean = when {
        f == Feature.GUEST_JOIN -> true          // tamu selalu gratis
        FREE_PROMO -> true
        f in premiumLater -> isPremium
        else -> true
    }
}
