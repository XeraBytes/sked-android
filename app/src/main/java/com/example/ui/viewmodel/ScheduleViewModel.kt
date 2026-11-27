package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.database.ScheduleDatabase
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.prefs.AppPreferences
import com.example.data.repository.ScheduleRepository
import com.example.lms.EguroClient
import com.example.lms.EguroSyncResult
import com.example.updater.AppUpdateInstaller
import com.example.updater.AppUpdater
import com.example.updater.ReleaseInfo
import com.example.updater.UpdateCheckResult
import com.example.util.GeminiHelper
import com.example.util.LocalStorageHelper
import com.example.util.ScheduleAutoArranger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScheduleAction {
    data class Insert(val block: ClassBlock) : ScheduleAction
    data class BatchInsert(val blocks: List<ClassBlock>) : ScheduleAction
    data class Update(val before: ClassBlock, val after: ClassBlock) : ScheduleAction
    data class BatchUpdate(val transitions: List<Pair<ClassBlock, ClassBlock>>) : ScheduleAction
    data class Delete(val block: ClassBlock) : ScheduleAction
}

data class ImportUiState(
    val inputMode: String = "file", // "file" or "text"
    val pastedText: String = "",
    val selectedFileUri: String? = null,
    val selectedFileName: String = "",
    val selectedFileType: String = "",
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val parsedClasses: List<ClassBlock>? = null,
    val isSaving: Boolean = false,
    val lastSavedCount: Int? = null
)

data class UpdaterUiState(
    val isCheckingUpdates: Boolean = false,
    val availableUpdate: UpdateCheckResult.UpdateAvailable? = null,
    val showUpdateDialog: Boolean = false,
    val updateStatusSummary: String? = null,
    val updateError: String? = null,
    val updateErrorReleaseUrl: String? = null,
    val downloadProgress: AppUpdateInstaller.Progress? = null,
    val downloadingRelease: ReleaseInfo? = null,
    val showWhatsNewDialog: Boolean = false,
    val whatsNewNotes: List<String> = emptyList()
)

data class EguroUiState(
    val savedUsername: String = "",
    val customSchedulePath: String = "",
    val lastSyncedMs: Long = 0L,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val lastSourceUrl: String? = null,
    val lastSyncedCount: Int? = null,
    val checkedPages: List<String> = emptyList()
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduleRepository

    val classBlocks: StateFlow<List<ClassBlock>>
    val settings: StateFlow<ScheduleSettings>

    private val _draggedBlockId = MutableStateFlow<Int?>(null)
    val draggedBlockId = _draggedBlockId.asStateFlow()

    private val edits = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val undoStack = mutableListOf<ScheduleAction>()
    private val redoStack = mutableListOf<ScheduleAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _updaterState = MutableStateFlow(UpdaterUiState())
    val updaterState: StateFlow<UpdaterUiState> = _updaterState.asStateFlow()

    private val _eguroState = MutableStateFlow(
        EguroUiState(
            savedUsername = AppPreferences.getEguroUsername(application),
            customSchedulePath = AppPreferences.getEguroSchedulePath(application),
            lastSyncedMs = AppPreferences.getEguroLastSyncMs(application)
        )
    )
    val eguroState: StateFlow<EguroUiState> = _eguroState.asStateFlow()

    private var hasRunStartupUpdateCheck = false
    private var hasCheckedWhatsNewOnLaunch = false
    private var downloadJob: Job? = null

    init {
        val database = ScheduleDatabase.getDatabase(application)
        repository = ScheduleRepository(database.scheduleDao())

        classBlocks = repository.allClassBlocks
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        settings = repository.settings
            .map { it ?: ScheduleSettings() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ScheduleSettings()
            )

        viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction {
                repository.initializeSchedule(application) { seedDemoScheduleInternal() }
            }

            launch {
                repository.allClassBlocks.collect { list ->
                    LocalStorageHelper.saveClasses(application, list)
                }
            }

            launch {
                repository.settings.collect { settingsObj ->
                    if (settingsObj != null) {
                        LocalStorageHelper.saveSettings(application, settingsObj)
                    }
                }
            }
            for (edit in edits) edit()
        }
    }

    private fun enqueueEdit(edit: suspend () -> Unit) {
        edits.trySend(edit)
    }

    override fun onCleared() {
        downloadJob?.cancel()
        edits.cancel()
        super.onCleared()
    }

    private fun refreshUndoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun pushToUndo(action: ScheduleAction) {
        undoStack.add(action)
        redoStack.clear()
        refreshUndoFlags()
    }

    fun undo() {
        enqueueEdit {
            if (undoStack.isEmpty()) return@enqueueEdit
            val action = undoStack.removeAt(undoStack.lastIndex)
            when (action) {
                is ScheduleAction.Insert -> {
                    repository.deleteClassBlockById(action.block.id)
                }
                is ScheduleAction.BatchInsert -> {
                    action.blocks.forEach { repository.deleteClassBlockById(it.id) }
                }
                is ScheduleAction.Update -> {
                    repository.updateClassBlock(action.before)
                }
                is ScheduleAction.BatchUpdate -> {
                    action.transitions.forEach { (before, _) ->
                        repository.updateClassBlock(before)
                    }
                }
                is ScheduleAction.Delete -> {
                    repository.insertClassBlock(action.block)
                }
            }
            redoStack.add(action)
            refreshUndoFlags()
        }
    }

    fun redo() {
        enqueueEdit {
            if (redoStack.isEmpty()) return@enqueueEdit
            val action = redoStack.removeAt(redoStack.lastIndex)
            when (action) {
                is ScheduleAction.Insert -> {
                    repository.insertClassBlock(action.block)
                }
                is ScheduleAction.BatchInsert -> {
                    action.blocks.forEach { repository.insertClassBlock(it) }
                }
                is ScheduleAction.Update -> {
                    repository.updateClassBlock(action.after)
                }
                is ScheduleAction.BatchUpdate -> {
                    action.transitions.forEach { (_, after) ->
                        repository.updateClassBlock(after)
                    }
                }
                is ScheduleAction.Delete -> {
                    repository.deleteClassBlockById(action.block.id)
                }
            }
            undoStack.add(action)
            refreshUndoFlags()
        }
    }

    fun setDraggedBlockId(id: Int?) {
        _draggedBlockId.value = id
    }

    // --- Import Extraction State & Actions (survive rotation, prevent duplicate runs) ---

    fun setImportInputMode(mode: String) {
        _importState.update { it.copy(inputMode = mode, lastSavedCount = null) }
    }

    fun setImportPastedText(text: String) {
        _importState.update {
            it.copy(
                pastedText = text,
                analysisError = null,
                lastSavedCount = null
            )
        }
    }

    fun setImportSelectedFile(uri: String?, name: String, type: String) {
        _importState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                selectedFileType = type,
                parsedClasses = null,
                analysisError = null,
                lastSavedCount = null
            )
        }
    }

    fun clearImportFile() {
        _importState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = "",
                selectedFileType = "",
                parsedClasses = null,
                analysisError = null
            )
        }
    }

    fun clearImportResults() {
        _importState.update {
            it.copy(
                parsedClasses = null,
                analysisError = null,
                lastSavedCount = null
            )
        }
    }

    fun startImportExtraction(
        context: Context,
        uriReader: suspend (Context, Uri) -> GeminiHelper.ParseInput = GeminiHelper::processUriToInput,
        extractor: suspend (Context, GeminiHelper.ParseInput) -> List<ClassBlock> = GeminiHelper::parseScheduleWithAI
    ) {
        val snapshot = _importState.value
        if (snapshot.isAnalyzing) return

        val appContext = context.applicationContext ?: context
        val hasValidInput = if (snapshot.inputMode == "text") {
            snapshot.pastedText.isNotBlank()
        } else {
            snapshot.selectedFileUri != null
        }
        if (!hasValidInput) return

        _importState.update {
            it.copy(
                isAnalyzing = true,
                analysisError = null,
                parsedClasses = null,
                lastSavedCount = null
            )
        }

        viewModelScope.launch {
            try {
                val input = if (snapshot.inputMode == "text") {
                    GeminiHelper.ParseInput.Text(snapshot.pastedText)
                } else {
                    val uri = Uri.parse(snapshot.selectedFileUri!!)
                    uriReader(appContext, uri)
                }
                val parsed = extractor(appContext, input)
                _importState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisError = null,
                        parsedClasses = parsed
                    )
                }
            } catch (ce: CancellationException) {
                _importState.update { it.copy(isAnalyzing = false) }
                throw ce
            } catch (e: Exception) {
                _importState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisError = e.localizedMessage ?: "Extraction failed"
                    )
                }
            }
        }
    }

    fun saveExtractedClasses(onCompleted: (Int) -> Unit = {}) {
        val current = _importState.value
        val extracted = current.parsedClasses.orEmpty()
        if (extracted.isEmpty() || current.isSaving) return

        _importState.update { it.copy(isSaving = true) }
        enqueueEdit {
            try {
                val inserted = insertClassBlocksInternal(extracted)
                _importState.update {
                    it.copy(
                        isSaving = false,
                        parsedClasses = null,
                        selectedFileUri = null,
                        selectedFileName = "",
                        selectedFileType = "",
                        pastedText = "",
                        analysisError = null,
                        lastSavedCount = inserted.size
                    )
                }
                viewModelScope.launch(Dispatchers.Main) {
                    onCompleted(inserted.size)
                }
            } catch (t: Throwable) {
                _importState.update {
                    it.copy(
                        isSaving = false,
                        analysisError = t.localizedMessage ?: "Could not save extracted classes"
                    )
                }
            }
        }
    }

    // --- CCC e-GURO LMS Sync & Schedule Auto-Arrange ---

    fun clearEguroError() {
        _eguroState.update { it.copy(syncError = null) }
    }

    fun syncFromEguro(
        context: Context,
        username: String,
        password: String,
        customSchedulePath: String,
        syncProvider: suspend (Context, String, String, String) -> EguroSyncResult = { ctx, u, p, path ->
            EguroClient.loginAndFetchSchedule(ctx, u, p, path)
        },
        onSuccess: (EguroSyncResult) -> Unit = {}
    ) {
        if (_eguroState.value.isSyncing) return
        val appContext = context.applicationContext ?: context
        _eguroState.update {
            it.copy(
                isSyncing = true,
                syncError = null
            )
        }
        viewModelScope.launch {
            try {
                val result = syncProvider(appContext, username, password, customSchedulePath)
                val now = System.currentTimeMillis()
                AppPreferences.saveEguroConnection(appContext, username, customSchedulePath, now)
                _eguroState.update {
                    it.copy(
                        savedUsername = username.trim(),
                        customSchedulePath = customSchedulePath.trim(),
                        lastSyncedMs = now,
                        isSyncing = false,
                        syncError = null,
                        lastSourceUrl = result.sourcePageUrl,
                        lastSyncedCount = result.classes.size,
                        checkedPages = result.checkedPages
                    )
                }
                _importState.update {
                    it.copy(
                        parsedClasses = result.classes,
                        analysisError = null,
                        lastSavedCount = null
                    )
                }
                onSuccess(result)
            } catch (ce: CancellationException) {
                _eguroState.update { it.copy(isSyncing = false) }
                throw ce
            } catch (e: Exception) {
                _eguroState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = e.localizedMessage ?: "Could not sync schedule from CCC e-GURO."
                    )
                }
            }
        }
    }

    fun disconnectEguro(context: Context) {
        val appContext = context.applicationContext ?: context
        EguroClient.clearSessionCookies()
        AppPreferences.clearEguroConnection(appContext)
        _eguroState.value = EguroUiState()
    }

    fun autoArrangePool(onCompleted: (placedCount: Int, unplacedCount: Int) -> Unit = { _, _ -> }) {
        enqueueEdit {
            val currentClasses = repository.allClassBlocks.first()
            val currentSettings = repository.settings.first() ?: ScheduleSettings()
            val plan = ScheduleAutoArranger.planAutoArrange(currentClasses, currentSettings)
            if (plan.placedBlocks.isNotEmpty()) {
                plan.placedBlocks.forEach { (_, after) ->
                    repository.updateClassBlock(after)
                }
                pushToUndo(ScheduleAction.BatchUpdate(plan.placedBlocks))
            }
            viewModelScope.launch(Dispatchers.Main) {
                onCompleted(plan.placedBlocks.size, plan.unplacedBlocks.size)
            }
        }
    }

    // --- In-App Updater State & Actions ---

    fun runStartupUpdateCheckIfNeeded(context: Context) {
        if (hasRunStartupUpdateCheck) return
        hasRunStartupUpdateCheck = true
        if (AppUpdater.shouldAutoCheck(context)) {
            triggerUpdateCheck(context = context, manual = false)
        }
    }

    fun checkWhatsNewOnLaunch(context: Context) {
        if (hasCheckedWhatsNewOnLaunch) return
        hasCheckedWhatsNewOnLaunch = true
        val appContext = context.applicationContext ?: context
        if (AppPreferences.isWhatsNewDontShowAgain(appContext)) return
        val seenCode = AppPreferences.getLastSeenWhatsNewVersionCode(appContext)
        if (BuildConfig.VERSION_CODE > seenCode) {
            _updaterState.update {
                it.copy(
                    showWhatsNewDialog = true,
                    whatsNewNotes = resolveCurrentVersionWhatsNewNotes(appContext)
                )
            }
        }
    }

    fun openWhatsNewDialog(context: Context) {
        val appContext = context.applicationContext ?: context
        _updaterState.update {
            it.copy(
                showWhatsNewDialog = true,
                whatsNewNotes = resolveCurrentVersionWhatsNewNotes(appContext)
            )
        }
    }

    fun dismissWhatsNewDialog(context: Context, dontShowAgain: Boolean) {
        val appContext = context.applicationContext ?: context
        AppPreferences.setLastSeenWhatsNewVersionCode(appContext, BuildConfig.VERSION_CODE)
        if (dontShowAgain) {
            AppPreferences.setWhatsNewDontShowAgain(appContext, true)
        }
        _updaterState.update { it.copy(showWhatsNewDialog = false) }
    }

    fun clearUpdateError() {
        _updaterState.update { it.copy(updateError = null, updateErrorReleaseUrl = null) }
    }

    fun clearSkippedVersion(context: Context) {
        val appContext = context.applicationContext ?: context
        AppPreferences.setSkippedVersion(appContext, null)
    }

    private fun resolveCurrentVersionWhatsNewNotes(context: Context): List<String> {
        val cachedJson = AppPreferences.getCachedReleasesJson(context)
        val cachedNotes = cachedJson?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { AppUpdater.parseReleasesJson(raw) }.getOrNull()
        }?.firstOrNull { release ->
            AppUpdater.getReleaseVersionName(release) == BuildConfig.VERSION_NAME
        }?.body?.lines()?.mapNotNull { line ->
            val cleaned = line.trim().removePrefix("- ").removePrefix("* ").trim()
            cleaned.takeIf { it.isNotBlank() && !it.startsWith("#") }
        }.orEmpty()

        if (cachedNotes.isNotEmpty()) return cachedNotes
        return listOf(
            "Home tab with live Next Class spotlight and quick workspace shortcuts",
            "Animated Sked folio launch intro and header brand emblem",
            "CCC e-GURO My Class parser supporting all subjects, T vs Th, Saturday & TBA",
            "Editorial paper Settings layout and responsive Weekly Sheet navigation",
            "In-app updater What's New dialog with Don't show again & GitHub fallback"
        )
    }

    fun triggerUpdateCheck(
        context: Context,
        manual: Boolean,
        onStatusMessage: (String, Boolean) -> Unit = { _, _ -> }
    ) {
        if (_updaterState.value.isCheckingUpdates) return
        val appContext = context.applicationContext ?: context
        _updaterState.update { it.copy(isCheckingUpdates = true, updateError = null, updateErrorReleaseUrl = null) }

        viewModelScope.launch {
            val res = AppUpdater.checkForUpdates(appContext, forceRefresh = manual)
            when (res) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val skipped = AppPreferences.getSkippedVersion(appContext)
                    _updaterState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            availableUpdate = res,
                            updateStatusSummary = "Update v${res.versionName} ready",
                            showUpdateDialog = manual || skipped != res.versionName
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _updaterState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            availableUpdate = null,
                            updateStatusSummary = "Up to date (v${res.currentVersion})"
                        )
                    }
                    if (manual) {
                        onStatusMessage("You are on the latest version (v${res.currentVersion}).", false)
                    }
                }
                is UpdateCheckResult.Error -> {
                    val owner = AppPreferences.getUpdateOwner(appContext)
                    val repo = AppPreferences.getUpdateRepo(appContext)
                    _updaterState.update {
                        it.copy(
                            isCheckingUpdates = false,
                            updateStatusSummary = res.message,
                            updateError = if (manual) res.message else null,
                            updateErrorReleaseUrl = if (manual) "https://github.com/$owner/$repo/releases" else null
                        )
                    }
                    if (manual) {
                        onStatusMessage(res.message, true)
                    }
                }
            }
        }
    }

    fun openUpdateDialog() {
        if (_updaterState.value.availableUpdate != null) {
            _updaterState.update { it.copy(showUpdateDialog = true) }
        }
    }

    fun dismissUpdateDialog() {
        _updaterState.update { it.copy(showUpdateDialog = false) }
    }

    fun skipUpdateVersion(context: Context) {
        val version = _updaterState.value.availableUpdate?.versionName ?: return
        AppPreferences.setSkippedVersion(context, version)
        _updaterState.update { it.copy(showUpdateDialog = false) }
    }

    fun startUpdateDownload(
        context: Context,
        release: ReleaseInfo,
        onFailure: (String) -> Unit = {}
    ) {
        if (downloadJob?.isActive == true) return
        val appContext = context.applicationContext ?: context
        _updaterState.update {
            it.copy(
                showUpdateDialog = false,
                updateError = null,
                updateErrorReleaseUrl = null,
                downloadingRelease = release,
                downloadProgress = AppUpdateInstaller.Progress(0L, release.asset?.sizeBytes ?: -1L)
            )
        }
        downloadJob = viewModelScope.launch {
            val result = AppUpdateInstaller.downloadAndInstall(appContext, release) { prog ->
                _updaterState.update { it.copy(downloadProgress = prog) }
            }
            result.onFailure { err ->
                val msg = err.localizedMessage ?: "Failed to install update"
                val owner = AppPreferences.getUpdateOwner(appContext)
                val repo = AppPreferences.getUpdateRepo(appContext)
                _updaterState.update {
                    it.copy(
                        downloadingRelease = null,
                        downloadProgress = null,
                        updateError = msg,
                        updateErrorReleaseUrl = release.htmlUrl.ifBlank { "https://github.com/$owner/$repo/releases" }
                    )
                }
                onFailure(msg)
            }.onSuccess {
                _updaterState.update {
                    it.copy(
                        downloadingRelease = null,
                        downloadProgress = null
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _updaterState.update {
            it.copy(
                downloadingRelease = null,
                downloadProgress = null
            )
        }
    }

    // --- Schedule Mutations ---

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
        enqueueEdit {
            val block = ClassBlock(
                name = name.trim(),
                professor = professor.trim(),
                room = room.trim(),
                dayOfWeek = dayOfWeek.coerceIn(0, 7),
                startHour = startHour.coerceIn(0, 23),
                startMinute = startMinute.coerceIn(0, 59),
                durationMinutes = durationMinutes.coerceIn(15, 480),
                colorHex = colorHex
            )
            val newId = repository.insertClassBlock(block).toInt()
            pushToUndo(ScheduleAction.Insert(block.copy(id = newId)))
        }
    }

    fun addClassBlocks(blocks: List<ClassBlock>) {
        if (blocks.isEmpty()) return
        enqueueEdit {
            insertClassBlocksInternal(blocks)
        }
    }

    private suspend fun insertClassBlocksInternal(blocks: List<ClassBlock>): List<ClassBlock> {
        val inserted = blocks.map { block ->
            val clean = block.copy(
                id = 0,
                name = block.name.trim(),
                professor = block.professor.trim(),
                room = block.room.trim(),
                dayOfWeek = block.dayOfWeek.coerceIn(0, 7),
                startHour = block.startHour.coerceIn(0, 23),
                startMinute = block.startMinute.coerceIn(0, 59),
                durationMinutes = block.durationMinutes.coerceIn(15, 480)
            )
            val newId = repository.insertClassBlock(clean).toInt()
            clean.copy(id = newId)
        }
        pushToUndo(ScheduleAction.BatchInsert(inserted))
        return inserted
    }

    fun updateClassBlock(block: ClassBlock) {
        enqueueEdit {
            val previous = repository.getClassBlock(block.id)
            val normalized = block.copy(
                name = block.name.trim(),
                professor = block.professor.trim(),
                room = block.room.trim(),
                dayOfWeek = block.dayOfWeek.coerceIn(0, 7),
                startHour = block.startHour.coerceIn(0, 23),
                startMinute = block.startMinute.coerceIn(0, 59),
                durationMinutes = block.durationMinutes.coerceIn(15, 480)
            )
            repository.updateClassBlock(normalized)
            if (previous != null && previous != normalized) {
                pushToUndo(ScheduleAction.Update(before = previous, after = normalized))
            }
        }
    }

    fun updateClassSchedule(id: Int, dayOfWeek: Int, startHour: Int, startMinute: Int) {
        enqueueEdit {
            val match = repository.getClassBlock(id) ?: return@enqueueEdit
            val updated = match.copy(
                dayOfWeek = dayOfWeek.coerceIn(0, 7),
                startHour = startHour.coerceIn(0, 23),
                startMinute = startMinute.coerceIn(0, 59)
            )
            if (updated != match) {
                repository.updateClassBlock(updated)
                pushToUndo(ScheduleAction.Update(before = match, after = updated))
            }
        }
    }

    fun unscheduleClassBlock(id: Int) {
        enqueueEdit {
            val match = repository.getClassBlock(id) ?: return@enqueueEdit
            if (match.dayOfWeek != 0) {
                val updated = match.copy(dayOfWeek = 0)
                repository.updateClassBlock(updated)
                pushToUndo(ScheduleAction.Update(before = match, after = updated))
            }
        }
    }

    fun deleteClassBlockById(id: Int) {
        enqueueEdit {
            val match = repository.getClassBlock(id)
            repository.deleteClassBlockById(id)
            if (match != null) {
                pushToUndo(ScheduleAction.Delete(match))
            }
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
        enqueueEdit {
            val safeStart = startHour.coerceIn(0, 22)
            val safeEnd = endHour.coerceIn(safeStart + 1, 23)
            val currentSettings = repository.settings.first() ?: ScheduleSettings()
            val updated = currentSettings.copy(
                schoolName = schoolName.trim().ifBlank { "My Schedule" },
                termName = termName.trim().ifBlank { "Current Term" },
                startHour = safeStart,
                endHour = safeEnd,
                showWeekends = showWeekends,
                referenceScreenshotUri = screenshotUri
            )
            repository.saveSettings(updated)
        }
    }

    fun clearAllData() {
        enqueueEdit {
            repository.clearAllClassBlocks()
            repository.saveSettings(ScheduleSettings())
            LocalStorageHelper.clearAll(getApplication())
            undoStack.clear()
            redoStack.clear()
            refreshUndoFlags()
        }
    }

    fun seedDemoSchedule() {
        enqueueEdit {
            undoStack.clear()
            redoStack.clear()
            refreshUndoFlags()
            seedDemoScheduleInternal()
        }
    }

    private suspend fun seedDemoScheduleInternal() {
        repository.clearAllClassBlocks()

        val sampleClasses = listOf(
            ClassBlock(
                name = "Linear Algebra",
                professor = "Prof. R. Mendoza",
                room = "North Hall 204",
                dayOfWeek = 1,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#2E5A4F"
            ),
            ClassBlock(
                name = "Operating Systems",
                professor = "Dr. K. Watanabe",
                room = "Systems Lab 2",
                dayOfWeek = 1,
                startHour = 13,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#38598B"
            ),
            ClassBlock(
                name = "Organic Chemistry II",
                professor = "Dr. M. Navarro",
                room = "Science Wing 108",
                dayOfWeek = 2,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#B85D3B"
            ),
            ClassBlock(
                name = "Technical Writing",
                professor = "Prof. E. Rivera",
                room = "Humanities 312",
                dayOfWeek = 2,
                startHour = 14,
                startMinute = 30,
                durationMinutes = 75,
                colorHex = "#9A6B2F"
            ),
            ClassBlock(
                name = "Linear Algebra",
                professor = "Prof. R. Mendoza",
                room = "North Hall 204",
                dayOfWeek = 3,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#2E5A4F"
            ),
            ClassBlock(
                name = "Digital Logic Lab",
                professor = "Engr. L. Santos",
                room = "ECE Lab B",
                dayOfWeek = 3,
                startHour = 14,
                startMinute = 0,
                durationMinutes = 120,
                colorHex = "#744253"
            ),
            ClassBlock(
                name = "Organic Chemistry II",
                professor = "Dr. M. Navarro",
                room = "Science Wing 108",
                dayOfWeek = 4,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#B85D3B"
            ),
            ClassBlock(
                name = "Operating Systems",
                professor = "Dr. K. Watanabe",
                room = "Systems Lab 2",
                dayOfWeek = 4,
                startHour = 13,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#38598B"
            ),
            ClassBlock(
                name = "Design Studio Seminar",
                professor = "Prof. C. Bautista",
                room = "Studio 101",
                dayOfWeek = 5,
                startHour = 10,
                startMinute = 0,
                durationMinutes = 120,
                colorHex = "#4A6B5D"
            ),
            ClassBlock(
                name = "Compiler Construction",
                professor = "Dr. D. Ocampo",
                room = "CS Room 405",
                dayOfWeek = 0,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 90,
                colorHex = "#5D5179"
            ),
            ClassBlock(
                name = "Engineering Ethics",
                professor = "Prof. A. Fernandez",
                room = "Room 115",
                dayOfWeek = 0,
                startHour = 15,
                startMinute = 0,
                durationMinutes = 60,
                colorHex = "#2F6F7E"
            )
        )

        for (c in sampleClasses) {
            repository.insertClassBlock(c)
        }

        repository.saveSettings(
            ScheduleSettings(
                schoolName = "College of Engineering",
                termName = "First Semester 2026–2027",
                startHour = 8,
                endHour = 18,
                showWeekends = false,
                referenceScreenshotUri = null
            )
        )
    }
}

internal suspend fun ScheduleRepository.initializeSchedule(context: Context, seedDemo: suspend () -> Unit) {
    if (settings.first() != null) return

    val existingClasses = allClassBlocks.first()
    val savedClasses = LocalStorageHelper.getClasses(context)
    val savedSettings = LocalStorageHelper.getSettings(context)
    if (existingClasses.isEmpty() && savedClasses == null && savedSettings == null) {
        seedDemo()
        return
    }

    if (existingClasses.isEmpty()) {
        savedClasses.orEmpty().forEach { insertClassBlock(it) }
    }
    saveSettings(savedSettings ?: ScheduleSettings())
}
