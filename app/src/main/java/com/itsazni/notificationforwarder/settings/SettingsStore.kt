package com.itsazni.notificationforwarder.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class FilterMode { ALL_APPS, WHITELIST, BLACKLIST }
enum class AuthMode { NONE, BEARER, CUSTOM }

data class AppSettings(
    val webhookUrl: String,
    val forwardingEnabled: Boolean,
    val filterMode: FilterMode,
    val filterPackages: Set<String>,
    val authMode: AuthMode,
    val bearerToken: String,
    val customHeadersRaw: String,
    val maxRetries: Int,
    val batchSize: Int
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("notif_settings", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WEBHOOK_URL, value.trim()) }

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_FORWARDING_ENABLED, value) }

    var filterMode: FilterMode
        get() = FilterMode.valueOf(prefs.getString(KEY_FILTER_MODE, FilterMode.ALL_APPS.name)!!)
        set(value) = prefs.edit { putString(KEY_FILTER_MODE, value.name) }

    var filterPackages: Set<String>
        get() = parsePackages(prefs.getString(KEY_FILTER_PACKAGES, "") ?: "")
        set(value) = prefs.edit { putString(KEY_FILTER_PACKAGES, value.joinToString(",")) }

    var authMode: AuthMode
        get() = AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, AuthMode.NONE.name)!!)
        set(value) = prefs.edit { putString(KEY_AUTH_MODE, value.name) }

    var bearerToken: String
        get() = prefs.getString(KEY_BEARER_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BEARER_TOKEN, value.trim()) }

    var customHeadersRaw: String
        get() = prefs.getString(KEY_CUSTOM_HEADERS_RAW, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_HEADERS_RAW, value) }

    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRY, 10)
        set(value) = prefs.edit { putInt(KEY_MAX_RETRY, value.coerceIn(1, 20)) }

    var batchSize: Int
        get() = prefs.getInt(KEY_BATCH_SIZE, 20)
        set(value) = prefs.edit { putInt(KEY_BATCH_SIZE, value.coerceIn(1, 100)) }

    fun readAll(): AppSettings {
        return AppSettings(
            webhookUrl = webhookUrl,
            forwardingEnabled = forwardingEnabled,
            filterMode = filterMode,
            filterPackages = filterPackages,
            authMode = authMode,
            bearerToken = bearerToken,
            customHeadersRaw = customHeadersRaw,
            maxRetries = maxRetries,
            batchSize = batchSize
        )
    }

    fun parseHeaders(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        customHeadersRaw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains(':')) {
                return@forEach
            }
            val idx = trimmed.indexOf(':')
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    companion object {
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_FILTER_MODE = "filter_mode"
        private const val KEY_FILTER_PACKAGES = "filter_packages"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_CUSTOM_HEADERS_RAW = "custom_headers_raw"
        private const val KEY_MAX_RETRY = "max_retry"
        private const val KEY_BATCH_SIZE = "batch_size"

        fun parsePackages(raw: String): Set<String> {
            return raw.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }
}
