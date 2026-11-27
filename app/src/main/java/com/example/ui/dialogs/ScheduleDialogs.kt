package com.example.ui.dialogs

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.prefs.AppPreferences
import com.example.ui.components.ClayPrimaryButton
import com.example.ui.components.ConfirmDangerDialog
import com.example.ui.components.PaperOutlinedButton
import com.example.ui.theme.CourseColorSwatches
import com.example.ui.theme.ScheduleTheme
import com.example.util.ScheduleLayoutHelper
import kotlin.math.roundToInt

/**
 * One-tap details and actions sheet for compact or overlapping weekly grid blocks.
 */
@Composable
fun ClassQuickActionsDialog(
    classBlock: ClassBlock,
    overlappingClasses: List<ClassBlock>,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onMoveToPool: () -> Unit,
    onDismiss: () -> Unit
) {
    val planner = ScheduleTheme.colors
    val accentColor = remember(classBlock.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(classBlock.colorHex)))
        } catch (_: Exception) {
            planner.sagePrimary
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("class_quick_actions_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
            border = BorderStroke(1.dp, planner.ruleStrong)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 28.dp)
                            .background(accentColor, RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = classBlock.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = planner.inkPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = planner.sagePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${ScheduleLayoutHelper.dayFullName(classBlock.dayOfWeek)} · ${
                            ScheduleLayoutHelper.formatTimeRange(
                                classBlock.startHour,
                                classBlock.startMinute,
                                classBlock.durationMinutes
                            )
                        } (${ScheduleLayoutHelper.formatDuration(classBlock.durationMinutes)})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = planner.inkPrimary
                    )
                }

                if (classBlock.room.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = planner.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = classBlock.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = planner.inkSecondary
                        )
                    }
                }

                if (classBlock.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = planner.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = classBlock.professor,
                            style = MaterialTheme.typography.bodyMedium,
                            color = planner.inkSecondary
                        )
                    }
                }

                if (overlappingClasses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Overlaps with:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            overlappingClasses.forEach { other ->
                                Text(
                                    text = "• ${other.name} (${ScheduleLayoutHelper.formatTimeRange(other.startHour, other.startMinute, other.durationMinutes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayPrimaryButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_action_edit_button")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Class", style = MaterialTheme.typography.labelLarge)
                    }
                    PaperOutlinedButton(
                        onClick = onMove,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_action_move_button")
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move / Reschedule", style = MaterialTheme.typography.labelLarge)
                    }
                    PaperOutlinedButton(
                        onClick = onMoveToPool,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_action_pool_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move to Pool", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Native soft-clay sign-in dialog for CCC e-GURO (https://lms.ccc.edu.ph).
 * Keeps credentials in-app without launching a web browser or WebView page.
 */
@Composable
fun EguroLoginDialog(
    initialUsername: String,
    initialSchedulePath: String,
    isSyncing: Boolean,
    errorMessage: String?,
    onSync: (username: String, password: String, schedulePath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val planner = ScheduleTheme.colors
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showAdvancedPath by remember { mutableStateOf(initialSchedulePath.isNotBlank()) }
    var schedulePath by remember { mutableStateOf(initialSchedulePath) }

    Dialog(onDismissRequest = { if (!isSyncing) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("eguro_login_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
            border = BorderStroke(1.dp, planner.ruleStrong)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = planner.sageContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = null,
                            tint = planner.sageOnContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect CCC e-GURO",
                            style = MaterialTheme.typography.titleLarge,
                            color = planner.inkPrimary
                        )
                        Text(
                            text = "Optional native sign-in to lms.ccc.edu.ph",
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.inkSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !isSyncing,
                    label = { Text("CCC e-GURO Username") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("eguro_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !isSyncing,
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("eguro_password_input")
                )

                TextButton(
                    onClick = { showAdvancedPath = !showAdvancedPath },
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        if (showAdvancedPath) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Optional schedule page path")
                }

                AnimatedVisibility(visible = showAdvancedPath) {
                    OutlinedTextField(
                        value = schedulePath,
                        onValueChange = { schedulePath = it },
                        enabled = !isSyncing,
                        label = { Text("Schedule URL or path (auto-detect if blank)") },
                        placeholder = { Text("e.g. student/schedule.php") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("eguro_path_input")
                    )
                }

                Surface(
                    color = planner.terracottaContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, planner.terracotta.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "Your password is used once to sign in and is not saved on disk. Note: CCC e-GURO locks accounts after 3 failed login attempts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.onTerracottaContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eguro_error_banner")
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ClayPrimaryButton(
                    onClick = { onSync(username.trim(), password, schedulePath.trim()) },
                    enabled = !isSyncing && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eguro_submit_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = planner.inkSecondary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signing in & detecting…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("Sign in & Detect Schedule", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                PaperOutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eguro_cancel_button")
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun isColorHexLight(hex: String): Boolean = try {
    val color = android.graphics.Color.parseColor(hex)
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    (0.299 * r + 0.587 * g + 0.114 * b) > 180
} catch (_: Exception) {
    false
}

@Composable
fun ClassEditDialog(
    classBlock: ClassBlock?,
    settings: ScheduleSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Int, Int, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(classBlock?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var professor by remember { mutableStateOf(classBlock?.professor ?: "") }
    var room by remember { mutableStateOf(classBlock?.room ?: "") }
    var dayOfWeek by remember { mutableStateOf(classBlock?.dayOfWeek ?: 0) }

    // Buffered numeric text fields prevent cursor snap-back when deleting digits
    var startHourText by remember { mutableStateOf((classBlock?.startHour ?: 9).toString()) }
    var startMinuteText by remember { mutableStateOf((classBlock?.startMinute ?: 0).toString()) }
    var durationText by remember { mutableStateOf((classBlock?.durationMinutes ?: 60).toString()) }

    var colorHex by remember {
        mutableStateOf(
            ScheduleLayoutHelper.normalizeColorHex(classBlock?.colorHex, CourseColorSwatches.first())
        )
    }
    var showCustomPicker by remember {
        mutableStateOf(!CourseColorSwatches.any { it.equals(colorHex, ignoreCase = true) })
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedHour = startHourText.toIntOrNull()
    val parsedMinute = startMinuteText.toIntOrNull()
    val parsedDuration = durationText.toIntOrNull()

    val hourValid = parsedHour != null && parsedHour in 0..23
    val minuteValid = parsedMinute != null && parsedMinute in 0..59
    val durationValid = parsedDuration != null && parsedDuration in 15..480

    if (confirmDelete && onDelete != null) {
        ConfirmDangerDialog(
            title = "Delete Class",
            message = "Remove \"${classBlock?.name ?: "this class"}\" from your schedule?",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("class_edit_dialog"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (classBlock == null) "Add Course" else "Edit Course",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Course / Subject Name *") },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("Course name is required") }) else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("input_subject_name")
                )

                OutlinedTextField(
                    value = professor,
                    onValueChange = { professor = it },
                    label = { Text("Instructor (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("input_professor")
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / Location (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("input_room")
                )

                Text("Day Assignment", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val limit = if (settings.showWeekends) 7 else 6
                    for (i in 0..limit) {
                        FilterChip(
                            selected = dayOfWeek == i,
                            onClick = { dayOfWeek = i },
                            label = { Text(ScheduleLayoutHelper.dayShortName(i)) }
                        )
                    }
                }

                if (dayOfWeek > 0) {
                    Text("Start Time (24-Hour)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startHourText,
                            onValueChange = { v -> startHourText = v.filter { it.isDigit() }.take(2) },
                            label = { Text("Hour (0–23)") },
                            isError = !hourValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = { v -> startMinuteText = v.filter { it.isDigit() }.take(2) },
                            label = { Text("Min (0–59)") },
                            isError = !minuteValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (hourValid && minuteValid) {
                        Text(
                            text = "Starts at ${ScheduleLayoutHelper.formatTime12h(parsedHour!!, parsedMinute!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                Text("Duration (Minutes)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(45, 60, 75, 90, 120, 180).forEach { preset ->
                        FilterChip(
                            selected = parsedDuration == preset,
                            onClick = { durationText = preset.toString() },
                            label = { Text("${preset}m") }
                        )
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { v -> durationText = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Custom minutes (15–480)") },
                    isError = !durationValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Color Marker", style = MaterialTheme.typography.labelLarge)
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
                        Text(if (showCustomPicker) "Hide Custom" else "Custom")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 14.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CourseColorSwatches.forEach { swatch ->
                        val isSelected = colorHex.equals(swatch, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(android.graphics.Color.parseColor(swatch)), CircleShape)
                                .border(
                                    BorderStroke(
                                        if (isSelected) 2.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                                    ),
                                    CircleShape
                                )
                                .clickable { colorHex = swatch },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (isColorHexLight(swatch)) Color.Black else Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    val isCustomActive = !CourseColorSwatches.any { it.equals(colorHex, ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(Color(0xFF2E5A4F), Color(0xFFB85D3B), Color(0xFF38598B))),
                                shape = CircleShape
                            )
                            .border(
                                BorderStroke(if (isCustomActive) 2.5.dp else 1.dp, MaterialTheme.colorScheme.outline),
                                CircleShape
                            )
                            .clickable { showCustomPicker = true }
                            .testTag("custom_color_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isCustomActive) Icons.Filled.Check else Icons.Filled.Palette,
                            contentDescription = "Custom color",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showCustomPicker) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("custom_color_picker_panel"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val currentHsv = remember(colorHex) {
                                FloatArray(3).also { hsv ->
                                    try {
                                        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(colorHex), hsv)
                                    } catch (_: Exception) {
                                        hsv[0] = 150f; hsv[1] = 0.5f; hsv[2] = 0.4f
                                    }
                                }
                            }
                            val updateHsv = { h: Float, s: Float, v: Float ->
                                val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                                colorHex = String.format("#%06X", 0xFFFFFF and c)
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Hue", style = MaterialTheme.typography.labelMedium)
                                Text("${currentHsv[0].roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = currentHsv[0],
                                onValueChange = { updateHsv(it, currentHsv[1], currentHsv[2]) },
                                valueRange = 0f..360f,
                                modifier = Modifier.testTag("hue_slider")
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                                Text("${(currentHsv[1] * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = currentHsv[1],
                                onValueChange = { updateHsv(currentHsv[0], it, currentHsv[2]) },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("saturation_slider")
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                                Text("${(currentHsv[2] * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = currentHsv[2],
                                onValueChange = { updateHsv(currentHsv[0], currentHsv[1], it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("value_slider")
                            )

                            var hexInput by remember(colorHex) { mutableStateOf(colorHex) }
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { input ->
                                    hexInput = input
                                    val candidate = if (input.startsWith("#")) input else "#$input"
                                    if (candidate.length == 7) {
                                        runCatching { android.graphics.Color.parseColor(candidate) }
                                            .onSuccess { colorHex = candidate.uppercase() }
                                    }
                                },
                                label = { Text("Hex Code") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hex_code_input")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    nameError = true
                                    return@Button
                                }
                                val h = (parsedHour ?: 9).coerceIn(0, 23)
                                val m = (parsedMinute ?: 0).coerceIn(0, 59)
                                val d = (parsedDuration ?: 60).coerceIn(15, 480)
                                onSave(name.trim(), professor.trim(), room.trim(), dayOfWeek, h, m, d, colorHex)
                            },
                            enabled = (dayOfWeek == 0 || (hourValid && minuteValid)) && durationValid,
                            shape = RoundedCornerShape(8.dp),
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

private enum class SettingsSection(val title: String, val summary: String) {
    Timetable("Timetable", "Term, teaching hours and days"),
    Appearance("Appearance", "Light, dark or system"),
    Import("Import", "Gemini extraction key"),
    Updates("Updates", "Version and update preferences"),
    Data("Schedule data", "Restore sample or clear classes")
}

@Composable
fun SettingsDialog(
    context: Context,
    settings: ScheduleSettings,
    themeMode: String,
    isCheckingUpdates: Boolean,
    updateStatusSummary: String?,
    onThemeModeChange: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, Boolean) -> Unit,
    onResetDemo: () -> Unit,
    onClearAll: () -> Unit,
    openImport: Boolean = false
) {
    var schoolName by rememberSaveable { mutableStateOf(settings.schoolName) }
    var termName by rememberSaveable { mutableStateOf(settings.termName) }
    var startHour by rememberSaveable { mutableStateOf(settings.startHour) }
    var endHour by rememberSaveable { mutableStateOf(settings.endHour) }
    var showWeekends by rememberSaveable { mutableStateOf(settings.showWeekends) }

    // API key & Updater configuration
    var geminiKey by remember { mutableStateOf(AppPreferences.getCustomGeminiApiKey(context)) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var autoCheckUpdates by remember { mutableStateOf(AppPreferences.isAutoCheckUpdatesEnabled(context)) }
    var showUpdateSourceConfig by remember { mutableStateOf(false) }
    var repoOwner by rememberSaveable { mutableStateOf(AppPreferences.getUpdateOwner(context)) }
    var repoName by rememberSaveable { mutableStateOf(AppPreferences.getUpdateRepo(context)) }
    var repoToken by remember { mutableStateOf(AppPreferences.getUpdateToken(context)) }
    var showRepoToken by remember { mutableStateOf(false) }

    var pendingConfirmAction by remember { mutableStateOf<String?>(null) } // "reset" or "clear"

    if (pendingConfirmAction != null) {
        val isReset = pendingConfirmAction == "reset"
        ConfirmDangerDialog(
            title = if (isReset) "Reset to Sample Schedule?" else "Clear All Schedule Data?",
            message = if (isReset) {
                "Your current classes and settings will be replaced with the sample timetable."
            } else {
                "All scheduled and unscheduled classes will be permanently removed."
            },
            confirmLabel = if (isReset) "Reset" else "Clear All",
            onConfirm = {
                pendingConfirmAction = null
                if (isReset) onResetDemo() else onClearAll()
            },
            onDismiss = { pendingConfirmAction = null }
        )
    }

    var selected by rememberSaveable { mutableStateOf(if (openImport) SettingsSection.Import else null) }
    val planner = ScheduleTheme.colors

    var wideLayout by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!wideLayout && selected != null) selected = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides planner.inkPrimary) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(planner.paperSheet).safeDrawingPadding().testTag("settings_dialog"),
            contentAlignment = Alignment.TopCenter
        ) {
            val wide = maxWidth >= 680.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val section = selected ?: SettingsSection.Timetable.takeIf { wide }
            val returnToIndex = section != null && !wide
            androidx.compose.runtime.SideEffect { wideLayout = wide }
            Column(Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (returnToIndex) {
                        IconButton(onClick = { selected = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Settings index")
                        }
                    }
                    Text(
                        if (returnToIndex) section!!.title else "Settings",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close settings") }
                }
                HorizontalDivider(color = planner.ruleStrong)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (wide || section == null) {
                        Column(
                            (if (wide) Modifier.width(220.dp) else Modifier.fillMaxWidth())
                                .verticalScroll(rememberScrollState())
                        ) {
                            SettingsSection.entries.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selected = item }
                                        .background(if (wide && section == item) planner.sageContainer else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        Text(item.summary, style = MaterialTheme.typography.bodySmall, color = planner.inkSecondary)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = planner.inkSecondary)
                                }
                                HorizontalDivider(color = planner.ruleFine)
                            }
                        }
                    }
                    if (section != null) key(section) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                            if (wide) Text(section.title, Modifier.padding(bottom = 16.dp),
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif))
                            when (section) {
                                SettingsSection.Timetable -> {
                                    Text(
                                        text = "ACADEMIC HEADER",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    OutlinedTextField(
                                        value = schoolName,
                                        onValueChange = { schoolName = it },
                                        label = { Text("School / Program Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                            .testTag("settings_school_name")
                                    )
                                    OutlinedTextField(
                                        value = termName,
                                        onValueChange = { termName = it },
                                        label = { Text("Semester / Term") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .testTag("settings_term_details")
                                    )

                                    Text(
                                        text = "GRID HOURS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        HourStepperBox(
                                            label = "Start",
                                            hour = startHour,
                                            onDecrement = { if (startHour > 0) startHour -= 1 },
                                            onIncrement = { if (startHour < endHour - 1) startHour += 1 },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HourStepperBox(
                                            label = "End",
                                            hour = endHour,
                                            onDecrement = { if (endHour > startHour + 1) endHour -= 1 },
                                            onIncrement = { if (endHour < 23) endHour += 1 },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Include Sunday (Mon–Sun)", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = showWeekends,
                                            onCheckedChange = { showWeekends = it },
                                            modifier = Modifier.testTag("settings_weekend_switch")
                                        )
                                    }

                                }
                                SettingsSection.Appearance -> {
                                    Text(
                                        text = "THEME",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            Triple("light", Icons.Filled.LightMode, "Light"),
                                            Triple("dark", Icons.Filled.DarkMode, "Dark"),
                                            Triple("system", Icons.Filled.Contrast, "System")
                                        ).forEach { (mode, icon, label) ->
                                            val selected = themeMode == mode
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onThemeModeChange(mode) },
                                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        icon,
                                                        contentDescription = null,
                                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                                SettingsSection.Import -> {
                                    Text(
                                        text = "GEMINI EXTRACTION KEY",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                    )
                                    OutlinedTextField(
                                        value = geminiKey,
                                        onValueChange = { geminiKey = it },
                                        label = { Text("Gemini API Key (Optional override)") },
                                        singleLine = true,
                                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                                Icon(
                                                    if (showGeminiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                    contentDescription = "Toggle key visibility"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    )

                                }
                                SettingsSection.Updates -> {
                                    Text(
                                        text = "APP UPDATES",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Text(
                                                        text = updateStatusSummary ?: "Source: $repoOwner/$repoName",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        AppPreferences.setUpdateRepository(context, repoOwner, repoName)
                                                        AppPreferences.setUpdateToken(context, repoToken)
                                                        onCheckForUpdates()
                                                    },
                                                    enabled = !isCheckingUpdates,
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                    modifier = Modifier.testTag("check_updates_button")
                                                ) {
                                                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isCheckingUpdates) "Checking…" else "Check")
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Auto-check on launch", style = MaterialTheme.typography.bodySmall)
                                                Switch(
                                                    checked = autoCheckUpdates,
                                                    onCheckedChange = {
                                                        autoCheckUpdates = it
                                                        AppPreferences.setAutoCheckUpdatesEnabled(context, it)
                                                    }
                                                )
                                            }

                                            TextButton(
                                                onClick = { showUpdateSourceConfig = !showUpdateSourceConfig },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(
                                                    if (showUpdateSourceConfig) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Release Repository & Token")
                                            }

                                            AnimatedVisibility(visible = showUpdateSourceConfig) {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = repoOwner,
                                                        onValueChange = { repoOwner = it },
                                                        label = { Text("GitHub Owner") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    OutlinedTextField(
                                                        value = repoName,
                                                        onValueChange = { repoName = it },
                                                        label = { Text("GitHub Repository") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    OutlinedTextField(
                                                        value = repoToken,
                                                        onValueChange = { repoToken = it },
                                                        label = { Text("GitHub Token (only needed for private repos)") },
                                                        singleLine = true,
                                                        visualTransformation = if (showRepoToken) VisualTransformation.None else PasswordVisualTransformation(),
                                                        trailingIcon = {
                                                            IconButton(onClick = { showRepoToken = !showRepoToken }) {
                                                                Icon(
                                                                    if (showRepoToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                                    contentDescription = "Toggle token"
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                }
                                SettingsSection.Data -> {
                                    Text(
                                        text = "DATA MAINTENANCE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { pendingConfirmAction = "reset" },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Load Sample Schedule")
                                        }
                                        OutlinedButton(
                                            onClick = { pendingConfirmAction = "clear" },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Clear All Data")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = planner.ruleStrong)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    ClayPrimaryButton(
                        onClick = {
                            AppPreferences.setCustomGeminiApiKey(context, geminiKey)
                            AppPreferences.setUpdateRepository(context, repoOwner, repoName)
                            AppPreferences.setUpdateToken(context, repoToken)
                            onSave(schoolName, termName, startHour, endHour, showWeekends)
                        },
                        modifier = Modifier.testTag("settings_save_button")
                    ) { Text("Save settings") }
                }
            }
        }
        }
    }
}

@Composable
private fun HourStepperBox(
    label: String,
    hour: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = ScheduleLayoutHelper.formatHourShort(hour),
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
