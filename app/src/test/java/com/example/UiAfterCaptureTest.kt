package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.example.ui.dialogs.SettingsDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.screens.ClassPoolView
import com.example.ui.screens.MainScheduleContainer
import com.example.ui.screens.ScreenshotReferenceView
import com.example.ui.theme.ScheduleMakerTheme
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.updater.ReleaseAsset
import com.example.updater.ReleaseInfo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi", sdk = [36])
class UiAfterCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSettings = ScheduleSettings(
        schoolName = "College of Engineering",
        termName = "First Semester 2026–2027",
        startHour = 8,
        endHour = 18,
        showWeekends = false
    )

    private val sampleClasses = listOf(
        ClassBlock(
            id = 1,
            name = "Introduction to Distributed Systems & Consensus Algorithms",
            professor = "Dr. K. Watanabe",
            room = "Systems Lab 204",
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            durationMinutes = 90,
            colorHex = "#2E5A4F"
        ),
        ClassBlock(
            id = 2,
            name = "Organic Chemistry II Lab",
            professor = "Dr. M. Navarro",
            room = "Sci 108",
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 30,
            durationMinutes = 60,
            colorHex = "#B85D3B"
        ),
        ClassBlock(
            id = 3,
            name = "Linear Algebra",
            professor = "Prof. R. Mendoza",
            room = "North 204",
            dayOfWeek = 3,
            startHour = 11,
            startMinute = 0,
            durationMinutes = 60,
            colorHex = "#38598B"
        ),
        ClassBlock(
            id = 4,
            name = "Compiler Construction",
            professor = "Dr. D. Ocampo",
            room = "CS Room 405",
            dayOfWeek = 0,
            startHour = 9,
            startMinute = 0,
            durationMinutes = 90,
            colorHex = "#5D5179"
        )
    )

    @Test
    fun captureAfter_schedule_grid_light() {
        showLightGrid()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/schedule_grid_light.png")
        composeTestRule.onNodeWithContentDescription("Next days").performClick()
        composeTestRule.onNodeWithTag("class_card_3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next days").performClick()
        composeTestRule.onNodeWithContentDescription("Saturday").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1000dp-h720dp-mdpi")
    fun captureAfter_grid_tablet() {
        showLightGrid()
        composeTestRule.onNodeWithContentDescription("Monday").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Saturday").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next days").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/grid_tablet.png")
    }

    private fun showSettings() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeTestRule.setContent {
            ScheduleMakerTheme {
                SettingsDialog(context, sampleSettings, "system", false, null, {}, {}, {}, { _, _, _, _, _ -> }, {}, {})
            }
        }
    }

    @Test
    fun captureAfter_settings_phone() {
        showSettings()
        composeTestRule.onNodeWithTag("settings_dialog").captureRoboImage("src/test/screenshots/after/settings_phone.png")
        composeTestRule.onNodeWithText("Timetable").performClick()
        composeTestRule.onNodeWithTag("settings_school_name").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings index").performClick()
        composeTestRule.onNodeWithText("Updates").performClick()
        composeTestRule.onNodeWithTag("check_updates_button").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1000dp-h720dp-mdpi")
    fun captureAfter_settings_tablet() {
        showSettings()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_school_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_dialog").captureRoboImage("src/test/screenshots/after/settings_tablet.png")
    }

    private fun showLightGrid() {
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = false) {
                MainScheduleContainer(
                    classes = sampleClasses,
                    settings = sampleSettings,
                    activePlacementBlock = null,
                    gridStyle = "classic",
                    viewMode = "grid",
                    onViewModeChange = {},
                    onClassClick = {},
                    onStartPlacement = {},
                    onCancelPlacement = {},
                    onPlaceBlock = { _, _, _ -> },
                    onUnscheduleBlock = {},
                    onAddClassClick = {}
                )
            }
        }
    }

    @Test
    fun captureAfter_schedule_grid_dark() {
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = true) {
                MainScheduleContainer(
                    classes = sampleClasses,
                    settings = sampleSettings,
                    activePlacementBlock = null,
                    gridStyle = "classic",
                    viewMode = "grid",
                    onViewModeChange = {},
                    onClassClick = {},
                    onStartPlacement = {},
                    onCancelPlacement = {},
                    onPlaceBlock = { _, _, _ -> },
                    onUnscheduleBlock = {},
                    onAddClassClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/schedule_grid_dark.png")
    }

    @Test
    fun captureAfter_schedule_timeline_light() {
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = false) {
                MainScheduleContainer(
                    classes = sampleClasses,
                    settings = sampleSettings,
                    activePlacementBlock = null,
                    gridStyle = "classic",
                    viewMode = "timeline",
                    onViewModeChange = {},
                    onClassClick = {},
                    onStartPlacement = {},
                    onCancelPlacement = {},
                    onPlaceBlock = { _, _, _ -> },
                    onUnscheduleBlock = {},
                    onAddClassClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/schedule_timeline_light.png")
    }

    @Test
    fun captureAfter_schedule_timeline_200pct_font() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)) {
                ScheduleMakerTheme(darkTheme = false) {
                    MainScheduleContainer(
                        classes = sampleClasses,
                        settings = sampleSettings,
                        activePlacementBlock = null,
                        gridStyle = "classic",
                        viewMode = "timeline",
                        onViewModeChange = {},
                        onClassClick = {},
                        onStartPlacement = {},
                        onCancelPlacement = {},
                        onPlaceBlock = { _, _, _ -> },
                        onUnscheduleBlock = {},
                        onAddClassClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/schedule_timeline_font200.png")
    }

    @Test
    fun captureAfter_pool_light() {
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = false) {
                ClassPoolView(
                    classes = sampleClasses,
                    onClassClick = {},
                    onScheduleClick = {},
                    onDeleteClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/pool_light.png")
    }

    @Test
    fun captureAfter_import_light() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ScheduleViewModel(app)
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = false) {
                ScreenshotReferenceView(
                    settings = sampleSettings,
                    viewModel = vm,
                    onImportClick = {},
                    onClearClick = {},
                    onOpenSettingsClick = {},
                    onAiImportCompleted = {},
                    resolveFileName = { "timetable.pdf" }
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/import_light.png")
    }

    @Test
    fun captureAfter_updater_light() {
        val rel = ReleaseInfo(
            tagName = "v1.2.0",
            name = "1.2.0",
            body = "Soft clay controls, paper timetable, and improved overlap scanning.",
            publishedAt = "2026-11-26T00:00:00Z",
            htmlUrl = "https://github.com/example/repo/releases/tag/v1.2.0",
            isPrerelease = false,
            asset = ReleaseAsset(
                name = "app-release.apk",
                browserDownloadUrl = "https://example.com/app.apk",
                apiUrl = "",
                sizeBytes = 12_582_912L
            )
        )
        composeTestRule.setContent {
            ScheduleMakerTheme(darkTheme = false) {
                UpdateAvailableDialog(
                    release = rel,
                    latestVersion = "1.2.0",
                    needsInstallPermission = false,
                    onInstallClick = {},
                    onGrantPermissionClick = {},
                    onSkipVersion = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/after/updater_light.png")
    }
}
