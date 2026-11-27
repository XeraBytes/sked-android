package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.repository.ScheduleRepository
import com.example.lms.EguroClient
import com.example.ui.dialogs.EguroLoginDialog
import com.example.ui.theme.ScheduleMakerTheme
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.util.ScheduleAutoArranger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class EguroAndAutoArrangeTest {

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
    fun autoArranger_placesPoolWithoutConflictsAndBalancesDays() {
        val existing = ClassBlock(
            id = 1,
            name = "Fixed Class",
            dayOfWeek = 1,
            startHour = 8,
            startMinute = 0,
            durationMinutes = 120
        )
        val poolA = ClassBlock(id = 2, name = "Pool A", dayOfWeek = 0, durationMinutes = 90)
        val poolB = ClassBlock(id = 3, name = "Pool B", dayOfWeek = 0, durationMinutes = 60)

        val plan = ScheduleAutoArranger.planAutoArrange(
            allClasses = listOf(existing, poolA, poolB),
            settings = ScheduleSettings(startHour = 8, endHour = 18, showWeekends = false)
        )

        assertEquals(2, plan.placedBlocks.size)
        assertTrue(plan.unplacedBlocks.isEmpty())
        val placedAfter = plan.placedBlocks.map { it.second }
        // Day 1 already has 120m, so Pool A and Pool B spread to Tuesday (2) and Wednesday (3)
        assertEquals(2, placedAfter[0].dayOfWeek)
        assertEquals(3, placedAfter[1].dayOfWeek)
    }

    @Test
    fun viewModel_autoArrangeAndUndoRestoresPoolAtomically(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = ScheduleDatabase.getDatabase(app)
        val repo = ScheduleRepository(db.scheduleDao())
        repo.clearAllClassBlocks()
        repo.saveSettings(ScheduleSettings(startHour = 8, endHour = 18, showWeekends = false))
        val p1 = repo.insertClassBlock(ClassBlock(name = "Math", dayOfWeek = 0, durationMinutes = 60)).toInt()
        val p2 = repo.insertClassBlock(ClassBlock(name = "Science", dayOfWeek = 0, durationMinutes = 60)).toInt()

        val vm = ScheduleViewModel(app)
        try {
            vm.autoArrangePool()
            withTimeout(5000) {
                repo.allClassBlocks.first { blocks ->
                    blocks.any { it.id == p1 && it.isScheduled } &&
                        blocks.any { it.id == p2 && it.isScheduled }
                }
            }

            vm.undo()
            withTimeout(5000) {
                repo.allClassBlocks.first { blocks ->
                    blocks.any { it.id == p1 && !it.isScheduled } &&
                        blocks.any { it.id == p2 && !it.isScheduled }
                }
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun eguroClient_parsesCsrfTableJsonAndMockedAuthFlow(): Unit = runBlocking {
        val loginHtml = """
            <form id="login_form" action="https://lms.ccc.edu.ph/app/login.php?formSubmitted=true" method="POST">
              <input type="hidden" name="token_login_form" value="abc123token"/>
            </form>
        """.trimIndent()
        assertEquals("abc123token", EguroClient.extractCsrfToken(loginHtml))

        val errorHtml = """
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
              <button type="button" class="close"><span>&times;</span></button>
              Invalid username or password.
            </div>
            <span>Login Attempts: 0</span>
        """.trimIndent()
        assertEquals("Invalid username or password.", EguroClient.extractLoginErrorMessage(errorHtml))

        val dashboardHtml = """
            <html><body>
              <a href="student/my_schedule.php">My Class Schedule</a>
            </body></html>
        """.trimIndent()
        val scheduleHtml = """
            <table>
              <tr><th>Course</th><th>Schedule</th><th>Room</th><th>Instructor</th></tr>
              <tr><td>IT 301 Systems Integration</td><td>MWF 9:00 AM - 10:30 AM</td><td>Lab 3</td><td>Prof. Santos</td></tr>
              <tr><td>GE 105 Ethics</td><td>TTh 1:00 PM - 2:30 PM</td><td>Room 204</td><td>Dr. Cruz</td></tr>
            </table>
        """.trimIndent()

        var postCount = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val bodyText = when {
                    url.endsWith("/index.php") -> loginHtml
                    url.contains("/app/login.php") -> {
                        postCount++
                        dashboardHtml
                    }
                    url.contains("my_schedule.php") -> scheduleHtml
                    else -> ""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyText.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val result = EguroClient.loginAndFetchSchedule(
            context = app,
            username = "2024-0001",
            password = "secret",
            httpClient = mockClient
        )

        assertEquals(1, postCount) // single login POST, never retried
        assertEquals(5, result.classes.size) // MWF (3) + TTh (2)
        assertTrue(result.classes.any { it.name.contains("IT 301") && it.dayOfWeek == 1 && it.startHour == 9 })
        assertTrue(result.classes.any { it.name.contains("GE 105") && it.dayOfWeek == 4 && it.startHour == 13 })
    }

    @Test
    fun eguroClient_discoversMyClassByAnchorText_andImportsCardCoursesIntoPool(): Unit = runBlocking {
        val loginHtml = """<input type="hidden" name="token_login_form" value="tok"/>"""
        val dashboardHtml = """
            <html><body>
              <nav><a href="student/portal.php?p=42">My Class</a></nav>
            </body></html>
        """.trimIndent()
        val myClassHtml = """
            <html><body>
              <div class="card">
                <div class="card-body">
                  <h5 class="card-title">CS 301 - Software Engineering</h5>
                  <p>Prof. R. Mendoza</p>
                </div>
              </div>
              <div class="card">
                <div class="card-body">
                  <h5 class="card-title">IT 204 - Networking</h5>
                  <p>Sat 8:00 AM - 11:00 AM · Room 302</p>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val body = when {
                    url.endsWith("/index.php") -> loginHtml
                    url.contains("/app/login.php") -> dashboardHtml
                    url.contains("portal.php?p=42") -> myClassHtml
                    else -> ""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val app = ApplicationProvider.getApplicationContext<Application>()
        val timedResult = EguroClient.loginAndFetchSchedule(
            context = app,
            username = "2024-0001",
            password = "secret",
            httpClient = mockClient
        )
        assertTrue(timedResult.checkedPages.any { it.contains("portal.php?p=42") })
        assertEquals(1, timedResult.classes.size)
        assertEquals(6, timedResult.classes.first().dayOfWeek) // Saturday = 6

        // Verify untimed "My Class" fallback puts enrolled courses into Pool (dayOfWeek = 0)
        val untimedOnlyHtml = """
            <div class="card"><div class="card-body"><h5 class="card-title">CS 301 - Software Engineering</h5><p>Prof. R. Mendoza</p></div></div>
        """.trimIndent()
        val untimedBlocks = EguroClient.parseUntimedCourseCards(untimedOnlyHtml)
        assertEquals(1, untimedBlocks.size)
        assertEquals(0, untimedBlocks.first().dayOfWeek)
        assertEquals("CS 301 - Software Engineering", untimedBlocks.first().name)
    }

    @Test
    fun eguroLoginDialog_submitsCredentialsNatively() {
        var submittedUser = ""
        var submittedPass = ""
        var submittedPath = ""
        composeTestRule.setContent {
            ScheduleMakerTheme {
                EguroLoginDialog(
                    initialUsername = "",
                    initialSchedulePath = "",
                    isSyncing = false,
                    errorMessage = null,
                    onSync = { u, p, path ->
                        submittedUser = u
                        submittedPass = p
                        submittedPath = path
                    },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("eguro_login_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eguro_username_input").performTextInput("2024-0123")
        composeTestRule.onNodeWithTag("eguro_password_input").performTextInput("mypassword")
        composeTestRule.onNodeWithTag("eguro_submit_button").performClick()

        assertEquals("2024-0123", submittedUser)
        assertEquals("mypassword", submittedPass)
        assertEquals("", submittedPath)
    }
}
