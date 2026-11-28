package com.natan.streambynatan.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.natan.streambynatan.BuildConfig
import com.natan.streambynatan.R
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Profil publik pengguna (tampil di aplikasi & halaman web /@username). */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val links: Map<String, String> = emptyMap(),
    /** Foto profil kecil (JPEG base64, data URL). */
    val photo: String = "",
    val live: Boolean = false,
    val liveLinks: Map<String, String> = emptyMap(),
    val room: String = "",
    val schedule: List<Pair<String, Long>> = emptyList(),
) {
    val shareUrl: String get() = BuildConfig.HOSTING_URL + "/@" + username
}

/**
 * Semua fitur sosial lewat Firebase (tanpa server tambahan):
 * profil & username, foto profil, ikuti kreator + notifikasi live & jadwal, moderator,
 * kotak saran, mode khusus pembuat aplikasi, riwayat akun (beralih akun), hapus akun.
 */
class Social(private val context: Context) {
    private val db: FirebaseFirestore? get() = if (Graph.firebase.available) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null
    private val uid: String? get() = Graph.firebase.uid

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile
    private val _isCreator = MutableStateFlow(false)
    /** Akun ini termasuk email pembuat aplikasi. */
    val isCreator: StateFlow<Boolean> = _isCreator
    private val _unreadFeedback = MutableStateFlow(0)
    val unreadFeedback: StateFlow<Int> = _unreadFeedback
    private var feedbackReg: ListenerRegistration? = null

    // ------------------------------------------------------------------ mode pembuat
    companion object {
        /** Email pembuat aplikasi. Kunci khusus (Euler Stream, YouTube API) hanya bisa dibaca akun-akun ini. */
        val CREATOR_EMAILS = setOf(
            "natsenterteiment@gmail.com",
            "hebronisland@gmail.com",
            "gurundanatan@gmail.com",
            "pengikutmesias77@gmail.com",
            "gevarielimanuel1@gmail.com",
            "gevarielimanuel884@gmail.com",
            "natanelliaha16@gmail.com",
        )

        fun photoToDataUrl(context: Context, uri: Uri): String? = runCatching {
            val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val small = Bitmap.createScaledBitmap(square, 256, 256, true)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 78, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()

        fun dataUrlToBitmap(url: String): Bitmap? = runCatching {
            if (!url.startsWith("data:")) return null
            val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()

        fun cleanUsername(raw: String) = raw.trim().removePrefix("@").lowercase().replace(Regex("[^a-z0-9._]"), "").take(24)
    }

    /** Dipanggil setelah login / saat aplikasi dibuka. */
    fun onSignedIn() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val email = user.email.orEmpty().lowercase()
        rememberAccount(email, user.providerData.any { it.providerId == "google.com" })
        val creator = email in CREATOR_EMAILS
        _isCreator.value = creator
        loadProfile()
        if (creator) {
            fetchCreatorKeys()
            watchFeedback()
        }
        FollowCheckReceiver.schedule(context)
    }

    /** Akun email pembuat harus memverifikasi email dulu supaya kunci bisa dibaca (Google otomatis terverifikasi). */
    val creatorNeedsVerification: Boolean get() {
        val u = FirebaseAuth.getInstance().currentUser ?: return false
        return _isCreator.value && !u.isEmailVerified
    }

    suspend fun sendVerification(): String? = runCatching {
        FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await(); null
    }.getOrElse { it.message }

    /** Mengambil kunci Euler Stream & YouTube API milik pembuat dari Firestore (tidak tertanam di APK). */
    fun fetchCreatorKeys() {
        val d = db ?: return
        d.collection("private").document("creatorKeys").get().addOnSuccessListener { doc ->
            doc.getString("euler")?.takeIf { it.isNotBlank() }?.let { Graph.secure.put(SecureStore.EULER_API_KEY, it) }
            doc.getString("youtubeApi")?.takeIf { it.isNotBlank() }?.let { Graph.secure.put(SecureStore.YOUTUBE_API_KEY, it) }
        }
    }

    /** Pembuat menyimpan kunci sekali, berlaku untuk semua email pembuat. */
    suspend fun saveCreatorKeys(euler: String, youtubeApi: String): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        d.collection("private").document("creatorKeys").set(mapOf(
            "euler" to euler.trim(), "youtubeApi" to youtubeApi.trim(),
            "updatedBy" to (Graph.firebase.email ?: ""), "at" to System.currentTimeMillis(),
        ), SetOptions.merge()).await()
        if (euler.isNotBlank()) Graph.secure.put(SecureStore.EULER_API_KEY, euler.trim())
        if (youtubeApi.isNotBlank()) Graph.secure.put(SecureStore.YOUTUBE_API_KEY, youtubeApi.trim())
        null
    }.getOrElse { it.message ?: "Gagal menyimpan. Pastikan email pembuat sudah terverifikasi." }

    /** Pembuat memakai Euler Stream untuk TikTok kalau kuncinya ada. */
    val useEuler: Boolean get() = _isCreator.value && Graph.secure.get(SecureStore.EULER_API_KEY).isNotBlank()

    // ------------------------------------------------------------------ profil
    private fun profiles() = db?.collection("profiles")

    fun loadProfile() {
        val id = uid ?: return
        profiles()?.document(id)?.get()?.addOnSuccessListener { d ->
            if (!d.exists()) {
                val name = Graph.firebase.displayName ?: "Pengguna"
                _profile.value = UserProfile(uid = id, name = name)
                return@addOnSuccessListener
            }
            _profile.value = parse(id, d.data.orEmpty())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(id: String, m: Map<String, Any?>) = UserProfile(
        uid = id,
        name = m["name"] as? String ?: "",
        username = m["username"] as? String ?: "",
        bio = m["bio"] as? String ?: "",
        links = (m["links"] as? Map<String, String>).orEmpty(),
        photo = m["photo"] as? String ?: "",
        live = m["live"] as? Boolean ?: false,
        liveLinks = (m["liveLinks"] as? Map<String, String>).orEmpty(),
        room = m["room"] as? String ?: "",
        schedule = ((m["schedule"] as? List<Map<String, Any?>>).orEmpty()).mapNotNull { e ->
            val t = e["title"] as? String ?: return@mapNotNull null
            val at = (e["at"] as? Number)?.toLong() ?: return@mapNotNull null
            t to at
        },
    )

    /** Simpan profil. Username dicek supaya tidak dipakai orang lain. */
    suspend fun saveProfile(name: String, usernameRaw: String, bio: String, links: Map<String, String>, photo: String): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        val id = uid ?: return "Login dulu."
        val username = cleanUsername(usernameRaw)
        if (username.length < 3) return "Username minimal 3 huruf (huruf kecil, angka, titik, garis bawah)."
        val old = _profile.value.username
        if (username != old) {
            val ref = d.collection("usernames").document(username)
            val taken = ref.get().await()
            if (taken.exists() && taken.getString("uid") != id) return "Username @$username sudah dipakai orang lain."
            ref.set(mapOf("uid" to id)).await()
            if (old.isNotBlank()) runCatching { d.collection("usernames").document(old).delete().await() }
        }
        val clean = links.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
        profiles()!!.document(id).set(mapOf(
            "name" to name.trim().take(40), "username" to username, "bio" to bio.trim().take(300),
            "links" to clean, "photo" to photo, "updatedAt" to System.currentTimeMillis(),
        ), SetOptions.merge()).await()
        _profile.value = _profile.value.copy(uid = id, name = name.trim(), username = username, bio = bio.trim(), links = clean, photo = photo)
        null
    }.getOrElse { it.message ?: "Gagal menyimpan profil." }

    /** Status live di profil (halaman web menampilkan "Sedang live" + tautannya). */
    fun publishLive(live: Boolean, links: Map<String, String>) {
        val id = uid ?: return
        profiles()?.document(id)?.set(mapOf(
            "live" to live, "liveLinks" to links,
            "liveStartedAt" to if (live) System.currentTimeMillis() else 0L,
        ), SetOptions.merge())
    }

    /** Ruang tamu yang sedang dibuka (untuk moderator & tombol "Gabung sebagai tamu" di profil web). */
    fun publishRoom(room: String) {
        val id = uid ?: return
        profiles()?.document(id)?.set(mapOf("room" to room, "roomOpen" to room.isNotBlank()), SetOptions.merge())
    }

    /** Jadwal live: tampil di profil, pengikut dapat pengingat. */
    fun publishSchedule(items: List<Pair<String, Long>>) {
        val id = uid ?: return
        profiles()?.document(id)?.set(mapOf("schedule" to items.map { mapOf("title" to it.first, "at" to it.second) }), SetOptions.merge())
        _profile.value = _profile.value.copy(schedule = items)
    }

    suspend fun findByUsername(raw: String): UserProfile? = runCatching {
        val d = db ?: return null
        val u = cleanUsername(raw)
        val ref = d.collection("usernames").document(u).get().await()
        val target = ref.getString("uid") ?: return null
        val p = d.collection("profiles").document(target).get().await()
        parse(target, p.data.orEmpty())
    }.getOrNull()

    suspend fun profileOf(id: String): UserProfile? = runCatching {
        val p = db?.collection("profiles")?.document(id)?.get()?.await() ?: return null
        parse(id, p.data.orEmpty())
    }.getOrNull()

    // ------------------------------------------------------------------ ikuti kreator
    suspend fun follow(creator: UserProfile, on: Boolean): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        val me = uid ?: return "Login dulu."
        if (creator.uid == me) return "Tidak bisa mengikuti diri sendiri."
        val ref = d.collection("follows").document(me).collection("creators").document(creator.uid)
        if (on) ref.set(mapOf("name" to creator.name, "username" to creator.username, "at" to System.currentTimeMillis())).await()
        else ref.delete().await()
        null
    }.getOrElse { it.message }

    suspend fun following(): List<UserProfile> = runCatching {
        val d = db ?: return emptyList()
        val me = uid ?: return emptyList()
        d.collection("follows").document(me).collection("creators").get().await().documents.mapNotNull { profileOf(it.id) }
    }.getOrDefault(emptyList())

    suspend fun isFollowing(creatorUid: String): Boolean = runCatching {
        val me = uid ?: return false
        db?.collection("follows")?.document(me)?.collection("creators")?.document(creatorUid)?.get()?.await()?.exists() == true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ moderator
    /** Kode undangan moderator milik host (dibuat sekali). */
    suspend fun moderatorInviteLink(): String? = runCatching {
        val d = db ?: return null
        val me = uid ?: return null
        // Kode disimpan di subkoleksi rahasia (profil bisa dibaca publik, kodenya tidak).
        val doc = d.collection("profiles").document(me).collection("secret").document("modInvite")
        var code = runCatching { doc.get().await().getString("code") }.getOrNull()
        if (code.isNullOrBlank()) {
            code = UUID.randomUUID().toString().take(10)
            doc.set(mapOf("code" to code)).await()
        }
        "${BuildConfig.HOSTING_URL}/mod/?h=$me&c=$code"
    }.getOrNull()

    /** Host menambah moderator lewat username. */
    suspend fun addModeratorByUsername(raw: String): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        val me = uid ?: return "Login dulu."
        val target = findByUsername(raw) ?: return "Username tidak ditemukan. Pastikan orangnya sudah membuat profil di aplikasi."
        if (target.uid == me) return "Itu akun kamu sendiri."
        d.collection("profiles").document(me).collection("moderators").document(target.uid)
            .set(mapOf("name" to target.name, "username" to target.username, "at" to System.currentTimeMillis())).await()
        d.collection("moderating").document(target.uid).collection("hosts").document(me)
            .set(mapOf("name" to (_profile.value.name.ifBlank { Graph.firebase.displayName ?: "Host" }), "at" to System.currentTimeMillis())).await()
        null
    }.getOrElse { it.message }

    /** Orang yang diundang menerima undangan moderator dari link. */
    suspend fun acceptModeratorInvite(hostUid: String, code: String): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        val me = uid ?: return "Login dulu, lalu buka lagi link undangannya."
        val host = profileOf(hostUid)
        d.collection("profiles").document(hostUid).collection("moderators").document(me)
            .set(mapOf("name" to _profile.value.name.ifBlank { Graph.firebase.displayName ?: "Moderator" },
                "username" to _profile.value.username, "code" to code, "at" to System.currentTimeMillis())).await()
        d.collection("moderating").document(me).collection("hosts").document(hostUid)
            .set(mapOf("name" to (host?.name ?: "Host"), "at" to System.currentTimeMillis())).await()
        null
    }.getOrElse { it.message ?: "Undangan tidak valid atau sudah diganti." }

    data class Moderator(val uid: String, val name: String, val username: String)

    suspend fun myModerators(): List<Moderator> = runCatching {
        val me = uid ?: return emptyList()
        db!!.collection("profiles").document(me).collection("moderators").get().await().documents.map {
            Moderator(it.id, it.getString("name").orEmpty(), it.getString("username").orEmpty())
        }
    }.getOrDefault(emptyList())

    suspend fun removeModerator(modUid: String) = runCatching {
        val me = uid ?: return@runCatching
        db!!.collection("profiles").document(me).collection("moderators").document(modUid).delete().await()
        runCatching { db!!.collection("moderating").document(modUid).collection("hosts").document(me).delete().await() }
    }

    /** Host-host yang mempercayakan aku sebagai moderator. */
    suspend fun hostsIModerate(): List<UserProfile> = runCatching {
        val me = uid ?: return emptyList()
        db!!.collection("moderating").document(me).collection("hosts").get().await().documents.mapNotNull { profileOf(it.id) }
    }.getOrDefault(emptyList())

    /** Moderator mengirim perintah ke HP host (dijalankan di sana). */
    fun sendCommand(room: String, type: String, data: Map<String, Any?> = emptyMap()) {
        db?.collection("rooms")?.document(room)?.collection("commands")?.add(data + mapOf(
            "type" to type, "by" to (Graph.firebase.displayName ?: "Moderator"), "at" to System.currentTimeMillis(), "done" to false,
        ))
    }

    // ------------------------------------------------------------------ kotak saran
    suspend fun sendFeedback(text: String, kind: String): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        d.collection("feedback").add(mapOf(
            "uid" to (uid ?: ""), "name" to (_profile.value.name.ifBlank { Graph.firebase.displayName ?: "" }),
            "email" to (Graph.firebase.email ?: ""), "text" to text.trim().take(2000), "kind" to kind,
            "version" to BuildConfig.VERSION_NAME, "device" to android.os.Build.MODEL,
            "at" to System.currentTimeMillis(), "read" to false,
        )).await()
        null
    }.getOrElse { it.message }

    data class Feedback(val id: String, val name: String, val email: String, val text: String, val kind: String, val version: String, val at: Long, val read: Boolean)

    suspend fun feedbackList(): List<Feedback> = runCatching {
        db!!.collection("feedback").orderBy("at", Query.Direction.DESCENDING).limit(200).get().await().documents.map {
            Feedback(it.id, it.getString("name").orEmpty(), it.getString("email").orEmpty(), it.getString("text").orEmpty(),
                it.getString("kind").orEmpty(), it.getString("version").orEmpty(), it.getLong("at") ?: 0L, it.getBoolean("read") ?: false)
        }
    }.getOrDefault(emptyList())

    fun markFeedbackRead(id: String) { db?.collection("feedback")?.document(id)?.update("read", true) }

    /** Pembuat: angka saran baru + notifikasi saat ada saran masuk. */
    private fun watchFeedback() {
        if (feedbackReg != null) return
        val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
        feedbackReg = db?.collection("feedback")?.whereEqualTo("read", false)?.addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            _unreadFeedback.value = docs.size
            val lastSeen = prefs.getLong("lastFeedbackAt", 0L)
            val newest = docs.maxOfOrNull { it.getLong("at") ?: 0L } ?: 0L
            if (newest > lastSeen) {
                prefs.edit().putLong("lastFeedbackAt", newest).apply()
                if (lastSeen > 0) notify(context, "Saran baru masuk", "${docs.size} saran belum dibaca di Kotak saran", 7001)
            }
        }
    }

    // ------------------------------------------------------------------ akun
    data class SavedAccount(val email: String, val google: Boolean)

    private fun rememberAccount(email: String, google: Boolean) {
        if (email.isBlank()) return
        val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
        val list = savedAccounts().filter { it.email != email } + SavedAccount(email, google)
        prefs.edit().putString("accounts", list.joinToString("\n") { "${it.email}|${it.google}" }).apply()
    }

    fun savedAccounts(): List<SavedAccount> {
        val raw = context.getSharedPreferences("social", Context.MODE_PRIVATE).getString("accounts", "").orEmpty()
        return raw.lines().filter { it.contains("|") }.map { SavedAccount(it.substringBefore("|"), it.substringAfter("|") == "true") }
    }

    fun forgetAccount(email: String) {
        val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
        prefs.edit().putString("accounts", savedAccounts().filter { it.email != email }.joinToString("\n") { "${it.email}|${it.google}" }).apply()
    }

    fun signOut() {
        feedbackReg?.remove(); feedbackReg = null
        _isCreator.value = false
        _profile.value = UserProfile()
        _unreadFeedback.value = 0
        Graph.firebase.signOut()
    }

    /** Hapus akun beserta profil, username, data cadangan, dan daftar ikuti. */
    suspend fun deleteAccount(): String? = runCatching {
        val d = db ?: return "Firebase belum aktif."
        val user = FirebaseAuth.getInstance().currentUser ?: return "Login dulu."
        val id = user.uid
        val p = _profile.value
        runCatching { if (p.username.isNotBlank()) d.collection("usernames").document(p.username).delete().await() }
        for (sub in listOf("moderators", "secret")) runCatching {
            d.collection("profiles").document(id).collection(sub).get().await().documents.forEach { it.reference.delete() }
        }
        runCatching { d.collection("profiles").document(id).delete().await() }
        runCatching { d.collection("follows").document(id).collection("creators").get().await().documents.forEach { it.reference.delete() } }
        for (sub in listOf("backup", "viewers", "lives", "schedules")) runCatching {
            d.collection("users").document(id).collection(sub).get().await().documents.forEach { it.reference.delete() }
        }
        runCatching { d.collection("users").document(id).delete().await() }
        user.delete().await()
        forgetAccount(user.email.orEmpty())
        signOut()
        null
    }.getOrElse {
        if (it.message.orEmpty().contains("recent", true)) "Demi keamanan, keluar lalu masuk lagi, kemudian ulangi Hapus akun."
        else it.message ?: "Gagal menghapus akun."
    }
}

fun notify(context: Context, title: String, body: String, id: Int) {
    val pi = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(context, Graph.CH_REMINDER)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title).setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pi).setAutoCancel(true).build()
    runCatching { NotificationManagerCompat.from(context).notify(id, n) }
}

/**
 * Memeriksa kreator yang diikuti setiap ±15 menit: kirim notifikasi saat mereka mulai live,
 * dan pengingat sebelum jadwal live mereka. Tanpa server berbayar.
 */
class FollowCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { kotlinx.coroutines.runBlocking { check(context) } } catch (_: Throwable) {} finally { pending.finish() }
        }.start()
    }

    private suspend fun check(context: Context) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val prefs = context.getSharedPreferences("follow_notify", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (p in Graph.social.following()) {
            val liveKey = "live_${p.uid}"
            if (p.live && !prefs.getBoolean(liveKey, false)) {
                prefs.edit().putBoolean(liveKey, true).apply()
                notify(context, "${p.name} sedang live!", "Ketuk untuk menonton. ${p.liveLinks.values.firstOrNull().orEmpty()}", p.uid.hashCode())
            }
            if (!p.live) prefs.edit().putBoolean(liveKey, false).apply()
            for ((title, at) in p.schedule) {
                val key = "sch_${p.uid}_$at"
                if (at - now in 0..20 * 60_000L && !prefs.getBoolean(key, false)) {
                    prefs.edit().putBoolean(key, true).apply()
                    notify(context, "${p.name} segera live", "$title, sekitar ${((at - now) / 60000).coerceAtLeast(1)} menit lagi.", (p.uid + at).hashCode())
                }
            }
        }
    }

    companion object {
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(context, 77, Intent(context, FollowCheckReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi)
        }
    }
}
