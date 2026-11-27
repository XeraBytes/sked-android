package com.example.updater

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.data.prefs.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val apiUrl: String,
    val sizeBytes: Long
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isPrerelease: Boolean,
    val asset: ReleaseAsset?
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val release: ReleaseInfo,
        val versionName: String,
        val currentVersion: String
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String?
    ) : UpdateCheckResult

    data class Error(
        val message: String,
        val mayNeedAuthOrPublicRepo: Boolean = false
    ) : UpdateCheckResult
}

object AppUpdater {

    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<PreReleaseIdentifier> = emptyList()
    ) : Comparable<SemVer> {

        override fun compareTo(other: SemVer): Int {
            major.compareTo(other.major).let { if (it != 0) return it }
            minor.compareTo(other.minor).let { if (it != 0) return it }
            patch.compareTo(other.patch).let { if (it != 0) return it }

            val thisStable = preRelease.isEmpty()
            val otherStable = other.preRelease.isEmpty()
            if (thisStable && !otherStable) return 1
            if (!thisStable && otherStable) return -1

            val minCount = minOf(preRelease.size, other.preRelease.size)
            for (i in 0 until minCount) {
                val cmp = preRelease[i].compareTo(other.preRelease[i])
                if (cmp != 0) return cmp
            }
            return preRelease.size.compareTo(other.preRelease.size)
        }

        fun normalizedName(): String =
            if (preRelease.isEmpty()) "$major.$minor.$patch"
            else "$major.$minor.$patch-${preRelease.joinToString(".") { it.raw }}"
    }

    sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
        val raw: String
    }

    data class NumericIdentifier(override val raw: String, val value: Long) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int = when (other) {
            is NumericIdentifier -> value.compareTo(other.value)
            is AlphaIdentifier -> -1
        }
    }

    data class AlphaIdentifier(override val raw: String) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int = when (other) {
            is NumericIdentifier -> 1
            is AlphaIdentifier -> raw.compareTo(other.raw)
        }
    }

    private val semVerRegex =
        Regex("""(?i)\bv?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\b""")

    fun parseSemVerOrNull(text: String): SemVer? {
        val match = semVerRegex.find(text.trim()) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val patch = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val preText = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
        val pre = preText?.split('.')
            ?.filter { it.isNotBlank() }
            ?.map { id ->
                id.toLongOrNull()?.let { NumericIdentifier(id, it) } ?: AlphaIdentifier(id)
            }
            ?: emptyList()
        return SemVer(major, minor, patch, pre)
    }

    fun isUpdateAvailable(latestVersion: String, currentVersion: String): Boolean {
        val latest = parseSemVerOrNull(latestVersion)
        val current = parseSemVerOrNull(currentVersion)
        return if (latest != null && current != null) {
            latest > current
        } else {
            latestVersion.trim().removePrefix("v") != currentVersion.trim().removePrefix("v")
        }
    }

    fun getReleaseVersionName(release: ReleaseInfo): String {
        val parsed = parseSemVerOrNull(release.tagName) ?: parseSemVerOrNull(release.name)
        return parsed?.normalizedName() ?: release.name.ifBlank { release.tagName }
    }

    fun parseReleasesJson(json: String): List<ReleaseInfo> {
        val array = JSONArray(json)
        val result = ArrayList<ReleaseInfo>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.optBoolean("draft", false)) continue
            val assets = item.optJSONArray("assets")
            val apkAsset = selectBestApkAsset(assets)
            result.add(
                ReleaseInfo(
                    tagName = item.optString("tag_name", ""),
                    name = item.optString("name", ""),
                    body = if (item.isNull("body")) "" else item.optString("body", ""),
                    publishedAt = item.optString("published_at", ""),
                    htmlUrl = item.optString("html_url", ""),
                    isPrerelease = item.optBoolean("prerelease", false),
                    asset = apkAsset
                )
            )
        }
        return result
    }

    internal fun selectBestApkAsset(assets: JSONArray?): ReleaseAsset? {
        if (assets == null) return null
        val candidates = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { obj ->
                val name = obj.optString("name", "")
                name.endsWith(".apk", ignoreCase = true)
            }
            .map { obj ->
                ReleaseAsset(
                    name = obj.optString("name", ""),
                    browserDownloadUrl = obj.optString("browser_download_url", ""),
                    apiUrl = obj.optString("url", ""),
                    sizeBytes = obj.optLong("size", -1L)
                )
            }
        if (candidates.isEmpty()) return null

        val abiPrefs = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return candidates.sortedBy { asset ->
            val lower = asset.name.lowercase()
            when {
                lower.contains("universal") -> 0
                abiPrefs.any { abi -> lower.contains(abi.lowercase()) } -> 1
                !lower.contains("unsigned") -> 2
                else -> 3
            }
        }.firstOrNull()
    }

    fun findLatestRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        val withVersions = releases.mapNotNull { rel ->
            val ver = parseSemVerOrNull(rel.tagName) ?: parseSemVerOrNull(rel.name)
            ver?.let { it to rel }
        }
        if (withVersions.isEmpty()) return releases.firstOrNull()

        val stable = withVersions.filter { !it.second.isPrerelease && it.first.preRelease.isEmpty() }
        return stable.maxWithOrNull(compareBy({ it.first }, { it.second.publishedAt }))?.second
    }

    fun shouldAutoCheck(context: Context): Boolean {
        if (!BuildConfig.UPDATER_AVAILABLE) return false
        if (!AppPreferences.isAutoCheckUpdatesEnabled(context)) return false
        val lastCheck = AppPreferences.getLastUpdateCheckMs(context)
        return (System.currentTimeMillis() - lastCheck) >= AUTO_CHECK_INTERVAL_MS
    }

    suspend fun checkForUpdates(
        context: Context,
        forceRefresh: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return@withContext UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_NAME)
        }

        val owner = AppPreferences.getUpdateOwner(context)
        val repo = AppPreferences.getUpdateRepo(context)
        val token = AppPreferences.getUpdateToken(context)
        val now = System.currentTimeMillis()

        val cachedJson = AppPreferences.getCachedReleasesJson(context)
        val cachedReleases = cachedJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { parseReleasesJson(it) }.getOrNull()
        }

        if (!forceRefresh && cachedReleases != null && !shouldAutoCheck(context)) {
            return@withContext evaluateReleases(cachedReleases)
        }

        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=20"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Sked/${BuildConfig.VERSION_NAME}")
        AppPreferences.getCachedReleasesEtag(context)?.takeIf { !forceRefresh && it.isNotBlank() }?.let {
            requestBuilder.header("If-None-Match", it)
        }
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val etag = response.header("ETag")
                if (code == 304 && cachedReleases != null) {
                    AppPreferences.setLastUpdateCheckMs(context, now)
                    return@withContext evaluateReleases(cachedReleases)
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                    }.getOrNull()

                    val authIssue = code == 401 || code == 403 || code == 404
                    val readableMessage = when (code) {
                        404 -> "No accessible releases found at $owner/$repo (HTTP 404). If the repo is private, add a read-only GitHub token below or publish releases to a public repo."
                        401, 403 -> "GitHub access denied (${apiMessage ?: "HTTP $code"}). Check your optional access token or repository permissions."
                        else -> "GitHub release check failed (HTTP $code${apiMessage?.let { ": $it" }.orEmpty()})."
                    }
                    return@withContext UpdateCheckResult.Error(readableMessage, mayNeedAuthOrPublicRepo = authIssue)
                }

                AppPreferences.saveReleasesCache(context, body, etag, now)
                val parsed = parseReleasesJson(body)
                return@withContext evaluateReleases(parsed)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (!forceRefresh && cachedReleases != null) {
                return@withContext evaluateReleases(cachedReleases)
            }
            return@withContext UpdateCheckResult.Error(
                message = "Could not check for updates: ${e.localizedMessage ?: "network unavailable"}",
                mayNeedAuthOrPublicRepo = false
            )
        }
    }

    private fun evaluateReleases(releases: List<ReleaseInfo>): UpdateCheckResult {
        val latest = findLatestRelease(releases)
            ?: return UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, null)
        val latestVer = getReleaseVersionName(latest)
        return if (isUpdateAvailable(latestVer, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable(
                release = latest,
                versionName = latestVer,
                currentVersion = BuildConfig.VERSION_NAME
            )
        } else {
            UpdateCheckResult.UpToDate(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = latestVer
            )
        }
    }

    fun getReleaseDir(context: Context): File = File(context.cacheDir, "app_update")
}
