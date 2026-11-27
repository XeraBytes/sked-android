package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.repository.ScheduleRepository
import com.example.ui.screens.DailyTimelineView
import com.example.ui.screens.WeeklyGridView
import com.example.ui.theme.ScheduleMakerTheme
import com.example.ui.viewmodel.ScheduleViewModel
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi", sdk = [36])
class PlacementTest {

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
    fun conflictCancel_preservesPool_andUndoOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = ScheduleDatabase.getDatabase(app)
        val repo = ScheduleRepository(db.scheduleDao())
        val (occupiedId, poolId) = runBlocking {
            repo.clearAllClassBlocks()
            repo.saveSettings(
                ScheduleSettings(
                    schoolName = "Test College",
                    termName = "Fall",
                    startHour = 8,
                    endHour = 18,
                    showWeekends = false
                )
            )
            val occ = repo.insertClassBlock(
                ClassBlock(
                    name = "Linear Algebra",
                    dayOfWeek = 1,
                    startHour = 9,
                    startMinute = 0,
                    durationMinutes = 90,
                    colorHex = "#2E5A4F"
                )
            ).toInt()
            val pool = repo.insertClassBlock(
                ClassBlock(
                    name = "Compiler Construction",
                    dayOfWeek = 0,
                    startHour = 9,
                    startMinute = 0,
                    durationMinutes = 90,
                    colorHex = "#5D5179"
                )
            ).toInt()
            occ to pool
        }

        val viewModel = ScheduleViewModel(app)
        try {
            composeTestRule.setContent {
                ScheduleMakerTheme {
                    ScheduleApp(viewModel = viewModel)
                }
            }

            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.classBlocks.value.any { it.id == occupiedId } &&
                    viewModel.classBlocks.value.any { it.id == poolId }
            }

            // Switch to Pool tab
            composeTestRule.onNodeWithTag("nav_tab_pool").performClick()
            composeTestRule.onNodeWithText("1 unscheduled course", substring = true).assertIsDisplayed()

            // Start placement for Compiler Construction
            composeTestRule.onNodeWithTag("pool_place_$poolId").performClick()

            // Course name, duration, selected day, and Cancel button should be visible
            composeTestRule.onNodeWithTag("timeline_placement_banner").assertIsDisplayed()
            composeTestRule.onNodeWithText("Compiler Construction").assertIsDisplayed()
            composeTestRule.onNodeWithText("1h 30m ·", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("cancel_placement_button").assertIsDisplayed()

            // Select Monday so occupied slot (9 AM) is deterministic regardless of system day
            composeTestRule.onNodeWithTag("day_chip_1").performClick()
            composeTestRule.onNodeWithTag("place_slot_1_9").performClick()

            // Conflict dialog shows BOTH courses and their time ranges
            composeTestRule.onNodeWithTag("schedule_conflict_dialog").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Monday · 9:00 AM – 10:30 AM", substring = true).assertCountEquals(2)
            composeTestRule.onAllNodesWithText("Linear Algebra").assertCountEquals(2)

            // Cancel conflict -> pool count remains 1
            composeTestRule.onNodeWithTag("dismiss_conflict_placement_button").performClick()
            composeTestRule.onNodeWithTag("schedule_conflict_dialog").assertDoesNotExist()
            assertEquals(1, viewModel.classBlocks.value.count { !it.isScheduled })

            // Place at free slot 11 AM on Monday
            composeTestRule.onNodeWithTag("place_slot_1_11").performClick()
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.classBlocks.value.count { !it.isScheduled } == 0
            }
            val moved = viewModel.classBlocks.value.first { it.id == poolId }
            assertEquals(1, moved.dayOfWeek)
            assertEquals(11, moved.startHour)

            // Snackbar shows assigned day & time and Undo restores course to pool once
            composeTestRule.onNodeWithText("Monday at 11:00 AM", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Undo").performClick()
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.classBlocks.value.count { !it.isScheduled } == 1
            }
            val restored = viewModel.classBlocks.value.first { it.id == poolId }
            assertEquals(0, restored.dayOfWeek)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun overlappingShortClasses_openQuickActions() {
        val c1 = ClassBlock(
            id = 10,
            name = "Algorithms Recitation",
            room = "Rm 101",
            professor = "Prof. Lee",
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            durationMinutes = 30,
            colorHex = "#2E5A4F"
        )
        val c2 = ClassBlock(
            id = 11,
            name = "Physics Lab Brief",
            room = "Lab B",
            professor = "Dr. Tan",
            dayOfWeek = 1,
            startHour = 9,
            startMinute = 0,
            durationMinutes = 30,
            colorHex = "#B85D3B"
        )

        var selectedForDetails: ClassBlock? = null
        composeTestRule.setContent {
            ScheduleMakerTheme {
                WeeklyGridView(
                    classes = listOf(c1, c2),
                    settings = ScheduleSettings(startHour = 8, endHour = 12),
                    activePlacementBlock = null,
                    onClassClick = { selectedForDetails = it },
                    onStartPlacement = {},
                    onCancelPlacement = {},
                    onPlaceBlock = { _, _, _ -> },
                    onUnscheduleBlock = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("class_card_10").assertIsDisplayed().performClick()
        assertEquals(10, selectedForDetails?.id)

        composeTestRule.onNodeWithTag("class_card_11").assertIsDisplayed().performClick()
        assertEquals(11, selectedForDetails?.id)
    }

    @Test
    fun emptyDayWithPool_showsChooseFromPool_andDaySemantics() {
        val poolClass = ClassBlock(id = 1, name = "Ethics", dayOfWeek = 0, durationMinutes = 60)
        var chosePool = false
        composeTestRule.setContent {
            ScheduleMakerTheme {
                DailyTimelineView(
                    classes = listOf(poolClass),
                    settings = ScheduleSettings(showWeekends = false),
                    activePlacementBlock = null,
                    onClassClick = {},
                    onPlaceBlock = { _, _, _ -> },
                    onUnscheduleBlock = {},
                    onAddClassClick = {},
                    onChooseFromPoolClick = { chosePool = true },
                    initialSelectedDay = 1
                )
            }
        }

        composeTestRule.onNodeWithTag("day_chip_1").assertIsSelected()
        composeTestRule.onNodeWithTag("day_chip_6").assertIsDisplayed()
        composeTestRule.onNodeWithTag("day_chip_7").assertDoesNotExist()
        composeTestRule.onNode(
            SemanticsMatcher("Monday full name in contentDescription") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { it.contains("Monday") } == true
            }
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("choose_from_pool_empty_button").assertIsDisplayed().performClick()
        assertEquals(true, chosePool)
    }

    @Test
    fun placementState_survivesRestore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = ScheduleDatabase.getDatabase(app)
        val repo = ScheduleRepository(db.scheduleDao())
        val poolId = runBlocking {
            repo.clearAllClassBlocks()
            repo.saveSettings(ScheduleSettings())
            repo.insertClassBlock(
                ClassBlock(name = "Systems Design", dayOfWeek = 0, durationMinutes = 60)
            ).toInt()
        }
        val viewModel = ScheduleViewModel(app)
        val restorationTester = StateRestorationTester(composeTestRule)
        try {
            restorationTester.setContent {
                ScheduleMakerTheme {
                    ScheduleApp(viewModel = viewModel)
                }
            }
            composeTestRule.waitUntil(5000) {
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.classBlocks.value.any { it.id == poolId }
            }
            composeTestRule.onNodeWithTag("nav_tab_pool").performClick()
            composeTestRule.onNodeWithTag("pool_place_$poolId").performClick()
            composeTestRule.onNodeWithTag("timeline_placement_banner").assertIsDisplayed()

            restorationTester.emulateSavedInstanceStateRestore()

            composeTestRule.onNodeWithTag("timeline_placement_banner").assertIsDisplayed()
            assertEquals(1, composeTestRule.onAllNodesWithText("Systems Design").fetchSemanticsNodes().size)
            assertEquals(1, viewModel.classBlocks.value.count { !it.isScheduled })
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}

private fun <T> SemanticsConfiguration.getOrNull(
    key: SemanticsPropertyKey<T>
): T? = if (contains(key)) get(key) else null
