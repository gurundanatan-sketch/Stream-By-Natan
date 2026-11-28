package com.natan.streambynatan.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.AppSettings
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEngine
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.Platform
import com.natan.streambynatan.overlay.BannerItem
import com.natan.streambynatan.overlay.CardItem
import com.natan.streambynatan.overlay.ImageItem
import com.natan.streambynatan.overlay.StageView
import com.natan.streambynatan.overlay.VideoOverlayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fitur live tambahan (semuanya opsional, bisa dinyalakan di Pengaturan > Fitur):
 * giveaway, kuis, daftar lagu permintaan, peringatan live sepi, pengingat minum & istirahat,
 * peringatan baterai & suhu, susunan acara, TEMA otomatis lewat suara, balas komentar YouTube pakai suara,
 * komentar di layar siaran, papan nama, teks layar, overlay gambar/video, penanda getar/suara/ucapan,
 * laporan latihan Catatan live, dan perintah dari moderator.
 */
class LiveExtras(private val context: Context, private val engine: LiveEngine, private val brain: LiveBrain) {
    private val g = Graph
    private val s: AppSettings get() = g.settings.current
    private val stage: StageView get() = engine.stageHost.stage
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    @Volatile private var lastCommentAt = 0L

    // ------------------------------------------------------------ giveaway
    data class Giveaway(val keyword: String, val prize: String, val endsAt: Long, val entrants: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet()))
    @Volatile var giveaway: Giveaway? = null; private set
    var lastWinner: String? = null; private set

    // ------------------------------------------------------------ kuis
    private var quizIndex = 0
    @Volatile var activeQuiz: com.natan.streambynatan.data.QuizItem? = null; private set
    val quizScores = ConcurrentHashMap<String, Int>()

    // ------------------------------------------------------------ lagu permintaan
    data class SongRequest(val name: String, val song: String)
    val songQueue = CopyOnWriteArrayList<SongRequest>()

    // ------------------------------------------------------------ susunan acara
    @Volatile var rundownIndex = -1; private set
    private var rundownStartedAt = 0L

    // ------------------------------------------------------------ balas YouTube & latihan
    private val ytCommenters = java.util.Collections.synchronizedList(ArrayList<String>())
    private val pointTimes = ArrayList<Pair<String, Long>>()

    fun start() {
        startedAt = System.currentTimeMillis()
        lastCommentAt = startedAt
        applyStage()
        if (s.features.nameplate && s.nameplate.showSeconds > 0) main.postDelayed({ stage.nameplate = null }, s.nameplate.showSeconds * 1000L)
        scope.launch { loop() }
        g.guests.onWaiting = { r -> cue("guest_waiting", "${r.name} masuk ruang tunggu ${r.viaLabel}") }
        g.guests.onGuestMessage = { r, text -> cue("guest_message", "Pesan dari ${r?.name ?: "tamu"}: $text") }
        g.guests.onCommand = { type, data -> onModeratorCommand(type, data) }
    }

    fun stop() {
        if (s.features.practiceReport && engine.practice) practiceReport()
        g.guests.onWaiting = null
        g.guests.onGuestMessage = null
        g.guests.onCommand = null
        stopCueSound()
        scope.cancel()
    }

    /** Teks layar, papan nama, dan posisi diterapkan ke lapisan grafis siaran. */
    fun applyStage() {
        stage.screenText = s.screenText
        val np = s.nameplate
        val showPlate = s.features.nameplate && (np.showSeconds == 0 || System.currentTimeMillis() - startedAt < np.showSeconds * 1000L)
        stage.nameplate = if (showPlate) (np.name.ifBlank { g.social.profile.value.name.ifBlank { g.firebase.displayName ?: "" } } to np.role) else null
        stage.nameplatePosition = np.position
    }

    // ================================================================ komentar
    /** Dipanggil untuk setiap komentar. Mengembalikan true kalau komentar sudah "dipakai" (tidak dibacakan sebagai komentar biasa). */
    fun onComment(e: LiveEvent.Comment): Boolean {
        lastCommentAt = System.currentTimeMillis()
        val text = e.text.trim()
        if (e.platform == Platform.YOUTUBE) synchronized(ytCommenters) { ytCommenters.remove(e.name); ytCommenters.add(0, e.name); while (ytCommenters.size > 50) ytCommenters.removeAt(ytCommenters.size - 1) }
        if (s.commentDisplay.onStream) stage.pushComment(e.name, text)
        // giveaway
        giveaway?.let { gw ->
            if (System.currentTimeMillis() < gw.endsAt && TextTools.normalize(text).contains(TextTools.normalize(gw.keyword))) {
                if (gw.entrants.add(e.platform.label + ":" + e.name)) LiveBus.feed("🎉 ${e.name} ikut giveaway (${gw.entrants.size})")
                return true
            }
        }
        // kuis: yang pertama menjawab benar dapat poin
        activeQuiz?.let { q ->
            if (TextTools.normalize(text).contains(TextTools.normalize(q.answer)) && q.answer.isNotBlank()) {
                activeQuiz = null
                quizScores.merge(e.name, 1, Int::plus)
                brain.publicSay("Benar! ${e.name} menjawab ${q.answer} paling cepat. Dapat satu poin.")
                stage.show("banner_quiz", BannerItem("✅ ${e.name} benar: ${q.answer}"), 7000)
                g.sounds.play("sfx_success", s.giftSfx.volume, s.customSounds)
                return true
            }
        }
        // lagu permintaan
        if (s.features.songRequests) {
            val kw = s.songRequestKeyword.trim().lowercase()
            if (kw.isNotBlank() && text.lowercase().startsWith(kw)) {
                val song = text.substring(kw.length).trim(' ', ':', '-')
                if (song.isNotBlank()) {
                    songQueue += SongRequest(e.name, song)
                    LiveBus.feed("🎵 Permintaan lagu dari ${e.name}: $song (${songQueue.size} di antrean)")
                    LiveBus.announce("Permintaan lagu dari ${e.name}")
                }
                return true
            }
        }
        return false
    }

    // ================================================================ giveaway
    fun startGiveaway(prize: String = s.giveaway.prize) {
        val c = s.giveaway
        val gw = Giveaway(c.keyword, prize, System.currentTimeMillis() + c.durationSec * 1000L)
        giveaway = gw
        val what = if (prize.isNotBlank()) " berhadiah $prize" else ""
        brain.publicSay("Giveaway$what dimulai! Ketik ${c.keyword} di komentar untuk ikut. Waktunya ${c.durationSec / 60} menit ${c.durationSec % 60} detik.")
        stage.show("banner_giveaway", BannerItem("🎁 GIVEAWAY$what\nKetik ${c.keyword} di komentar"), c.durationSec * 1000L)
        main.postDelayed({ if (giveaway === gw) drawWinner() }, c.durationSec * 1000L)
    }

    fun drawWinner() {
        val gw = giveaway ?: run { g.tts.whisper("Belum ada giveaway yang berjalan."); return }
        giveaway = null
        val list = gw.entrants.toList()
        if (list.isEmpty()) { brain.publicSay("Giveaway selesai, belum ada yang ikut."); return }
        val winner = list.random().substringAfter(":")
        lastWinner = winner
        g.sounds.play("sfx_drumroll", s.giftSfx.volume, s.customSounds)
        main.postDelayed({
            brain.publicSay("Selamat! Pemenang giveaway adalah $winner, dari ${list.size} peserta.")
            stage.show("full_giveaway", CardItem("🏆 $winner", "Dari ${list.size} peserta" + if (gw.prize.isNotBlank()) " · Hadiah: ${gw.prize}" else "", s.faqCard, header = "Pemenang giveaway"), 12_000)
            g.sounds.play("sfx_applause_synth", s.giftSfx.volume, s.customSounds)
            LiveBus.announce("Pemenang giveaway: $winner")
        }, 2500)
    }

    // ================================================================ kuis
    fun nextQuiz() {
        val list = s.quiz.filter { it.question.isNotBlank() }
        if (list.isEmpty()) { g.tts.whisper("Belum ada soal kuis. Tambahkan di Pengaturan, Alat live."); return }
        val q = list[quizIndex % list.size]; quizIndex++
        activeQuiz = q
        brain.publicSay("Kuis! ${q.question} Jawab di komentar, yang paling cepat benar dapat poin.")
        stage.show("banner_quiz", BannerItem("❓ KUIS\n${q.question}"), 60_000)
    }

    fun showQuizScores() {
        val top = quizScores.entries.sortedByDescending { it.value }.take(5)
        if (top.isEmpty()) { g.tts.whisper("Belum ada skor kuis."); return }
        stage.show("full_quizscore", CardItem(top.mapIndexed { i, e -> "${i + 1}. ${e.key} — ${e.value} poin" }.joinToString("\n"), "Terima kasih sudah ikut kuis!", s.faqCard, header = "Papan skor kuis"), 12_000)
        brain.publicSay("Juara kuis sementara: ${top.first().key} dengan ${top.first().value} poin.")
    }

    // ================================================================ lagu permintaan
    fun nextSong() {
        val r = songQueue.firstOrNull() ?: run { g.tts.whisper("Antrean lagu kosong."); return }
        songQueue.remove(r)
        stage.show("banner_song", BannerItem("🎵 Permintaan ${r.name}:\n${r.song}"), 15_000)
        g.tts.whisper("Lagu berikutnya: ${r.song}, dari ${r.name}. Sisa ${songQueue.size}.")
        LiveBus.announce("Lagu berikutnya ${r.song}")
    }

    // ================================================================ susunan acara
    fun nextRundown() {
        val list = s.rundown
        if (list.isEmpty()) { g.tts.whisper("Susunan acara belum diisi."); return }
        rundownIndex = (rundownIndex + 1).coerceAtMost(list.size)
        if (rundownIndex >= list.size) { g.tts.whisper("Susunan acara selesai."); return }
        rundownStartedAt = System.currentTimeMillis()
        val item = list[rundownIndex]
        g.tts.whisper("Acara: ${item.title}, ${item.minutes} menit.")
        LiveBus.hostNote("Acara sekarang: ${item.title} (${item.minutes} menit)")
    }

    // ================================================================ overlay galeri
    fun showOverlay(item: com.natan.streambynatan.data.OverlayMedia) {
        val id = if (item.size == "full") "full_ov_${item.id}" else "ov_${item.id}"
        if (stage.isShowing(id)) { stage.hide(id); return }
        scope.launch {
            val shown: StageView.Item? = withContext(Dispatchers.IO) {
                if (item.type == "video") VideoOverlayItem.load(context, Uri.parse(item.uri)).takeIf { it.isNotEmpty() }?.let { VideoOverlayItem(it, item.size, item.position) }
                else loadBitmap(item.uri)?.let { ImageItem(it, item.size, item.position) }
            }
            if (shown == null) g.tts.whisper("Overlay ${item.name} tidak bisa dibuka.") else stage.show(id, shown)
        }
    }

    fun showImageOnce(uri: Uri) {
        scope.launch {
            val b = withContext(Dispatchers.IO) { loadBitmap(uri.toString()) } ?: return@launch
            stage.show("full_import", ImageItem(b, "full", "center"), 15_000)
        }
    }

    // ================================================================ suara: TEMA & balas komentar
    /** Dipanggil untuk setiap ucapan host. Mengembalikan true kalau ucapan dipakai sebagai perintah. */
    fun onSpeech(text: String, final: Boolean): Boolean {
        val n = TextTools.normalize(text)
        // TEMA: cukup ada kata "tema" di kalimat, gambar tema langsung tampil penuh layar
        val trigger = TextTools.normalize(s.themeTrigger.ifBlank { "tema" })
        if (final && s.features.themes && n.split(" ").contains(trigger)) {
            val themes = s.themes.filter { it.imagePath.isNotBlank() }
            if (themes.isNotEmpty()) {
                val after = n.substringAfter(trigger).trim()
                val best = themes.maxByOrNull { t -> TextTools.overlap(after.ifBlank { n }, t.title) } ?: themes.first()
                val pick = if (themes.size == 1 || TextTools.overlap(after.ifBlank { n }, best.title) > 0f) best else themes.first()
                showTheme(pick)
                return true
            }
        }
        // balas komentar YouTube pakai suara: "balas Budi terima kasih sudah bergabung"
        val rt = TextTools.normalize(s.voiceReplyTrigger.ifBlank { "balas" })
        if (final && s.features.voiceReply && n.startsWith("$rt ")) {
            val rest = text.trim().substring(minOf(text.trim().length, s.voiceReplyTrigger.length)).trim()
            val names = synchronized(ytCommenters) { ytCommenters.toList() }
            val who = names.firstOrNull { rest.lowercase().startsWith(it.lowercase()) }
                ?: names.firstOrNull { nm -> TextTools.normalize(rest).startsWith(TextTools.normalize(nm).split(" ").first()) }
            if (who == null) { g.tts.whisper("Nama penonton YouTube tidak ditemukan."); return true }
            val msg = rest.substring(minOf(rest.length, who.length)).trim().ifBlank { rest }
            replyYouTube(who, msg)
            return true
        }
        return false
    }

    fun showTheme(t: com.natan.streambynatan.data.ThemeImage) {
        scope.launch {
            val b = withContext(Dispatchers.IO) { loadBitmap(t.imagePath) }
            if (b == null) { g.tts.whisper("Gambar tema ${t.title} tidak bisa dibuka."); return@launch }
            stage.show("full_theme", ImageItem(b, "full", "center", t.title), s.themeDurationSec.coerceAtLeast(3) * 1000L)
            LiveBus.announce("Tema ${t.title} ditampilkan")
        }
    }

    fun replyYouTube(name: String, message: String) {
        val chat = g.ytChat.chatId ?: run { g.tts.whisper("Live chat YouTube belum tersambung."); return }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { g.ytApi.sendChat(chat, "@$name $message") } }
                .onSuccess { g.tts.whisper("Balasan untuk $name terkirim."); LiveBus.feed("↩️ Kamu → $name: $message") }
                .onFailure { g.tts.whisper("Balasan gagal. Pastikan YouTube sudah dihubungkan.") }
        }
    }

    // ================================================================ latihan Catatan live
    fun onPoint(title: String) { synchronized(pointTimes) { pointTimes += title to System.currentTimeMillis() } }

    private fun practiceReport() {
        val list = synchronized(pointTimes) { pointTimes.toList() }
        if (list.isEmpty()) return
        val end = System.currentTimeMillis()
        val lines = list.mapIndexed { i, (t, at) ->
            val next = list.getOrNull(i + 1)?.second ?: end
            val sec = (next - at) / 1000
            "$t: ${sec / 60} menit ${sec % 60} detik"
        }
        val text = "Laporan latihan. " + lines.joinToString(". ") + "."
        LiveBus.feed("📊 $text")
        g.tts.whisper(text)
    }

    // ================================================================ moderator
    private fun onModeratorCommand(type: String, data: Map<String, Any?>) {
        val by = data["by"] as? String ?: "Moderator"
        when (type) {
            "mute_viewer" -> (data["name"] as? String)?.let { brain.muteViewer(it); cue("moderator", "$by membisukan $it") }
            "next_question" -> { brain.nextQuestion(); cue("moderator", "$by: pertanyaan berikutnya") }
            "skip_question" -> { brain.skipQuestion(); cue("moderator", "$by melewati pertanyaan") }
            "hold_question" -> brain.toggleHold()
            "admit_next" -> g.guests.admitNext(1)
            "note" -> (data["text"] as? String)?.let { cue("moderator", "Pesan $by: $it") }
        }
    }

    /** Status untuk panel moderator (diperbarui tiap beberapa detik). */
    private fun publishRoomState() {
        val st = LiveBus.stats.value
        g.guests.publishRoomState(mapOf(
            "live" to st.isLive,
            "viewers" to st.viewers,
            "questions" to brain.questions.take(20).map { "${it.name}: ${it.text}" },
            "currentQuestion" to (brain.currentQuestion?.let { "${it.name}: ${it.text}" } ?: ""),
            "commenters" to synchronized(brain.recentCommenters) { brain.recentCommenters.take(20) },
            "updatedAt" to System.currentTimeMillis(),
        ))
    }

    // ================================================================ penanda (getar / suara / ucapan)
    private var cuePlayer: MediaPlayer? = null

    /** Penanda untuk host saja: pola getar, efek suara pribadi, dan/atau ucapan TTS. Bisa diatur per kejadian. */
    fun cue(event: String, message: String) {
        LiveBus.feed("🔔 $message")
        LiveBus.announce(message)
        if (!s.features.cues) return
        val c = s.cues[event] ?: return
        if (c.vibrate) vibrate(c.pattern)
        if (c.sound.isNotBlank()) playPrivate(c.sound)
        if (c.speak) g.tts.speakAlert(message)
    }

    private fun vibrate(pattern: String) = runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)!!.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)!!
        val timings = when (pattern) {
            "double" -> longArrayOf(0, 120, 120, 120)
            "triple" -> longArrayOf(0, 100, 100, 100, 100, 100)
            "long" -> longArrayOf(0, 700)
            else -> longArrayOf(0, 140)
        }
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    /** Efek suara yang hanya terdengar host (tidak ikut ke siaran). */
    private fun playPrivate(id: String) = runCatching {
        val res = g.sounds.rawId(id); if (res == 0) return@runCatching
        stopCueSound()
        val afd = context.resources.openRawResourceFd(res)
        cuePlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            setOnCompletionListener { it.release(); if (cuePlayer === it) cuePlayer = null }
            prepare(); start()
        }
    }

    private fun stopCueSound() { runCatching { cuePlayer?.release() }; cuePlayer = null }

    // ================================================================ loop waktu
    private suspend fun loop() {
        var lastDrink = startedAt
        var lastQuiet = 0L
        var batteryWarned = false
        var tempWarned = false
        var tick = 0
        while (scope.isActive) {
            delay(5_000)
            tick++
            val f = s.features
            val now = System.currentTimeMillis()
            applyStage()
            if (tick % 2 == 0) publishRoomState()
            // giveaway: info sisa peserta di layar host
            // pengingat minum & istirahat
            if (f.drinkReminder && s.drinkReminderMinutes > 0 && now - lastDrink >= s.drinkReminderMinutes * 60_000L) {
                lastDrink = now
                g.tts.whisper("Jangan lupa minum dan tarik napas sebentar.")
            }
            // peringatan live sepi
            if (f.quietWarning && s.quietWarnMinutes > 0 && now - lastCommentAt >= s.quietWarnMinutes * 60_000L && now - lastQuiet >= s.quietWarnMinutes * 60_000L) {
                lastQuiet = now
                g.tts.whisper(QUIET_IDEAS.random())
            }
            // baterai & suhu (tiap 30 detik)
            if (tick % 6 == 0) {
                val (level, temp) = battery()
                LiveBus.stats { it.copy(battery = level, temperatureC = temp) }
                if (f.batteryWarning) {
                    if (level in 0..s.batteryWarnPercent && !batteryWarned) { batteryWarned = true; cue("battery_low", "Baterai tinggal $level persen. Colokkan charger.") }
                    if (level > s.batteryWarnPercent + 5) batteryWarned = false
                    if (temp >= s.temperatureWarnC && !tempWarned) { tempWarned = true; cue("battery_low", "HP mulai panas, ${temp.toInt()} derajat. Coba turunkan kualitas video.") }
                    if (temp in 0f..(s.temperatureWarnC - 3f)) tempWarned = false
                }
            }
            // susunan acara
            if (f.rundown && rundownIndex in s.rundown.indices) {
                val item = s.rundown[rundownIndex]
                val elapsedMin = (now - rundownStartedAt) / 60000
                if (item.minutes > 0 && elapsedMin >= item.minutes && rundownStartedAt > 0) {
                    rundownStartedAt = now + 365L * 24 * 3600 * 1000 // supaya tidak diulang
                    val next = s.rundown.getOrNull(rundownIndex + 1)
                    g.tts.whisper("Waktu ${item.title} habis." + (next?.let { " Berikutnya: ${it.title}." } ?: " Ini acara terakhir."))
                }
            }
        }
    }

    private fun battery(): Pair<Int, Float> = runCatching {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1 to -1f
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -10) / 10f
        (level * 100 / scale) to temp
    }.getOrDefault(-1 to -1f)

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        if (path.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    companion object {
        val QUIET_IDEAS = listOf(
            "Live agak sepi. Coba ajak penonton menjawab pertanyaan sederhana.",
            "Belum ada komentar beberapa menit. Coba sapa penonton dan minta mereka menyebut asal kotanya.",
            "Coba mulai polling atau kuis singkat supaya penonton ikut aktif.",
            "Coba ajak penonton membagikan live ini ke temannya.",
            "Coba ceritakan hal menarik hari ini, lalu tanyakan pendapat penonton.",
        )
    }
}
