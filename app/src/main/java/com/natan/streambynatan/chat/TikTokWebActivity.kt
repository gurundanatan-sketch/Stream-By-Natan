package com.natan.streambynatan.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import com.natan.streambynatan.core.Graph

/**
 * Halaman TikTok yang terlihat. Dipakai kalau TikTok minta login atau verifikasi (geser puzzle).
 * Cookie-nya sama dengan pembaca tersembunyi, jadi setelah selesai di sini pembaca bisa jalan lagi.
 */
class TikTokWebActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = Graph.settings.current.tiktokUsername.trim().removePrefix("@")
        web = WebView(this)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.userAgentString = TikTokClient.DESKTOP_UA
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme.orEmpty()
                return s != "http" && s != "https"
            }
        }
        val done = Button(this).apply {
            text = "Selesai, kembali ke aplikasi"
            contentDescription = "Selesai, kembali ke aplikasi"
            setOnClickListener {
                CookieManager.getInstance().flush()
                Graph.tiktok.reload()
                finish()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        web.loadUrl(if (user.isBlank()) "https://www.tiktok.com/login" else "https://www.tiktok.com/@$user/live")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else { CookieManager.getInstance().flush(); super.onBackPressed() }
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
