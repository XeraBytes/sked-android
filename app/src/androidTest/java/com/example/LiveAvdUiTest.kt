package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveAvdUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun avdWalkthrough_schedulePoolImportAndRotation() {
        // 1. Schedule Daily Agenda -> Weekly Sheet -> back to Daily Agenda
        composeRule.onNodeWithTag("daily_timeline_view").assertIsDisplayed()
        composeRule.onNodeWithTag("view_mode_grid").performClick()
        composeRule.onNodeWithTag("weekly_grid_view").assertIsDisplayed()
        composeRule.onNodeWithTag("view_mode_timeline").performClick()

        // 2. Pool tab -> Place a seeded unscheduled course ("Compiler Construction")
        composeRule.onNodeWithTag("nav_tab_pool").performClick()
        composeRule.onNodeWithTag("class_pool_view").assertIsDisplayed()
        composeRule.onNodeWithText("Compiler Construction").assertIsDisplayed()

        // Tap Place on first unscheduled course
        composeRule.onAllNodesWithText("Place")[0].performClick()
        composeRule.onNodeWithTag("timeline_placement_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_placement_button").assertIsDisplayed()

        // Rotate / recreate Activity while placing -> banner survives
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("timeline_placement_banner").assertIsDisplayed()

        // Select Monday (9 AM is occupied by Linear Algebra) -> Conflict dialog shows both ranges
        composeRule.onNodeWithTag("day_chip_1").performClick()
        composeRule.onNodeWithTag("place_slot_1_9").performClick()
        composeRule.onNodeWithTag("schedule_conflict_dialog").assertIsDisplayed()
        composeRule.onAllNodesWithText("Monday · 9:00 AM – 10:30 AM", substring = true)
            .assertCountEquals(2)

        // Cancel conflict -> stay unscheduled -> cancel placement
        composeRule.onNodeWithTag("dismiss_conflict_placement_button").performClick()
        composeRule.onNodeWithTag("cancel_placement_button").performClick()
        composeRule.onNodeWithTag("timeline_placement_banner").assertDoesNotExist()

        // 3. Import tab -> Extraction leads, offline Reference timetable is present
        composeRule.onNodeWithTag("nav_tab_screenshot").performClick()
        composeRule.onNodeWithTag("screenshot_reference_view").assertIsDisplayed()
        composeRule.onNodeWithTag("import_mode_text_button").performClick()
        composeRule.onNodeWithTag("import_pasted_text_field").performTextInput("CS 202 Tue 10 AM")
        composeRule.onNodeWithTag("ai_organize_button").assertIsDisplayed()
    }
}
