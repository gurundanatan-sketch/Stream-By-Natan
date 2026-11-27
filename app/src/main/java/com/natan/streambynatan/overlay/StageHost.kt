package com.natan.streambynatan.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.Platform

/**
 * Wadah grafis siaran: [StageView] + halaman overlay Saweria (link overlay rahasia milikmu).
 * Notifikasi & suara donasi Saweria tampil persis seperti di TikTok Live Studio, tanpa server tambahan.
 */
@SuppressLint("ViewConstructor", "SetJavaScriptEnabled")
class StageHost(context: Context) : FrameLayout(context) {
    val stage = StageView(context)
    private var web: WebView? = null
    private var lastDonationText = ""

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setSaweria(overlayUrl: String) {
        if (overlayUrl.isBlank()) { removeSaweria(); return }
        val w = web ?: WebView(context).also { wv ->
            wv.setBackgroundColor(Color.TRANSPARENT)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.mediaPlaybackRequiresUserGesture = false
            wv.webChromeClient = WebChromeClient()
            wv.addJavascriptInterface(Bridge(), "SBN")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript(OBSERVER_JS, null) }
            }
            addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            web = wv
        }
        if (w.url != overlayUrl) w.loadUrl(overlayUrl)
    }

    fun removeSaweria() {
        web?.let { removeView(it); it.destroy() }
        web = null
    }

    /** Membaca teks notifikasi di halaman overlay, lalu diubah jadi kejadian donasi. */
    private inner class Bridge {
        @JavascriptInterface
        fun onText(text: String) {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.length < 4 || clean == lastDonationText) return
            lastDonationText = clean
            val amountMatch = Regex("Rp\\s?([0-9.,]+)", RegexOption.IGNORE_CASE).find(clean)
            val amountText = amountMatch?.value ?: ""
            val amount = amountMatch?.groupValues?.get(1)?.replace(".", "")?.replace(",", "")?.toLongOrNull() ?: 0L
            val name = (if (amountMatch != null) clean.substring(0, amountMatch.range.first) else clean)
                .replace(Regex("(baru saja|memberikan|mengirim|menyawer|donasi|telah)", RegexOption.IGNORE_CASE), "")
                .trim().take(40).ifBlank { "Seseorang" }
            val message = if (amountMatch != null) clean.substring(amountMatch.range.last + 1).trim().take(200) else ""
            LiveBus.emit(LiveEvent.Donation(Platform.SAWERIA, name, name, amount, amountText, message))
        }
    }

    companion object {
        private const val OBSERVER_JS = """
            (function(){
              var last='';
              function check(){
                var t=(document.body&&document.body.innerText)||'';
                t=t.trim();
                if(t && t!==last){ last=t; try{ SBN.onText(t); }catch(e){} }
              }
              new MutationObserver(check).observe(document.documentElement,{subtree:true,childList:true,characterData:true});
              setInterval(check,1500);
            })();
        """
    }
}
