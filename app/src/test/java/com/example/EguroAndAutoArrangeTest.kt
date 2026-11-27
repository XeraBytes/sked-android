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
    fun myClassImportsAllEightSubjectsAndTheirOwnMeetings(): Unit = runBlocking {
        val courses = listOf(
            Triple("CEEL 301", "Graphics Design", "(M|4:00PM-5:30PM|1JMC-CL4) (W|4:00PM-5:30PM|1JMC-CL4) (T|11:00AM-1:00PM|R-4)"),
            Triple("CEEL 311", "Mobile Application Development", "(M|1:00PM-2:30PM|1JMC-CL3) (W|1:00PM-2:30PM|1JMC-CL3) (Th|7:00AM-9:00AM|R-2)"),
            Triple("CS 301", "Computer Organization & Assembly Language", "(M|10:00AM-11:30AM|1JMC-CL2) (W|10:00AM-11:30AM|1JMC-CL2) (Th|9:00AM-11:00AM|R-11)"),
            Triple("CS 311", "Software Engineering 2", "(F|7:00AM-10:00AM|R-10)"),
            Triple("CS 321", "Visual Programming", "(M|7:00AM-8:30AM|1JMC-CL1) (W|7:00AM-8:30AM|1JMC-CL1) (T|1:00PM-3:00PM|R-5)"),
            Triple("HCI 301", "Human Computer Interaction", "(T|6:00PM-9:00PM|R-11)"),
            Triple("RIZAL 301", "The Life and Works of Rizal", "(S|1:00PM-4:00PM|3JMC-7)"),
            Triple("SQ 301", "Software Quality Assurance", "(S|7:00AM-10:00AM|R-8)")
        )
        val banner = "<div class='card-body'><h5>Fiscal Year : 2026-2027 / Semester : 1st Semester</h5></div>"
        val page = banner + courses.mapIndexed { index, (code, title, slots) ->
            """
            <section><table><tr><td>QUIZ</td><td>0</td></tr><tr><td>ACTIVITY</td><td>3</td></tr></table>
            <ul><li>Class Ref #:${100 + index}</li><li>3-CS1</li><li>$code</li>
            <li>${title.replace("&", "&amp;")}</li><li>UNITS: 3</li><li>Instructor $index</li></ul>
            <b>SCHEDULES:</b><ul>${slots.split(" ").joinToString("") { "<li>$it</li>" }}</ul>
            <button>OPEN CLASS</button></section>
            """.trimIndent()
        }.joinToString("")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/index.php" -> "<input name='token_login_form' value='abc'>"
                "/app/login.php" -> "$banner<a href='/calendar'>Calendar of Events</a><a href='/enrolled'>My Class</a>"
                "/enrolled" -> page
                else -> throw AssertionError("Unexpected page ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }.build()
        val result = EguroClient.loginAndFetchSchedule(
            ApplicationProvider.getApplicationContext(), "user", "pass", httpClient = client,
            geminiFallback = { _, _ -> throw AssertionError("Structured cards should parse locally") }
        )
        assertEquals(courses.associate { it.second to it.third.split(" ").size }, result.classes.groupingBy { it.name }.eachCount())
        assertEquals(16, result.classes.size)
        assertTrue(result.classes.all { it.isScheduled })
        assertEquals(listOf("1/16/0/90/1JMC-CL4", "3/16/0/90/1JMC-CL4", "2/11/0/120/R-4"),
            result.classes.filter { it.name == "Graphics Design" }.map { "${it.dayOfWeek}/${it.startHour}/${it.startMinute}/${it.durationMinutes}/${it.room}" })
        assertTrue(result.classes.any { it.name == courses[1].second && it.dayOfWeek == 4 && it.professor == "Instructor 1" })
        assertTrue(result.classes.any { it.name == courses.last().second && it.dayOfWeek == 6 && it.startHour == 7 && it.room == "R-8" })
        assertTrue(EguroClient.parseUntimedCourseCards(banner).isEmpty())
        val partialPage = page.replace("(S|7:00AM-10:00AM|R-8)", "TBA")
        val partial = EguroClient.parseScheduleFromPayload(partialPage)
        assertEquals(15, partial.count { it.isScheduled })
        assertEquals("Software Quality Assurance", partial.single { !it.isScheduled }.name)
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
