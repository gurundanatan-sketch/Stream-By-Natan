package com.natan.streambynatan.live

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.natan.streambynatan.R
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.features.SystemHelpers
import com.natan.streambynatan.floating.FloatingControls
import com.natan.streambynatan.ui.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Layanan latar depan (foreground) "LiveService" yang menjaga siaran tetap jalan
 * walau layar dimatikan atau kamu berpindah aplikasi (berbagi layar).
 */
class LiveService : LifecycleService() {
    private var wake: PowerManager.WakeLock? = null
    private var floating: FloatingControls? = null
    private var prevDnd = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stopLive()
            ACTION_MIC -> engine?.let { it.setMic(!LiveBus.stats.value.micOn) }
            ACTION_NEXT_Q -> engine?.brain?.nextQuestion()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (engine != null) return
        val mode = LiveMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: LiveMode.CAMERA.name)
        val practice = intent.getBooleanExtra(EXTRA_PRACTICE, false)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent.getParcelableExtra(EXTRA_DATA)
        val hasProjection = resultCode == Activity.RESULT_OK && data != null

        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (mode == LiveMode.CAMERA || mode == LiveMode.USB) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (hasProjection) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification("Menyiapkan live…"), types) }
            .onFailure { LiveBus.feed("Gagal memulai layanan: ${it.message}"); stopSelf(); return }

        val projection: MediaProjection? = if (hasProjection) {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data!!)
        } else null
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { LiveBus.feed("Izin rekam layar berakhir.") }
        }, null)

        val e = LiveEngine(this, this)
        try {
            e.prepare(mode, practice, projection)
        } catch (t: Throwable) {
            LiveBus.feed("Gagal menyiapkan: ${t.message}")
            LiveBus.announce("Gagal menyiapkan live: ${t.message}")
            stopSelf(); return
        }
        engine = e
        val s = Graph.settings.current
        if (s.features.keepScreenOn) {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StreamByNatan:live").apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (s.features.doNotDisturb) prevDnd = SystemHelpers.setDnd(this, true)
        e.start()
        if (mode == LiveMode.SCREEN && SystemHelpers.canDrawOverlays(this)) {
            floating = FloatingControls(this, e, this).also { it.show() }
        }
        lifecycleScope.launch {
            LiveBus.stats.collectLatest { st ->
                val text = if (st.practice) "Mode latihan · merekam" else "YouTube: ${st.youtubeState} · TikTok: ${st.tiktokState} · ${st.viewers} penonton"
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
            }
        }
    }

    private fun stopLive() {
        val e = engine ?: run { stopSelf(); return }
        floating?.hide(); floating = null
        e.requestStop {
            engine = null
            SystemHelpers.setDnd(this, false, prevDnd)
            runCatching { wake?.release() }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        floating?.hide()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, req: Int) = PendingIntent.getService(this, req, Intent(this, LiveService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Graph.CH_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stream By Natan sedang live")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Mikrofon", action(ACTION_MIC, 1))
            .addAction(0, "Pertanyaan berikutnya", action(ACTION_NEXT_Q, 2))
            .addAction(0, "Akhiri live", action(ACTION_STOP, 3))
            .build()
    }

    companion object {
        @Volatile var engine: LiveEngine? = null
            private set
        const val NOTIF_ID = 77
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_MIC = "mic"
        const val ACTION_NEXT_Q = "next_q"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PRACTICE = "practice"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"

        fun start(context: Context, mode: LiveMode, practice: Boolean, resultCode: Int, data: Intent?) {
            val i = Intent(context, LiveService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_PRACTICE, practice)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveService::class.java).setAction(ACTION_STOP))
        }
    }
}
