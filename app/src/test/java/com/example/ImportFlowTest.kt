package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.prefs.AppPreferences
import com.example.data.repository.ScheduleRepository
import com.example.ui.screens.ScreenshotReferenceView
import com.example.ui.theme.ScheduleMakerTheme
import com.example.ui.viewmodel.ScheduleViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi", sdk = [36])
class ImportFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun resetDbBefore() {
        ScheduleDatabase.resetForTest()
    }

    @After
    fun resetDbAfter() {
        ScheduleDatabase.resetForTest()
    }

    @Test
    fun retryAndSave_showsSavedCountAfterCommit() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppPreferences.setCustomGeminiApiKey(app, "")
        val db = ScheduleDatabase.getDatabase(app)
        val repo = ScheduleRepository(db.scheduleDao())
        runBlocking {
            repo.clearAllClassBlocks()
            repo.saveSettings(ScheduleSettings())
        }

        val viewModel = ScheduleViewModel(app)
        val gate = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        var settingsOpened = false
        var savedCallbackCount = -1

        try {
            composeTestRule.setContent {
                ScheduleMakerTheme {
                    ScreenshotReferenceView(
                        settings = ScheduleSettings(),
                        viewModel = viewModel,
                        onImportClick = {},
                        onClearClick = {},
                        onOpenSettingsClick = { settingsOpened = true },
                        onAiImportCompleted = {},
                        resolveFileName = { "syllabus.txt" },
                        extractor = { _, _ ->
                            val attempt = callCount.incrementAndGet()
                            when (attempt) {
                                1 -> throw IllegalStateException("No Gemini API key configured.")
                                2 -> {
                                    gate.await()
                                    throw IOException("Network timeout")
                                }
                                else -> listOf(
                                    ClassBlock(
                                        name = "CS 101",
                                        dayOfWeek = 1,
                                        startHour = 9,
                                        startMinute = 0,
                                        durationMinutes = 90
                                    )
                                )
                            }
                        },
                        onClassesSaved = { savedCallbackCount = it }
                    )
                }
            }

            composeTestRule.onNodeWithTag("import_mode_text_button").performClick()
            composeTestRule.onNodeWithTag("import_pasted_text_field")
                .performTextInput("CS 101 Mon 9:00-10:30 AM")

            // 1. Missing API key failure keeps input and offers Configure + Retry
            composeTestRule.onNodeWithTag("ai_organize_button").performClick()
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.importState.value.analysisError != null
            }
            composeTestRule.onNodeWithTag("ai_error_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ai_open_settings_button").performClick()
            assertEquals(true, settingsOpened)
            composeTestRule.onNodeWithText("CS 101 Mon 9:00-10:30 AM").assertIsDisplayed()

            // 2. Retry hits in-flight network gate -> button disabled & duplicate taps ignored
            composeTestRule.onNodeWithTag("ai_retry_button").performClick()
            composeTestRule.onNodeWithTag("ai_progress_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ai_organize_button").assertIsNotEnabled()
            composeTestRule.onNodeWithTag("ai_organize_button").performClick()
            assertEquals(2, callCount.get())

            // Unblock network failure -> Retry again -> succeeds
            gate.complete(Unit)
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.importState.value.analysisError != null
            }
            composeTestRule.onNodeWithTag("ai_error_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ai_retry_button").performClick()
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.importState.value.parsedClasses != null
            }

            composeTestRule.onNodeWithText("1 class ready to save").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ai_save_all_button").performClick()

            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                savedCallbackCount == 1
            }
            composeTestRule.onNodeWithTag("import_saved_banner").assertIsDisplayed()
            composeTestRule.onNodeWithText("Saved 1 class to your schedule.").assertIsDisplayed()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun zeroClasses_showsEmptyCard_andSurvivesRestore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ScheduleViewModel(app)
        val restorationTester = StateRestorationTester(composeTestRule)
        try {
            restorationTester.setContent {
                ScheduleMakerTheme {
                    ScreenshotReferenceView(
                        settings = ScheduleSettings(referenceScreenshotUri = "file:///tmp/ref.jpg"),
                        viewModel = viewModel,
                        onImportClick = {},
                        onClearClick = {},
                        onOpenSettingsClick = {},
                        onAiImportCompleted = {},
                        resolveFileName = { "empty.txt" },
                        extractor = { _, _ -> emptyList() }
                    )
                }
            }

            composeTestRule.onNodeWithTag("reference_timetable_section").assertIsDisplayed()
            composeTestRule.onNodeWithTag("reference_photo_preview").assertIsDisplayed()

            composeTestRule.onNodeWithTag("import_mode_text_button").performClick()
            composeTestRule.onNodeWithTag("import_pasted_text_field").performTextInput("Random notes")
            composeTestRule.onNodeWithTag("ai_organize_button").performClick()
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.importState.value.parsedClasses != null
            }

            composeTestRule.onNodeWithTag("ai_empty_result_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("ai_save_all_button").assertDoesNotExist()

            restorationTester.emulateSavedInstanceStateRestore()
            composeTestRule.onNodeWithTag("ai_empty_result_card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Random notes").assertIsDisplayed()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
