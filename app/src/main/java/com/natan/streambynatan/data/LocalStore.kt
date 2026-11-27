package com.natan.streambynatan.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

@Serializable
data class LiveSummary(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val profile: String,
    val practice: Boolean,
    val peakViewers: Int,
    val totalLikes: Long,
    val totalCoins: Long,
    val comments: Int,
    val questions: Int,
    val follows: Int,
    val shares: Int,
    val subscribes: Int,
    val newViewers: Int,
    val donations: Long,
    val topSupporters: List<String>,
)

@Serializable
data class LoyalViewer(val id: String, val name: String, val visits: Int, val lastVisit: Long)

@Serializable
data class Moment(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val kind: String, // question | point
)

@Serializable
data class RecordingEntry(
    val id: String,
    val title: String,
    val path: String,
    val createdAt: Long,
    val durationMs: Long,
    val parentId: String = "",
    val kind: String = "full", // full | clip | transcript
)

/** Penyimpanan lokal sederhana berbasis file JSON di memori aplikasi. */
class LocalStore(context: Context) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }

    private fun file(name: String) = File(dir, name)

    @Synchronized
    fun summaries(): List<LiveSummary> = read("summaries.json") {
        AppJson.decodeFromString(ListSerializer(LiveSummary.serializer()), it)
    } ?: emptyList()

    @Synchronized
    fun addSummary(s: LiveSummary) {
        val list = (summaries() + s).takeLast(200)
        write("summaries.json", AppJson.encodeToString(ListSerializer(LiveSummary.serializer()), list))
    }

    private val loyalSer = MapSerializer(String.serializer(), LoyalViewer.serializer())

    @Synchronized
    fun loyalViewers(): MutableMap<String, LoyalViewer> =
        (read("loyal.json") { AppJson.decodeFromString(loyalSer, it) } ?: emptyMap()).toMutableMap()

    @Synchronized
    fun saveLoyal(map: Map<String, LoyalViewer>) = write("loyal.json", AppJson.encodeToString(loyalSer, map))

    @Synchronized
    fun recordings(): List<RecordingEntry> = read("recordings.json") {
        AppJson.decodeFromString(ListSerializer(RecordingEntry.serializer()), it)
    }?.filter { File(it.path).exists() } ?: emptyList()

    @Synchronized
    fun addRecordings(items: List<RecordingEntry>) {
        val list = recordings() + items
        write("recordings.json", AppJson.encodeToString(ListSerializer(RecordingEntry.serializer()), list))
    }

    @Synchronized
    fun deleteRecording(id: String) {
        val list = recordings()
        list.filter { it.id == id || it.parentId == id }.forEach { File(it.path).delete() }
        write(
            "recordings.json",
            AppJson.encodeToString(ListSerializer(RecordingEntry.serializer()), list.filterNot { it.id == id || it.parentId == id })
        )
    }

    private fun <T> read(name: String, parse: (String) -> T): T? =
        runCatching { file(name).takeIf { it.exists() }?.readText()?.let(parse) }.getOrNull()

    private fun write(name: String, text: String) {
        val tmp = file("$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(file(name))
    }
}
