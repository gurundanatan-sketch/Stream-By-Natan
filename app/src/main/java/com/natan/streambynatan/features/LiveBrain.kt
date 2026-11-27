package com.natan.streambynatan.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.TextPaint
import com.natan.streambynatan.core.Graph
import com.natan.streambynatan.data.AppSettings
import com.natan.streambynatan.data.FaqItem
import com.natan.streambynatan.data.LiveSummary
import com.natan.streambynatan.data.LoyalViewer
import com.natan.streambynatan.data.SecureStore
import com.natan.streambynatan.live.LiveBus
import com.natan.streambynatan.live.LiveEngine
import com.natan.streambynatan.live.LiveEvent
import com.natan.streambynatan.live.LiveMode
import com.natan.streambynatan.live.Platform
import com.natan.streambynatan.overlay.BannerItem
import com.natan.streambynatan.overlay.CardItem
import com.natan.streambynatan.overlay.ImageItem
import com.natan.streambynatan.overlay.QrItem
import com.natan.streambynatan.overlay.StageHost
import com.natan.streambynatan.overlay.StageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * "Otak" live: mengolah semua komentar & kejadian, membacakan TTS, efek suara, antrean pertanyaan,
 * doa, polling, FAQ, penonton setia, target, papan pendukung, pengumuman, timer, perintah suara,
 * subtitle, naskah khotbah, ayat otomatis, lirik, dan transkrip.
 */
class LiveBrain(private val context: Context, private val engine: LiveEngine, private val host: StageHost) {
    private val g = Graph
    private val s: AppSettings get() = g.settings.current
    private val stage: StageView get() = host.stage
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------ antrean pertanyaan
    data class Question(val platform: Platform, val name: String, val text: String)
    val questions = CopyOnWriteArrayList<Question>()
    @Volatile var currentQuestion: Question? = null; private set
    @Volatile var holding = false; private set
    private val missed = CopyOnWriteArrayList<LiveEvent.Comment>()

    // ------------------------------------------------ doa, polling, FAQ
    data class Prayer(val name: String, val text: String)
    val prayers = CopyOnWriteArrayList<Prayer>()
    private var prayerIndex = 0
    data class Poll(val question: String, val options: List<String>, val votes: ConcurrentHashMap<String, Int> = ConcurrentHashMap())
    @Volatile var poll: Poll? = null; private set
    private var pendingFaq: FaqItem? = null
    private val faqCounts = HashMap<FaqItem, Int>()

    // ------------------------------------------------ penonton & angka
    private val loyal = g.local.loyalViewers()
    private val seenThisLive = HashSet<String>()
    private val supporters = ConcurrentHashMap<String, Long>() // nama -> koin
    private val likesBy = ConcurrentHashMap<String, Long>()
    private val commentsBy = ConcurrentHashMap<String, Int>()
    private val reachedTargets = HashSet<String>()
    /** Nama penonton yang baru berkomentar (untuk tombol mute saat live). */
    val recentCommenters = java.util.Collections.synchronizedList(ArrayList<String>())

    // ------------------------------------------------ suara
    private val transcript = StringBuilder()
    private var script: ParsedScript? = null
    private var scriptPos = 0
    private var scriptWaiting = false
    private var activeVerse: ParsedScript.Seg.Verse? = null
    private var voiceVerseRef: String? = null
    private var voiceVerseText = StringBuilder()
    private var lastSpeechAt = 0L
    private var lyricIndex = 0
    private var startedAt = 0L

    private val speechListener = object : SpeechHub.Listener {
        override fun onPartial(text: String) { main.post { onSpeech(text, final = false) } }
        override fun onFinal(text: String) { main.post { onSpeech(text, final = true) } }
    }

    fun start() {
        startedAt = System.currentTimeMillis()
        LiveBus.clearFeed()
        applyStageSettings()
        val f = s.features
        if (f.saweria) host.setSaweria(g.secure.get(SecureStore.SAWERIA_OVERLAY))
        if (f.music) g.sounds.startMusic(s.music.trackId, s.music.customPath, s.music.loop)
        if (f.guests) g.guests.start(s.guest).also { ok ->
            if (ok) {
                g.guests.onRemotesChanged = { refreshGuestTiles() }
                g.guests.onFrame = { refreshGuestTiles() }
            }
        }
        // komentar & kejadian
        scope.launch { LiveBus.events.collect { runCatching { handle(it) } } }
        // TikTok & YouTube
        if (!engine.practice) {
            if (s.enableTikTok) g.tiktok.connect(s.tiktokUsername, g.secure.get(SecureStore.EULER_API_KEY))
            if (s.enableYouTube) g.ytChat.start(s.youtubeVideoId, g.secure.get(SecureStore.YOUTUBE_API_KEY))
        }
        // pengenal suara
        val needSpeech = f.subtitles || f.voiceCommands || f.notesWhisper || f.verseFromScript || f.verseFromVoice || f.lyrics || f.transcript
        if (needSpeech) { g.speech.addListener(speechListener); g.speech.start() }
        loadScript()
        if (f.notesWhisper && script != null) main.postDelayed({ whisperNextSentence() }, if (f.intro) s.intro.durationSec * 1000L + 1500 else 2000)
        scope.launch { timerLoop() }
        scope.launch { announcementLoop() }
        scope.launch { housekeepingLoop() }
    }

    fun stop() {
        g.speech.removeListener(speechListener)
        g.speech.stop()
        g.tiktok.disconnect()
        g.ytChat.stop()
        g.guests.stop()
        g.tts.stopWhisper()
        recordSummary()
        speakSummary()
        g.local.saveLoyal(loyal)
        g.firebase.syncLoyal(loyal.values.toList())
        scope.cancel()
    }

    fun applyStageSettings() {
        val f = s.features
        stage.transition = s.transition
        stage.tickerText = if (f.ticker) s.ticker.text else ""
        stage.tickerSpeed = s.ticker.speed
        stage.overlayText = if (f.overlayText) s.overlayText else ""
        stage.subtitleScale = s.subtitles.textSize
        if (!f.subtitles) stage.subtitle = ""
        engine.updateMicFx()
        g.mixer.musicGain = s.music.volume
    }

    // ================================================================ kejadian
    private fun say(text: String) {
        if (text.isBlank()) return
        val clean = TextTools.expand(text, s.abbreviations)
        if (s.features.commentsToBroadcast) g.tts.speakPublic(clean) else g.tts.whisper(clean)
    }

    private fun thank(text: String) { g.tts.speakPublic(TextTools.expand(text, s.abbreviations)) }

    private fun sfx(id: String) { if (s.features.giftSfx && id.isNotBlank()) g.sounds.play(id, s.giftSfx.volume, s.customSounds) }

    private suspend fun handle(e: LiveEvent) {
        val f = s.features
        if (e.userId.isNotBlank() && s.mutedViewers.any { it.equals(e.name, true) || it.equals(e.userId, true) }) return
        when (e) {
            is LiveEvent.Comment -> onComment(e)
            is LiveEvent.Gift -> {
                LiveBus.stats { it.copy(coins = it.coins + e.totalCoins) }
                supporters.merge(e.name, e.totalCoins.toLong(), Long::plus)
                LiveBus.feed("🎁 ${e.name}: ${e.count}× ${e.giftName} (${e.totalCoins} koin)")
                sfx(if (e.totalCoins >= s.giftSfx.bigThresholdCoins) s.giftSfx.bigSound else s.giftSfx.smallSound)
                if (f.thanksGift) thank(TextTools.fill(s.templates.gift, "nama" to e.name, "hadiah" to e.giftName, "jumlah" to e.count, "koin" to e.totalCoins))
                checkTargets()
            }
            is LiveEvent.Like -> {
                LiveBus.stats { if (e.platform == Platform.TIKTOK) it.copy(likesTikTok = maxOf(it.likesTikTok + e.count, e.total)) else it.copy(likesYouTube = maxOf(it.likesYouTube, e.total)) }
                if (e.name.isNotBlank()) likesBy.merge(e.name, e.count.toLong(), Long::plus)
                checkTargets()
            }
            is LiveEvent.Follow -> {
                LiveBus.stats { it.copy(follows = it.follows + 1) }
                LiveBus.feed("➕ ${e.name} follow")
                sfx(s.giftSfx.followSound)
                if (f.thanksFollow) thank(TextTools.fill(s.templates.follow, "nama" to e.name))
            }
            is LiveEvent.Subscribe -> {
                LiveBus.stats { it.copy(subscribes = it.subscribes + 1) }
                LiveBus.feed("🔔 ${e.name} subscribe")
                sfx(s.giftSfx.followSound)
                if (f.thanksSubscribe) thank(TextTools.fill(s.templates.subscribe, "nama" to e.name))
            }
            is LiveEvent.Share -> {
                LiveBus.stats { it.copy(shares = it.shares + 1) }
                sfx(s.giftSfx.shareSound)
                if (f.thanksShare) thank(TextTools.fill(s.templates.share, "nama" to e.name))
            }
            is LiveEvent.SuperChat -> {
                supporters.merge(e.name, e.micros / 1_000_000L * 10, Long::plus)
                LiveBus.feed("💲 ${e.name}: ${e.amountText} ${e.text}")
                sfx(s.giftSfx.bigSound)
                if (f.thanksGift) thank(TextTools.fill(s.templates.superChat, "nama" to e.name, "jumlah" to e.amountText))
                if (e.text.isNotBlank()) onComment(LiveEvent.Comment(e.platform, e.userId, e.name, e.text))
            }
            is LiveEvent.Member -> {
                sfx(s.giftSfx.bigSound)
                thank(TextTools.fill(s.templates.member, "nama" to e.name))
            }
            is LiveEvent.Donation -> {
                LiveBus.stats { it.copy(donations = it.donations + e.amount) }
                supporters.merge(e.name, e.amount / 100, Long::plus)
                LiveBus.feed("💝 Saweria ${e.name}: ${e.amountText} ${e.message}")
                thank(TextTools.fill(s.templates.saweria, "nama" to e.name, "jumlah" to e.amountText, "pesan" to e.message))
            }
            is LiveEvent.Join -> greet(e.platform, e.userId, e.name, fromComment = false)
            is LiveEvent.Viewers -> {
                LiveBus.stats {
                    val n = if (e.platform == Platform.TIKTOK) it.copy(viewersTikTok = e.count) else it.copy(viewersYouTube = e.count)
                    n.copy(peakViewers = maxOf(n.peakViewers, n.viewers))
                }
                checkTargets()
            }
        }
    }

    private suspend fun onComment(raw: LiveEvent.Comment) {
        val f = s.features
        var text = raw.text.trim()
        if (text.isEmpty()) return
        if (f.profanityFilter) text = TextTools.censor(text, s.profanity).first
        val e = raw.copy(text = text)
        LiveBus.stats { it.copy(comments = it.comments + 1) }
        commentsBy.merge(e.name, 1, Int::plus)
        synchronized(recentCommenters) { recentCommenters.remove(e.name); recentCommenters.add(0, e.name); while (recentCommenters.size > 30) recentCommenters.removeAt(recentCommenters.size - 1) }
        LiveBus.feed("💬 ${e.platform.label} · ${e.name}: $text")
        greet(e.platform, e.userId, e.name, fromComment = true)

        // polling
        poll?.let { p ->
            val opt = text.trim().uppercase().removePrefix("PILIH ").trim()
            val idx = if (opt.length == 1 && opt[0] in 'A'..'Z') opt[0] - 'A' else opt.toIntOrNull()?.minus(1) ?: -1
            if (idx in p.options.indices) { p.votes[e.platform.name + e.userId.ifBlank { e.name }] = idx; return }
        }
        // permohonan doa
        if (f.prayerList) TextTools.startsWithAny(text, s.prayerTriggers)?.let { trig ->
            val body = text.substring(minOf(text.length, trig.length)).trim(' ', ':', ',', '-')
            prayers += Prayer(e.name, body.ifBlank { text })
            LiveBus.feed("🙏 Permohonan doa dari ${e.name}")
            return
        }
        // FAQ
        if (f.faq) s.faq.firstOrNull { item -> TextTools.matchesAny(text, item.keywords) != null }?.let { item ->
            val n = (faqCounts[item] ?: 0) + 1
            faqCounts[item] = n
            pendingFaq = item
            g.tts.whisper("Ada yang tanya ${item.keywords.first()}" + if (n > 1) ", sudah $n kali." else ".")
        }
        // terjemahan
        var readText = text
        if (f.translate) g.translator.translate(text, s.translate.targetLanguage)?.let { (tr, lang) ->
            LiveBus.feed("🌐 ($lang) $tr")
            if (s.translate.readTranslated) readText = tr
        }
        // pertanyaan
        if (f.questionQueue && TextTools.isQuestion(text, s.questionWords)) {
            questions += Question(e.platform, e.name, readText)
            LiveBus.stats { it.copy(questions = it.questions + 1) }
            LiveBus.announce("Pertanyaan baru dari ${e.name}. ${questions.size} di antrean")
            return
        }
        // komentar biasa tetap dibacakan
        if (holding || currentQuestion != null) { if (f.missedSummary) missed += e.copy(text = readText); return }
        if (f.readComments && !g.mixer.isSpeaking) say(TextTools.fill(s.templates.comment, "nama" to e.name, "pesan" to readText))
        else if (f.missedSummary) missed += e.copy(text = readText)
    }

    /** Sapaan penonton baru & penonton setia. */
    private fun greet(platform: Platform, userId: String, name: String, fromComment: Boolean) {
        if (name.isBlank()) return
        val key = platform.name + ":" + userId.ifBlank { name }
        if (!seenThisLive.add(key)) return
        val prev = loyal[key]
        val visits = (prev?.visits ?: 0) + 1
        loyal[key] = LoyalViewer(key, name, visits, System.currentTimeMillis())
        val f = s.features
        if (prev != null && visits >= 2) {
            if (f.loyalGreeting) thank(TextTools.fill(s.templates.loyal, "nama" to name, "jumlah" to visits))
        } else if (fromComment) {
            LiveBus.stats { it.copy(newViewers = it.newViewers + 1) }
            if (f.newViewerGreeting) thank(TextTools.fill(s.templates.newViewer, "nama" to name))
        }
    }

    private fun checkTargets() {
        if (!s.features.targets) return
        val st = LiveBus.stats.value
        val t = s.targets
        fun hit(key: String, label: String, value: Long, target: Int) {
            if (target > 0 && value >= target && reachedTargets.add(key)) {
                thank(TextTools.fill(s.templates.targetReached, "target" to label))
                sfx(s.giftSfx.bigSound)
                stage.show("banner_target", BannerItem("🎉 Target $label tercapai!"), 6000)
            }
        }
        hit("likes_tt", "${t.likes} like TikTok", st.likesTikTok, t.likes)
        hit("likes_yt", "${t.likes} like YouTube", st.likesYouTube, t.likes)
        hit("coins", "${t.coins} koin", st.coins, t.coins)
        hit("viewers_tt", "${t.viewers} penonton TikTok", st.viewersTikTok.toLong(), t.viewers)
        hit("viewers_yt", "${t.viewers} penonton YouTube", st.viewersYouTube.toLong(), t.viewers)
        if (t.combined) {
            hit("likes_all", "${t.likes} like gabungan", st.likes, t.likes)
            hit("viewers_all", "${t.viewers} penonton gabungan", st.viewers.toLong(), t.viewers)
        }
    }

    // ================================================================ aksi (tombol, jendela mengambang, perintah suara)
    fun nextQuestion() {
        g.recordings.endMoment()
        currentQuestion = null
        val q = questions.firstOrNull()
        if (q == null) { g.tts.whisper("Antrean pertanyaan kosong."); flushMissedHint(); return }
        questions.remove(q)
        currentQuestion = q
        holding = false
        g.recordings.beginMoment(q.text, "question")
        say(TextTools.fill(s.templates.question, "nama" to q.name, "pesan" to q.text))
        LiveBus.announce("Pertanyaan dari ${q.name}. Sisa ${questions.size}")
    }

    fun repeatQuestion() { currentQuestion?.let { say(TextTools.fill(s.templates.question, "nama" to it.name, "pesan" to it.text)) } ?: g.tts.whisper("Belum ada pertanyaan yang aktif.") }

    fun toggleHold() {
        holding = !holding
        g.tts.whisper(if (holding) "Ditahan. Komentar ditampung dulu." else "Lanjut.")
    }

    fun skipQuestion() {
        val q = currentQuestion ?: questions.firstOrNull()
        if (q == null) { g.tts.whisper("Tidak ada pertanyaan."); return }
        if (q == currentQuestion) { currentQuestion = null } else questions.remove(q)
        g.tts.whisper("Dilewati.")
    }

    private fun flushMissedHint() { if (missed.isNotEmpty()) g.tts.whisper("Ada ${missed.size} komentar yang terlewat.") }

    /** Ringkasan komentar yang terlewat saat menjawab pertanyaan atau saat ditahan. */
    fun missedSummary() {
        val list = missed.toList(); missed.clear()
        if (list.isEmpty()) { g.tts.whisper("Tidak ada komentar yang terlewat."); return }
        val cats = linkedMapOf(
            "minta lagu" to listOf("lagu", "nyanyi", "musik"),
            "bertanya soal jadwal" to listOf("jadwal", "kapan", "jam berapa"),
            "minta didoakan" to listOf("doa", "doakan"),
            "menyapa" to listOf("halo", "hai", "salam", "selamat", "shalom", "syalom"),
            "memuji" to listOf("keren", "mantap", "bagus", "berkat", "amin"),
        )
        val parts = cats.mapNotNull { (label, kw) ->
            val n = list.count { c -> kw.any { TextTools.normalize(c.text).contains(it) } }
            if (n > 0) "$n orang $label" else null
        }
        val names = list.map { it.name }.distinct().take(5).joinToString(", ")
        g.tts.whisper("Ada ${list.size} komentar. " + (if (parts.isNotEmpty()) parts.joinToString(", ") + ". " else "") + "Dari $names.")
    }

    fun readNextPrayer() {
        if (prayerIndex >= prayers.size) { g.tts.whisper(if (prayers.isEmpty()) "Belum ada permohonan doa." else "Semua permohonan doa sudah dibacakan."); return }
        val p = prayers[prayerIndex++]
        g.tts.whisper("Dari ${p.name}: ${p.text}")
    }

    fun readAllPrayers() {
        if (prayers.isEmpty()) { g.tts.whisper("Belum ada permohonan doa."); return }
        prayers.drop(prayerIndex).forEach { g.tts.whisper("Dari ${it.name}: ${it.text}") }
        prayerIndex = prayers.size
    }

    fun topSupporters(show: Boolean = true) {
        val top = supporters.entries.sortedByDescending { it.value }.take(3)
        val text = if (top.isEmpty()) "Belum ada pendukung dengan gift hari ini."
        else "Pendukung teratas hari ini: " + top.joinToString(", ") { "${it.key} ${it.value} koin" } + "."
        thank(text)
        if (show && top.isNotEmpty()) stage.show("full_leader", CardItem(top.mapIndexed { i, e -> "${i + 1}. ${e.key} — ${e.value} koin" }.joinToString("\n"),
            "Terima kasih atas dukungannya!", s.faqCard, header = "Pendukung teratas"), 10_000)
    }

    fun startPoll(question: String, options: List<String>) {
        val opts = options.filter { it.isNotBlank() }.take(6)
        if (opts.size < 2) return
        poll = Poll(question, opts)
        val letters = opts.mapIndexed { i, o -> "${'A' + i}. $o" }
        thank("Polling: $question. " + letters.joinToString(". ") + ". Ketik hurufnya di komentar.")
        stage.show("poll", BannerItem("📊 $question\n" + letters.joinToString("   ")), s.pollDurationSec * 1000L)
        main.postDelayed({ finishPoll() }, s.pollDurationSec * 1000L)
    }

    fun finishPoll() {
        val p = poll ?: return
        poll = null
        val counts = p.options.indices.map { i -> p.votes.values.count { it == i } }
        val total = counts.sum()
        val lines = p.options.mapIndexed { i, o -> "${'A' + i}. $o: ${counts[i]} suara" }
        val win = counts.indices.maxByOrNull { counts[it] } ?: 0
        thank(if (total == 0) "Polling selesai, belum ada suara." else "Hasil polling: ${p.options[win]} menang dengan ${counts[win]} dari $total suara.")
        stage.show("full_poll", CardItem(lines.joinToString("\n"), "Total $total suara", s.faqCard, header = p.question), 10_000)
    }

    fun showQr(show: Boolean) {
        if (!show) { stage.hide("qr"); return }
        val link = s.saweria.pageLink
        if (link.isBlank()) { g.tts.whisper("Link Saweria belum diisi di pengaturan."); return }
        stage.show(if (s.qrStyle.size == "full") "full_qr" else "qr", QrItem(link, s.qrStyle), s.qrStyle.durationSec * 1000L)
    }

    fun showOffering(show: Boolean) {
        val o = s.offering
        if (!show) { stage.hide("offering"); stage.hide("full_offering"); return }
        val bmp = loadBitmap(o.imagePath)
        if (bmp == null && o.text.isBlank()) { g.tts.whisper("Gambar persembahan belum dipilih."); return }
        val item = if (bmp != null) ImageItem(bmp, o.size, o.position, o.text) else BannerItem(o.text)
        stage.show(if (o.size == "full") "full_offering" else "offering", item, o.durationSec * 1000L)
    }

    /** Menampilkan jawaban dari pertanyaan yang sering ditanyakan (gambar/TTS/keduanya). */
    fun showFaqAnswer() {
        val item = pendingFaq ?: run { g.tts.whisper("Belum ada pertanyaan berulang yang dikenali."); return }
        pendingFaq = null
        if (item.mode != "tts") {
            val bmp = loadBitmap(item.imagePath)
            if (bmp != null) stage.show("full_faq", ImageItem(bmp, "full", "center", item.answer), 12_000)
            else stage.show("full_faq", CardItem(item.answer, item.keywords.first(), s.faqCard, header = "Jawaban"), 12_000)
        }
        if (item.mode != "image") thank(item.answer)
    }

    fun hideOverlays() { stage.hideAll() }

    fun announceTime() {
        val m = (System.currentTimeMillis() - startedAt) / 60000
        g.tts.whisper("Sudah live $m menit.")
    }

    // ================================================================ suara & naskah
    private fun onSpeech(text: String, final: Boolean) {
        val f = s.features
        lastSpeechAt = System.currentTimeMillis()
        if (f.subtitles) { stage.subtitle = text; main.removeCallbacks(clearSubtitle); main.postDelayed(clearSubtitle, if (final) 3500 else 5000) }
        if (final && f.transcript) {
            val sec = (System.currentTimeMillis() - startedAt) / 1000
            transcript.append(String.format("[%02d:%02d] ", sec / 60, sec % 60)).append(text).append('\n')
        }
        if (final && f.voiceCommands && handleCommand(text)) return
        if (f.verseFromScript) verseFromScript(text)
        if (f.verseFromVoice && engine.mode != LiveMode.SCREEN) verseFromVoice(text, final)
        if (f.notesWhisper) followScript(text, final)
        if (f.lyrics) followLyrics(text)
    }

    private val clearSubtitle = Runnable { stage.subtitle = "" }

    private fun handleCommand(text: String): Boolean {
        val n = TextTools.normalize(text)
        for ((action, phrases) in s.voiceCommands) {
            val hit = phrases.firstOrNull { p -> val k = TextTools.normalize(p); k.isNotEmpty() && n.contains(k) } ?: continue
            when (action) {
                "next_question" -> nextQuestion()
                "repeat_question" -> repeatQuestion()
                "hold_question" -> toggleHold()
                "skip_question" -> skipQuestion()
                "missed_summary" -> missedSummary()
                "top_supporters" -> topSupporters()
                "read_prayers" -> readNextPrayer()
                "show_qr" -> showQr(true)
                "hide_qr" -> showQr(false)
                "show_offering" -> showOffering(true)
                "hide_offering" -> showOffering(false)
                "show_answer" -> showFaqAnswer()
                "hide_overlay" -> hideOverlays()
                "next_point" -> nextPoint()
                "camera_front" -> engine.switchFrontBack(true)
                "camera_back" -> engine.switchFrontBack(false)
                "switch_camera" -> {
                    val rest = n.substringAfter(TextTools.normalize(hit)).trim()
                    if (rest.isBlank() || !engine.switchTo(rest)) g.tts.whisper("Kamera $rest tidak ditemukan.")
                }
                "mic_off" -> engine.setMic(false)
                "mic_on" -> engine.setMic(true)
                "poll_result" -> finishPoll()
                "live_time" -> announceTime()
                "music_toggle" -> if (g.sounds.musicPlaying) g.sounds.stopMusic() else g.sounds.startMusic(s.music.trackId, s.music.customPath, true)
                else -> continue
            }
            return true
        }
        // kalimat perintah FAQ yang bisa diatur sendiri
        if (s.faqCommandPhrases.any { n.contains(TextTools.normalize(it)) }) { showFaqAnswer(); return true }
        return false
    }

    private fun loadScript() {
        val note = s.notes.firstOrNull { it.id == s.activeNoteId } ?: s.notes.firstOrNull()
        script = note?.let { ParsedScript(it) }
        scriptPos = 0
    }

    /** Naskah dibisikkan pelan satu kalimat, lalu menunggu sampai kamu selesai mengucapkannya. */
    private fun whisperNextSentence() {
        val sc = script ?: return
        if (scriptPos >= sc.segments.size) { g.tts.whisper("Naskah selesai."); return }
        val seg = sc.segments[scriptPos]
        if (seg is ParsedScript.Seg.Heading) {
            g.recordings.beginMoment(seg.text, "point")
            g.tts.whisper("Poin: ${seg.text}", s.tts.scriptRate)
            scriptPos++
            main.postDelayed({ whisperNextSentence() }, 600)
            return
        }
        val text = if (seg is ParsedScript.Seg.Verse) "${seg.ref}. ${seg.text}" else seg.text
        scriptWaiting = false
        g.tts.whisper(text, s.tts.scriptRate) { main.post { scriptWaiting = true } }
    }

    private fun followScript(spoken: String, final: Boolean) {
        val sc = script ?: return
        if (!scriptWaiting || scriptPos >= sc.segments.size) return
        val target = sc.segments[scriptPos].text
        val last = TextTools.lastWords(target, 2).joinToString(" ")
        val done = TextTools.normalize(spoken).contains(last) || (final && TextTools.overlap(spoken, target) >= 0.5f)
        if (done) { scriptWaiting = false; scriptPos++; main.postDelayed({ whisperNextSentence() }, 300) }
    }

    fun nextPoint() {
        val sc = script ?: return
        val next = sc.headings.firstOrNull { it > scriptPos }
        if (next == null) { g.tts.whisper("Sudah poin terakhir."); return }
        g.tts.stopWhisper()
        scriptPos = next
        whisperNextSentence()
    }

    fun nextSentence() {
        val sc = script ?: return
        if (scriptPos < sc.segments.size) scriptPos++
        g.tts.stopWhisper(); whisperNextSentence()
    }

    /** Ayat dari naskah: muncul saat disebut, hilang saat kata terakhir ayat selesai dibaca. */
    private fun verseFromScript(spoken: String) {
        val sc = script ?: return
        val cur = activeVerse
        if (cur == null) {
            val v = sc.findVerse(spoken) ?: return
            activeVerse = v
            showVerse(v.ref, v.text, cardBg())
            main.removeCallbacks(verseSafetyHide); main.postDelayed(verseSafetyHide, 90_000)
        } else {
            val last = TextTools.lastWords(cur.text, 3).joinToString(" ")
            if (last.isNotBlank() && TextTools.normalize(spoken).contains(last)) hideVerse()
        }
    }

    private val verseSafetyHide = Runnable { hideVerse() }

    private fun hideVerse() {
        activeVerse = null
        voiceVerseRef = null
        stage.hide("full_verse")
    }

    private fun showVerse(ref: String, text: String, bg: Bitmap?) {
        stage.show("full_verse", CardItem(text, ref, s.verseCard, bg))
    }

    private fun cardBg(): Bitmap? = loadBitmap(s.verseCard.bgImage)

    /** Ayat dari suara (live biasa tanpa naskah): teks diambil dari bacaanmu sendiri. */
    private fun verseFromVoice(text: String, final: Boolean) {
        val ref = voiceVerseRef
        if (ref == null) {
            val found = Scripture.detect(text) ?: return
            voiceVerseRef = found
            voiceVerseText = StringBuilder()
            showVerse(found, "…", cardBg())
            scheduleVoiceVerseHide()
            return
        }
        if (Scripture.detect(text) == ref) return // masih menyebut referensinya
        val shown = (voiceVerseText.toString() + " " + text).trim()
        if (final) voiceVerseText.append(' ').append(text)
        showVerse(ref, shown.takeLast(400), cardBg())
        scheduleVoiceVerseHide()
    }

    private val voiceVerseHide = Runnable { if (System.currentTimeMillis() - lastSpeechAt >= s.silenceToHideVerseMs) hideVerse() else scheduleVoiceVerseHide() }
    private fun scheduleVoiceVerseHide() { main.removeCallbacks(voiceVerseHide); main.postDelayed(voiceVerseHide, s.silenceToHideVerseMs.toLong()) }

    /** Lirik pujian: baris yang sedang dinyanyikan muncul di bawah layar. */
    private fun followLyrics(spoken: String) {
        val song = s.lyrics.firstOrNull { it.id == s.activeLyricId } ?: s.lyrics.firstOrNull() ?: return
        val lines = song.lines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return
        var best = -1; var score = 0.35f
        for (i in lyricIndex until minOf(lines.size, lyricIndex + 4)) {
            val sc = TextTools.overlap(spoken, lines[i]); if (sc > score) { score = sc; best = i }
        }
        if (best < 0 && lyricIndex > 0) { // mungkin kembali ke reff/awal
            for (i in lines.indices) { val sc = TextTools.overlap(spoken, lines[i]); if (sc > 0.6f) { best = i; break } }
        }
        if (best >= 0) {
            lyricIndex = best
            stage.show("lyric", LyricItem(lines[best], lines.getOrNull(best + 1) ?: ""), 9000)
        }
    }

    fun transcriptText(): String = if (s.features.transcript) transcript.toString() else ""

    // ================================================================ loop waktu
    private suspend fun timerLoop() {
        var lastReminder = 0L
        var warned = false
        while (scope.isActive) {
            delay(20_000)
            if (!s.features.liveTimer) continue
            val min = (System.currentTimeMillis() - startedAt) / 60000
            val t = s.timer
            if (t.reminderMinutes > 0 && min > 0 && min % t.reminderMinutes == 0L && min != lastReminder) {
                lastReminder = min
                g.tts.whisper("Sudah live $min menit.")
            }
            if (t.limitMinutes > 0) {
                if (!warned && min >= t.limitMinutes - t.warnBeforeMinutes) { warned = true; g.tts.whisper("${t.warnBeforeMinutes} menit lagi waktu live habis.") }
                if (min >= t.limitMinutes) { g.tts.whisper("Waktu live sudah habis."); delay(60_000) }
            }
        }
    }

    private suspend fun announcementLoop() {
        val last = HashMap<Int, Long>()
        while (scope.isActive) {
            delay(15_000)
            if (!s.features.announcements) continue
            val now = System.currentTimeMillis()
            s.announcements.forEachIndexed { i, a ->
                if (!a.enabled || a.text.isBlank()) return@forEachIndexed
                val prev = last[i] ?: startedAt
                if (now - prev >= a.everyMinutes.coerceAtLeast(1) * 60_000L) {
                    last[i] = now
                    if (a.mode != "screen") thank(a.text)
                    if (a.mode != "tts") stage.show("banner_ann", BannerItem(a.text), 10_000)
                }
            }
        }
    }

    private suspend fun housekeepingLoop() {
        while (scope.isActive) {
            delay(5_000)
            applyStageSettings()
        }
    }

    /** Di mode berbagi layar, tamu tampil sebagai kotak kecil. Di mode kamera, tamu disusun oleh StageCompositor. */
    private fun refreshGuestTiles() {
        stage.guestTiles.clear()
        if (engine.composesGuests) return
        for (r in g.guests.stage.value) if (r.status == "onstage") r.frame?.let { stage.guestTiles[r.id] = r.name to it }
    }

    /** Mute penonton tertentu: komentarnya tidak dibaca dan tidak diproses lagi. */
    fun muteViewer(name: String) {
        g.settings.update { it.copy(mutedViewers = (it.mutedViewers + name).distinct()) }
        g.tts.whisper("$name di-mute.")
    }

    /** Ringkasan setelah live, dibacakan untukmu. */
    private fun speakSummary() {
        val st = LiveBus.stats.value
        val min = (System.currentTimeMillis() - startedAt) / 60000
        val top = supporters.entries.maxByOrNull { it.value }?.key
        val text = "Live selesai. Durasi $min menit. Puncak ${st.peakViewers} penonton, ${st.likes} like, ${st.coins} koin, " +
            "${st.comments} komentar, ${st.questions} pertanyaan, ${st.follows} follow, ${st.subscribes} subscribe, ${st.shares} share, " +
            "${st.newViewers} penonton baru." + (top?.let { " Pendukung teratas: $it." } ?: "")
        g.tts.whisper(text)
        LiveBus.feed("📋 $text")
    }

    private fun recordSummary() {
        val st = LiveBus.stats.value
        val summary = LiveSummary(
            UUID.randomUUID().toString(), startedAt, System.currentTimeMillis(), s.activeProfileId, engine.practice,
            st.peakViewers, st.likes, st.coins, st.comments, st.questions, st.follows, st.shares, st.subscribes,
            st.newViewers, st.donations, supporters.entries.sortedByDescending { it.value }.take(5).map { "${it.key} (${it.value})" },
        )
        g.local.addSummary(summary)
        g.firebase.saveSummary(summary)
    }

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        if (path.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /** Baris lirik di sepertiga bawah layar. */
    private class LyricItem(private val line: String, private val next: String) : StageView.Item() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        private val n = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCBFD4FF.toInt(); typeface = Typeface.SERIF }
        override fun draw(c: Canvas, w: Int, h: Int, elapsedMs: Long) {
            t.textSize = w * 0.058f; n.textSize = w * 0.04f
            val l1 = StageView.layout(line, t, (w * 0.86f).toInt(), Layout.Alignment.ALIGN_CENTER, 3)
            val l2 = StageView.layout(next, n, (w * 0.86f).toInt(), Layout.Alignment.ALIGN_CENTER, 2)
            val total = l1.height + l2.height + 30
            val top = h * 0.68f - total / 2f
            p.color = 0x99000000.toInt()
            c.drawRoundRect(RectF(w * 0.04f, top - 20, w * 0.96f, top + total + 10), 28f, 28f, p)
            c.save(); c.translate(w * 0.07f, top); l1.draw(c); c.translate(0f, l1.height + 14f); l2.draw(c); c.restore()
        }
    }
}
