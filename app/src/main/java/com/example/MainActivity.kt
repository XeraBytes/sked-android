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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.ClassBlock
import com.example.data.model.getOverlappingClasses
import com.example.data.model.ScheduleSettings
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.util.ExportHelper
import com.example.util.GeminiHelper
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        setContent {
            var themeMode by rememberSaveable {
                mutableStateOf(sharedPrefs.getString("theme_mode", "system") ?: "system")
            }
            
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScheduleApp(
                        themeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            sharedPrefs.edit().putString("theme_mode", newMode).apply()
                        }
                    )
                }
            }
        }
    }
}

// Save picked image internally to ensure permanent offline access
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "reference_schedule.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Helper to resolve display names from Content Provider URIs
fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Unknown file"
}

data class ConflictData(
    val block: ClassBlock,
    val day: Int,
    val hour: Int,
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
    val classes by viewModel.classBlocks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draggedBlockId by viewModel.draggedBlockId.collectAsStateWithLifecycle()

    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(0) } // 0 = Schedule Grid, 1 = Reference Screenshot, 2 = Unscheduled Pool
    var gridStyle by rememberSaveable { mutableStateOf("classic") } // "classic" or "minimal"
    var viewMode by rememberSaveable { mutableStateOf("timeline") } // "timeline" or "grid"
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedClassForEdit by remember { mutableStateOf<ClassBlock?>(null) }
    var activePlacementBlock by remember { mutableStateOf<ClassBlock?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }
    var conflictDialogData by remember { mutableStateOf<ConflictData?>(null) }

    // Result Launcher for Reference Photo picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
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
                Toast.makeText(context, "Reference screenshot imported!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save screenshot.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = settings.schoolName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = settings.termName,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Quick Action: Add
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_class_quick_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Class Block")
                    }
                    // Quick Action: Undo
                    IconButton(
                        onClick = {
                            viewModel.undo()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Action undone")
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Undo,
                            contentDescription = "Undo last action",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    // Quick Action: Redo
                    IconButton(
                        onClick = {
                            viewModel.redo()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Action redone")
                            }
                        },
                        enabled = canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Redo,
                            contentDescription = "Redo last action",
                            tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    // Quick Action: Toggle Layout Style (only shown in Calendar Grid view mode)
                    if (activeTab == 0 && viewMode == "grid") {
                        IconButton(
                            onClick = { gridStyle = if (gridStyle == "classic") "minimal" else "classic" },
                            modifier = Modifier.testTag("grid_style_toggle_button")
                        ) {
                            Icon(
                                if (gridStyle == "classic") Icons.Filled.GridView else Icons.Outlined.GridView,
                                contentDescription = "Toggle Grid Style",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Consolidated Export & Share Menu
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.testTag("export_dropdown_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Export and Download Options")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as PNG Image") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.downloadPngToDevice(context, classes, settings)
                                },
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = "Save as PNG Image") },
                                modifier = Modifier.testTag("download_png_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Save as PDF Document") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.downloadPdfToDevice(context, classes, settings)
                                },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Save as PDF Document") },
                                modifier = Modifier.testTag("download_pdf_button")
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Share PNG Image") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.exportAndSharePhoto(context, classes, settings)
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = "Share PNG Image") },
                                modifier = Modifier.testTag("export_photo_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF Document") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.exportAndSharePdf(context, classes, settings)
                                },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share PDF Document") },
                                modifier = Modifier.testTag("export_pdf_button")
                            )
                        }
                    }
                    // Quick Action: Quick Theme Toggle
                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                "light" -> "dark"
                                "dark" -> "system"
                                else -> "light"
                            }
                            onThemeModeChange(nextMode)
                        },
                        modifier = Modifier.testTag("quick_theme_toggle_button")
                    ) {
                        val icon = when (themeMode) {
                            "light" -> Icons.Filled.LightMode
                            "dark" -> Icons.Filled.DarkMode
                            else -> Icons.Filled.Contrast
                        }
                        Icon(
                            icon,
                            contentDescription = "Quick Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Quick Action: Settings
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Schedule Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Weekly Grid") },
                    label = { Text("Grid") },
                    modifier = Modifier.testTag("nav_tab_grid")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.Camera, contentDescription = "Reference Screen") },
                    label = { Text("Screenshot") },
                    modifier = Modifier.testTag("nav_tab_screenshot")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                val unscheduledCount = classes.filter { !it.isScheduled }.size
                                if (unscheduledCount > 0) {
                                    Badge { Text(unscheduledCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Class Pool")
                        }
                    },
                    label = { Text("Pool") },
                    modifier = Modifier.testTag("nav_tab_pool")
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0 && activePlacementBlock == null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("add_class_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Class Block")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen Contents based on Tab selection
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        // 1. SCHEDULE MAIN CONTAINER
                        MainScheduleContainer(
                            classes = classes,
                            settings = settings,
                            activePlacementBlock = activePlacementBlock,
                            gridStyle = gridStyle,
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it },
                            onClassClick = { selectedClassForEdit = it },
                            onStartPlacement = { activePlacementBlock = it },
                            onCancelPlacement = { activePlacementBlock = null },
                            onPlaceBlock = { block, day, hour ->
                                val targetBlock = block.copy(dayOfWeek = day, startHour = hour, startMinute = 0)
                                val overlapping = targetBlock.getOverlappingClasses(classes)
                                if (overlapping.isNotEmpty()) {
                                    conflictDialogData = ConflictData(block, day, hour, overlapping)
                                } else {
                                    viewModel.updateClassSchedule(block.id, day, hour, 0)
                                    activePlacementBlock = null
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Class scheduled successfully!",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undo()
                                        }
                                    }
                                }
                            },
                            onUnscheduleBlock = { block ->
                                viewModel.unscheduleClassBlock(block.id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Moved to Unscheduled Pool",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undo()
                                    }
                                }
                            },
                            onAddClassClick = { showAddDialog = true }
                        )
                    }
                    1 -> {
                        // 2. REFERENCE SCREENSHOT REFERENCE / AI ORGANIZER
                        ScreenshotReferenceView(
                            classes = classes,
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
                                Toast.makeText(context, "Screenshot reference cleared", Toast.LENGTH_SHORT).show()
                            },
                            onAiImportCompleted = {
                                activeTab = 0
                            }
                        )
                    }
                    2 -> {
                        // 3. CLASS POOL VIEW
                        ClassPoolView(
                            classes = classes,
                            onClassClick = { selectedClassForEdit = it },
                            onScheduleClick = {
                                activePlacementBlock = it
                                activeTab = 0 // Go back to calendar grid to place it!
                            },
                            onDeleteClick = { viewModel.deleteClassBlockById(it.id) }
                        )
                    }
                }
            }

            // Placement Instruction Banner
            if (activePlacementBlock != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .testTag("placement_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Scheduling: ${activePlacementBlock?.name}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Tap any grid hour cell below to schedule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        IconButton(onClick = { activePlacementBlock = null }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel Placement",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add Class Block
    if (showAddDialog) {
        ClassEditDialog(
            classBlock = null,
            settings = settings,
            onDismiss = { showAddDialog = false },
            onSave = { name, prof, room, day, hour, min, duration, color ->
                viewModel.addClassBlock(name, prof, room, day, hour, min, duration, color)
                showAddDialog = false
                Toast.makeText(context, "Class block added!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Edit Class Block
    if (selectedClassForEdit != null) {
        ClassEditDialog(
            classBlock = selectedClassForEdit,
            settings = settings,
            onDismiss = { selectedClassForEdit = null },
            onSave = { name, prof, room, day, hour, min, duration, color ->
                val updated = selectedClassForEdit!!.copy(
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
                selectedClassForEdit = null
                Toast.makeText(context, "Class block updated!", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteClassBlockById(selectedClassForEdit!!.id)
                selectedClassForEdit = null
                Toast.makeText(context, "Class deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Settings
    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onDismiss = { showSettingsDialog = false },
            onSave = { name, term, start, end, showWE ->
                viewModel.saveSettings(name, term, start, end, showWE, settings.referenceScreenshotUri)
                showSettingsDialog = false
                Toast.makeText(context, "Settings updated!", Toast.LENGTH_SHORT).show()
            },
            onResetDemo = {
                viewModel.seedDemoSchedule()
                showSettingsDialog = false
                Toast.makeText(context, "Schedule reset to demo values", Toast.LENGTH_SHORT).show()
            },
            onClearAll = {
                viewModel.clearAllData()
                showSettingsDialog = false
                Toast.makeText(context, "All data deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Schedule Conflict Warning
    if (conflictDialogData != null) {
        val data = conflictDialogData!!
        AlertDialog(
            onDismissRequest = { conflictDialogData = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Schedule Conflict")
                }
            },
            text = {
                Column {
                    Text(
                        text = "You are attempting to place \"${data.block.name}\" in a slot that has overlapping classes:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    data.overlapping.forEach { other ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            Color(android.graphics.Color.parseColor(other.colorHex)),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = other.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    if (other.professor.isNotEmpty() || other.room.isNotEmpty()) {
                                        Text(
                                            text = listOfNotNull(
                                                other.professor.takeIf { it.isNotEmpty() }?.let { "Prof: $it" },
                                                other.room.takeIf { it.isNotEmpty() }?.let { "Room: $it" }
                                            ).joinToString(" | "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Would you like to schedule it anyway?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = conflictDialogData!!
                        viewModel.updateClassSchedule(d.block.id, d.day, d.hour, 0)
                        conflictDialogData = null
                        activePlacementBlock = null
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = "Class scheduled despite conflict!",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undo()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_conflict_placement_button")
                ) {
                    Text("Place Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { conflictDialogData = null },
                    modifier = Modifier.testTag("dismiss_conflict_placement_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("schedule_conflict_dialog")
        )
    }
}

// ------------------------------------
// SUB-VIEWS (Timeline & Container)
// ------------------------------------

@Composable
fun MainScheduleContainer(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    activePlacementBlock: ClassBlock?,
    gridStyle: String,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    onClassClick: (ClassBlock) -> Unit,
    onStartPlacement: (ClassBlock) -> Unit,
    onCancelPlacement: () -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onUnscheduleBlock: (ClassBlock) -> Unit,
    onAddClassClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Styled View Selector (Pill tabs) at the top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf("timeline" to "Daily Timeline", "grid" to "Calendar Grid")
                    for ((mode, label) in modes) {
                        val isSelected = viewMode == mode
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable { onViewModeChange(mode) }
                                .testTag("view_mode_$mode"),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(100.dp),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) else null,
                            shadowElevation = if (isSelected) 3.dp else 0.dp
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Display selected layout
        if (viewMode == "timeline") {
            DailyTimelineView(
                classes = classes,
                settings = settings,
                activePlacementBlock = activePlacementBlock,
                onClassClick = onClassClick,
                onStartPlacement = onStartPlacement,
                onPlaceBlock = onPlaceBlock,
                onUnscheduleBlock = onUnscheduleBlock,
                onAddClassClick = onAddClassClick
            )
        } else {
            WeeklyGridView(
                classes = classes,
                settings = settings,
                activePlacementBlock = activePlacementBlock,
                gridStyle = gridStyle,
                onClassClick = onClassClick,
                onStartPlacement = onStartPlacement,
                onCancelPlacement = onCancelPlacement,
                onPlaceBlock = onPlaceBlock,
                onUnscheduleBlock = onUnscheduleBlock
            )
        }
    }
}

@Composable
fun DailyTimelineView(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    activePlacementBlock: ClassBlock?,
    onClassClick: (ClassBlock) -> Unit,
    onStartPlacement: (ClassBlock) -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onUnscheduleBlock: (ClassBlock) -> Unit,
    onAddClassClick: () -> Unit
) {
    // Current selected day of the week (1 = Mon, 2 = Tue, ..., 7 = Sun)
    var selectedDay by rememberSaveable {
        val today = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> 1
        }
        val showWeekends = settings.showWeekends
        val initial = if (!showWeekends && today > 5) 1 else today
        mutableStateOf(initial)
    }

    val scheduledClasses = classes.filter { it.isScheduled }
    val daysRange = if (settings.showWeekends) 1..7 else 1..5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("daily_timeline_view")
    ) {
        // Horizontal Day Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (day in daysRange) {
                val dayName = when (day) {
                    1 -> "Mon"
                    2 -> "Tue"
                    3 -> "Wed"
                    4 -> "Thu"
                    5 -> "Fri"
                    6 -> "Sat"
                    7 -> "Sun"
                    else -> ""
                }
                val isSelected = selectedDay == day
                val dayClassesCount = scheduledClasses.count { it.dayOfWeek == day }

                DaySelectorPill(
                    dayName = dayName,
                    isSelected = isSelected,
                    classesCount = dayClassesCount,
                    onClick = { selectedDay = day },
                    modifier = Modifier.testTag("day_chip_$day")
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Scheduled Classes for selectedDay
        val dayClassesSorted = scheduledClasses
            .filter { it.dayOfWeek == selectedDay }
            .sortedBy { it.startHour * 60 + it.startMinute }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (dayClassesSorted.isEmpty()) {
                // Friendly empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EventNote,
                        contentDescription = "No classes",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Classes Today!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enjoy your free time, or add a class to fill this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onAddClassClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_class_empty_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Course")
                    }
                }
            } else {
                // Timeline List of Cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dayClassesSorted) { cls ->
                        val overlapping = cls.getOverlappingClasses(scheduledClasses)
                        TimelineClassCard(
                            classBlock = cls,
                            overlappingClasses = overlapping,
                            onClick = { onClassClick(cls) },
                            onUnschedule = { onUnscheduleBlock(cls) }
                        )
                    }
                }
            }

            // Quick placement overlay if there's an active placement block from pool
            if (activePlacementBlock != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .testTag("timeline_placement_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Schedule \"${activePlacementBlock.name}\" on " + when(selectedDay) {
                                1 -> "Monday"
                                2 -> "Tuesday"
                                3 -> "Wednesday"
                                4 -> "Thursday"
                                5 -> "Friday"
                                6 -> "Saturday"
                                7 -> "Sunday"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose a start hour to place this block:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hoursRange = settings.startHour..settings.endHour
                            for (h in hoursRange) {
                                val displayH = if (h > 12) h - 12 else if (h == 0) 12 else h
                                val amPm = if (h >= 12) "PM" else "AM"
                                Button(
                                    onClick = { onPlaceBlock(activePlacementBlock, selectedDay, h) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("$displayH $amPm")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DaySelectorPill(
    dayName: String,
    isSelected: Boolean,
    classesCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (classesCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun TimelineClassCard(
    classBlock: ClassBlock,
    overlappingClasses: List<ClassBlock> = emptyList(),
    onClick: () -> Unit,
    onUnschedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(classBlock.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val displayStartHour = when {
        classBlock.startHour == 0 -> 12
        classBlock.startHour > 12 -> classBlock.startHour - 12
        else -> classBlock.startHour
    }
    val startAmPm = if (classBlock.startHour >= 12) "PM" else "AM"

    val endTotalMinutes = classBlock.startHour * 60 + classBlock.startMinute + classBlock.durationMinutes
    val endHour = (endTotalMinutes / 60) % 24
    val endMinute = endTotalMinutes % 60
    val displayEndHour = when {
        endHour == 0 -> 12
        endHour > 12 -> endHour - 12
        else -> endHour
    }
    val endAmPm = if (endHour >= 12) "PM" else "AM"

    val formattedTime = String.format(
        "%d:%02d %s - %d:%02d %s",
        displayStartHour, classBlock.startMinute, startAmPm,
        displayEndHour, endMinute, endAmPm
    )

    val calendar = java.util.Calendar.getInstance()
    val todayDayOfWeek = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> 1
        java.util.Calendar.TUESDAY -> 2
        java.util.Calendar.WEDNESDAY -> 3
        java.util.Calendar.THURSDAY -> 4
        java.util.Calendar.FRIDAY -> 5
        java.util.Calendar.SATURDAY -> 6
        java.util.Calendar.SUNDAY -> 7
        else -> 1
    }
    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute

    val startTotalMinutes = classBlock.startHour * 60 + classBlock.startMinute
    val isToday = classBlock.dayOfWeek == todayDayOfWeek
    val isNowInSession = isToday && (currentTotalMinutes in startTotalMinutes until endTotalMinutes)

    val isOverlapping = overlappingClasses.isNotEmpty()

    var isAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(classBlock.id, classBlock.dayOfWeek, classBlock.startHour) {
        isAppeared = false
        delay(15)
        isAppeared = true
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "entranceScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "entranceAlpha"
    )

    val animatedCardColor by animateColorAsState(
        targetValue = if (isOverlapping) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        },
        animationSpec = tween(durationMillis = 250),
        label = "cardColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isOverlapping) {
            MaterialTheme.colorScheme.error
        } else if (isNowInSession) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 250),
        label = "borderColor"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isNowInSession) 4.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale
                scaleY = entranceScale
                alpha = entranceAlpha
            }
            .clickable(onClick = onClick)
            .testTag("timeline_card_${classBlock.id}"),
        colors = CardDefaults.cardColors(
            containerColor = animatedCardColor
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isOverlapping) 2.dp else if (isNowInSession) 2.dp else 1.dp,
            color = animatedBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // High-contrast color vertical stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(if (isOverlapping) MaterialTheme.colorScheme.error else cardColor)
            )

            // Content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                if (isNowInSession) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.error, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NOW IN SESSION",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = classBlock.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOverlapping) {
                        var showTooltip by remember { mutableStateOf(false) }

                        Box {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Conflict",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showTooltip = true }
                            )

                            if (showTooltip) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    onDismissRequest = { showTooltip = false },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .widthIn(max = 260.dp)
                                            .shadow(8.dp, RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.Warning,
                                                    contentDescription = "Warning",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Schedule Conflict!",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "This time slot overlaps with:\n" + overlappingClasses.joinToString("\n") { "• ${it.name}" },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Time",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Duration chip
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${classBlock.durationMinutes}m",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (classBlock.room.isNotBlank() || classBlock.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (classBlock.room.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = "Room",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = classBlock.room,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (classBlock.professor.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Professor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = classBlock.professor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions column on right of card
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit Class",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onUnschedule) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "Move to Unscheduled Pool",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------
// ORIGINAL SUB-VIEWS
// ------------------------------------

@Composable
fun WeeklyGridView(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    activePlacementBlock: ClassBlock?,
    gridStyle: String = "classic",
    onClassClick: (ClassBlock) -> Unit,
    onStartPlacement: (ClassBlock) -> Unit,
    onCancelPlacement: () -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onUnscheduleBlock: (ClassBlock) -> Unit
) {
    var activePlacementDay by remember { mutableStateOf<Int?>(null) }
    var activePlacementHour by remember { mutableStateOf<Int?>(null) }

    val scheduledClasses = classes.filter { it.isScheduled }
    val numDays = if (settings.showWeekends) 7 else 5
    val numHours = settings.endHour - settings.startHour
    val rowHeightDp = 76.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("weekly_grid_view")
    ) {
        // Headers: Day Names Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Column space holder
            Spacer(modifier = Modifier.width(60.dp))

            for (day in 1..numDays) {
                val dayLabel = when (day) {
                    1 -> "Mon"
                    2 -> "Tue"
                    3 -> "Wed"
                    4 -> "Thu"
                    5 -> "Fri"
                    6 -> "Sat"
                    7 -> "Sun"
                    else -> ""
                }
                Text(
                    text = dayLabel.uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

        // Grid Scrollable Body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Horizontal row lines and Time Labels
            Column {
                for (h in 0..numHours) {
                    val currentHour = settings.startHour + h
                    val displayHour = when {
                        currentHour == 0 -> 12
                        currentHour > 12 -> currentHour - 12
                        else -> currentHour
                    }
                    val amPm = if (currentHour >= 12) "PM" else "AM"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Time label cell
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(rowHeightDp)
                                .padding(top = 4.dp, end = 8.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = String.format("%02d:00", displayHour),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // Grid cells across days
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (day in 1..numDays) {
                                val isPlacementActive = activePlacementBlock != null
                                val isHovered = isPlacementActive && activePlacementDay == day && activePlacementHour == currentHour
                                
                                // Animated cell properties
                                val cellBgColor by animateColorAsState(
                                    targetValue = when {
                                        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        isPlacementActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    },
                                    animationSpec = tween(durationMillis = 150),
                                    label = "cellBgColor"
                                )
                                val cellBorderColor by animateColorAsState(
                                    targetValue = when {
                                        isHovered -> MaterialTheme.colorScheme.primary
                                        isPlacementActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    },
                                    animationSpec = tween(durationMillis = 150),
                                    label = "cellBorderColor"
                                )
                                val cellBorderWidth by animateDpAsState(
                                    targetValue = when {
                                        isHovered -> 2.dp
                                        isPlacementActive -> 1.2.dp
                                        else -> 0.5.dp
                                    },
                                    animationSpec = tween(durationMillis = 150),
                                    label = "cellBorderWidth"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = when {
                                        isHovered -> 1.35f
                                        isPlacementActive -> 1f
                                        else -> 0f
                                    },
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "iconScale"
                                )
                                val iconAlpha by animateFloatAsState(
                                    targetValue = when {
                                        isHovered -> 1f
                                        isPlacementActive -> 0.6f
                                        else -> 0f
                                    },
                                    animationSpec = tween(durationMillis = 200),
                                    label = "iconAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(rowHeightDp)
                                        .background(cellBgColor)
                                        .border(
                                            BorderStroke(
                                                cellBorderWidth,
                                                cellBorderColor
                                            )
                                        )
                                        .then(
                                            if (isPlacementActive && activePlacementBlock != null) {
                                                Modifier.clickable {
                                                    onPlaceBlock(activePlacementBlock, day, currentHour)
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlacementActive) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isHovered) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                                                contentDescription = "Place Here",
                                                tint = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier
                                                    .size(if (isHovered) 28.dp else 22.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                        alpha = iconAlpha
                                                    }
                                            )
                                            if (isHovered) {
                                                Text(
                                                    text = "DROP",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 8.sp,
                                                        letterSpacing = 0.8.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cards Overlay Row containing day column overlays
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp) // Offset of the time label column
            ) {
                for (day in 1..numDays) {
                    val dayClasses = scheduledClasses.filter { it.dayOfWeek == day }
                    val isDraggingThisColumn = activePlacementBlock != null && activePlacementDay == day
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(rowHeightDp * (numHours + 1))
                            .zIndex(if (isDraggingThisColumn) 10f else 1f)
                    ) {
                        for (cls in dayClasses) {
                            val startFraction = cls.startHour + cls.startMinute / 60f
                            val endFraction = startFraction + cls.durationMinutes / 60f

                            // Verify if class lies inside active grid hours
                            if (endFraction >= settings.startHour && startFraction <= settings.endHour) {
                                val topOffsetRatio = startFraction - settings.startHour
                                val heightRatio = cls.durationMinutes / 60f

                                val offsetY = (topOffsetRatio * rowHeightDp.value).dp
                                val cardHeight = (heightRatio * rowHeightDp.value).dp

                                val overlapping = cls.getOverlappingClasses(scheduledClasses)

                                ClassGridCard(
                                    classBlock = cls,
                                    overlappingClasses = overlapping,
                                    gridStyle = gridStyle,
                                    numDays = numDays,
                                    settings = settings,
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                        .fillMaxWidth()
                                        .offset(y = offsetY)
                                        .height(cardHeight),
                                    onClick = { onClassClick(cls) },
                                    onMoveRequest = {
                                        onStartPlacement(cls)
                                        activePlacementDay = cls.dayOfWeek
                                        activePlacementHour = cls.startHour
                                    },
                                    onUnscheduleRequest = { onUnscheduleBlock(cls) },
                                    onPlaceBlock = { block, targetDay, targetHour ->
                                        onPlaceBlock(block, targetDay, targetHour)
                                        activePlacementDay = null
                                        activePlacementHour = null
                                    },
                                    onCancelPlacement = {
                                        onCancelPlacement()
                                        activePlacementDay = null
                                        activePlacementHour = null
                                    },
                                    onDragMove = { targetDay, targetHour ->
                                        activePlacementDay = targetDay
                                        activePlacementHour = targetHour
                                    },
                                    onDragEndMove = {
                                        activePlacementDay = null
                                        activePlacementHour = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassGridCard(
    classBlock: ClassBlock,
    overlappingClasses: List<ClassBlock> = emptyList(),
    gridStyle: String = "classic",
    numDays: Int = 5,
    settings: ScheduleSettings = ScheduleSettings(1, "My School Schedule", "First Semester", 8, 18, false, null),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoveRequest: () -> Unit,
    onUnscheduleRequest: () -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit = { _, _, _ -> },
    onCancelPlacement: () -> Unit = {},
    onDragMove: (Int, Int) -> Unit = { _, _ -> },
    onDragEndMove: () -> Unit = {}
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(classBlock.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 76.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var cardWidthPx by remember { mutableStateOf(0) }

    var isAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(classBlock.id, classBlock.dayOfWeek, classBlock.startHour) {
        isAppeared = false
        delay(15)
        isAppeared = true
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "entranceScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "entranceAlpha"
    )

    val animatedDragOffsetX by animateFloatAsState(
        targetValue = if (isDragging) dragOffset.x else 0f,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "drag_offset_x"
    )
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = if (isDragging) dragOffset.y else 0f,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "drag_offset_y"
    )

    // High fidelity springs for physical feeling motion
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elevation"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.8f else 1f,
        label = "alpha"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isDragging) -2f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    val dragModifier = Modifier
        .onSizeChanged { cardWidthPx = it.width }
        .pointerInput(classBlock.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { _ ->
                    isDragging = true
                    dragOffset = Offset.Zero
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMoveRequest()
                    onDragMove(classBlock.dayOfWeek, classBlock.startHour)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                    val changeInDays = if (cardWidthPx > 0) (dragOffset.x / cardWidthPx).roundToInt() else 0
                    val changeInHours = (dragOffset.y / rowHeightPx).roundToInt()
                    val targetDay = (classBlock.dayOfWeek + changeInDays).coerceIn(1, numDays)
                    val targetHour = (classBlock.startHour + changeInHours).coerceIn(settings.startHour, settings.endHour)
                    onDragMove(targetDay, targetHour)
                },
                onDragEnd = {
                    isDragging = false
                    val changeInDays = if (cardWidthPx > 0) (dragOffset.x / cardWidthPx).roundToInt() else 0
                    val changeInHours = (dragOffset.y / rowHeightPx).roundToInt()
                    val targetDay = (classBlock.dayOfWeek + changeInDays).coerceIn(1, numDays)
                    val targetHour = (classBlock.startHour + changeInHours).coerceIn(settings.startHour, settings.endHour)
                    onPlaceBlock(classBlock, targetDay, targetHour)
                    dragOffset = Offset.Zero
                    onDragEndMove()
                },
                onDragCancel = {
                    isDragging = false
                    onCancelPlacement()
                    dragOffset = Offset.Zero
                    onDragEndMove()
                }
            )
        }
        .graphicsLayer {
            scaleX = scale * entranceScale
            scaleY = scale * entranceScale
            this.alpha = alpha * entranceAlpha
            rotationZ = rotation
            translationX = animatedDragOffsetX
            translationY = animatedDragOffsetY
        }

    val finalCardModifier = Modifier
        .fillMaxSize()
        .then(dragModifier)

    var showQuickMenu by remember { mutableStateOf(false) }
    val isOverlapping = overlappingClasses.isNotEmpty()

    Box(modifier = modifier) {
        if (isDragging) {
            // Draw visual placeholder back at original coordinate
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = cardColor.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .drawBehind {
                        val stroke = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                        drawRoundRect(
                            color = cardColor.copy(alpha = 0.4f),
                            style = stroke,
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenWith,
                        contentDescription = null,
                        tint = cardColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Moving...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (gridStyle == "minimal") {
        val containerColor = if (isOverlapping) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        }
        val textCol = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        val secTextCol = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

        Card(
            modifier = finalCardModifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showQuickMenu = true }
                )
                .testTag("class_card_${classBlock.id}"),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = if (isOverlapping) 2.dp else 1.dp,
                color = if (isOverlapping) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left color accent stripe
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(if (isOverlapping) MaterialTheme.colorScheme.error else cardColor)
                    )
                    
                    // Class details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = classBlock.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = textCol,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isOverlapping) {
                                var showTooltip by remember { mutableStateOf(false) }

                                Box {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Conflict",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { showTooltip = true }
                                    )

                                    if (showTooltip) {
                                        Popup(
                                            alignment = Alignment.TopCenter,
                                            onDismissRequest = { showTooltip = false },
                                            properties = PopupProperties(focusable = true)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .widthIn(max = 240.dp)
                                                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Filled.Warning,
                                                            contentDescription = "Warning",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Schedule Conflict!",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Overlaps with:\n" + overlappingClasses.joinToString("\n") { "• ${it.name}" },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (classBlock.room.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = secTextCol,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = classBlock.room,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secTextCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (classBlock.professor.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = secTextCol,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = classBlock.professor,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secTextCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Small Menu Trigger at top-right
                IconButton(
                    onClick = { showQuickMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Class Actions",
                        tint = textCol.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick Actions Dropdown
                DropdownMenu(
                    expanded = showQuickMenu,
                    onDismissRequest = { showQuickMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Class") },
                        onClick = {
                            showQuickMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move / Reschedule") },
                        onClick = {
                            showQuickMenu = false
                            onMoveRequest()
                        },
                        leadingIcon = { Icon(Icons.Filled.OpenWith, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Pool (Unschedule)") },
                        onClick = {
                            showQuickMenu = false
                            onUnscheduleRequest()
                        },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) }
                    )
                }
            }
        }
    } else {
        // Classic mode (Premium Translucent with solid left stripe)
        val containerColor = if (isOverlapping) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        } else {
            // Soft translucent tint of the class's color
            cardColor.copy(alpha = 0.15f)
        }
        val textColor = if (isOverlapping) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val secondaryTextColor = if (isOverlapping) {
            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Card(
            modifier = finalCardModifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showQuickMenu = true }
                )
                .testTag("class_card_${classBlock.id}"),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = if (isOverlapping) 2.dp else 1.dp,
                color = if (isOverlapping) MaterialTheme.colorScheme.error else cardColor.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left color accent stripe
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(if (isOverlapping) MaterialTheme.colorScheme.error else cardColor)
                    )

                    // Content details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = classBlock.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isOverlapping) {
                                var showTooltip by remember { mutableStateOf(false) }

                                Box {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Conflict",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { showTooltip = true }
                                    )

                                    if (showTooltip) {
                                        Popup(
                                            alignment = Alignment.TopCenter,
                                            onDismissRequest = { showTooltip = false },
                                            properties = PopupProperties(focusable = true)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .widthIn(max = 240.dp)
                                                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Filled.Warning,
                                                            contentDescription = "Warning",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Schedule Conflict!",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Overlaps with:\n" + overlappingClasses.joinToString("\n") { "• ${it.name}" },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (classBlock.room.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = classBlock.room,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (classBlock.professor.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = classBlock.professor,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Small Menu Trigger at top-right
                IconButton(
                    onClick = { showQuickMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Class Actions",
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick Actions Dropdown
                DropdownMenu(
                    expanded = showQuickMenu,
                    onDismissRequest = { showQuickMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Class") },
                        onClick = {
                            showQuickMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move / Reschedule") },
                        onClick = {
                            showQuickMenu = false
                            onMoveRequest()
                        },
                        leadingIcon = { Icon(Icons.Filled.OpenWith, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Pool (Unschedule)") },
                        onClick = {
                            showQuickMenu = false
                            onUnscheduleRequest()
                        },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun ScreenshotReferenceView(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    viewModel: ScheduleViewModel,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit,
    onAiImportCompleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subTab by rememberSaveable { mutableStateOf("ai") } // "ai" or "manual"

    // AI Organizer State
    var aiFileUri by remember { mutableStateOf<Uri?>(null) }
    var aiFileName by remember { mutableStateOf("") }
    var aiFileType by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var parsedClasses by remember { mutableStateOf<List<ClassBlock>?>(null) }

    val aiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            aiFileUri = uri
            aiFileName = getFileName(context, uri)
            aiFileType = context.contentResolver.getType(uri) ?: "Unknown"
            parsedClasses = null
            analysisError = null
        }
    }

    val performAnalysis = {
        val uri = aiFileUri
        if (uri != null) {
            isAnalyzing = true
            analysisError = null
            parsedClasses = null
            
            coroutineScope.launch {
                try {
                    val input = GeminiHelper.processUriToInput(context, uri)
                    val results = GeminiHelper.parseScheduleWithAI(input)
                    parsedClasses = results
                } catch (e: Exception) {
                    e.printStackTrace()
                    analysisError = e.localizedMessage ?: "An error occurred during parsing"
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("screenshot_reference_view"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Elegant segment switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(100.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val isAiSelected = subTab == "ai"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { subTab = "ai" },
                color = if (isAiSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                shape = RoundedCornerShape(100.dp),
                shadowElevation = if (isAiSelected) 2.dp else 0.dp,
                border = if (isAiSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = if (isAiSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Organizer",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isAiSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isAiSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            val isManualSelected = subTab == "manual"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { subTab = "manual" },
                color = if (isManualSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                shape = RoundedCornerShape(100.dp),
                shadowElevation = if (isManualSelected) 2.dp else 0.dp,
                border = if (isManualSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = if (isManualSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reference Photo",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isManualSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isManualSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (subTab == "manual") {
            // ORIGINAL MANUAL REFERENCE PHOTO MODE
            if (settings.referenceScreenshotUri != null) {
                // Header actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reference Timetable Screenshot",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row {
                        TextButton(onClick = onImportClick) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replace")
                        }
                        TextButton(
                            onClick = onClearClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }

                // Interactive image zoomable preview box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = settings.referenceScreenshotUri,
                        contentDescription = "School Timetable Reference Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            } else {
                // Gorgeous empty state
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Reference Screenshot Uploaded",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import a screenshot or photo of your official syllabus or school schedule. You can reference it side-by-side while creating your digital calendar blocks offline!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onImportClick,
                        modifier = Modifier.testTag("upload_screenshot_button")
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Reference Photo")
                    }
                }
            }
        } else {
            // NEW AI AUTO-ORGANIZER MODE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✨ AI Schedule Auto-Organizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Upload a timetable photo, syllabus PDF, or schedule text file, and Gemini AI will automatically extract, organize, and color-code your classes offline!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
                )

                // Upload Zone / File Status Card
                if (aiFileUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { aiFilePickerLauncher.launch("*/*") }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select or Drop Schedule File",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Supports Screenshots, PDFs, or Text Files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    // Selected file description card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    aiFileType.startsWith("image/") -> Icons.Filled.Image
                                    aiFileType.contains("pdf") -> Icons.Filled.PictureAsPdf
                                    else -> Icons.Filled.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = aiFileName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ready for AI analysis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                aiFileUri = null
                                parsedClasses = null
                                analysisError = null
                            }) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove file",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Analyze Trigger Button
                if (aiFileUri != null && parsedClasses == null && !isAnalyzing) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { performAnalysis() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .testTag("ai_organize_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Organize with Gemini AI",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                // Analyzing/Loading indicator
                if (isAnalyzing) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gemini AI is analyzing schedule...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scanning timetable layout, extracting subjects, days, and classes. Retrying automatically if busy.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                // Error Card
                if (analysisError != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Parsing Failed",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = analysisError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (analysisError!!.contains("API key", ignoreCase = true)) {
                                Text(
                                    text = "\nTip: Make sure you have entered your API Key in the Secrets panel in AI Studio with key 'GEMINI_API_KEY'.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { performAnalysis() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }

                // Extracted Parsed Classes Results
                if (parsedClasses != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Extracted Classes (${parsedClasses!!.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = { parsedClasses = null }) {
                            Text("Clear Results", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Render Extracted Cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parsedClasses!!.forEach { parsed ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                ) {
                                    // Visual color indicator on side
                                    val indicatorColor = try {
                                        Color(android.graphics.Color.parseColor(parsed.colorHex))
                                    } catch (e: Exception) {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(6.dp)
                                            .background(indicatorColor)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = parsed.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${formatDayOfWeek(parsed.dayOfWeek)} • ${formatTime(parsed.startHour, parsed.startMinute)} (${parsed.durationMinutes}m)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (parsed.professor.isNotBlank()) {
                                                Icon(
                                                    Icons.Filled.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = parsed.professor,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }
                                            if (parsed.room.isNotBlank()) {
                                                Icon(
                                                    Icons.Filled.Room,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = parsed.room,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val classesToSave = parsedClasses ?: return@Button
                            viewModel.addClassBlocks(classesToSave)
                            Toast.makeText(context, "Successfully saved ${classesToSave.size} classes offline!", Toast.LENGTH_LONG).show()
                            aiFileUri = null
                            parsedClasses = null
                            onAiImportCompleted() // Return back to main tab automatically!
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("ai_save_all_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm & Save Offline")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { parsedClasses = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel & Discard")
                    }
                }
            }
        }
    }
}

private fun formatDayOfWeek(day: Int): String {
    return when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unscheduled"
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}

@Composable
fun ClassPoolView(
    classes: List<ClassBlock>,
    onClassClick: (ClassBlock) -> Unit,
    onScheduleClick: (ClassBlock) -> Unit,
    onDeleteClick: (ClassBlock) -> Unit
) {
    val poolClasses = classes.filter { !it.isScheduled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("class_pool_view")
    ) {
        Text(
            text = "Unscheduled Class Pool",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "These classes exist in your catalog but haven't been scheduled on the weekly planner yet. Click 'Place' to position them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (poolClasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unscheduled pool is empty!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "All created classes are scheduled on the calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(poolClasses, key = { it.id }) { cls ->
                    PoolClassItem(
                        classBlock = cls,
                        onClick = { onClassClick(cls) },
                        onSchedule = { onScheduleClick(cls) },
                        onDelete = { onDeleteClick(cls) }
                    )
                }
            }
        }
    }
}

@Composable
fun PoolClassItem(
    classBlock: ClassBlock,
    onClick: () -> Unit,
    onSchedule: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(classBlock.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val containerColor = cardColor.copy(alpha = 0.12f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val secTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var isAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(classBlock.id) {
        isAppeared = false
        delay(15)
        isAppeared = true
    }

    val entranceScale by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "entranceScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "entranceAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale
                scaleY = entranceScale
                alpha = entranceAlpha
            }
            .clickable { onClick() }
            .testTag("pool_item_${classBlock.id}"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left color accent stripe
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(cardColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = classBlock.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (classBlock.room.isNotBlank()) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = secTextColor, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = classBlock.room, style = MaterialTheme.typography.bodySmall, color = secTextColor)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (classBlock.professor.isNotBlank()) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = secTextColor, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = classBlock.professor, style = MaterialTheme.typography.bodySmall, color = secTextColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Duration: ${classBlock.durationMinutes} minutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = secTextColor.copy(alpha = 0.8f)
                    )
                }

                // Actions: Schedule or Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onSchedule,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Place", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Class")
                    }
                }
            }
        }
    }
}


// ------------------------------------
// DIALOGS & POPUPS
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditDialog(
    classBlock: ClassBlock?,
    settings: ScheduleSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Int, Int, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(classBlock?.name ?: "") }
    var professor by remember { mutableStateOf(classBlock?.professor ?: "") }
    var room by remember { mutableStateOf(classBlock?.room ?: "") }
    var dayOfWeek by remember { mutableStateOf(classBlock?.dayOfWeek ?: 0) } // 0 = unscheduled
    var startHour by remember { mutableStateOf(classBlock?.startHour ?: 9) }
    var startMinute by remember { mutableStateOf(classBlock?.startMinute ?: 0) }
    var durationMinutes by remember { mutableStateOf(classBlock?.durationMinutes ?: 60) }
    var colorHex by remember { mutableStateOf(classBlock?.colorHex ?: "#6366F1") }

    // Colors list
    val colors = listOf(
        "#6366F1", "#8B5CF6", "#EC4899", "#EF4444",
        "#F59E0B", "#10B981", "#14B8A6", "#06B6D4", "#3B82F6", "#64748B"
    )

    var showCustomPicker by remember {
        mutableStateOf(!colors.any { it.uppercase() == colorHex.uppercase() })
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("class_edit_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (classBlock == null) "Add Class Block" else "Edit Class Block",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject / Class Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("input_subject_name")
                )

                OutlinedTextField(
                    value = professor,
                    onValueChange = { professor = it },
                    label = { Text("Professor / Lecturer (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("input_professor")
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Classroom / Hall (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("input_room")
                )

                // Day Selection
                Text("Day of Week Schedule", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val days = listOf("Pool", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val limit = if (settings.showWeekends) 7 else 5

                    for (i in 0..limit) {
                        FilterChip(
                            selected = dayOfWeek == i,
                            onClick = { dayOfWeek = i },
                            label = { Text(days[i]) }
                        )
                    }
                }

                // If scheduled, setup time hours/minutes and duration
                if (dayOfWeek > 0) {
                    Text("Time Slot Setup", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startHour.toString(),
                            onValueChange = { startHour = it.toIntOrNull() ?: 9 },
                            label = { Text("Hour (24h)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startMinute.toString(),
                            onValueChange = { startMinute = it.toIntOrNull() ?: 0 },
                            label = { Text("Minute") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                Text("Duration (Minutes)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                OutlinedTextField(
                    value = durationMinutes.toString(),
                    onValueChange = { durationMinutes = it.toIntOrNull() ?: 60 },
                    label = { Text("Duration (e.g. 60, 90, 120)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                )

                // Color Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme Color Card", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    TextButton(
                        onClick = { showCustomPicker = !showCustomPicker },
                        modifier = Modifier.testTag("toggle_custom_color_button")
                    ) {
                        Icon(
                            imageVector = if (showCustomPicker) Icons.Filled.ExpandLess else Icons.Filled.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showCustomPicker) "Hide Custom" else "Custom Picker...")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (color in colors) {
                        val isSelected = colorHex.uppercase() == color.uppercase()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                .border(
                                    BorderStroke(
                                        if (isSelected) 3.dp else 0.dp,
                                        MaterialTheme.colorScheme.onSurface
                                    ),
                                    CircleShape
                                )
                                .clickable { colorHex = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (isColorHexLight(color)) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Plus / Custom color indicator circle
                    val isCustomActive = !colors.any { it.uppercase() == colorHex.uppercase() }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isCustomActive) {
                                    Modifier.background(Color(android.graphics.Color.parseColor(colorHex)), CircleShape)
                                } else {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF3B82F6))
                                        ),
                                        CircleShape
                                    )
                                }
                            )
                            .border(
                                BorderStroke(
                                    if (isCustomActive) 3.dp else 1.dp,
                                    if (isCustomActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                ),
                                CircleShape
                            )
                            .clickable {
                                showCustomPicker = true
                                if (!isCustomActive) {
                                    colorHex = "#6366F1"
                                }
                            }
                            .testTag("custom_color_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCustomActive) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected Custom Color",
                                tint = if (isColorHexLight(colorHex)) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Palette,
                                contentDescription = "Custom Color Picker",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showCustomPicker) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .testTag("custom_color_picker_panel"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Custom Color Designer",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Extracts HSV on every colorHex change to sync sliders correctly
                            val currentHsv = remember(colorHex) {
                                val hsv = FloatArray(3)
                                try {
                                    val colorInt = android.graphics.Color.parseColor(colorHex)
                                    android.graphics.Color.colorToHSV(colorInt, hsv)
                                } catch (e: Exception) {
                                    hsv[0] = 0f
                                    hsv[1] = 1f
                                    hsv[2] = 1f
                                }
                                hsv
                            }

                            val updateColorFromHsv = { h: Float, s: Float, v: Float ->
                                val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                                colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                            }

                            // Hue Slider (0-360)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hue", style = MaterialTheme.typography.labelMedium)
                                Text("${currentHsv[0].roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = currentHsv[0],
                                onValueChange = { h -> updateColorFromHsv(h, currentHsv[1], currentHsv[2]) },
                                valueRange = 0f..360f,
                                modifier = Modifier.testTag("hue_slider")
                            )

                            // Saturation Slider (0-1)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                                Text("${(currentHsv[1] * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = currentHsv[1],
                                onValueChange = { s -> updateColorFromHsv(currentHsv[0], s, currentHsv[2]) },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("saturation_slider")
                            )

                            // Value Slider (0-1)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                                Text("${(currentHsv[2] * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = currentHsv[2],
                                onValueChange = { v -> updateColorFromHsv(currentHsv[0], currentHsv[1], v) },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("value_slider")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Preview & Hex Input Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Live Preview Card
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(android.graphics.Color.parseColor(colorHex)), RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isColorHexLight(colorHex)) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                var hexInputText by remember(colorHex) { mutableStateOf(colorHex) }
                                OutlinedTextField(
                                    value = hexInputText,
                                    onValueChange = { input ->
                                        hexInputText = input
                                        val cleanInput = if (input.startsWith("#")) input else "#$input"
                                        if (cleanInput.length == 7) {
                                            try {
                                                android.graphics.Color.parseColor(cleanInput)
                                                colorHex = cleanInput
                                            } catch (e: Exception) {
                                                // ignore invalid color parsing while typing
                                            }
                                        }
                                    },
                                    label = { Text("Hex Code") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hex_code_input")
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    name = "Untitled Subject"
                                }
                                onSave(name, professor, room, dayOfWeek, startHour, startMinute, durationMinutes, colorHex)
                            },
                            modifier = Modifier.testTag("save_class_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: ScheduleSettings,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, Boolean) -> Unit,
    onResetDemo: () -> Unit,
    onClearAll: () -> Unit
) {
    var schoolName by remember { mutableStateOf(settings.schoolName) }
    var termName by remember { mutableStateOf(settings.termName) }
    var startHour by remember { mutableStateOf(settings.startHour) }
    var endHour by remember { mutableStateOf(settings.endHour) }
    var showWeekends by remember { mutableStateOf(settings.showWeekends) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calendar Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Section: Header Details
                Text(
                    text = "ACADEMIC DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("School / Faculty Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("settings_school_name")
                )

                OutlinedTextField(
                    value = termName,
                    onValueChange = { termName = it },
                    label = { Text("Semester / Term Details") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("settings_term_details")
                )

                // Section: App Theme Mode
                Text(
                    text = "VISUAL THEME",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            RoundedCornerShape(100.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val modes = listOf(
                        Triple("light", Icons.Filled.LightMode, "Light"),
                        Triple("dark", Icons.Filled.DarkMode, "Dark"),
                        Triple("system", Icons.Filled.Contrast, "System")
                    )
                    modes.forEach { (mode, icon, label) ->
                        val isSelected = themeMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable { onThemeModeChange(mode) },
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = RoundedCornerShape(100.dp),
                            shadowElevation = if (isSelected) 3.dp else 0.dp,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Section: Constraints
                Text(
                    text = "GRID VIEW CONSTRAINTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startHour.toString(),
                        onValueChange = { startHour = it.toIntOrNull() ?: 8 },
                        label = { Text("Start Hour (24h)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endHour.toString(),
                        onValueChange = { endHour = it.toIntOrNull() ?: 18 },
                        label = { Text("End Hour (24h)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Weekends (Sat/Sun)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = showWeekends,
                        onCheckedChange = { showWeekends = it },
                        modifier = Modifier.testTag("settings_weekend_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                // Section: Utility reset / clear actions
                Text(
                    text = "DATA MAINTENANCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetDemo,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Demo Schedule")
                    }

                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Data")
                    }
                }

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(schoolName, termName, startHour, endHour, showWeekends) },
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.testTag("settings_save_button")
                    ) {
                        Text("Apply Settings")
                    }
                }
            }
        }
    }
}

// Helper to calculate luminance from hex color string
private fun isColorHexLight(hex: String): Boolean {
    return try {
        val color = android.graphics.Color.parseColor(hex)
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        luminance > 180
    } catch (e: Exception) {
        false
    }
}
