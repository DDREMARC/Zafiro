package com.dremarc.grokbot.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings persisted in EncryptedSharedPreferences (AES-256 GCM values, SIV keys).
 * The API key is never hardcoded: the user pastes it on the Settings screen.
 */
class EncryptedSettingsStore(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences = openPrefs(context)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_MODEL, settings.model.trim())
            .putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            .putBoolean(KEY_WEB_SEARCH, settings.webSearchEnabled)
            .putBoolean(KEY_DARK_THEME, settings.darkTheme)
            .apply()
        state.value = settings
    }

    private fun read(): AppSettings = AppSettings(
        apiKey = prefs.getString(KEY_API_KEY, null).orEmpty(),
        model = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: AppSettings.DEFAULT_MODEL,
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, null)
            ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
        webSearchEnabled = prefs.getBoolean(KEY_WEB_SEARCH, false),
        darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
    )

    private companion object {
        const val FILE_NAME = "grokbot_secure_prefs"
        const val KEY_API_KEY = "anthropic_api_key"
        const val KEY_MODEL = "model_id"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_WEB_SEARCH = "web_search_enabled"
        const val KEY_DARK_THEME = "dark_theme"

        /**
         * Keystore can fail on some devices (corrupted key or file). In that case the
         * encrypted file is discarded and recreated; the user simply re-enters the key.
         */
        fun openPrefs(context: Context): SharedPreferences = try {
            createEncrypted(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(FILE_NAME)
            createEncrypted(context)
        }

        fun createEncrypted(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
