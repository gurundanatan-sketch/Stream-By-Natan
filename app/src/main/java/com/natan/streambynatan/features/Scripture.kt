package com.natan.streambynatan.features

import com.natan.streambynatan.data.NoteScript

/** Pengenal referensi ayat Alkitab dalam ucapan dan naskah (bahasa Indonesia). */
object Scripture {
    val books = listOf(
        "Kejadian", "Keluaran", "Imamat", "Bilangan", "Ulangan", "Yosua", "Hakim-hakim", "Rut", "1 Samuel", "2 Samuel",
        "1 Raja-raja", "2 Raja-raja", "1 Tawarikh", "2 Tawarikh", "Ezra", "Nehemia", "Ester", "Ayub", "Mazmur", "Amsal",
        "Pengkhotbah", "Kidung Agung", "Yesaya", "Yeremia", "Ratapan", "Yehezkiel", "Daniel", "Hosea", "Yoel", "Amos",
        "Obaja", "Yunus", "Mikha", "Nahum", "Habakuk", "Zefanya", "Hagai", "Zakharia", "Maleakhi", "Matius", "Markus",
        "Lukas", "Yohanes", "Kisah Para Rasul", "Roma", "1 Korintus", "2 Korintus", "Galatia", "Efesus", "Filipi",
        "Kolose", "1 Tesalonika", "2 Tesalonika", "1 Timotius", "2 Timotius", "Titus", "Filemon", "Ibrani", "Yakobus",
        "1 Petrus", "2 Petrus", "1 Yohanes", "2 Yohanes", "3 Yohanes", "Yudas", "Wahyu",
    )

    private val units = mapOf(
        "nol" to 0, "satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5, "enam" to 6, "tujuh" to 7,
        "delapan" to 8, "sembilan" to 9, "sepuluh" to 10, "sebelas" to 11, "seratus" to 100,
    )

    /** "tiga belas" -> "13", "dua puluh satu" -> "21", "pertama" -> "1". */
    fun wordsToDigits(text: String): String {
        val words = TextTools.normalize(text)
            .replace("pertama", "satu").replace("kedua", "dua").replace("ketiga", "tiga")
            .split(' ')
        val out = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val base = units[w]
            if (base == null) { out += w; i++; continue }
            var v: Int = base
            var used = 1
            val next = words.getOrNull(i + 1)
            if (next == "belas" && v < 10) { v += 10; used = 2 }
            else if (next == "puluh" && v < 10) {
                v *= 10; used = 2
                val u = units[words.getOrNull(i + 2) ?: ""]
                if (u != null && u < 10) { v += u; used = 3 }
            } else if (next == "ratus" && v < 10) { v *= 100; used = 2 }
            out += v.toString(); i += used
        }
        return out.joinToString(" ")
    }

    fun key(ref: String): String = wordsToDigits(ref)
        .replace(Regex("\\b(pasal|ayat|sampai|sampe|hingga|dan|kitab|surat|injil)\\b"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private val bookKeys = books.map { it to TextTools.normalize(it.replace("-", " ")).replace(" ", "\\s?") }

    /** Mencari referensi ayat di ucapan, misal "Yohanes pasal 3 ayat 16" -> "Yohanes 3:16". */
    fun detect(spoken: String): String? {
        val s = wordsToDigits(spoken).replace("-", " ")
        for ((book, k) in bookKeys.sortedByDescending { it.second.length }) {
            val m = Regex("\\b$k\\s+(?:pasal\\s+)?(\\d{1,3})\\s+(?:ayat\\s+)?(\\d{1,3})(?:\\s+(?:sampai|sampe|hingga)\\s+(\\d{1,3}))?").find(s)
            if (m != null) {
                val (ch, v, v2) = m.destructured
                return "$book $ch:$v" + if (v2.isNotBlank()) "-$v2" else ""
            }
        }
        return null
    }
}

/** Naskah khotbah / catatan poin yang dipecah menjadi judul poin, kalimat, dan ayat. */
class ParsedScript(note: NoteScript) {
    sealed class Seg(val text: String) {
        class Heading(text: String) : Seg(text)
        class Sentence(text: String) : Seg(text)
        class Verse(val ref: String, text: String) : Seg(text) { val key = Scripture.key(ref) }
    }

    val title = note.title
    val segments: List<Seg> = buildList {
        val verseRe = Regex("^\\[([^\\]]+)]\\s*(.*)$")
        for (raw in note.content.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#") -> add(Seg.Heading(line.trimStart('#').trim()))
                verseRe.matches(line) -> { val (r, t) = verseRe.find(line)!!.destructured; add(Seg.Verse(r.trim(), t.trim())) }
                else -> line.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.forEach { add(Seg.Sentence(it.trim())) }
            }
        }
    }
    val verses: List<Seg.Verse> = segments.filterIsInstance<Seg.Verse>()
    val headings: List<Int> = segments.indices.filter { segments[it] is Seg.Heading }

    fun findVerse(spoken: String): Seg.Verse? {
        val k = Scripture.key(spoken)
        return verses.firstOrNull { v ->
            val parts = v.key.split(' ')
            // cukup sampai nomor ayat pertama, misal "yohanes 3 16"
            val needed = parts.take(parts.indexOfFirst { it.all(Char::isDigit) }.let { if (it < 0) parts.size else minOf(parts.size, it + 2) }).joinToString(" ")
            needed.isNotBlank() && k.contains(needed)
        }
    }
}
