package com.natan.streambynatan.chat

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Izin resmi Google ("Hubungkan YouTube"), tanpa kata sandi. Token diminta ulang otomatis. */
object YouTubeAuth {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/youtube"),
        Scope("https://www.googleapis.com/auth/youtube.upload"),
    )

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    /** Dari layar aplikasi: kalau perlu izin, kembalikan PendingIntent untuk dibuka. */
    suspend fun authorize(activity: Activity): Pair<String?, PendingIntent?> {
        val r: AuthorizationResult = Identity.getAuthorizationClient(activity).authorize(request()).await()
        return if (r.hasResolution()) null to r.pendingIntent else r.accessToken to null
    }

    fun fromIntent(context: Context, data: android.content.Intent?): String? = runCatching {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()

    /** Diam-diam (tanpa layar). Null kalau belum pernah diizinkan. */
    suspend fun silentToken(context: Context): String? = runCatching {
        val r = Identity.getAuthorizationClient(context).authorize(request()).await()
        if (r.hasResolution()) null else r.accessToken
    }.getOrNull()
}

data class BroadcastSettings(
    val title: String,
    val description: String,
    val privacy: String = "public", // public | unlisted | private
    val madeForKids: Boolean = false,
    val scheduledStart: Long = 0,
    val categoryId: String = "22",
    val tags: List<String> = emptyList(),
    val latency: String = "low", // normal | low | ultraLow
    val enableDvr: Boolean = true,
    val thumbnail: ByteArray? = null,
    val thumbnailMime: String = "image/jpeg",
)

class YouTubeApi(private val context: Context, private val http: OkHttpClient) {
    private val base = "https://www.googleapis.com/youtube/v3"
    private val json = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(method: String, url: String, body: JSONObject? = null, apiKey: String = ""): JSONObject =
        withContext(Dispatchers.IO) {
            val token = if (apiKey.isBlank()) YouTubeAuth.silentToken(context) else null
            val full = if (apiKey.isNotBlank()) url + (if (url.contains('?')) "&" else "?") + "key=" + apiKey else url
            val b = Request.Builder().url(full)
            token?.let { b.header("Authorization", "Bearer $it") }
            when (method) {
                "GET" -> b.get()
                else -> b.method(method, (body ?: JSONObject()).toString().toRequestBody(json))
            }
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IllegalStateException("YouTube ${r.code}: " + text.take(300))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /**
     * Membuat siaran YouTube lengkap dari aplikasi: judul, deskripsi, thumbnail, audiens, visibilitas,
     * jadwal, kategori, tag, latensi. Stream key diambil otomatis.
     * Mengembalikan (url server rtmp, stream key, id video).
     */
    suspend fun createBroadcast(s: BroadcastSettings): Triple<String, String, String> {
        val start = if (s.scheduledStart > System.currentTimeMillis()) s.scheduledStart else System.currentTimeMillis() + 30_000
        val bc = call("POST", "$base/liveBroadcasts?part=snippet,status,contentDetails", JSONObject().apply {
            put("snippet", JSONObject().put("title", s.title).put("description", s.description).put("scheduledStartTime", iso(start)))
            put("status", JSONObject().put("privacyStatus", s.privacy).put("selfDeclaredMadeForKids", s.madeForKids))
            put("contentDetails", JSONObject()
                .put("enableAutoStart", true).put("enableAutoStop", true)
                .put("enableDvr", s.enableDvr).put("latencyPreference", s.latency)
                .put("monitorStream", JSONObject().put("enableMonitorStream", false)))
        })
        val broadcastId = bc.getString("id")
        val st = call("POST", "$base/liveStreams?part=snippet,cdn,contentDetails", JSONObject().apply {
            put("snippet", JSONObject().put("title", "Stream By Natan"))
            put("cdn", JSONObject().put("ingestionType", "rtmp").put("resolution", "variable").put("frameRate", "variable"))
            put("contentDetails", JSONObject().put("isReusable", true))
        })
        val ingestion = st.getJSONObject("cdn").getJSONObject("ingestionInfo")
        call("POST", "$base/liveBroadcasts/bind?id=$broadcastId&part=id,contentDetails&streamId=${st.getString("id")}")
        // kategori & tag lewat videos.update
        runCatching {
            call("PUT", "$base/videos?part=snippet", JSONObject().put("id", broadcastId).put("snippet", JSONObject()
                .put("title", s.title).put("description", s.description).put("categoryId", s.categoryId)
                .put("tags", JSONArray(s.tags))))
        }
        s.thumbnail?.let { setThumbnail(broadcastId, it, s.thumbnailMime) }
        return Triple(ingestion.getString("ingestionAddress"), ingestion.getString("streamName"), broadcastId)
    }

    /** Thumbnail butuh kanal yang sudah verifikasi nomor HP. */
    suspend fun setThumbnail(videoId: String, bytes: ByteArray, mime: String) = withContext(Dispatchers.IO) {
        val token = YouTubeAuth.silentToken(context) ?: throw IllegalStateException("Belum terhubung ke YouTube")
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=$videoId&uploadType=media")
            .header("Authorization", "Bearer $token")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw IllegalStateException("Thumbnail gagal: ${it.code} ${it.body?.string()?.take(200)}") }
    }

    suspend fun activeChatId(videoId: String, apiKey: String): Pair<String, String>? {
        if (videoId.isNotBlank()) {
            val v = call("GET", "$base/videos?part=liveStreamingDetails&id=$videoId", apiKey = apiKey)
            val chat = v.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("liveStreamingDetails")?.optString("activeLiveChatId")
            if (!chat.isNullOrBlank()) return chat to videoId
        }
        if (YouTubeAuth.silentToken(context) != null) {
            val b = call("GET", "$base/liveBroadcasts?part=snippet&broadcastStatus=active&broadcastType=all")
            val item = b.optJSONArray("items")?.optJSONObject(0) ?: return null
            return item.getJSONObject("snippet").optString("liveChatId") to item.optString("id")
        }
        return null
    }

    suspend fun chatPage(chatId: String, pageToken: String?, apiKey: String): JSONObject =
        call("GET", "$base/liveChat/messages?liveChatId=$chatId&part=snippet,authorDetails&maxResults=200" +
            (pageToken?.let { "&pageToken=$it" } ?: ""), apiKey = apiKey)

    suspend fun videoStats(videoId: String, apiKey: String): Pair<Int, Long> {
        val v = call("GET", "$base/videos?part=liveStreamingDetails,statistics&id=$videoId", apiKey = apiKey)
        val item = v.optJSONArray("items")?.optJSONObject(0) ?: return 0 to 0L
        val viewers = item.optJSONObject("liveStreamingDetails")?.optString("concurrentViewers")?.toIntOrNull() ?: 0
        val likes = item.optJSONObject("statistics")?.optString("likeCount")?.toLongOrNull() ?: 0L
        return viewers to likes
    }

    suspend fun recentSubscribers(): List<Pair<String, String>> = runCatching {
        val r = call("GET", "$base/subscriptions?part=subscriberSnippet&myRecentSubscribers=true&maxResults=20")
        val items = r.optJSONArray("items") ?: JSONArray()
        (0 until items.length()).map {
            val sn = items.getJSONObject(it).getJSONObject("subscriberSnippet")
            sn.optString("channelId") to sn.optString("title")
        }
    }.getOrDefault(emptyList())
}

/** Membaca komentar, Super Chat, member, subscriber baru, penonton & like dari YouTube. */
class YouTubeChatClient(private val api: YouTubeApi) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    fun start(videoId: String, apiKey: String) {
        stop()
        job = scope.launch {
            LiveBus.stats { it.copy(youtubeChat = "Mencari siaran aktif…") }
            var found: Pair<String, String>? = null
            for (attempt in 0 until 40) {
                found = runCatching { api.activeChatId(videoId, apiKey) }.getOrNull()
                if (found != null) break
                delay(15_000)
            }
            val (chatId, vid) = found ?: run { LiveBus.stats { it.copy(youtubeChat = "Siaran aktif tidak ditemukan") }; return@launch }
            LiveBus.stats { it.copy(youtubeChat = "Tersambung") }
            launch { pollStats(vid, apiKey) }
            launch { pollSubscribers() }
            var page: String? = null
            var first = true
            while (isActive) {
                val r = runCatching { api.chatPage(chatId, page, apiKey) }
                r.onFailure { LiveBus.stats { s -> s.copy(youtubeChat = "Galat: ${it.message?.take(60)}") }; delay(20_000) }
                val js = r.getOrNull() ?: continue
                page = js.optString("nextPageToken").ifBlank { page }
                if (!first) handle(js.optJSONArray("items") ?: JSONArray())
                first = false // komentar lama sebelum aplikasi tersambung tidak dibacakan
                delay(js.optLong("pollingIntervalMillis", 5000).coerceAtLeast(3000))
            }
        }
    }

    private fun handle(items: JSONArray) {
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val sn = item.optJSONObject("snippet") ?: continue
            val au = item.optJSONObject("authorDetails") ?: JSONObject()
            val id = au.optString("channelId"); val name = au.optString("displayName")
            val e: LiveEvent? = when (sn.optString("type")) {
                "textMessageEvent" -> LiveEvent.Comment(Platform.YOUTUBE, id, name, sn.optString("displayMessage"))
                "superChatEvent" -> sn.optJSONObject("superChatDetails")?.let {
                    LiveEvent.SuperChat(Platform.YOUTUBE, id, name, it.optString("amountDisplayString"), it.optString("userComment"), it.optLong("amountMicros"))
                }
                "superStickerEvent" -> sn.optJSONObject("superStickerDetails")?.let {
                    LiveEvent.SuperChat(Platform.YOUTUBE, id, name, it.optString("amountDisplayString"), "", it.optLong("amountMicros"))
                }
                "newSponsorEvent", "memberMilestoneChatEvent" -> LiveEvent.Member(Platform.YOUTUBE, id, name)
                else -> null
            }
            e?.let { LiveBus.emit(it) }
        }
    }

    private suspend fun pollStats(videoId: String, apiKey: String) {
        var lastLikes = -1L
        while (true) {
            runCatching { api.videoStats(videoId, apiKey) }.getOrNull()?.let { (v, likes) ->
                LiveBus.emit(LiveEvent.Viewers(Platform.YOUTUBE, v))
                if (lastLikes >= 0 && likes > lastLikes) LiveBus.emit(LiveEvent.Like(Platform.YOUTUBE, "", "", (likes - lastLikes).toInt(), likes))
                lastLikes = likes
            }
            delay(30_000)
        }
    }

    private suspend fun pollSubscribers() {
        val seen = HashSet<String>()
        api.recentSubscribers().forEach { seen.add(it.first) }
        while (true) {
            delay(60_000)
            for ((id, name) in api.recentSubscribers()) if (seen.add(id)) LiveBus.emit(LiveEvent.Subscribe(Platform.YOUTUBE, id, name))
        }
    }

    fun stop() { job?.cancel(); job = null; LiveBus.stats { it.copy(youtubeChat = "Mati") } }
}
