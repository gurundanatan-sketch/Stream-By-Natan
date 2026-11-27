package com.natan.streambynatan.features

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Pengenal suara bahasa Indonesia yang terus mendengarkan selama live.
 * Satu sumber untuk: subtitle, perintah suara, ayat otomatis, mengikuti naskah & lirik, transkrip.
 *
 * Android 13+: suara diambil dari mikrofon siaran (tidak berebut mikrofon).
 * Android lebih lama: pengenal memakai mikrofon sendiri (bisa kurang stabil di beberapa HP).
 */
class SpeechHub(private val context: Context) {
    interface Listener {
        fun onPartial(text: String) {}
        fun onFinal(text: String) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<Listener>()
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var running = false
    private var pipeOut: OutputStream? = null
    private val pcmQueue = ArrayBlockingQueue<ShortArray>(64)
    private var writer: Thread? = null
    val usesStreamAudio: Boolean get() = Build.VERSION.SDK_INT >= 33
    var language = "id-ID"

    fun addListener(l: Listener) { synchronized(listeners) { listeners.add(l) } }
    fun removeListener(l: Listener) { synchronized(listeners) { listeners.remove(l) } }
    private fun each(block: (Listener) -> Unit) = synchronized(listeners) { listeners.toList() }.forEach(block)

    /** Dipanggil dari utas audio siaran dengan PCM mono 16 kHz. */
    fun feed(pcm: ShortArray) {
        if (running && usesStreamAudio) pcmQueue.offer(pcm)
    }

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        main.post { startSession() }
    }

    fun stop() {
        running = false
        main.post {
            runCatching { recognizer?.cancel(); recognizer?.destroy() }
            recognizer = null
            closePipe()
        }
    }

    private fun closePipe() {
        runCatching { pipeOut?.close() }
        pipeOut = null
        writer?.interrupt()
        writer = null
        pcmQueue.clear()
    }

    private fun startSession() {
        if (!running) return
        runCatching { recognizer?.destroy() }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) { restart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400) }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (t.isNotBlank()) each { it.onPartial(t) }
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) each { it.onFinal(t) }
                restart(150)
            }
            override fun onSegmentResults(segmentResults: Bundle) {
                val t = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) each { it.onFinal(t) }
            }
            override fun onEndOfSegmentedSession() { restart(150) }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        if (usesStreamAudio) {
            closePipe()
            val pipe = ParcelFileDescriptor.createPipe()
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            val out = pipeOut
            writer = Thread({
                try {
                    while (running && !Thread.currentThread().isInterrupted) {
                        val pcm = pcmQueue.poll(300, TimeUnit.MILLISECONDS) ?: continue
                        val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        bb.asShortBuffer().put(pcm)
                        out?.write(bb.array())
                    }
                } catch (_: Exception) { }
            }, "SpeechPipe").apply { start() }
        }
        runCatching { r.startListening(intent) }.onFailure { restart(1500) }
    }

    private fun restart(delayMs: Long) {
        if (!running) return
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ startSession() }, delayMs)
    }
}
