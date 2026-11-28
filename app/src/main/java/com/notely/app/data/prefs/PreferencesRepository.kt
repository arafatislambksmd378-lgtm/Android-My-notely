package com.notely.app.data.prefs

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notely.app.ui.theme.Accent
import com.notely.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notely_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val WORKSPACE_NAME = stringPreferencesKey("workspace_name")
        val WORKSPACE_ICON = stringPreferencesKey("workspace_icon")
        val AUTOSAVE = booleanPreferencesKey("autosave")
        val SPELLCHECK = booleanPreferencesKey("spellcheck")
        val DEFAULT_FONT = stringPreferencesKey("default_font")
        val DEFAULT_FONT_SIZE = intPreferencesKey("default_font_size")
        val DEFAULT_LINE_SPACING = stringPreferencesKey("default_line_spacing")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val PIN_FAILURES = intPreferencesKey("pin_failures")
        val PIN_RETRY_AT = longPreferencesKey("pin_retry_at")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val accent: Flow<Accent> = context.dataStore.data.map {
        runCatching { Accent.valueOf(it[Keys.ACCENT] ?: "PURPLE") }.getOrDefault(Accent.PURPLE)
    }
    val workspaceName: Flow<String> = context.dataStore.data.map { it[Keys.WORKSPACE_NAME] ?: "Writer" }
    val workspaceIcon: Flow<String> = context.dataStore.data.map { it[Keys.WORKSPACE_ICON] ?: "W" }
    val autosave: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTOSAVE] ?: true }
    val spellcheck: Flow<Boolean> = context.dataStore.data.map { it[Keys.SPELLCHECK] ?: true }
    val defaultFont: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_FONT] ?: "Default" }
    val defaultFontSize: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_FONT_SIZE] ?: 16 }
    val defaultLineSpacing: Flow<Float> = context.dataStore.data.map { it[Keys.DEFAULT_LINE_SPACING]?.toFloatOrNull() ?: 1.55f }
    // A single DataStore emission prevents an unlocked frame while separate lock flows initialize.
    val lockConfiguration: Flow<Pair<Boolean, String>?> = context.dataStore.data.map {
        (it[Keys.APP_LOCK] ?: false) to (it[Keys.PIN_HASH] ?: "")
    }
    val appLock: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK] ?: false }
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        val raw = it[Keys.RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { s -> s.isNotBlank() }
    }
    private val pinMutex = Mutex()
    val pinRetryAt: Flow<Long> = context.dataStore.data.map { it[Keys.PIN_RETRY_AT] ?: 0L }
    val pinHash: Flow<String> = context.dataStore.data.map { it[Keys.PIN_HASH] ?: "" }
    val aiBaseUrl: Flow<String> = context.dataStore.data.map { it[Keys.AI_BASE_URL] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[Keys.AI_MODEL] ?: "" }
    val aiEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AI_ENABLED] ?: false }

    /**
     * The provider key is stored encrypted with an Android Keystore AES key (never plaintext,
     * never logged). Encryption failures are reported to the user, never silently stored as plaintext.
     */
    suspend fun getAiApiKey(): String {
        val raw = context.dataStore.data.first()[Keys.AI_API_KEY] ?: ""
        return Crypto.decrypt(raw)
    }

    /** Serial verification with persisted cooldown; recomposition/relaunch cannot reset attempts. */
    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        pinMutex.withLock {
            val prefs = context.dataStore.data.first()
            val now = System.currentTimeMillis()
            if ((prefs[Keys.PIN_RETRY_AT] ?: 0L) > now) return@withLock false
            val stored = prefs[Keys.PIN_HASH].orEmpty()
            val ok = com.notely.app.util.Security.verifyPin(pin, stored)
            val upgraded = if (ok && com.notely.app.util.Security.isLegacyHash(stored))
                com.notely.app.util.Security.hashPin(pin) else null
            context.dataStore.edit {
                if (ok) {
                    it[Keys.PIN_FAILURES] = 0
                    it[Keys.PIN_RETRY_AT] = 0L
                    if (upgraded != null) it[Keys.PIN_HASH] = upgraded
                } else {
                    val attempts = ((it[Keys.PIN_FAILURES] ?: 0) + 1).coerceAtMost(20)
                    it[Keys.PIN_FAILURES] = attempts
                    if (attempts >= 5) {
                        val waitMs = (30_000L * (1L shl (attempts - 5).coerceAtMost(5))).coerceAtMost(900_000L)
                        it[Keys.PIN_RETRY_AT] = System.currentTimeMillis() + waitMs
                    }
                }
            }
            ok
        }
    }

    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = m.name }
    suspend fun setAccent(a: Accent) = context.dataStore.edit { it[Keys.ACCENT] = a.name }
    suspend fun setWorkspace(name: String, icon: String) = context.dataStore.edit {
        it[Keys.WORKSPACE_NAME] = name; it[Keys.WORKSPACE_ICON] = icon
    }
    suspend fun setAutosave(v: Boolean) = context.dataStore.edit { it[Keys.AUTOSAVE] = v }
    suspend fun setSpellcheck(v: Boolean) = context.dataStore.edit { it[Keys.SPELLCHECK] = v }
    suspend fun setDefaultFont(f: String) = context.dataStore.edit { it[Keys.DEFAULT_FONT] = f }
    suspend fun setDefaultFontSize(s: Int) = context.dataStore.edit { it[Keys.DEFAULT_FONT_SIZE] = s }
    suspend fun setDefaultLineSpacing(v: Float) = context.dataStore.edit { it[Keys.DEFAULT_LINE_SPACING] = v.toString() }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = v }
    suspend fun setPinHash(h: String) = context.dataStore.edit { it[Keys.PIN_HASH] = h }
    suspend fun addRecentSearch(q: String) {
        val cur = recentSearches.first().toMutableList()
        cur.remove(q); cur.add(0, q)
        context.dataStore.edit { it[Keys.RECENT_SEARCHES] = cur.take(8).joinToString("\n") }
    }
    suspend fun setAi(baseUrl: String, model: String, apiKey: String?, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            // Null preserves the existing credential; empty explicitly removes it.
            val encrypted = apiKey?.let { if (it.isBlank()) "" else Crypto.encrypt(it) }
            context.dataStore.edit {
                it[Keys.AI_BASE_URL] = baseUrl; it[Keys.AI_MODEL] = model
                if (encrypted != null) it[Keys.AI_API_KEY] = encrypted
                it[Keys.AI_ENABLED] = enabled
            }
        }

    suspend fun clearAiKey() = context.dataStore.edit { it.remove(Keys.AI_API_KEY) }

}

/** AES-GCM via the Android Keystore for user-supplied API keys. */
object Crypto {
    private const val KEY_ALIAS = "notely_ai_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): javax.crypto.SecretKey? = runCatching {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
            ?: javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                            android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                    ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
    }.getOrNull()

    fun encrypt(plain: String): String {
        val key = secretKey() ?: error("Android Keystore is unavailable")
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (stored == "plain") return ""
        val parts = stored.split(":")
        if (parts.size != 2) return stored // unencrypted legacy value
        return runCatching {
            val key = secretKey() ?: return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
