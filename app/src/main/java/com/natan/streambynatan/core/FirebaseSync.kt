package com.natan.streambynatan.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.R
import com.natan.streambynatan.data.LiveSummary
import com.natan.streambynatan.data.LoyalViewer
import com.natan.streambynatan.ui.MainActivity
import kotlinx.coroutines.tasks.await

/**
 * Semua urusan Firebase: Auth (login email), Firestore (cadangan pengaturan, penonton setia, ringkasan live),
 * Cloud Messaging (notifikasi & pengingat), Crashlytics (laporan error), Analytics (statistik pemakaian).
 * Kalau google-services.json belum ada, semua fitur Firebase otomatis dimatikan dengan aman.
 */
class FirebaseSync(private val app: Application) {
    val available: Boolean get() = BuildConfig.HAS_FIREBASE && FirebaseApp.getApps(app).isNotEmpty()
    private val auth get() = if (available) FirebaseAuth.getInstance() else null
    private val db get() = if (available) FirebaseFirestore.getInstance() else null
    val uid: String? get() = auth?.currentUser?.uid
    val email: String? get() = auth?.currentUser?.email
    val displayName: String? get() = auth?.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@')

    fun init() {
        if (!available) return
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true) }
        runCatching { FirebaseAnalytics.getInstance(app).logEvent("app_open", null) }
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> saveToken(t) }
            FirebaseMessaging.getInstance().subscribeToTopic("pengingat")
        }
    }

    fun log(event: String) { if (available) runCatching { FirebaseAnalytics.getInstance(app).logEvent(event, null) } }

    suspend fun signIn(email: String, password: String): String? = runCatching {
        auth?.signInWithEmailAndPassword(email, password)?.await(); null
    }.getOrElse { it.message ?: "Gagal masuk" }

    suspend fun register(email: String, password: String): String? = runCatching {
        auth?.createUserWithEmailAndPassword(email, password)?.await(); null
    }.getOrElse { it.message ?: "Gagal daftar" }

    suspend fun resetPassword(email: String): String? = runCatching {
        auth?.sendPasswordResetEmail(email)?.await(); null
    }.getOrElse { it.message }

    /** Login dengan akun Google (pilih akun di HP, tanpa kata sandi). */
    @SuppressLint("DiscouragedApi")
    suspend fun signInWithGoogle(activity: Activity): String? = runCatching {
        val a = auth ?: return "Firebase belum diatur."
        val resId = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        if (resId == 0) return "Login Google belum aktif: aktifkan Google di Firebase Authentication lalu unduh ulang google-services.json."
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(app.getString(resId))
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cred = CredentialManager.create(activity).getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
            a.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } else "Akun Google tidak dikenali."
    }.getOrElse { it.message ?: "Login Google gagal" }

    fun signOut() { auth?.signOut() }

    private fun userDoc() = uid?.let { db?.collection("users")?.document(it) }

    fun saveToken(token: String) { userDoc()?.set(mapOf("fcmToken" to token), SetOptions.merge()) }

    /** Cadangan pengaturan (tanpa stream key/API key, yang tetap aman di HP). */
    suspend fun backupSettings(json: String): String? = runCatching {
        val doc = userDoc() ?: return "Login dulu untuk mencadangkan."
        doc.collection("backup").document("settings").set(mapOf("json" to json, "updatedAt" to System.currentTimeMillis())).await()
        null
    }.getOrElse { it.message }

    suspend fun restoreSettings(): String? = runCatching {
        val doc = userDoc() ?: return null
        doc.collection("backup").document("settings").get().await().getString("json")
    }.getOrNull()

    fun syncLoyal(list: List<LoyalViewer>) {
        val doc = userDoc() ?: return
        val batch = db?.batch() ?: return
        list.takeLast(400).forEach { v ->
            batch.set(doc.collection("viewers").document(v.id.replace("/", "_")), mapOf("name" to v.name, "visits" to v.visits, "lastVisit" to v.lastVisit))
        }
        runCatching { batch.commit() }
    }

    fun saveSummary(s: LiveSummary) {
        userDoc()?.collection("lives")?.document(s.id)?.set(mapOf(
            "startedAt" to s.startedAt, "endedAt" to s.endedAt, "profile" to s.profile, "peakViewers" to s.peakViewers,
            "likes" to s.totalLikes, "coins" to s.totalCoins, "comments" to s.comments, "questions" to s.questions,
            "follows" to s.follows, "shares" to s.shares, "subscribes" to s.subscribes, "donations" to s.donations,
        ))
        log("live_finished")
    }

    fun saveSchedule(title: String, at: Long) {
        userDoc()?.collection("schedules")?.add(mapOf("title" to title, "at" to at))
    }
}

/** Menerima notifikasi Cloud Messaging (misal pengingat jadwal live yang dikirim dari Firebase Console). */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { Graph.firebase.saveToken(token) }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Stream By Natan"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Graph.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n) }
    }
}
