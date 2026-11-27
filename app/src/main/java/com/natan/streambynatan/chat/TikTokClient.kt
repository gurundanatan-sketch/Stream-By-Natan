package com.natan.streambynatan.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Komentar & kejadian TikTok LIVE TANPA API key.
 *
 * Cara kerja: aplikasi membuka halaman https://www.tiktok.com/@username/live di browser tersembunyi
 * (WebView), lalu skrip tiktok_reader.js membaca komentar, hadiah, like, ikuti, bagikan, bergabung
 * dan jumlah penonton dari tampilan halaman. Suara & video halaman dibisukan supaya tidak ikut ke siaran.
 *
 * Skrip terbaru diambil dari GitHub (supaya bisa diperbaiki tanpa pasang ulang aplikasi),
 * kalau gagal memakai salinan bawaan di assets.
 */
class TikTokClient(private val app: Context, private val http: OkHttpClient) {
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var attached = false
    @Volatile private var wanted = false
    private var username = ""
    private var script = ""
    @Volatile private var lastEventAt = 0L
    @Volatile private var pageState = ""
    private var reloads = 0

    val liveUrl: String get() = "https://www.tiktok.com/@$username/live"

    /** Dipanggil saat live mulai. Parameter kedua tidak dipakai lagi (dulu API key Euler). */
    fun connect(uniqueId: String, @Suppress("UNUSED_PARAMETER") unused: String = "") {
        val u = uniqueId.trim().removePrefix("@").substringBefore("/")
        if (u.isBlank()) {
            LiveBus.stats { it.copy(tiktokChat = "Isi username TikTok di pengaturan") }
            return
        }
        username = u; wanted = true; reloads = 0
        LiveBus.stats { it.copy(tiktokChat = "Membuka halaman live TikTok…") }
        Thread {
            script = loadScript()
            main.post { if (wanted) openWeb() }
        }.start()
    }

    fun disconnect() {
        wanted = false
        main.removeCallbacksAndMessages(null)
        main.post { destroyWeb() }
        LiveBus.stats { it.copy(tiktokChat = "Mati") }
    }

    /** Muat ulang halaman (tombol "Sambung ulang komentar TikTok"). */
    fun reload() { main.post { web?.reload() } }

    private fun loadScript(): String {
        val remote = runCatching {
            val url = "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/app/src/main/assets/tiktok_reader.js"
            http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string().orEmpty() else ""
                }
        }.getOrDefault("")
        if (remote.contains("__sbnReady")) return remote
        return runCatching { app.assets.open("tiktok_reader.js").bufferedReader().use { it.readText() } }.getOrDefault("")
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun openWeb() {
        destroyWeb()
        val w = WebView(app)
        web = w
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true   // video TikTok tidak berbunyi otomatis
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = DESKTOP_UA
            blockNetworkImage = false
        }
        w.setInitialScale(1)
        w.addJavascriptInterface(Bridge(), "SBN")
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme.orEmpty()
                return s != "http" && s != "https"   // jangan buka aplikasi TikTok / intent lain
            }
            override fun onPageFinished(view: WebView, url: String) { inject() }
        }
        attach(w)
        w.onResume(); w.resumeTimers()
        w.loadUrl(liveUrl)
        lastEventAt = System.currentTimeMillis()
        main.postDelayed(watchdog, 15_000)
    }

    /** WebView ditempel ke jendela tak terlihat (kalau izin tampil di atas aplikasi ada) supaya halaman terus berjalan. */
    private fun attach(w: WebView) {
        val wm = app.getSystemService(WindowManager::class.java)
        if (Settings.canDrawOverlays(app)) {
            runCatching {
                val lp = WindowManager.LayoutParams(
                    1280, 900,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply { alpha = 0f; x = -4000; y = -4000 }
                wm.addView(w, lp)
                attached = true
            }
        }
        if (!attached) {
            // tanpa jendela: ukur manual supaya halaman tetap punya lebar desktop
            w.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            w.layout(0, 0, 1280, 900)
        }
    }

    private fun destroyWeb() {
        val w = web ?: return
        web = null
        if (attached) runCatching { app.getSystemService(WindowManager::class.java).removeView(w) }
        attached = false
        runCatching { w.stopLoading(); w.loadUrl("about:blank"); w.removeJavascriptInterface("SBN"); w.destroy() }
    }

    private fun inject() {
        if (script.isNotBlank()) web?.evaluateJavascript(script, null)
    }

    /** Pemeriksa: kalau lama tidak ada kejadian atau skrip hilang (halaman berganti), suntik ulang / muat ulang. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!wanted) return
            val w = web ?: return
            w.evaluateJavascript("(function(){return window.__sbnReady===true})()") { r ->
                if (r != "true") inject()
            }
            val idle = System.currentTimeMillis() - lastEventAt
            if (pageState != "offline" && pageState != "captcha" && idle > 4 * 60_000 && reloads < 20) {
                reloads++; lastEventAt = System.currentTimeMillis()
                LiveBus.stats { it.copy(tiktokChat = "Memuat ulang halaman TikTok…") }
                w.reload()
            }
            if (pageState == "offline" && idle > 60_000 && reloads < 60) {
                // akun belum live: coba lagi tiap menit
                reloads++; lastEventAt = System.currentTimeMillis(); w.reload()
            }
            main.postDelayed(this, 15_000)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(json: String) {
            runCatching { handle(JSONObject(json)) }
        }
    }

    private fun handle(o: JSONObject) {
        val t = o.optString("t")
        if (t != "status") lastEventAt = System.currentTimeMillis()
        val id = o.optString("id")
        val name = o.optString("name").ifBlank { id }
        val e: LiveEvent? = when (t) {
            "status" -> {
                pageState = o.optString("v")
                val msg = when (pageState) {
                    "ready" -> "Membaca halaman live…"
                    "chat" -> "Tersambung (halaman live)"
                    "offline" -> "Akun belum live di TikTok, menunggu…"
                    "captcha" -> "TikTok minta verifikasi. Buka Pengaturan, Stream key, lalu Buka halaman TikTok"
                    else -> "Memuat halaman TikTok…"
                }
                LiveBus.stats { it.copy(tiktokChat = msg) }
                null
            }
            "comment" -> LiveEvent.Comment(Platform.TIKTOK, id, name, o.optString("text"))
            "gift" -> {
                val g = o.optString("gift").ifBlank { "hadiah" }
                LiveEvent.Gift(Platform.TIKTOK, id, name, g, o.optInt("count", 1).coerceAtLeast(1), coinsOf(g))
            }
            "like" -> LiveEvent.Like(Platform.TIKTOK, id, name, o.optInt("count", 1), 0)
            "likes" -> {
                val total = o.optLong("total")
                LiveBus.stats { it.copy(likesTikTok = maxOf(it.likesTikTok, total)) }
                null
            }
            "follow" -> LiveEvent.Follow(Platform.TIKTOK, id, name)
            "share" -> LiveEvent.Share(Platform.TIKTOK, id, name)
            "join" -> LiveEvent.Join(Platform.TIKTOK, id, name)
            "viewers" -> LiveEvent.Viewers(Platform.TIKTOK, o.optInt("v"))
            else -> null
        }
        e?.let { LiveBus.emit(it) }
    }

    companion object {
        /** Perkiraan koin hadiah populer (halaman web tidak menampilkan nilai koin). Lainnya dihitung 1. */
        private val GIFT_COINS = mapOf(
            "rose" to 1, "mawar" to 1, "tiktok" to 1, "gg" to 1, "ice cream" to 1, "es krim" to 1, "heart me" to 1,
            "finger heart" to 5, "hati jari" to 5, "perfume" to 20, "parfum" to 20, "doughnut" to 30, "donat" to 30,
            "paper crane" to 99, "bangau kertas" to 99, "hand hearts" to 100, "confetti" to 100, "konfeti" to 100,
            "corgi" to 299, "money gun" to 500, "pistol uang" to 500, "swan" to 699, "angsa" to 699,
            "galaxy" to 1000, "galaksi" to 1000, "whale diving" to 2150, "paus" to 2150, "motorcycle" to 2988, "motor" to 2988,
            "private jet" to 4888, "jet pribadi" to 4888, "lion" to 29999, "singa" to 29999, "universe" to 34999, "alam semesta" to 34999,
        )
        fun coinsOf(gift: String): Int {
            val g = gift.lowercase().trim()
            GIFT_COINS[g]?.let { return it }
            return GIFT_COINS.entries.firstOrNull { g.contains(it.key) }?.value ?: 1
        }
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
