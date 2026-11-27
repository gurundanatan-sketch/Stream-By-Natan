package com.natan.streambynatan.core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.natan.streambynatan.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Penangkap error. Kalau aplikasi berhenti mendadak:
 * 1) pesan error disimpan di aplikasi dan di folder Download (StreamByNatan-error.txt),
 * 2) layar "Aplikasi berhenti" muncul berisi pesan error lengkap + tombol Salin / Bagikan.
 */
object CrashReporter {
    private const val FILE = "error_terakhir.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = report(thread, error)
                File(app.filesDir, FILE).writeText(text)
                saveToDownloads(app, text)
                app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            // Biarkan Crashlytics mencatat juga, lalu tutup proses yang rusak
            runCatching { previous?.uncaughtException(thread, error) }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun report(thread: Thread, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("STREAM BY NATAN - LAPORAN ERROR")
            appendLine("Waktu   : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("Versi   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("HP      : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread  : ${thread.name}")
            appendLine()
            append(sw.toString())
        }
    }

    /** Simpan salinan di folder Download supaya mudah dikirim. */
    private fun saveToDownloads(context: Context, text: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val r = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "StreamByNatan-error.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            r.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }

    fun lastReport(context: Context): String = runCatching { File(context.filesDir, FILE).readText() }.getOrDefault("")
}

/** Layar sederhana (tanpa Compose, supaya tetap bisa tampil walau bagian lain rusak). */
class CrashActivity : Activity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = CrashReporter.lastReport(this).ifBlank { "Tidak ada laporan error." }
        val title = TextView(this).apply {
            this.text = "Aplikasi berhenti karena error"
            textSize = 22f; setTextColor(Color.WHITE); setPadding(32, 48, 32, 8)
        }
        val info = TextView(this).apply {
            this.text = "Laporan ini juga tersimpan di folder Download dengan nama StreamByNatan-error.txt. Kirim file itu atau tekan Bagikan laporan."
            textSize = 15f; setTextColor(0xFFA6B0CF.toInt()); setPadding(32, 0, 32, 16)
        }
        val share = Button(this).apply {
            this.text = "Bagikan laporan"
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Bagikan laporan error"))
            }
        }
        val copy = Button(this).apply {
            this.text = "Salin laporan"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("error", text))
                this.text = "Laporan disalin"
            }
        }
        val close = Button(this).apply { this.text = "Tutup"; setOnClickListener { finishAndRemoveTask() } }
        val body = TextView(this).apply {
            this.text = text; textSize = 12f; setTextColor(0xFFF2F5FF.toInt()); setTextIsSelectable(true); setPadding(32, 16, 32, 48)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070A14.toInt())
            addView(title); addView(info); addView(share); addView(copy); addView(close)
            addView(ScrollView(this@CrashActivity).apply { addView(body) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }
}
