package com.example

import android.content.Context
import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.prefs.AppPreferences
import com.example.data.repository.ScheduleRepository
import com.example.util.ExportHelper
import com.example.util.GeminiHelper
import com.example.util.LocalStorageHelper
import com.example.ui.viewmodel.initializeSchedule
import com.example.ui.viewmodel.ScheduleViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchedulePersistenceAndExportTest {

    private lateinit var context: Context
    private lateinit var db: ScheduleDatabase
    private lateinit var repo: ScheduleRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ScheduleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ScheduleRepository(db.scheduleDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `demo starts once and deleting its last class survives startup`() = runBlocking {
        LocalStorageHelper.clearAll(context)
        repo.initializeSchedule(context) {
            repo.insertClassBlock(ClassBlock(name = "Demo"))
            repo.saveSettings(ScheduleSettings())
        }
        val demo = repo.allClassBlocks.first().single()
        repo.deleteClassBlockById(demo.id)

        repo.initializeSchedule(context) { error("Demo schedule loaded twice") }
        assertTrue(repo.allClassBlocks.first().isEmpty())
    }

    @Test
    fun `startup preserves an intentionally empty schedule even with a stale backup`() = runBlocking {
        val settings = ScheduleSettings(termName = "Second semester")
        repo.saveSettings(settings)
        LocalStorageHelper.saveClasses(context, listOf(ClassBlock(name = "Deleted class")))

        repo.initializeSchedule(context) { error("Demo schedule should not be loaded") }

        assertTrue(repo.allClassBlocks.first().isEmpty())
        assertEquals(settings, repo.settings.first())
    }

    @Test
    fun `rapid moves undo and redo in submission order`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ScheduleRepository(ScheduleDatabase.getDatabase(app).scheduleDao())
        val id = store.insertClassBlock(ClassBlock(name = "Math", dayOfWeek = 1)).toInt()
        val model = ScheduleViewModel(app)
        suspend fun awaitDay(day: Int) {
            withTimeout(10000) {
                store.allClassBlocks.first { blocks -> blocks.any { it.id == id && it.dayOfWeek == day } }
            }
        }
        try {
            model.updateClassSchedule(id, 2, 9, 0)
            model.updateClassSchedule(id, 3, 9, 0)
            awaitDay(3)
            model.undo()
            awaitDay(2)
            model.undo()
            awaitDay(1)
            model.redo()
            model.redo()
            awaitDay(3)
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `repository inserts updates and deletes class blocks accurately`() = runBlocking {
        val insertedId = repo.insertClassBlock(
            ClassBlock(
                name = "Databases",
                professor = "Dr. Santos",
                room = "Lab 1",
                dayOfWeek = 1,
                startHour = 10,
                startMinute = 30,
                durationMinutes = 90,
                colorHex = "#2E5A4F"
            )
        ).toInt()

        val initial = repo.allClassBlocks.first()
        assertEquals(1, initial.size)
        assertEquals("Databases", initial.first().name)
        assertEquals(30, initial.first().startMinute)

        repo.updateClassBlock(initial.first().copy(dayOfWeek = 3, startMinute = 30))
        val moved = repo.allClassBlocks.first().first()
        assertEquals(3, moved.dayOfWeek)
        assertEquals(30, moved.startMinute)

        repo.deleteClassBlockById(insertedId)
        assertTrue(repo.allClassBlocks.first().isEmpty())
    }

    @Test
    fun `preferences persist custom api key and updater repository settings`() {
        AppPreferences.setCustomGeminiApiKey(context, "test-gemini-key")
        assertEquals("test-gemini-key", AppPreferences.getEffectiveGeminiApiKey(context))

        AppPreferences.setUpdateRepository(context, "MyOrg", "MyReleases")
        assertEquals("MyOrg", AppPreferences.getUpdateOwner(context))
        assertEquals("MyReleases", AppPreferences.getUpdateRepo(context))

        AppPreferences.setAutoCheckUpdatesEnabled(context, false)
        assertFalse(AppPreferences.isAutoCheckUpdatesEnabled(context))
    }

    @Test
    fun `export helper renders non-empty bitmap including overlapping lanes`() {
        val classes = listOf(
            ClassBlock(id = 1, name = "Algo", dayOfWeek = 1, startHour = 9, startMinute = 0, durationMinutes = 90, colorHex = "#2E5A4F"),
            ClassBlock(id = 2, name = "Lab", dayOfWeek = 1, startHour = 9, startMinute = 30, durationMinutes = 60, colorHex = "#B85D3B")
        )
        val bitmap = ExportHelper.generateScheduleBitmap(
            classes = classes,
            settings = ScheduleSettings(schoolName = "Engineering", termName = "Fall", startHour = 8, endHour = 18)
        )
        assertTrue(bitmap.width == 1920)
        assertTrue(bitmap.height >= 1360)
        bitmap.recycle()
    }

    @Test
    fun `gemini response cleaner strips markdown json fences`() {
        val raw = """
            ```json
            [{"name":"Physics","dayOfWeek":1,"startHour":9,"startMinute":0,"durationMinutes":60}]
            ```
        """.trimIndent()
        val cleaned = GeminiHelper.cleanJsonResponse(raw)
        assertTrue(cleaned.startsWith("["))
        assertTrue(cleaned.endsWith("]"))
    }
}
