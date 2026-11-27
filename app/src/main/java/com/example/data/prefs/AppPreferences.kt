package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

/** Local persistent device preferences for theme, optional API key, and update source settings. */
object AppPreferences {
    private const val PREFS_NAME = "schedule_maker_prefs"
    private const val LEGACY_PREFS_NAME = "app_prefs"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    private const val KEY_UPDATE_OWNER = "update_github_owner"
    private const val KEY_UPDATE_REPO = "update_github_repo"
    private const val KEY_UPDATE_TOKEN = "update_github_token"
    private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
    private const val KEY_RELEASES_JSON = "cached_releases_json"
    private const val KEY_RELEASES_ETAG = "cached_releases_etag"
    private const val KEY_SKIPPED_VERSION = "skipped_update_version"
    private const val KEY_WHATS_NEW_SEEN_VERSION_CODE = "whats_new_seen_version_code"
    private const val KEY_WHATS_NEW_DONT_SHOW_AGAIN = "whats_new_dont_show_again"
    private const val KEY_EGURO_USERNAME = "eguro_username"
    private const val KEY_EGURO_SCHEDULE_PATH = "eguro_schedule_path"
    private const val KEY_EGURO_LAST_SYNC_MS = "eguro_last_sync_ms"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): String {
        val current = prefs(context).getString(KEY_THEME_MODE, null)
        if (current != null) return current
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        return legacy ?: "system"
    }

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getCustomGeminiApiKey(context: Context): String =
        prefs(context).getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()

    fun setCustomGeminiApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getEffectiveGeminiApiKey(context: Context): String {
        val custom = getCustomGeminiApiKey(context)
        if (custom.isNotBlank()) return custom
        return BuildConfig.GEMINI_API_KEY
            .trim()
            .takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" && it != "UNSET" && it != "null" }
            .orEmpty()
    }

    fun isAutoCheckUpdatesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CHECK_UPDATES, true)

    fun setAutoCheckUpdatesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
    }

    fun getUpdateOwner(context: Context): String =
        prefs(context).getString(KEY_UPDATE_OWNER, null)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.RELEASE_GITHUB_OWNER

    fun getUpdateRepo(context: Context): String =
        prefs(context).getString(KEY_UPDATE_REPO, null)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.RELEASE_GITHUB_REPO

    fun setUpdateRepository(context: Context, owner: String, repo: String) {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        prefs(context).edit().apply {
            if (cleanOwner.isEmpty()) remove(KEY_UPDATE_OWNER) else putString(KEY_UPDATE_OWNER, cleanOwner)
            if (cleanRepo.isEmpty()) remove(KEY_UPDATE_REPO) else putString(KEY_UPDATE_REPO, cleanRepo)
            remove(KEY_RELEASES_JSON)
            remove(KEY_RELEASES_ETAG)
            putLong(KEY_LAST_UPDATE_CHECK_MS, 0L)
        }.apply()
    }

    fun getUpdateToken(context: Context): String =
        prefs(context).getString(KEY_UPDATE_TOKEN, "")?.trim().orEmpty()

    fun setUpdateToken(context: Context, token: String) {
        prefs(context).edit().apply {
            val clean = token.trim()
            if (clean.isEmpty()) remove(KEY_UPDATE_TOKEN) else putString(KEY_UPDATE_TOKEN, clean)
            remove(KEY_RELEASES_ETAG)
            putLong(KEY_LAST_UPDATE_CHECK_MS, 0L)
        }.apply()
    }

    fun getLastUpdateCheckMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK_MS, 0L)

    fun setLastUpdateCheckMs(context: Context, timestampMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK_MS, timestampMs).apply()
    }

    fun getCachedReleasesJson(context: Context): String? =
        prefs(context).getString(KEY_RELEASES_JSON, null)

    fun getCachedReleasesEtag(context: Context): String? =
        prefs(context).getString(KEY_RELEASES_ETAG, null)

    fun saveReleasesCache(context: Context, json: String, etag: String?, timestampMs: Long) {
        prefs(context).edit().apply {
            putString(KEY_RELEASES_JSON, json)
            if (etag != null) putString(KEY_RELEASES_ETAG, etag)
            putLong(KEY_LAST_UPDATE_CHECK_MS, timestampMs)
        }.apply()
    }

    fun getSkippedVersion(context: Context): String? =
        prefs(context).getString(KEY_SKIPPED_VERSION, null)

    fun setSkippedVersion(context: Context, version: String?) {
        prefs(context).edit().apply {
            if (version.isNullOrBlank()) remove(KEY_SKIPPED_VERSION) else putString(KEY_SKIPPED_VERSION, version.trim())
        }.apply()
    }

    fun getLastSeenWhatsNewVersionCode(context: Context): Int =
        prefs(context).getInt(KEY_WHATS_NEW_SEEN_VERSION_CODE, 0)

    fun setLastSeenWhatsNewVersionCode(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_WHATS_NEW_SEEN_VERSION_CODE, versionCode).apply()
    }

    fun isWhatsNewDontShowAgain(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WHATS_NEW_DONT_SHOW_AGAIN, false)

    fun setWhatsNewDontShowAgain(context: Context, suppressed: Boolean) {
        prefs(context).edit().putBoolean(KEY_WHATS_NEW_DONT_SHOW_AGAIN, suppressed).apply()
    }

    fun getEguroUsername(context: Context): String =
        prefs(context).getString(KEY_EGURO_USERNAME, "")?.trim().orEmpty()

    fun getEguroSchedulePath(context: Context): String =
        prefs(context).getString(KEY_EGURO_SCHEDULE_PATH, "")?.trim().orEmpty()

    fun getEguroLastSyncMs(context: Context): Long =
        prefs(context).getLong(KEY_EGURO_LAST_SYNC_MS, 0L)

    fun saveEguroConnection(context: Context, username: String, schedulePath: String, syncedAtMs: Long) {
        prefs(context).edit().apply {
            val cleanUser = username.trim()
            val cleanPath = schedulePath.trim()
            if (cleanUser.isEmpty()) remove(KEY_EGURO_USERNAME) else putString(KEY_EGURO_USERNAME, cleanUser)
            if (cleanPath.isEmpty()) remove(KEY_EGURO_SCHEDULE_PATH) else putString(KEY_EGURO_SCHEDULE_PATH, cleanPath)
            putLong(KEY_EGURO_LAST_SYNC_MS, syncedAtMs)
        }.apply()
    }

    fun clearEguroConnection(context: Context) {
        prefs(context).edit().apply {
            remove(KEY_EGURO_USERNAME)
            remove(KEY_EGURO_SCHEDULE_PATH)
            remove(KEY_EGURO_LAST_SYNC_MS)
        }.apply()
    }
}
