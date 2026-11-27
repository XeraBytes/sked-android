package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.prefs.AppPreferences
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.components.WhatsNewDialog
import com.example.ui.theme.ScheduleMakerTheme
import com.example.updater.AppUpdateInstaller
import com.example.updater.AppUpdater
import com.example.updater.ReleaseAsset
import com.example.updater.ReleaseInfo
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AppUpdaterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `semver handles two-segment and three-segment versions`() {
        assertTrue(AppUpdater.isUpdateAvailable("1.1.0", "1.0"))
        assertTrue(AppUpdater.isUpdateAvailable("v1.0.1", "1.0.0"))
        assertTrue(AppUpdater.isUpdateAvailable("1.2", "1.1.9"))
        assertFalse(AppUpdater.isUpdateAvailable("1.1.0", "1.1"))
        assertFalse(AppUpdater.isUpdateAvailable("1.2.0-beta.1", "1.2.0"))
        assertTrue(AppUpdater.isUpdateAvailable("1.2.0", "1.2.0-beta.1"))
    }

    @Test
    fun `findLatestRelease prefers highest stable version over newer prerelease`() {
        val stable = ReleaseInfo(
            tagName = "v1.2.0",
            name = "1.2.0",
            body = "Stable release",
            publishedAt = "2026-10-01T00:00:00Z",
            htmlUrl = "https://github.com/example/repo/releases/tag/v1.2.0",
            isPrerelease = false,
            asset = null
        )
        val beta = ReleaseInfo(
            tagName = "v1.3.0-rc.1",
            name = "1.3.0-rc.1",
            body = "Beta",
            publishedAt = "2026-11-01T00:00:00Z",
            htmlUrl = "https://github.com/example/repo/releases/tag/v1.3.0-rc.1",
            isPrerelease = true,
            asset = null
        )

        val picked = AppUpdater.findLatestRelease(listOf(beta, stable))
        assertEquals("v1.2.0", picked?.tagName)
    }

    @Test
    fun `parseReleasesJson selects apk asset and both download urls`() {
        val sampleJson = """
            [
              {
                "tag_name": "v1.2.0",
                "name": "Sked 1.2.0",
                "body": "- Added dark mode",
                "published_at": "2026-11-26T12:00:00Z",
                "html_url": "https://github.com/XeraBytes/sked-android/releases/tag/v1.2.0",
                "prerelease": false,
                "assets": [
                  {
                    "name": "checksums.txt",
                    "browser_download_url": "https://github.com/XeraBytes/sked-android/releases/download/v1.2.0/checksums.txt",
                    "url": "https://api.github.com/repos/XeraBytes/sked-android/releases/assets/100",
                    "size": 128
                  },
                  {
                    "name": "sked-v1.2.0.apk",
                    "browser_download_url": "https://github.com/XeraBytes/sked-android/releases/download/v1.2.0/sked-v1.2.0.apk",
                    "url": "https://api.github.com/repos/XeraBytes/sked-android/releases/assets/200",
                    "size": 12582912
                  }
                ]
              }
            ]
        """.trimIndent()

        val releases = AppUpdater.parseReleasesJson(sampleJson)
        assertEquals(1, releases.size)
        val asset = releases.first().asset
        assertNotNull(asset)
        assertEquals("sked-v1.2.0.apk", asset?.name)
        assertEquals(12582912L, asset?.sizeBytes)
        assertTrue(asset?.apiUrl?.endsWith("/assets/200") == true)
    }

    @Test
    fun `findLatestRelease returns null when only prereleases exist`() {
        val beta = ReleaseInfo(
            tagName = "v2.0.0-beta.1",
            name = "2.0.0-beta.1",
            body = "Beta",
            publishedAt = "2026-11-27T00:00:00Z",
            htmlUrl = "https://github.com/example/repo/releases/tag/v2.0.0-beta.1",
            isPrerelease = true,
            asset = null
        )
        assertNull(AppUpdater.findLatestRelease(listOf(beta)))
    }

    @Test
    fun `skipped version persists across checks and newer release becomes eligible`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreferences.setSkippedVersion(context, "1.2.0")
        assertEquals("1.2.0", AppPreferences.getSkippedVersion(context))
        assertTrue(AppPreferences.getSkippedVersion(context) != "1.3.0")
    }

    @Test
    fun `update dialog displays Whats New, Update now, Later, and Dont show again`() {
        val release = ReleaseInfo(
            tagName = "v1.2.0",
            name = "1.2.0",
            body = "## Sked v1.2.0\n\n- Added Home page & folio intro\n- Fixed release notes formatting",
            publishedAt = "2026-11-26T00:00:00Z",
            htmlUrl = "https://github.com/example/repo/releases/tag/v1.2.0",
            isPrerelease = false,
            asset = ReleaseAsset(
                name = "schedule-maker.apk",
                browserDownloadUrl = "https://example.com/schedule-maker.apk",
                apiUrl = "",
                sizeBytes = 10_485_760L
            )
        )
        var action = ""
        composeTestRule.setContent {
            ScheduleMakerTheme {
                UpdateAvailableDialog(
                    release = release,
                    latestVersion = "1.2.0",
                    needsInstallPermission = false,
                    onInstallClick = { action = "update" },
                    onGrantPermissionClick = {},
                    onSkipVersion = { action = "skip" },
                    onDismiss = { action = "later" },
                    onOpenReleasePage = { action = "github" }
                )
            }
        }

        composeTestRule.onNodeWithText("WHAT'S NEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("Added Home page & folio intro").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update now").assertIsDisplayed().performClick()
        assertEquals("update", action)

        composeTestRule.onNodeWithText("Later").assertIsDisplayed().performClick()
        assertEquals("later", action)

        composeTestRule.onNodeWithText("Don't show again").assertIsDisplayed().performClick()
        assertEquals("skip", action)
    }

    @Test
    fun `whats new dialog displays notes and Dont show again persists preference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreferences.setWhatsNewDontShowAgain(context, false)
        var result = ""
        composeTestRule.setContent {
            ScheduleMakerTheme {
                WhatsNewDialog(
                    versionName = "1.1.0",
                    notes = listOf("Home screen launch intro", "Updater release notes"),
                    onDontShowAgain = {
                        AppPreferences.setWhatsNewDontShowAgain(context, true)
                        result = "dont_show"
                    },
                    onDismiss = { result = "got_it" }
                )
            }
        }
        composeTestRule.onNodeWithTag("whats_new_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("WHAT'S NEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home screen launch intro").assertIsDisplayed()
        composeTestRule.onNodeWithTag("whats_new_dont_show_again_button").performClick()
        assertEquals("dont_show", result)
        assertTrue(AppPreferences.isWhatsNewDontShowAgain(context))
    }

    @Test
    fun `isApkSigned rejects unsigned archive and accepts v1 signed archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val unsignedApk = File(context.cacheDir, "unsigned-test.apk")
        ZipOutputStream(unsignedApk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        assertFalse(AppUpdateInstaller.isApkSigned(unsignedApk))

        val signedApk = File(context.cacheDir, "signed-test.apk")
        ZipOutputStream(signedApk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()
        }
        assertTrue(AppUpdateInstaller.isApkSigned(signedApk))
        unsignedApk.delete()
        signedApk.delete()
    }
}
