package com.natan.streambynatan.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}

/** Menyimpan pengaturan sebagai JSON di DataStore, dan menyediakannya sebagai StateFlow. */
class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("app_settings_json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val current: AppSettings get() = _state.value

    private fun load(): AppSettings = runBlocking(Dispatchers.IO) {
        val json = runCatching { context.dataStore.data.first()[key] }.getOrNull()
        val loaded = json?.let { runCatching { AppJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
        migrate(loaded)
    }

    /** Pengaturan lama disesuaikan: judul profil dibuat umum (fitur tetap sama). */
    private fun migrate(s: AppSettings): AppSettings = s.copy(
        profiles = s.profiles.map { p ->
            when {
                p.id == "khotbah" && p.name == "Khotbah" -> p.copy(name = "Catatan live")
                p.id == "pujian" && p.name.startsWith("Pujian") -> p.copy(name = "Live music")
                else -> p
            }
        },
        cues = Defaults.cues + s.cues,
        extraPlatforms = Defaults.extraPlatforms.map { d -> s.extraPlatforms.firstOrNull { it.id == d.id } ?: d } +
            s.extraPlatforms.filter { e -> Defaults.extraPlatforms.none { it.id == e.id } },
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        scope.launch {
            context.dataStore.edit { it[key] = AppJson.encodeToString(AppSettings.serializer(), next) }
        }
    }

    fun replaceAll(settings: AppSettings) = update { settings }

    fun exportJson(): String = AppJson.encodeToString(AppSettings.serializer(), current)

    fun importJson(json: String): Boolean = runCatching {
        replaceAll(AppJson.decodeFromString(AppSettings.serializer(), json)); true
    }.getOrDefault(false)

    /** Mengaktifkan profil live: saklar fitur, naskah, latar, dan template pembuka ikut berubah. */
    fun activateProfile(id: String) = update { s ->
        val p = s.profiles.firstOrNull { it.id == id } ?: return@update s
        s.copy(
            activeProfileId = id,
            features = p.features,
            activeNoteId = p.noteId.ifBlank { s.activeNoteId },
            background = if (p.backgroundPath.isNotBlank()) s.background.copy(type = "image", path = p.backgroundPath) else s.background,
            intro = if (p.introTemplate.isNotBlank()) s.intro.copy(introTemplate = p.introTemplate) else s.intro,
        )
    }
}

/**
 * Penyimpanan rahasia (stream key, API key, link overlay Saweria) dengan enkripsi Tink (AES-256-GCM),
 * kuncinya dilindungi Android Keystore. Tidak pernah ikut dicadangkan ke Firebase.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store_tink", Context.MODE_PRIVATE)
    private val aead: com.google.crypto.tink.Aead by lazy {
        com.google.crypto.tink.aead.AeadConfig.register()
        com.google.crypto.tink.integration.android.AndroidKeysetManager.Builder()
            .withSharedPref(context, "stream_by_natan_keyset", "secure_keyset_prefs")
            .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://stream_by_natan_master_key")
            .build()
            .keysetHandle
            .getPrimitive(com.google.crypto.tink.Aead::class.java)
    }
    private val ad = "StreamByNatan".toByteArray()

    fun put(name: String, value: String) {
        if (value.isEmpty()) { prefs.edit().remove(name).apply(); return }
        runCatching {
            val enc = aead.encrypt(value.toByteArray(Charsets.UTF_8), ad)
            prefs.edit().putString(name, Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
        }
    }

    fun get(name: String): String {
        val packed = prefs.getString(name, null) ?: return ""
        return runCatching { String(aead.decrypt(Base64.decode(packed, Base64.NO_WRAP), ad), Charsets.UTF_8) }.getOrDefault("")
    }

    companion object {
        const val YOUTUBE_KEY = "youtube_key"
        const val TIKTOK_KEY = "tiktok_key"
        const val EULER_API_KEY = "euler_api_key"
        const val YOUTUBE_API_KEY = "youtube_api_key"
        const val SAWERIA_OVERLAY = "saweria_overlay"
    }
}
