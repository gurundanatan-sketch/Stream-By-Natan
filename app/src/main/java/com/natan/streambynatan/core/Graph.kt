package com.natan.streambynatan.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.natan.streambynatan.R
import com.natan.streambynatan.audio.PcmMixer
import com.natan.streambynatan.audio.SoundBoard
import com.natan.streambynatan.audio.TtsHub
import com.natan.streambynatan.chat.TikTokClient
import com.natan.streambynatan.chat.YouTubeApi
import com.natan.streambynatan.chat.YouTubeChatClient
import com.natan.streambynatan.data.LocalStore
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.data.SettingsRepository
import com.natan.streambynatan.features.CommentTranslator
import com.natan.streambynatan.features.SpeechHub
import com.natan.streambynatan.guest.GuestManager
import com.natan.streambynatan.live.RecordingManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tempat semua komponen bersama dibuat sekali (service locator sederhana). */
object Graph {
    lateinit var app: Application; private set
    val settings by lazy { SettingsRepository(app) }
    val secure by lazy { SecureStore(app) }
    val local by lazy { LocalStore(app) }
    val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build() }
    val mixer by lazy { PcmMixer(44100, 2) }
    val tts by lazy { TtsHub(app, mixer).also { it.config = settings.current.tts } }
    val sounds by lazy { SoundBoard(app, mixer) }
    val speech by lazy { SpeechHub(app) }
    val translator by lazy { CommentTranslator() }
    val tiktok by lazy { TikTokClient(http) }
    val ytApi by lazy { YouTubeApi(app, http) }
    val ytChat by lazy { YouTubeChatClient(ytApi) }
    val guests by lazy { GuestManager(app) }
    val recordings by lazy { RecordingManager(app, local) }
    val firebase by lazy { FirebaseSync(app) }

    fun init(application: Application) {
        app = application
        val nm = application.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, application.getString(R.string.notif_channel_live), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, application.getString(R.string.notif_channel_reminder), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, application.getString(R.string.notif_channel_update), NotificationManager.IMPORTANCE_DEFAULT))
    }

    const val CH_LIVE = "live"
    const val CH_REMINDER = "reminder"
    const val CH_UPDATE = "update"
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Graph.firebase.init()
    }
}

fun Context.str(id: Int) = getString(id)
