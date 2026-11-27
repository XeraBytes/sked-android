package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ClassBlock
import com.example.data.model.getOverlappingClasses
import com.example.data.prefs.AppPreferences
import com.example.ui.components.ClayPrimaryButton
import com.example.ui.components.PaperOutlinedButton
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.components.UpdateDownloadDialog
import com.example.ui.dialogs.ClassEditDialog
import com.example.ui.dialogs.ClassQuickActionsDialog
import com.example.ui.dialogs.EguroLoginDialog
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.screens.ClassPoolView
import com.example.ui.screens.MainScheduleContainer
import com.example.ui.screens.ScreenshotReferenceView
import com.example.ui.theme.ScheduleMakerTheme
import com.example.ui.theme.ScheduleTheme
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.updater.AppUpdateInstaller
import com.example.util.ExportHelper
import com.example.util.ScheduleLayoutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themeMode by rememberSaveable {
                mutableStateOf(AppPreferences.getThemeMode(this))
            }

            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            ScheduleMakerTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScheduleApp(
                        themeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            AppPreferences.setThemeMode(this, newMode)
                        }
                    )
                }
            }
        }
    }
}

suspend fun saveImageToInternalStorage(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            // Remove older copies so every replacement creates a distinct URI & invalidates caches cleanly
            context.filesDir.listFiles { file ->
                file.name.startsWith("reference_schedule_") && file.name.endsWith(".jpg")
            }?.forEach { it.delete() }

            val targetFile = File(context.filesDir, "reference_schedule_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            Uri.fromFile(targetFile).toString()
        } catch (_: Exception) {
            null
        }
    }

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.substringAfterLast('/')
    }
    return result ?: "Selected file"
}

data class ConflictData(
    val block: ClassBlock,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val overlapping: List<ClassBlock>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp(
    themeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val planner = ScheduleTheme.colors
    val classes by viewModel.classBlocks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val updaterState by viewModel.updaterState.collectAsStateWithLifecycle()
    val eguroState by viewModel.eguroState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var gridStyle by rememberSaveable { mutableStateOf("classic") }
    var viewMode by rememberSaveable { mutableStateOf("timeline") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var openImportSettings by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showEguroDialog by rememberSaveable { mutableStateOf(false) }
    var editingClassId by rememberSaveable { mutableStateOf<Int?>(null) }
    var quickActionClassId by rememberSaveable { mutableStateOf<Int?>(null) }
    var activePlacementBlockId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    // Rotation-safe conflict dialog state
    var conflictBlockId by rememberSaveable { mutableStateOf<Int?>(null) }
    var conflictDay by rememberSaveable { mutableIntStateOf(0) }
    var conflictHour by rememberSaveable { mutableIntStateOf(0) }
    var conflictMinute by rememberSaveable { mutableIntStateOf(0) }

    val activePlacementBlock = remember(classes, activePlacementBlockId) {
        activePlacementBlockId?.let { id -> classes.find { it.id == id } }
    }
    val selectedClassForEdit = remember(classes, editingClassId) {
        editingClassId?.let { id -> classes.find { it.id == id } }
    }
    val quickActionClass = remember(classes, quickActionClassId) {
        quickActionClassId?.let { id -> classes.find { it.id == id } }
    }
    val conflictDialogData = remember(classes, conflictBlockId, conflictDay, conflictHour, conflictMinute) {
        val block = conflictBlockId?.let { id -> classes.find { it.id == id } } ?: return@remember null
        val candidate = block.copy(dayOfWeek = conflictDay, startHour = conflictHour, startMinute = conflictMinute)
        val overlapping = candidate.getOverlappingClasses(classes)
        if (overlapping.isEmpty()) null
        else ConflictData(block, conflictDay, conflictHour, conflictMinute, overlapping)
    }

    LaunchedEffect(Unit) {
        viewModel.runStartupUpdateCheckIfNeeded(context)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val internalPath = saveImageToInternalStorage(context, uri)
                if (internalPath != null) {
                    viewModel.saveSettings(
                        schoolName = settings.schoolName,
                        termName = settings.termName,
                        startHour = settings.startHour,
                        endHour = settings.endHour,
                        showWeekends = settings.showWeekends,
                        screenshotUri = internalPath
                    )
                    Toast.makeText(context, "Reference photo saved locally", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not save reference photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val performUnscheduleWithUndo: (ClassBlock) -> Unit = { block ->
        viewModel.unscheduleClassBlock(block.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Moved \"${block.name}\" to pool",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    Scaffold(
        containerColor = planner.paperBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = settings.schoolName,
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            color = planner.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = settings.termName,
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.undo()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Action undone", duration = SnackbarDuration.Short)
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) planner.sagePrimary
                            else planner.inkPrimary.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.redo()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Action redone", duration = SnackbarDuration.Short)
                            }
                        },
                        enabled = canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) planner.sagePrimary
                            else planner.inkPrimary.copy(alpha = 0.35f)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.testTag("export_dropdown_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export and view options")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save PNG Image") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        ExportHelper.downloadPngToDevice(context, classes, settings)
                                            .onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                            .onFailure { err -> Toast.makeText(context, "Export failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show() }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                modifier = Modifier.testTag("download_png_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Save PDF Document") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        ExportHelper.downloadPdfToDevice(context, classes, settings)
                                            .onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                            .onFailure { err -> Toast.makeText(context, "Export failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show() }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                                modifier = Modifier.testTag("download_pdf_button")
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Share Schedule Image") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        ExportHelper.exportAndSharePhoto(context, classes, settings)
                                            .onSuccess { intent -> context.startActivity(intent) }
                                            .onFailure { err -> Toast.makeText(context, "Share failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show() }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                modifier = Modifier.testTag("export_photo_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Share Schedule PDF") },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        ExportHelper.exportAndSharePdf(context, classes, settings)
                                            .onSuccess { intent -> context.startActivity(intent) }
                                            .onFailure { err -> Toast.makeText(context, "Share failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show() }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                                modifier = Modifier.testTag("export_pdf_button")
                            )
                            if (activeTab == 0 && viewMode == "grid") {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (gridStyle == "classic") "Card Style: Neutral Paper" else "Card Style: Color Tinted") },
                                    onClick = {
                                        gridStyle = if (gridStyle == "classic") "minimal" else "classic"
                                        showExportMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (gridStyle == "classic") Icons.Filled.GridView else Icons.Outlined.GridView,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.testTag("grid_style_toggle_button")
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Theme: ${themeMode.replaceFirstChar { it.uppercase() }}") },
                                onClick = {
                                    val nextMode = when (themeMode) {
                                        "light" -> "dark"
                                        "dark" -> "system"
                                        else -> "light"
                                    }
                                    onThemeModeChange(nextMode)
                                },
                                leadingIcon = {
                                    val icon = when (themeMode) {
                                        "light" -> Icons.Filled.LightMode
                                        "dark" -> Icons.Filled.DarkMode
                                        else -> Icons.Filled.Contrast
                                    }
                                    Icon(icon, contentDescription = null)
                                },
                                modifier = Modifier.testTag("quick_theme_toggle_button")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (updaterState.availableUpdate != null) {
                                            "Install Update (v${updaterState.availableUpdate!!.versionName})"
                                        } else {
                                            "Check for Updates"
                                        }
                                    )
                                },
                                onClick = {
                                    showExportMenu = false
                                    if (updaterState.availableUpdate != null) {
                                        viewModel.openUpdateDialog()
                                    } else {
                                        viewModel.triggerUpdateCheck(context, manual = true) { msg, isLong ->
                                            Toast.makeText(
                                                context,
                                                msg,
                                                if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                                modifier = Modifier.testTag("menu_check_updates_button")
                            )
                        }
                    }

                    IconButton(
                        onClick = { openImportSettings = false; showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (updaterState.availableUpdate != null) {
                                    Badge(containerColor = planner.terracotta)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = planner.paperSheet
                )
            )
        },
        bottomBar = {
            val poolCount = classes.count { !it.isScheduled }
            Column(Modifier.background(planner.paperSheet).navigationBarsPadding()) {
                HorizontalDivider(color = planner.ruleStrong)
                Row(
                    Modifier.fillMaxWidth().selectableGroup().testTag("bottom_nav_bar"),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("Schedule" to "nav_tab_grid", "Import" to "nav_tab_screenshot", "Pool" to "nav_tab_pool")
                    tabs.forEachIndexed { index, (label, tag) ->
                        val selected = activeTab == index
                        Column(
                            Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { activeTab = index })
                                .testTag(tag).padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.width(32.dp).height(3.dp)
                                .background(if (selected) planner.sagePrimary else Color.Transparent))
                            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (index == 2 && poolCount > 0) "$label ($poolCount)" else label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                                    color = if (selected) planner.inkPrimary else planner.inkSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 0 && activePlacementBlock == null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = planner.sagePrimary,
                    contentColor = planner.sageOnPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_class_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add class")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> MainScheduleContainer(
                        classes = classes,
                        settings = settings,
                        activePlacementBlock = activePlacementBlock,
                        gridStyle = gridStyle,
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        onClassClick = { editingClassId = it.id },
                        onGridClassClick = { quickActionClassId = it.id },
                        onStartPlacement = { activePlacementBlockId = it.id },
                        onCancelPlacement = { activePlacementBlockId = null },
                        onPlaceBlock = { block, day, hour ->
                            val minute = block.startMinute
                            val candidate = block.copy(dayOfWeek = day, startHour = hour, startMinute = minute)
                            val overlapping = candidate.getOverlappingClasses(classes)
                            if (overlapping.isNotEmpty()) {
                                conflictBlockId = block.id
                                conflictDay = day
                                conflictHour = hour
                                conflictMinute = minute
                            } else {
                                viewModel.updateClassSchedule(block.id, day, hour, minute)
                                activePlacementBlockId = null
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Placed \"${block.name}\" on ${ScheduleLayoutHelper.dayFullName(day)} at ${ScheduleLayoutHelper.formatTime12h(hour, minute)}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undo()
                                    }
                                }
                            }
                        },
                        onUnscheduleBlock = performUnscheduleWithUndo,
                        onAddClassClick = { showAddDialog = true },
                        onChooseFromPoolClick = { activeTab = 2 }
                    )

                    1 -> ScreenshotReferenceView(
                        settings = settings,
                        viewModel = viewModel,
                        onImportClick = { imagePickerLauncher.launch("image/*") },
                        onClearClick = {
                            viewModel.saveSettings(
                                schoolName = settings.schoolName,
                                termName = settings.termName,
                                startHour = settings.startHour,
                                endHour = settings.endHour,
                                showWeekends = settings.showWeekends,
                                screenshotUri = null
                            )
                            Toast.makeText(context, "Reference image cleared", Toast.LENGTH_SHORT).show()
                        },
                        onOpenSettingsClick = { openImportSettings = true; showSettingsDialog = true },
                        onAiImportCompleted = { activeTab = 0 },
                        resolveFileName = { uri -> getFileName(context, uri) },
                        onClassesSaved = { savedCount ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = "Saved $savedCount ${if (savedCount == 1) "class" else "classes"} to your schedule",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        onConnectEguroClick = {
                            viewModel.clearEguroError()
                            showEguroDialog = true
                        }
                    )

                    2 -> ClassPoolView(
                        classes = classes,
                        onClassClick = { editingClassId = it.id },
                        onScheduleClick = { block ->
                            activePlacementBlockId = block.id
                            activeTab = 0
                        },
                        onDeleteClick = { block ->
                            viewModel.deleteClassBlockById(block.id)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val res = snackbarHostState.showSnackbar(
                                    message = "Deleted \"${block.name}\"",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.undo()
                                }
                            }
                        },
                        onAutoArrangeClick = {
                            viewModel.autoArrangePool { placedCount, remainingCount ->
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val msg = when {
                                        placedCount == 0 -> "No conflict-free slots available within grid hours"
                                        remainingCount == 0 -> "Auto-arranged $placedCount ${if (placedCount == 1) "course" else "courses"}"
                                        else -> "Auto-arranged $placedCount courses ($remainingCount left in pool)"
                                    }
                                    val res = snackbarHostState.showSnackbar(
                                        message = msg,
                                        actionLabel = if (placedCount > 0) "Undo" else null,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed && placedCount > 0) {
                                        viewModel.undo()
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Weekly Sheet Placement Instruction Banner (carries course name, duration, and reachable Cancel)
            if (activePlacementBlock != null && (activeTab != 0 || viewMode == "grid")) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter)
                        .testTag("placement_banner"),
                    colors = CardDefaults.cardColors(containerColor = planner.claySurface),
                    border = BorderStroke(1.5.dp, planner.sagePrimary),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.TouchApp,
                                contentDescription = null,
                                tint = planner.sagePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Placing: ${activePlacementBlock.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = planner.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ScheduleLayoutHelper.formatDuration(activePlacementBlock.durationMinutes)} · Tap any slot on the weekly sheet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = planner.inkSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        PaperOutlinedButton(
                            onClick = { activePlacementBlockId = null },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("cancel_weekly_placement_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (quickActionClass != null) {
        val overlapping = quickActionClass.getOverlappingClasses(classes)
        ClassQuickActionsDialog(
            classBlock = quickActionClass,
            overlappingClasses = overlapping,
            onEdit = {
                quickActionClassId = null
                editingClassId = quickActionClass.id
            },
            onMove = {
                quickActionClassId = null
                activePlacementBlockId = quickActionClass.id
            },
            onMoveToPool = {
                quickActionClassId = null
                performUnscheduleWithUndo(quickActionClass)
            },
            onDismiss = { quickActionClassId = null }
        )
    }

    if (showAddDialog) {
        ClassEditDialog(
            classBlock = null,
            settings = settings,
            onDismiss = { showAddDialog = false },
            onSave = { name, prof, room, day, hour, min, duration, color ->
                viewModel.addClassBlock(name, prof, room, day, hour, min, duration, color)
                showAddDialog = false
                Toast.makeText(context, "Course added", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (selectedClassForEdit != null) {
        val editing = selectedClassForEdit
        ClassEditDialog(
            classBlock = editing,
            settings = settings,
            onDismiss = { editingClassId = null },
            onSave = { name, prof, room, day, hour, min, duration, color ->
                val updated = editing.copy(
                    name = name,
                    professor = prof,
                    room = room,
                    dayOfWeek = day,
                    startHour = hour,
                    startMinute = min,
                    durationMinutes = duration,
                    colorHex = color
                )
                viewModel.updateClassBlock(updated)
                editingClassId = null
                Toast.makeText(context, "Course updated", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteClassBlockById(editing.id)
                editingClassId = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val res = snackbarHostState.showSnackbar(
                        message = "Deleted \"${editing.name}\"",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.undo()
                    }
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            openImport = openImportSettings,
            context = context,
            settings = settings,
            themeMode = themeMode,
            isCheckingUpdates = updaterState.isCheckingUpdates,
            updateStatusSummary = updaterState.updateStatusSummary,
            onThemeModeChange = onThemeModeChange,
            onCheckForUpdates = {
                viewModel.triggerUpdateCheck(context, manual = true) { msg, isLong ->
                    Toast.makeText(
                        context,
                        msg,
                        if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showSettingsDialog = false },
            onSave = { name, term, start, end, showWE ->
                viewModel.saveSettings(name, term, start, end, showWE, settings.referenceScreenshotUri)
                showSettingsDialog = false
                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            },
            onResetDemo = {
                viewModel.seedDemoSchedule()
                showSettingsDialog = false
                Toast.makeText(context, "Sample schedule loaded", Toast.LENGTH_SHORT).show()
            },
            onClearAll = {
                viewModel.clearAllData()
                showSettingsDialog = false
                Toast.makeText(context, "All classes cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (conflictDialogData != null) {
        val data = conflictDialogData
        AlertDialog(
            onDismissRequest = { conflictBlockId = null },
            containerColor = planner.paperSheet,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Time Conflict", color = planner.inkPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Proposed placement:",
                        style = MaterialTheme.typography.labelMedium,
                        color = planner.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = planner.sageContainer.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, planner.sagePrimary.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val c = try {
                                Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(data.block.colorHex)))
                            } catch (_: Exception) {
                                planner.sagePrimary
                            }
                            Box(modifier = Modifier.size(12.dp).background(c, CircleShape))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(data.block.name, style = MaterialTheme.typography.labelLarge, color = planner.inkPrimary)
                                Text(
                                    text = "${ScheduleLayoutHelper.dayFullName(data.day)} · ${
                                        ScheduleLayoutHelper.formatTimeRange(
                                            data.hour,
                                            data.minute,
                                            data.block.durationMinutes
                                        )
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = planner.inkSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Overlaps with:",
                        style = MaterialTheme.typography.labelMedium,
                        color = planner.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    data.overlapping.forEach { other ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                val c = try {
                                    Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(other.colorHex)))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                Box(modifier = Modifier.size(12.dp).background(c, CircleShape))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        other.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "${ScheduleLayoutHelper.dayFullName(other.dayOfWeek)} · ${
                                            ScheduleLayoutHelper.formatTimeRange(
                                                other.startHour,
                                                other.startMinute,
                                                other.durationMinutes
                                            )
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Place alongside anyway?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = planner.inkPrimary
                    )
                }
            },
            confirmButton = {
                ClayPrimaryButton(
                    onClick = {
                        viewModel.updateClassSchedule(data.block.id, data.day, data.hour, data.minute)
                        conflictBlockId = null
                        activePlacementBlockId = null
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val res = snackbarHostState.showSnackbar(
                                message = "Placed \"${data.block.name}\" on ${ScheduleLayoutHelper.dayFullName(data.day)} at ${ScheduleLayoutHelper.formatTime12h(data.hour, data.minute)} (with conflict)",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                viewModel.undo()
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("confirm_conflict_placement_button")
                ) {
                    Text("Place Anyway", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                PaperOutlinedButton(
                    onClick = { conflictBlockId = null },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("dismiss_conflict_placement_button")
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            modifier = Modifier.testTag("schedule_conflict_dialog")
        )
    }

    if (updaterState.showUpdateDialog && updaterState.availableUpdate != null) {
        val update = updaterState.availableUpdate!!
        val lifecycleOwner = LocalLifecycleOwner.current
        var canInstall by remember(update.versionName) {
            mutableStateOf(AppUpdateInstaller.canInstallPackages(context))
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canInstall = AppUpdateInstaller.canInstallPackages(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        UpdateAvailableDialog(
            release = update.release,
            latestVersion = update.versionName,
            needsInstallPermission = !canInstall,
            onInstallClick = {
                viewModel.startUpdateDownload(context, update.release) { errMsg ->
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                }
            },
            onGrantPermissionClick = { AppUpdateInstaller.openInstallPermissionSettings(context) },
            onSkipVersion = { viewModel.skipUpdateVersion(context) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (updaterState.downloadingRelease != null && updaterState.downloadProgress != null) {
        val prog = updaterState.downloadProgress!!
        UpdateDownloadDialog(
            versionName = updaterState.availableUpdate?.versionName ?: updaterState.downloadingRelease!!.tagName,
            downloadedBytes = prog.downloadedBytes,
            totalBytes = prog.totalBytes,
            onCancel = { viewModel.cancelUpdateDownload() }
        )
    }

    if (showEguroDialog) {
        EguroLoginDialog(
            initialUsername = eguroState.savedUsername,
            initialSchedulePath = eguroState.customSchedulePath,
            isSyncing = eguroState.isSyncing,
            errorMessage = eguroState.syncError,
            onSync = { user, pass, path ->
                viewModel.syncFromEguro(
                    context = context,
                    username = user,
                    password = pass,
                    customSchedulePath = path,
                    onSuccess = { result ->
                        showEguroDialog = false
                        activeTab = 1
                        val subjects = result.classes.distinctBy { it.name.trim().lowercase() }.size
                        val meetings = result.classes.count { it.isScheduled }
                        val summary = if (subjects == 0) {
                            "Signed in to e-GURO. No subjects found."
                        } else {
                            "$subjects ${if (subjects == 1) "subject" else "subjects"} · " +
                                "$meetings scheduled ${if (meetings == 1) "meeting" else "meetings"}"
                        }
                        Toast.makeText(
                            context,
                            summary,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            },
            onDismiss = {
                viewModel.clearEguroError()
                showEguroDialog = false
            }
        )
    }
}
