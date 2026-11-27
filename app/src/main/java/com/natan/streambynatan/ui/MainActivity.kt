package com.natan.streambynatan.ui

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.natan.streambynatan.chat.YouTubeAuth
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.features.Updater
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveMode
import com.natan.streambynatan.live.LiveService
import com.natan.streambynatan.ui.theme.StreamTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Status yang dibaca layar Compose. */
    val clipboardKey = mutableStateOf<String?>(null)
    val updateAvailable = mutableStateOf<Updater.Release?>(null)
    val youtubeConnected = mutableStateOf(false)
    val message = mutableStateOf<String?>(null)

    private var pendingMode = LiveMode.CAMERA
    private var pendingPractice = false
    private var pendingPick: ((Uri) -> Unit)? = null
    private var afterYouTubeAuth: (() -> Unit)? = null

    private val permissions: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) LiveService.start(this, pendingMode, pendingPractice, r.resultCode, r.data)
        else if (pendingMode == LiveMode.SCREEN) message.value = "Izin rekam layar ditolak."
        else LiveService.start(this, pendingMode, pendingPractice, Activity.RESULT_CANCELED, null)
        afterStart()
    }

    private val ytAuthLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = YouTubeAuth.fromIntent(this, r.data)
        youtubeConnected.value = token != null
        message.value = if (token != null) "YouTube terhubung." else "Gagal menghubungkan YouTube."
        if (token != null) afterYouTubeAuth?.invoke()
        afterYouTubeAuth = null
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingPick?.invoke(uri)
        }
        pendingPick = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Graph.settings.current.features.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        askBasicPermissions()
        setContent { StreamTheme { AppRoot(this) } }
        lifecycleScope.launch {
            youtubeConnected.value = YouTubeAuth.silentToken(this@MainActivity) != null
            if (Graph.settings.current.updateCheckOnStart) updateAvailable.value = Updater.check()
        }
        lifecycleScope.launch { LiveBus.announce.collect { window.decorView.announceForAccessibility(it) } }
    }

    fun basicPermissionsGranted(): Boolean =
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    fun askBasicPermissions() {
        val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        val missing = list.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    /** Mulai live. Mode layar (dan audio internal) butuh izin rekam layar dari Android. */
    fun startLive(mode: LiveMode, practice: Boolean, withInternalAudio: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askBasicPermissions(); message.value = "Izinkan mikrofon dulu."; return
        }
        pendingMode = mode; pendingPractice = practice
        if (mode == LiveMode.SCREEN || withInternalAudio) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } else {
            LiveService.start(this, mode, practice, Activity.RESULT_CANCELED, null)
            afterStart()
        }
    }

    private fun afterStart() {
        val s = Graph.settings.current
        if (s.features.shareLink && !pendingPractice) {
            val yt = when {
                s.youtubeVideoId.isNotBlank() -> "https://youtube.com/watch?v=${s.youtubeVideoId}"
                s.youtubeChannelUrl.isNotBlank() -> s.youtubeChannelUrl.trimEnd('/') + "/live"
                else -> ""
            }
            val tt = if (s.tiktokUsername.isNotBlank()) "https://www.tiktok.com/@${s.tiktokUsername.removePrefix("@")}/live" else ""
            val text = s.shareMessage.replace("{youtube}", yt).replace("{tiktok}", tt).trim()
            window.decorView.postDelayed({
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Bagikan link live"))
            }, 4000)
        }
    }

    fun stopLive() = LiveService.stop(this)

    fun pick(mime: Array<String>, onPicked: (Uri) -> Unit) {
        pendingPick = onPicked
        picker.launch(mime)
    }

    fun connectYouTube(then: (() -> Unit)? = null) {
        lifecycleScope.launch {
            runCatching { YouTubeAuth.authorize(this@MainActivity) }.onSuccess { (token, pi) ->
                if (token != null) { youtubeConnected.value = true; message.value = "YouTube terhubung."; then?.invoke() }
                else if (pi != null) { afterYouTubeAuth = then; ytAuthLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build()) }
            }.onFailure { message.value = "Gagal: ${it.message}. Pastikan OAuth sudah diatur di Google Cloud Console." }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboardForTikTokKey()
    }

    /** Deteksi key TikTok di clipboard (key TikTok berganti setiap live). */
    private fun checkClipboardForTikTokKey() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() }.getOrNull() ?: return
        val looksLikeKey = text.length in 16..120 && !text.contains(' ') && !text.startsWith("http") &&
            (text.startsWith("stream-") || text.contains("?") || text.count { it.isDigit() } >= 4)
        if (looksLikeKey && text != Graph.secure.get(SecureStore.TIKTOK_KEY) && text != Graph.secure.get(SecureStore.YOUTUBE_KEY)) {
            clipboardKey.value = text
        }
    }

    fun clipboardText(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        return runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() }.getOrNull()
    }

    fun installUpdate(rel: Updater.Release) {
        if (!Updater.canInstall(this)) { Updater.openInstallPermission(this); message.value = "Nyalakan izin pasang aplikasi, lalu tekan Perbarui lagi."; return }
        lifecycleScope.launch {
            message.value = "Mengunduh pembaruan…"
            val f = Updater.download(this@MainActivity, rel) { }
            if (f != null) Updater.install(this@MainActivity, f) else message.value = "Unduhan gagal."
        }
    }
}
