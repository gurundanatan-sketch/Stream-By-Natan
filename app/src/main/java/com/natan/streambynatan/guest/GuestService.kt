package com.natan.streambynatan.guest

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.natan.streambynatan.R
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.ui.MainActivity

/**
 * Layanan latar depan untuk TAMU: menjaga kamera & mikrofon tetap jalan saat tamu berpindah aplikasi,
 * dan wajib ada sebelum berbagi layar (aturan Android 14 ke atas).
 */
class GuestService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val screen = intent?.getBooleanExtra(EXTRA_SCREEN, false) ?: false
        var types = 0
        if (granted(Manifest.permission.CAMERA)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (granted(Manifest.permission.RECORD_AUDIO)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (screen) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Graph.CH_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stream By Natan · Tamu")
            .setContentText(if (screen) "Kamu sedang berbagi layar ke host" else "Kamu tersambung sebagai tamu live")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val ok = runCatching { ServiceCompat.startForeground(this, NOTIF_ID, n, types) }.isSuccess
        val cb = onReady; onReady = null
        if (ok) cb?.invoke()
        return START_NOT_STICKY
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val NOTIF_ID = 4417
        private const val EXTRA_SCREEN = "screen"
        /** Dipanggil sekali setelah layanan benar-benar berjalan di latar depan. */
        @Volatile var onReady: (() -> Unit)? = null

        fun start(ctx: Context, screen: Boolean, then: (() -> Unit)? = null) {
            onReady = then
            val i = Intent(ctx, GuestService::class.java).putExtra(EXTRA_SCREEN, screen)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, GuestService::class.java)) }
    }
}
