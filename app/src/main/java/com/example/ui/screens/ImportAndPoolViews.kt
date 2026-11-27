package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.prefs.AppPreferences
import com.example.ui.components.ClayPrimaryButton
import com.example.ui.components.PaperOutlinedButton
import com.example.ui.theme.ScheduleTheme
import com.example.ui.viewmodel.ScheduleViewModel
import com.example.util.GeminiHelper
import com.example.util.ScheduleLayoutHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenshotReferenceView(
    settings: ScheduleSettings,
    viewModel: ScheduleViewModel,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onAiImportCompleted: () -> Unit,
    resolveFileName: (Uri) -> String,
    uriReader: suspend (Context, Uri) -> GeminiHelper.ParseInput = GeminiHelper::processUriToInput,
    extractor: suspend (Context, GeminiHelper.ParseInput) -> List<ClassBlock> = GeminiHelper::parseScheduleWithAI,
    onClassesSaved: (Int) -> Unit = {},
    onConnectEguroClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val planner = ScheduleTheme.colors
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val eguroState by viewModel.eguroState.collectAsStateWithLifecycle()
    var showFullPhotoViewer by remember { mutableStateOf(false) }

    val aiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setImportSelectedFile(
                uri = uri.toString(),
                name = resolveFileName(uri),
                type = context.contentResolver.getType(uri) ?: "Document"
            )
        }
    }

    val performAnalysis: () -> Unit = {
        viewModel.startImportExtraction(
            context = context,
            uriReader = uriReader,
            extractor = extractor
        )
    }

    val hasApiKey = AppPreferences.getEffectiveGeminiApiKey(context).isNotBlank()
    val hasInputReady = (importState.inputMode == "file" && importState.selectedFileUri != null) ||
        (importState.inputMode == "text" && importState.pastedText.isNotBlank())

    if (showFullPhotoViewer && settings.referenceScreenshotUri != null) {
        ReferencePhotoZoomDialog(
            imageUri = settings.referenceScreenshotUri,
            onDismiss = { showFullPhotoViewer = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(planner.paperBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("screenshot_reference_view")
    ) {
        // 1. Primary path: Gemini Extraction
        Text(
            text = "Extract Classes",
            style = MaterialTheme.typography.titleLarge,
            color = planner.inkPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Extract classes sends your file or pasted text to Gemini to parse into your schedule. Needs internet and an API key.",
            style = MaterialTheme.typography.bodyMedium,
            color = planner.inkSecondary
        )

        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = planner.terracottaContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, planner.terracotta.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            tint = planner.onTerracottaContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add your Gemini API key in Settings to enable extraction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.onTerracottaContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    PaperOutlinedButton(
                        onClick = onOpenSettingsClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("import_configure_key_button")
                    ) {
                        Text("Configure", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (importState.lastSavedCount != null) {
            val savedCount = importState.lastSavedCount!!
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = planner.sageContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, planner.sagePrimary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_saved_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = planner.sageOnContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved $savedCount ${if (savedCount == 1) "class" else "classes"} to your schedule.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = planner.sageOnContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    PaperOutlinedButton(
                        onClick = onAiImportCompleted,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("import_view_schedule_button")
                    ) {
                        Text("View Schedule", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Outlined input mode toggles
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaperOutlinedButton(
                onClick = { viewModel.setImportInputMode("file") },
                isActive = importState.inputMode == "file",
                enabled = !importState.isAnalyzing,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("import_mode_file_button")
            ) {
                Text("File / Image / PDF", style = MaterialTheme.typography.labelLarge)
            }
            PaperOutlinedButton(
                onClick = { viewModel.setImportInputMode("text") },
                isActive = importState.inputMode == "text",
                enabled = !importState.isAnalyzing,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("import_mode_text_button")
            ) {
                Text("Paste Text", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (importState.inputMode == "file") {
            if (importState.selectedFileUri == null) {
                Surface(
                    color = planner.paperSheet,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, planner.ruleStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !importState.isAnalyzing) {
                            aiFilePickerLauncher.launch("*/*")
                        }
                        .testTag("select_timetable_file_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = planner.sagePrimary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Choose Timetable File",
                            style = MaterialTheme.typography.titleMedium,
                            color = planner.inkPrimary
                        )
                        Text(
                            text = "Images, PDFs, or plain text schedules",
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.inkSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                    border = BorderStroke(1.dp, planner.ruleStrong),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                importState.selectedFileType.startsWith("image/") -> Icons.Filled.Image
                                importState.selectedFileType.contains("pdf") -> Icons.Filled.PictureAsPdf
                                else -> Icons.Filled.Description
                            },
                            contentDescription = null,
                            tint = planner.sagePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = importState.selectedFileName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = planner.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Selected for extraction",
                                style = MaterialTheme.typography.bodySmall,
                                color = planner.inkSecondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearImportFile() },
                            enabled = !importState.isAnalyzing
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Clear selected file",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = importState.pastedText,
                onValueChange = { viewModel.setImportPastedText(it) },
                enabled = !importState.isAnalyzing,
                label = { Text("Paste portal timetable, syllabus, or class list") },
                placeholder = { Text("e.g. CS 101 Mon/Wed 9:00-10:30 AM Room 204 Dr. Cruz") },
                minLines = 5,
                maxLines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_pasted_text_field")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Soft clay primary extraction CTA (stays visible & legible as disabled during extraction)
        ClayPrimaryButton(
            onClick = performAnalysis,
            enabled = hasInputReady && !importState.isAnalyzing,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_organize_button")
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (importState.isAnalyzing) "Extracting classes…" else "Extract classes",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (importState.isAnalyzing) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_progress_card"),
                colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                border = BorderStroke(1.dp, planner.ruleFine),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = planner.sagePrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Extracting classes from your input…",
                        style = MaterialTheme.typography.titleSmall,
                        color = planner.inkPrimary
                    )
                    Text(
                        text = "Parsing subjects, days, start times, and rooms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.inkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    LinearProgressIndicator(
                        color = planner.sagePrimary,
                        trackColor = planner.clayInsetTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (importState.analysisError != null) {
            val errorMsg = importState.analysisError!!
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_error_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extraction Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaperOutlinedButton(
                            onClick = performAnalysis,
                            enabled = !importState.isAnalyzing,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("ai_retry_button")
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                        if (errorMsg.contains("API key", ignoreCase = true)) {
                            ClayPrimaryButton(
                                onClick = onOpenSettingsClick,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("ai_open_settings_button")
                            ) {
                                Text("Configure", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        if (importState.parsedClasses != null) {
            val extracted = importState.parsedClasses!!
            Spacer(modifier = Modifier.height(16.dp))

            if (extracted.isEmpty()) {
                // Distinct zero-class successful extraction state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_empty_result_card"),
                    colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                    border = BorderStroke(1.dp, planner.terracotta.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No classes found (0 extracted)",
                            style = MaterialTheme.typography.titleMedium,
                            color = planner.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (eguroState.lastSyncedCount == 0 && eguroState.checkedPages.isNotEmpty()) {
                                "Signed in to CCC e-GURO, but no schedule table or course cards were detected on:\n${eguroState.checkedPages.joinToString("\n")}\nTry entering your My Class page URL/path or paste your schedule text above."
                            } else {
                                "Gemini completed extraction without finding any class meetings. Edit your text or choose another file to try again."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (importState.inputMode == "file") {
                                PaperOutlinedButton(
                                    onClick = { aiFilePickerLauncher.launch("*/*") },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier.testTag("ai_replace_file_button")
                                ) {
                                    Text("Choose different file", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                PaperOutlinedButton(
                                    onClick = { viewModel.clearImportResults() },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier.testTag("ai_edit_text_button")
                                ) {
                                    Text("Edit input text", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            TextButton(onClick = { viewModel.clearImportResults() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extracted.forEachIndexed { index, parsed ->
                        val dotColor = try {
                            Color(android.graphics.Color.parseColor(parsed.colorHex))
                        } catch (_: Exception) {
                            planner.sagePrimary
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extracted_class_card_$index"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                            border = BorderStroke(1.dp, planner.ruleFine)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(5.dp)
                                        .background(dotColor)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = parsed.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = planner.inkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = planner.inkSecondary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${ScheduleLayoutHelper.dayFullName(parsed.dayOfWeek)} • ${
                                                ScheduleLayoutHelper.formatTimeRange(
                                                    parsed.startHour,
                                                    parsed.startMinute,
                                                    parsed.durationMinutes
                                                )
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = planner.inkSecondary
                                        )
                                    }
                                    if (parsed.professor.isNotBlank() || parsed.room.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            if (parsed.professor.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = planner.inkSecondary
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        parsed.professor,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = planner.inkSecondary
                                                    )
                                                }
                                            }
                                            if (parsed.room.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Filled.Room,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = planner.inkSecondary
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        parsed.room,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = planner.inkSecondary
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

                Spacer(modifier = Modifier.height(14.dp))

                // Result count and save action grouped together
                Surface(
                    color = planner.claySurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, planner.ruleStrong),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${extracted.size} ${if (extracted.size == 1) "class" else "classes"} ready to save",
                                style = MaterialTheme.typography.titleSmall,
                                color = planner.inkPrimary
                            )
                            TextButton(
                                onClick = { viewModel.clearImportResults() },
                                modifier = Modifier.testTag("ai_discard_button")
                            ) {
                                Text("Discard", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ClayPrimaryButton(
                            onClick = {
                                viewModel.saveExtractedClasses { savedCount ->
                                    onClassesSaved(savedCount)
                                }
                            },
                            enabled = !importState.isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_save_all_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (importState.isSaving) {
                                    "Saving…"
                                } else {
                                    "Save ${extracted.size} ${if (extracted.size == 1) "class" else "classes"}"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // 2. Optional CCC e-GURO LMS native sync
        HorizontalDivider(
            color = planner.ruleStrong.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 20.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("eguro_lms_section")
        ) {
            Text(
                text = "CCC e-GURO LMS (Optional)",
                style = MaterialTheme.typography.titleMedium,
                color = planner.inkPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (eguroState.savedUsername.isNotBlank()) {
                    "Last connected as ${eguroState.savedUsername}. Sync natively in-app without leaving Sked."
                } else {
                    "Sign in with your CCC e-GURO account inside the app to auto-detect your timetable. Optional — local scheduling works offline."
                },
                style = MaterialTheme.typography.bodySmall,
                color = planner.inkSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaperOutlinedButton(
                    onClick = onConnectEguroClick,
                    modifier = Modifier.testTag("connect_eguro_button")
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (eguroState.savedUsername.isNotBlank()) "Sync from CCC e-GURO" else "Connect CCC e-GURO",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (eguroState.savedUsername.isNotBlank()) {
                    TextButton(
                        onClick = { viewModel.disconnectEguro(context) },
                        modifier = Modifier.testTag("disconnect_eguro_button")
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 3. Secondary offline section: Reference timetable
        HorizontalDivider(
            color = planner.ruleStrong.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 22.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reference_timetable_section")
        ) {
            Text(
                text = "Reference timetable",
                style = MaterialTheme.typography.titleMedium,
                color = planner.inkPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Save reference photo keeps a local image copy on your device only. Always available offline without AI or internet.",
                style = MaterialTheme.typography.bodySmall,
                color = planner.inkSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (settings.referenceScreenshotUri != null) {
                Surface(
                    color = planner.paperSheet,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, planner.ruleStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reference_photo_preview")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(planner.paperBackground)
                                .clickable { showFullPhotoViewer = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(settings.referenceScreenshotUri)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = "Saved reference timetable photo. Tap to zoom.",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PaperOutlinedButton(
                                onClick = { showFullPhotoViewer = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("zoom_reference_photo_button")
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zoom", style = MaterialTheme.typography.labelLarge)
                            }
                            PaperOutlinedButton(
                                onClick = onImportClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("replace_reference_photo_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Replace", style = MaterialTheme.typography.labelLarge)
                            }
                            PaperOutlinedButton(
                                onClick = onClearClick,
                                contentColor = MaterialTheme.colorScheme.error,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("clear_reference_photo_button")
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else {
                PaperOutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_screenshot_button")
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save reference photo", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReferencePhotoZoomDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val planner = ScheduleTheme.colors
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = planner.paperSheet,
            border = BorderStroke(1.dp, planner.ruleStrong)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reference timetable",
                        style = MaterialTheme.typography.titleMedium,
                        color = planner.inkPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scale > 1.05f) {
                            TextButton(onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }) {
                                Icon(Icons.Filled.ZoomOutMap, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close reference viewer")
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clipToBounds()
                        .background(planner.paperBackground)
                        .pointerInput(imageUri) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.2f
                                    }
                                }
                            )
                        }
                        .pointerInput(imageUri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = nextScale
                                offset = if (nextScale > 1f) {
                                    Offset(offset.x + pan.x, offset.y + pan.y)
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUri)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build(),
                        contentDescription = "Zoomable reference timetable photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pinch or double-tap image to zoom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = planner.inkSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassPoolView(
    classes: List<ClassBlock>,
    onClassClick: (ClassBlock) -> Unit,
    onScheduleClick: (ClassBlock) -> Unit,
    onDeleteClick: (ClassBlock) -> Unit,
    onAutoArrangeClick: (() -> Unit)? = null
) {
    val planner = ScheduleTheme.colors
    val poolClasses = remember(classes) { classes.filter { !it.isScheduled } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(planner.paperBackground)
            .padding(16.dp)
            .testTag("class_pool_view")
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Unscheduled Pool",
                style = MaterialTheme.typography.titleLarge,
                color = planner.inkPrimary,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .semantics { heading() }
            )
            if (poolClasses.isNotEmpty() && onAutoArrangeClick != null) {
                PaperOutlinedButton(
                    onClick = onAutoArrangeClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("pool_auto_arrange_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-arrange", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            text = "${poolClasses.size} unscheduled ${if (poolClasses.size == 1) "course" else "courses"}. Tap Place to assign a time slot on your timetable.",
            style = MaterialTheme.typography.bodyMedium,
            color = planner.inkSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
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
                        tint = planner.ruleStrong,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pool is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = planner.inkPrimary
                    )
                    Text(
                        text = "Every created course is placed on the weekly sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.inkSecondary,
                        textAlign = TextAlign.Center
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoolClassItem(
    classBlock: ClassBlock,
    onClick: () -> Unit,
    onSchedule: () -> Unit,
    onDelete: () -> Unit
) {
    val planner = ScheduleTheme.colors
    val cardColor = remember(classBlock.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(classBlock.colorHex)))
        } catch (_: Exception) {
            Color(0xFF2E5A4F)
        }
    }
    var showOverflow by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit ${classBlock.name}") { onClick() }
            .testTag("pool_item_${classBlock.id}"),
        colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, planner.ruleFine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(cardColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = classBlock.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = planner.inkPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (classBlock.room.isNotBlank() || classBlock.professor.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (classBlock.room.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = planner.inkSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        classBlock.room,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = planner.inkSecondary
                                    )
                                }
                            }
                            if (classBlock.professor.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = planner.inkSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        classBlock.professor,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = planner.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Duration: ${ScheduleLayoutHelper.formatDuration(classBlock.durationMinutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = planner.inkSecondary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ClayPrimaryButton(
                        onClick = onSchedule,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("pool_place_${classBlock.id}")
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Place", style = MaterialTheme.typography.labelLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit ${classBlock.name}",
                                tint = planner.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More actions for ${classBlock.name}",
                                    tint = planner.inkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit course") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        onClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete course", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        onDelete()
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
