package com.natan.streambynatan.chat

import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.Platform
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Komentar & kejadian TikTok LIVE lewat Euler Stream (WebSocket, JSON).
 * Isi: komentar, gift (jumlah koin & streak), like, follow, share, join, jumlah penonton.
 */
class TikTokClient(private val http: OkHttpClient) {
    private var ws: WebSocket? = null
    @Volatile private var wanted = false
    private var username = ""
    private var apiKey = ""
    private var retries = 0

    fun connect(uniqueId: String, key: String) {
        if (uniqueId.isBlank() || key.isBlank()) {
            LiveBus.stats { it.copy(tiktokChat = "Isi username TikTok & API key Euler di pengaturan") }
            return
        }
        username = uniqueId.removePrefix("@"); apiKey = key; wanted = true; retries = 0
        open()
    }

    private fun open() {
        val url = "https://ws.eulerstream.com".toHttpUrl().newBuilder()
            .addQueryParameter("uniqueId", username)
            .addQueryParameter("apiKey", apiKey)
            .addQueryParameter("features.bundleEvents", "true")
            .build().toString().replaceFirst("https://", "wss://")
        LiveBus.stats { it.copy(tiktokChat = "Menyambung…") }
        ws = http.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()
            .newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    retries = 0
                    LiveBus.stats { it.copy(tiktokChat = "Tersambung") }
                }
                override fun onMessage(webSocket: WebSocket, text: String) { runCatching { parse(text) } }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed(code, reason) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed(-1, t.message ?: "gagal") }
            })
    }

    private fun closed(code: Int, reason: String) {
        val msg = when (code) {
            4404 -> "Akun belum live di TikTok"
            4401 -> "API key Euler salah"
            4429 -> "Terlalu banyak sambungan (batas paket)"
            else -> "Terputus: $reason"
        }
        LiveBus.stats { it.copy(tiktokChat = msg) }
        if (wanted && code != 4401 && retries < 30) {
            retries++
            Thread { Thread.sleep(minOf(30_000L, 3_000L * retries)); if (wanted) open() }.start()
        }
    }

    fun disconnect() {
        wanted = false
        ws?.close(1000, "selesai")
        ws = null
        LiveBus.stats { it.copy(tiktokChat = "Mati") }
    }

    private fun parse(text: String) {
        val root = JSONObject(text)
        val msgs: JSONArray = root.optJSONArray("messages") ?: JSONArray().put(root)
        for (i in 0 until msgs.length()) handle(msgs.getJSONObject(i))
    }

    private fun userOf(d: JSONObject): Pair<String, String> {
        val u = d.optJSONObject("user") ?: JSONObject()
        val id = u.optString("uniqueId").ifBlank { u.optString("userId") }
        val name = u.optString("nickname").ifBlank { id }
        return id to name
    }

    private fun handle(m: JSONObject) {
        val type = m.optString("type")
        val d = m.optJSONObject("data") ?: return
        val (id, name) = userOf(d)
        val e: LiveEvent? = when (type) {
            "WebcastChatMessage" -> LiveEvent.Comment(Platform.TIKTOK, id, name, d.optString("comment"))
            "WebcastGiftMessage" -> {
                val gift = d.optJSONObject("giftDetails") ?: JSONObject()
                val combo = gift.optBoolean("combo", false) || gift.optInt("giftType") == 1
                val repeatEnd = d.optInt("repeatEnd", 0) == 1
                // gift beruntun (streak) baru dihitung saat streak selesai
                if (combo && !repeatEnd) null
                else LiveEvent.Gift(
                    Platform.TIKTOK, id, name,
                    gift.optString("giftName").ifBlank { "hadiah" },
                    d.optInt("repeatCount", 1).coerceAtLeast(1),
                    gift.optInt("diamondCount", 1),
                )
            }
            "WebcastLikeMessage" -> LiveEvent.Like(Platform.TIKTOK, id, name, d.optInt("likeCount", 1), d.optLong("totalLikeCount", 0))
            "WebcastSocialMessage" -> {
                val pattern = d.optJSONObject("common")?.optJSONObject("displayText")?.optString("defaultPattern").orEmpty().lowercase()
                val action = d.optString("action").lowercase()
                when {
                    pattern.contains("share") || action.contains("share") || d.optString("shareType").isNotBlank() && d.optInt("shareCount") > 0 ->
                        LiveEvent.Share(Platform.TIKTOK, id, name)
                    pattern.contains("follow") || action.contains("follow") || action == "1" ->
                        LiveEvent.Follow(Platform.TIKTOK, id, name)
                    else -> null
                }
            }
            "WebcastMemberMessage", "SyntheticJoinMessage" -> LiveEvent.Join(Platform.TIKTOK, id, name)
            "WebcastRoomUserSeqMessage" -> LiveEvent.Viewers(Platform.TIKTOK, d.optInt("viewerCount", d.optInt("totalUser", 0)))
            else -> null
        }
        e?.let { LiveBus.emit(it) }
    }
}
