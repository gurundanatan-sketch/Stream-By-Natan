package com.natan.streambynatan.features

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.R
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.AppSettings
import com.natan.streambynatan.data.ScheduleItem
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

// =====================================================================  pengingat jadwal live
object Scheduler {
    fun scheduleAll(context: Context, items: List<ScheduleItem>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        items.forEachIndexed { i, item ->
            var at = item.epochMillis - item.remindMinutesBefore * 60_000L
            if (item.repeatWeekly) while (at < System.currentTimeMillis()) at += 7L * 24 * 3600 * 1000
            if (at < System.currentTimeMillis()) return@forEachIndexed
            val pi = PendingIntent.getBroadcast(context, 1000 + i,
                Intent(context, ReminderReceiver::class.java).putExtra("title", item.title).putExtra("minutes", item.remindMinutesBefore),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Graph.firebase.saveSchedule(item.title, item.epochMillis)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Live"
        val minutes = intent.getIntExtra("minutes", 15)
        val pi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, Graph.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pengingat live: $title")
            .setContentText("Live dimulai $minutes menit lagi.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), n) }
        Scheduler.scheduleAll(context, Graph.settings.current.schedules)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.scheduleAll(context, Graph.settings.current.schedules)
    }
}

// =====================================================================  pemeriksaan sebelum live
object PreLiveCheck {
    data class Result(val ok: Boolean, val lines: List<String>) {
        val spoken: String get() = (if (ok) "Semua siap. " else "Ada yang perlu dicek. ") + lines.joinToString(". ") + if (ok) ". Silakan mulai live." else "."
    }

    suspend fun run(context: Context, s: AppSettings): Result = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        var ok = true
        // internet
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) { ok = false; lines += "Internet tidak tersambung" }
        else {
            val speed = measureUpload()
            val need = s.video.bitrateKbps + s.video.audioBitrateKbps
            val total = if (s.enableYouTube && s.enableTikTok) need * 2 else need
            lines += when {
                speed <= 0 -> "Kecepatan internet tidak bisa diukur"
                speed < total -> { ok = false; "Internet lemah, sekitar ${speed / 1000} megabit, butuh ${total / 1000} megabit" }
                else -> "Internet bagus, sekitar ${speed / 1000} megabit"
            }
        }
        // stream key
        val g = Graph
        if (s.enableYouTube) lines += if (g.secure.get(SecureStore.YOUTUBE_KEY).isBlank()) { ok = false; "Stream key YouTube kosong" } else "Stream key YouTube ada"
        if (s.enableTikTok) lines += if (g.secure.get(SecureStore.TIKTOK_KEY).isBlank() || s.tiktokServer.isBlank()) { ok = false; "Server atau key TikTok kosong" } else "Stream key TikTok ada"
        // mikrofon
        val micOk = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && testMic()
        lines += if (micOk) "Mikrofon berfungsi" else { ok = false; "Mikrofon tidak terdengar atau belum diizinkan" }
        // audio internal
        lines += if (Build.VERSION.SDK_INT >= 29) "Audio internal didukung, izinkan rekam layar saat mulai" else "Audio internal butuh Android 10 ke atas"
        // komentar
        if (s.enableTikTok && s.tiktokUsername.isBlank()) lines += "Username TikTok belum diisi (untuk membaca komentar)"
        Result(ok, lines)
    }

    private fun measureUpload(): Int = runCatching {
        val bytes = ByteArray(400_000) { (it % 251).toByte() }
        val start = System.nanoTime()
        val req = Request.Builder().url("https://speed.cloudflare.com/__up")
            .post(bytes.toRequestBody(null)).build()
        Graph.http.newCall(req).execute().close()
        val sec = (System.nanoTime() - start) / 1e9
        (bytes.size * 8 / sec / 1000).toInt() // kbps
    }.getOrDefault(-1)

    @android.annotation.SuppressLint("MissingPermission")
    private fun testMic(): Boolean = runCatching {
        val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        rec.startRecording()
        val buf = ShortArray(size)
        var peak = 0
        repeat(10) { val n = rec.read(buf, 0, buf.size); for (i in 0 until n) peak = maxOf(peak, abs(buf[i].toInt())) }
        rec.stop(); rec.release()
        peak > 30
    }.getOrDefault(false)
}

// =====================================================================  pembaruan otomatis dari GitHub
object Updater {
    data class Release(val versionCode: Int, val name: String, val apkUrl: String)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            Graph.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val js = JSONObject(r.body?.string().orEmpty())
                val tag = js.optString("tag_name")
                val code = tag.substringAfterLast('.').toIntOrNull() ?: return@runCatching null
                val assets = js.optJSONArray("assets") ?: return@runCatching null
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: return@runCatching null
                if (code > BuildConfig.VERSION_CODE) Release(code, js.optString("name", tag), apk.optString("browser_download_url")) else null
            }
        }.getOrNull()
    }

    suspend fun download(context: Context, rel: Release, progress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(context.cacheDir, "update.apk")
            Graph.http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { r ->
                val body = r.body ?: return@runCatching null
                val total = body.contentLength().coerceAtLeast(1)
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024); var done = 0L
                        while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; progress((done * 100 / total).toInt()) }
                    }
                }
            }
            out
        }.getOrNull()
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Halaman izin "Pasang aplikasi tidak dikenal" (cukup sekali). */
    fun openInstallPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// =====================================================================  izin sistem
object SystemHelpers {
    fun isIgnoringBattery(context: Context) = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBattery(context: Context) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun hasDndAccess(context: Context) = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun requestDnd(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Jangan Ganggu saat live (notifikasi lain tidak berbunyi). Mengembalikan mode sebelumnya. */
    fun setDnd(context: Context, on: Boolean, previous: Int = NotificationManager.INTERRUPTION_FILTER_ALL): Int {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return previous
        if (!nm.isNotificationPolicyAccessGranted) return previous
        val before = nm.currentInterruptionFilter
        runCatching { nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else previous) }
        return before
    }

    fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

    fun requestOverlay(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
