package com.natan.streambynatan.features

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/** Terjemahan teks komentar offline (ML Kit). Model diunduh otomatis saat pertama dipakai. */
class CommentTranslator {
    private val identifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, com.google.mlkit.nl.translate.Translator>()

    /** Mengembalikan (teks terjemahan, kode bahasa asal) atau null kalau sudah bahasa tujuan. */
    suspend fun translate(text: String, target: String): Pair<String, String>? {
        if (text.length < 4) return null
        val lang = runCatching { identifier.identifyLanguage(text).await() }.getOrNull() ?: return null
        if (lang == "und" || lang == target || (target == "id" && (lang == "ms" || lang == "jv" || lang == "su"))) return null
        val src = TranslateLanguage.fromLanguageTag(lang) ?: return null
        val dst = TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.INDONESIAN
        val key = "$src>$dst"
        val tr = translators.getOrPut(key) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build())
        }
        return runCatching {
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            tr.translate(text).await() to lang
        }.getOrNull()
    }

    fun close() {
        translators.values.forEach { it.close() }
        identifier.close()
    }
}
