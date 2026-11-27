package com.natan.streambynatan.features

import java.text.Normalizer

object TextTools {

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9?\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Huruf berulang dirapikan: "anjiiiing" -> "anjing" (untuk mendeteksi kata kasar yang disamarkan). */
    private fun squeeze(word: String) = word.replace(Regex("(.)\\1+"), "$1")

    /** Filter kata kasar: kata yang cocok diganti "***". */
    fun censor(text: String, words: List<String>): Pair<String, Boolean> {
        if (words.isEmpty()) return text to false
        val set = words.map { squeeze(normalize(it)) }.toSet()
        var found = false
        val out = text.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { token ->
            val n = squeeze(
                normalize(token).replace("4", "a").replace("1", "i").replace("3", "e").replace("0", "o")
                    .replace(Regex("[0-9?]"), "")
            )
            if (n.isNotBlank() && set.any { n == it || (it.length >= 4 && n.contains(it)) }) { found = true; "***" } else token
        }
        return out to found
    }

    /** Memanjangkan singkatan sebelum dibacakan TTS, misal "gmn" -> "gimana". */
    fun expand(text: String, abbreviations: Map<String, String>): String {
        if (abbreviations.isEmpty()) return text
        val lower = abbreviations.mapKeys { it.key.lowercase() }
        return Regex("[A-Za-z]+").replace(text) { m -> lower[m.value.lowercase()] ?: m.value }
    }

    /** Apakah komentar ini pertanyaan? Tanda tanya, kata tanya baku/tidak baku, "mau tanya", dan singkatan. */
    fun isQuestion(text: String, questionWords: List<String>): Boolean {
        if (text.contains('?')) return true
        val n = " " + normalize(text) + " "
        return questionWords.any { w ->
            val k = normalize(w)
            k.isNotEmpty() && (n.contains(" $k ") || n.startsWith(" $k"))
        }
    }

    /** Mengganti {nama}, {jumlah}, dst di template kalimat. */
    fun fill(template: String, vararg pairs: Pair<String, Any?>): String {
        var s = template
        for ((k, v) in pairs) s = s.replace("{$k}", v?.toString() ?: "")
        return s.replace(Regex("\\{[a-z]+\\}"), "").replace(Regex("\\s+"), " ").trim()
    }

    fun matchesAny(text: String, phrases: List<String>): String? {
        val n = normalize(text)
        return phrases.firstOrNull { p -> val k = normalize(p); k.isNotEmpty() && n.contains(k) }
    }

    fun startsWithAny(text: String, phrases: List<String>): String? {
        val n = normalize(text)
        return phrases.sortedByDescending { it.length }.firstOrNull { p -> val k = normalize(p); k.isNotEmpty() && n.startsWith(k) }
    }

    /** Kemiripan kata (0..1) untuk mengikuti bacaan naskah/lirik. */
    fun overlap(spoken: String, target: String): Float {
        val a = normalize(spoken).split(' ').filter { it.length > 1 }.toSet()
        val b = normalize(target).split(' ').filter { it.length > 1 }
        if (b.isEmpty() || a.isEmpty()) return 0f
        return b.count { it in a }.toFloat() / b.size
    }

    fun lastWords(text: String, n: Int): List<String> = normalize(text).split(' ').filter { it.isNotBlank() }.takeLast(n)
}
