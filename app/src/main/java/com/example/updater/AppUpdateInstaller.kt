package com.example.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.prefs.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

object AppUpdateInstaller {

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        val fraction: Float?
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val asset = release.asset
                ?: throw IOException("No APK asset attached to release ${release.tagName}.")

            val token = AppPreferences.getUpdateToken(context)
            val useApiAssetUrl = token.isNotBlank() && asset.apiUrl.isNotBlank()
            val targetUrl = if (useApiAssetUrl) asset.apiUrl else asset.browserDownloadUrl
            if (targetUrl.isBlank()) {
                throw IOException("Release ${release.tagName} has no downloadable APK URL.")
            }

            val updateDir = AppUpdater.getReleaseDir(context).apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File(updateDir, "sked-update.download").apply { if (exists()) delete() }
            val apkFile = File(updateDir, "sked-update.apk").apply { if (exists()) delete() }

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Sked/${BuildConfig.VERSION_NAME}")
            if (useApiAssetUrl) {
                requestBuilder.header("Authorization", "Bearer $token")
                requestBuilder.header("Accept", "application/octet-stream")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("APK download failed (HTTP ${response.code} ${response.message}).")
                }
                val body = response.body ?: throw IOException("Empty APK response stream.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: asset.sizeBytes

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0L
                        var lastEmitMs = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 150L) {
                                lastEmitMs = now
                                emitProgress(downloaded, totalBytes, onProgress)
                            }
                        }
                        emitProgress(downloaded, totalBytes, onProgress)
                    }
                }
            }

            if (!containsApkManifest(tempFile)) {
                tempFile.delete()
                throw IOException("Downloaded file is not a valid Android APK package.")
            }

            if (!isApkSigned(tempFile)) {
                tempFile.delete()
                throw IOException("This release APK is unsigned and cannot be installed by Android. Configure release signing or open the release page.")
            }

            if (!verifySignatureCompatibility(context, tempFile)) {
                tempFile.delete()
                throw IOException("Release APK signing key differs from this installed build. Uninstall this build first or install from GitHub Releases.")
            }

            if (!tempFile.renameTo(apkFile)) {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                launchApkInstaller(context, apkFile)
            }
            Result.success(apkFile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun openReleasePage(context: Context, url: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private suspend fun emitProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        onProgress: (Progress) -> Unit
    ) {
        withContext(Dispatchers.Main.immediate) {
            onProgress(Progress(downloadedBytes, totalBytes))
        }
    }

    private fun containsApkManifest(file: File): Boolean = runCatching {
        ZipFile(file).use { zip -> zip.getEntry("AndroidManifest.xml") != null }
    }.getOrDefault(false)

    internal fun isApkSigned(file: File): Boolean = runCatching {
        val hasV1Sig = ZipFile(file).use { zip ->
            val entries = zip.entries()
            var found = false
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("META-INF/", ignoreCase = true) &&
                    (name.endsWith(".RSA", ignoreCase = true) ||
                        name.endsWith(".DSA", ignoreCase = true) ||
                        name.endsWith(".EC", ignoreCase = true))
                ) {
                    found = true
                    break
                }
            }
            found
        }
        if (hasV1Sig) return@runCatching true

        // Check APK Signature Scheme v2/v3 magic ("APK Sig Block 42")
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            if (len < 64L) return@use false
            val scanSize = minOf(len, 256L * 1024L).toInt()
            val buf = ByteArray(scanSize)
            raf.seek(len - scanSize)
            raf.readFully(buf)
            outer@ for (i in 0..(buf.size - magic.size)) {
                for (j in magic.indices) {
                    if (buf[i + j] != magic[j]) continue@outer
                }
                return@use true
            }
            false
        }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun verifySignatureCompatibility(context: Context, apkFile: File): Boolean = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return@runCatching true
            val installedSet = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val archiveSet = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            if (installedSet.isEmpty() || archiveSet.isEmpty()) return@runCatching true
            installedSet.any { it in archiveSet }
        } else {
            val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                ?: return@runCatching true
            val installedSet = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            val archiveSet = archive.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            if (installedSet.isEmpty() || archiveSet.isEmpty()) return@runCatching true
            installedSet.any { it in archiveSet }
        }
    }.getOrDefault(true)

    private fun launchApkInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
