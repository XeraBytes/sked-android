package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.repository.ScheduleRepository
import com.example.util.LocalStorageHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScheduleMoveAction(
    val classBlockId: Int,
    val fromDayOfWeek: Int,
    val fromStartHour: Int,
    val fromStartMinute: Int,
    val toDayOfWeek: Int,
    val toStartHour: Int,
    val toStartMinute: Int
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduleRepository

    val classBlocks: StateFlow<List<ClassBlock>>
    val settings: StateFlow<ScheduleSettings>

    // Drag-and-drop state tracker
    private val _draggedBlockId = MutableStateFlow<Int?>(null)
    val draggedBlockId = _draggedBlockId.asStateFlow()

    // Undo/Redo stack and state
    private val undoStack = mutableListOf<ScheduleMoveAction>()
    private val redoStack = mutableListOf<ScheduleMoveAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private fun pushToUndo(action: ScheduleMoveAction) {
        undoStack.add(action)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        viewModelScope.launch {
            if (undoStack.isNotEmpty()) {
                val action = undoStack.removeAt(undoStack.size - 1)
                val currentList = classBlocks.value
                val match = currentList.find { it.id == action.classBlockId }
                if (match != null) {
                    val reverted = match.copy(
                        dayOfWeek = action.fromDayOfWeek,
                        startHour = action.fromStartHour,
                        startMinute = action.fromStartMinute
                    )
                    repository.updateClassBlock(reverted)
                }
                redoStack.add(action)
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = redoStack.isNotEmpty()
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            if (redoStack.isNotEmpty()) {
                val action = redoStack.removeAt(redoStack.size - 1)
                val currentList = classBlocks.value
                val match = currentList.find { it.id == action.classBlockId }
                if (match != null) {
                    val reapplied = match.copy(
                        dayOfWeek = action.toDayOfWeek,
                        startHour = action.toStartHour,
                        startMinute = action.toStartMinute
                    )
                    repository.updateClassBlock(reapplied)
                }
                undoStack.add(action)
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = redoStack.isNotEmpty()
            }
        }
    }

    init {
        val database = ScheduleDatabase.getDatabase(application)
        repository = ScheduleRepository(database.scheduleDao())

        // Class blocks state flow
        classBlocks = repository.allClassBlocks
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Settings flow (prepopulating with default if not configured)
        settings = repository.settings
            .map { it ?: ScheduleSettings() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ScheduleSettings()
            )

        // Sequentially initialize database, restoring from backup if empty, and then start automatic local storage sync
        viewModelScope.launch {
            // Wait for Room database flow's first emission
            val initialList = repository.allClassBlocks.first()
            if (initialList.isEmpty()) {
                val savedClasses = LocalStorageHelper.getClasses(application)
                val savedSettings = LocalStorageHelper.getSettings(application)
                if (savedClasses != null && savedClasses.isNotEmpty()) {
                    for (c in savedClasses) {
                        repository.insertClassBlock(c)
                    }
                    if (savedSettings != null) {
                        repository.saveSettings(savedSettings)
                    }
                } else {
                    seedDemoSchedule()
                }
            }

            // Start observing and auto-saving class blocks to LocalStorage
            launch {
                repository.allClassBlocks.collect { list ->
                    LocalStorageHelper.saveClasses(application, list)
                }
            }

            // Start observing and auto-saving settings to LocalStorage
            launch {
                repository.settings.collect { settingsObj ->
                    if (settingsObj != null) {
                        LocalStorageHelper.saveSettings(application, settingsObj)
                    }
                }
            }
        }
    }

    // Dragging helper setters
    fun setDraggedBlockId(id: Int?) {
        _draggedBlockId.value = id
    }

    fun addClassBlock(
        name: String,
        professor: String,
        room: String,
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        colorHex: String
    ) {
        viewModelScope.launch {
            val block = ClassBlock(
                name = name,
                professor = professor,
                room = room,
                dayOfWeek = dayOfWeek,
                startHour = startHour,
                startMinute = startMinute,
                durationMinutes = durationMinutes,
                colorHex = colorHex
            )
            repository.insertClassBlock(block)
        }
    }

    fun addClassBlocks(blocks: List<ClassBlock>) {
        viewModelScope.launch {
            for (block in blocks) {
                repository.insertClassBlock(block)
            }
        }
    }

    fun updateClassBlock(block: ClassBlock) {
        viewModelScope.launch {
            repository.updateClassBlock(block)
        }
    }

    fun updateClassSchedule(id: Int, dayOfWeek: Int, startHour: Int, startMinute: Int) {
        viewModelScope.launch {
            val currentList = classBlocks.value
            val match = currentList.find { it.id == id }
            if (match != null) {
                if (match.dayOfWeek != dayOfWeek || match.startHour != startHour || match.startMinute != startMinute) {
                    val action = ScheduleMoveAction(
                        classBlockId = id,
                        fromDayOfWeek = match.dayOfWeek,
                        fromStartHour = match.startHour,
                        fromStartMinute = match.startMinute,
                        toDayOfWeek = dayOfWeek,
                        toStartHour = startHour,
                        toStartMinute = startMinute
                    )
                    pushToUndo(action)
                }
                val updated = match.copy(
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute
                )
                repository.updateClassBlock(updated)
            }
        }
    }

    fun unscheduleClassBlock(id: Int) {
        viewModelScope.launch {
            val currentList = classBlocks.value
            val match = currentList.find { it.id == id }
            if (match != null) {
                if (match.dayOfWeek != 0) {
                    val action = ScheduleMoveAction(
                        classBlockId = id,
                        fromDayOfWeek = match.dayOfWeek,
                        fromStartHour = match.startHour,
                        fromStartMinute = match.startMinute,
                        toDayOfWeek = 0,
                        toStartHour = match.startHour,
                        toStartMinute = match.startMinute
                    )
                    pushToUndo(action)
                }
                val updated = match.copy(dayOfWeek = 0) // Moved to unscheduled pool
                repository.updateClassBlock(updated)
            }
        }
    }

    fun deleteClassBlockById(id: Int) {
        viewModelScope.launch {
            repository.deleteClassBlockById(id)
        }
    }

    fun saveSettings(
        schoolName: String,
        termName: String,
        startHour: Int,
        endHour: Int,
        showWeekends: Boolean,
        screenshotUri: String?
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val updated = currentSettings.copy(
                schoolName = schoolName,
                termName = termName,
                startHour = startHour,
                endHour = endHour,
                showWeekends = showWeekends,
                referenceScreenshotUri = screenshotUri
            )
            repository.saveSettings(updated)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllClassBlocks()
            // Reset settings as well
            repository.saveSettings(ScheduleSettings())
            LocalStorageHelper.clearAll(getApplication())
        }
    }

    // Seed data so first-time setup is beautifully rendered and playable
    fun seedDemoSchedule() {
        viewModelScope.launch {
            repository.clearAllClassBlocks()

            val demoClasses = listOf(
                // Scheduled
                ClassBlock(name = "Data Structures & Algorithms", professor = "Dr. Alan Turing", room = "Lab 3B", dayOfWeek = 1, startHour = 9, startMinute = 0, durationMinutes = 90, colorHex = "#6366F1"), // Indigo
                ClassBlock(name = "Physics II", professor = "Dr. Marie Curie", room = "Room 102", dayOfWeek = 1, startHour = 11, startMinute = 0, durationMinutes = 60, colorHex = "#EC4899"), // Pink
                
                ClassBlock(name = "Calculus III", professor = "Prof. Newton", room = "Hall A", dayOfWeek = 2, startHour = 10, startMinute = 0, durationMinutes = 90, colorHex = "#10B981"), // Emerald
                ClassBlock(name = "Human-Computer Interaction", professor = "Dr. Don Norman", room = "Studio D", dayOfWeek = 2, startHour = 13, startMinute = 30, durationMinutes = 120, colorHex = "#8B5CF6"), // Purple
                
                ClassBlock(name = "Data Structures & Algorithms", professor = "Dr. Alan Turing", room = "Lab 3B", dayOfWeek = 3, startHour = 9, startMinute = 0, durationMinutes = 90, colorHex = "#6366F1"), // Indigo
                ClassBlock(name = "History of Tech", professor = "Prof. Lovelace", room = "Hall C", dayOfWeek = 3, startHour = 15, startMinute = 0, durationMinutes = 60, colorHex = "#F59E0B"), // Amber

                ClassBlock(name = "Calculus III", professor = "Prof. Newton", room = "Hall A", dayOfWeek = 4, startHour = 10, startMinute = 0, durationMinutes = 90, colorHex = "#10B981"), // Emerald
                ClassBlock(name = "Cybersecurity Essentials", professor = "Dr. Hopper", room = "Room 304", dayOfWeek = 4, startHour = 13, startMinute = 0, durationMinutes = 90, colorHex = "#EF4444"), // Red

                ClassBlock(name = "Mobile App Development", professor = "Prof. Kotlin", room = "Lab 5A", dayOfWeek = 5, startHour = 9, startMinute = 0, durationMinutes = 120, colorHex = "#06B6D4"), // Cyan
                ClassBlock(name = "Weekly Tech Seminar", professor = "Guest Speakers", room = "Auditorium", dayOfWeek = 5, startHour = 14, startMinute = 0, durationMinutes = 60, colorHex = "#3B82F6"), // Blue

                // Unscheduled Pool (Templates)
                ClassBlock(name = "Database Systems (Lab)", professor = "Prof. Codd", room = "Lab 1A", dayOfWeek = 0, startHour = 10, startMinute = 0, durationMinutes = 120, colorHex = "#14B8A6"), // Teal
                ClassBlock(name = "English Composition", professor = "Dr. Shakespeare", room = "Room 201", dayOfWeek = 0, startHour = 14, startMinute = 0, durationMinutes = 90, colorHex = "#84CC16") // Lime
            )

            for (c in demoClasses) {
                repository.insertClassBlock(c)
            }

            // Default seed settings
            repository.saveSettings(
                ScheduleSettings(
                    schoolName = "Engineering Faculty",
                    termName = "Fall Semester 2026",
                    startHour = 8,
                    endHour = 18,
                    showWeekends = false,
                    referenceScreenshotUri = null
                )
            )
        }
    }
}
