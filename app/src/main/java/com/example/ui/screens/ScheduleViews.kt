package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.data.model.getOverlappingClasses
import com.example.ui.components.ClayPrimaryButton
import com.example.ui.components.ClaySelectableSurface
import com.example.ui.components.ConflictIndicator
import com.example.ui.components.PaperOutlinedButton
import com.example.ui.theme.ScheduleTheme
import com.example.util.LanePlacement
import com.example.util.ScheduleLayoutHelper
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

private fun currentDayOfWeekMonFirst(): Int {
    return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}

@Composable
fun MainScheduleContainer(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    activePlacementBlock: ClassBlock?,
    gridStyle: String,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    onClassClick: (ClassBlock) -> Unit,
    onGridClassClick: (ClassBlock) -> Unit = onClassClick,
    onStartPlacement: (ClassBlock) -> Unit,
    onCancelPlacement: () -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onUnscheduleBlock: (ClassBlock) -> Unit,
    onAddClassClick: () -> Unit,
    onChooseFromPoolClick: () -> Unit = {}
) {
    val planner = ScheduleTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(planner.paperBackground)
    ) {
        // Soft clay segmented mode switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(planner.paperBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                color = planner.clayInsetTrack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, planner.ruleStrong.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf("timeline" to "Daily Agenda", "grid" to "Weekly Sheet")
                    for ((mode, label) in modes) {
                        val isSelected = viewMode == mode
                        ClaySelectableSurface(
                            selected = isSelected,
                            onClick = { onViewModeChange(mode) },
                            shape = RoundedCornerShape(12.dp),
                            role = Role.Tab,
                            modifier = Modifier.testTag("view_mode_$mode")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) planner.sageOnPrimary else planner.inkPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = planner.ruleFine)

        if (viewMode == "timeline") {
            DailyTimelineView(
                classes = classes,
                settings = settings,
                activePlacementBlock = activePlacementBlock,
                onClassClick = onClassClick,
                onCancelPlacement = onCancelPlacement,
                onPlaceBlock = onPlaceBlock,
                onUnscheduleBlock = onUnscheduleBlock,
                onAddClassClick = onAddClassClick,
                onChooseFromPoolClick = onChooseFromPoolClick
            )
        } else {
            WeeklyGridView(
                classes = classes,
                settings = settings,
                activePlacementBlock = activePlacementBlock,
                gridStyle = gridStyle,
                onClassClick = onGridClassClick,
                onStartPlacement = onStartPlacement,
                onCancelPlacement = onCancelPlacement,
                onPlaceBlock = onPlaceBlock,
                onUnscheduleBlock = onUnscheduleBlock
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyTimelineView(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    activePlacementBlock: ClassBlock?,
    onClassClick: (ClassBlock) -> Unit,
    onCancelPlacement: () -> Unit = {},
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onUnscheduleBlock: (ClassBlock) -> Unit,
    onAddClassClick: () -> Unit,
    onChooseFromPoolClick: () -> Unit = {},
    initialSelectedDay: Int? = null
) {
    val planner = ScheduleTheme.colors

    var selectedDay by rememberSaveable {
        val initial = initialSelectedDay ?: run {
            val today = currentDayOfWeekMonFirst()
            if (!settings.showWeekends && today > 6) 1 else today
        }
        mutableIntStateOf(initial)
    }

    LaunchedEffect(settings.showWeekends) {
        if (!settings.showWeekends && selectedDay > 6) {
            selectedDay = 1
        }
    }

    // Refresh "current minute" once per 30s so active-session badge stays accurate
    var currentTimeMinutes by remember { mutableIntStateOf(0) }
    var todayMonFirst by remember { mutableIntStateOf(currentDayOfWeekMonFirst()) }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            todayMonFirst = currentDayOfWeekMonFirst()
            currentTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            delay(30_000L)
        }
    }

    val scheduledClasses = remember(classes) { classes.filter { it.isScheduled } }
    val poolCount = remember(classes) { classes.count { !it.isScheduled } }
    val daysRange = if (settings.showWeekends) 1..7 else 1..6
    val dayClassesSorted = remember(scheduledClasses, selectedDay) {
        scheduledClasses
            .filter { it.dayOfWeek == selectedDay }
            .sortedWith(
                compareBy<ClassBlock> { it.startHour * 60 + it.startMinute }
                    .thenBy { it.durationMinutes }
                    .thenBy { it.id }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(planner.paperBackground)
            .testTag("daily_timeline_view")
    ) {
        // Soft clay day selector ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(planner.paperBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (day in daysRange) {
                val count = scheduledClasses.count { it.dayOfWeek == day }
                DaySelectorChip(
                    day = day,
                    isSelected = selectedDay == day,
                    isToday = day == todayMonFirst,
                    classesCount = count,
                    onClick = { selectedDay = day },
                    modifier = Modifier.testTag("day_chip_$day")
                )
            }
        }

        // Day metadata summary strip on flat paper header
        val totalMinutes = dayClassesSorted.sumOf { it.durationMinutes }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(planner.paperSheet)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ScheduleLayoutHelper.dayFullName(selectedDay).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = planner.inkSecondary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (dayClassesSorted.isEmpty()) "Open Day"
                else "${dayClassesSorted.size} ${if (dayClassesSorted.size == 1) "class" else "classes"} · ${ScheduleLayoutHelper.formatDuration(totalMinutes)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = planner.sagePrimary
            )
        }
        HorizontalDivider(color = planner.ruleFine)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(planner.paperSheet)
        ) {
            if (dayClassesSorted.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            top = 24.dp,
                            end = 24.dp,
                            bottom = if (activePlacementBlock != null) 220.dp else 96.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.EventNote,
                        contentDescription = null,
                        tint = planner.inkSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Classes on ${ScheduleLayoutHelper.dayFullName(selectedDay)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = planner.inkPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (poolCount > 0) {
                            "Add a new course or choose from $poolCount ${if (poolCount == 1) "course" else "courses"} waiting in your pool."
                        } else {
                            "Add a course or place one from your pool."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = planner.inkSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ClayPrimaryButton(
                            onClick = onAddClassClick,
                            modifier = Modifier.testTag("add_class_empty_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Class", style = MaterialTheme.typography.labelLarge)
                        }
                        if (poolCount > 0) {
                            PaperOutlinedButton(
                                onClick = onChooseFromPoolClick,
                                modifier = Modifier.testTag("choose_from_pool_empty_button")
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose from Pool", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 14.dp,
                        end = 16.dp,
                        bottom = if (activePlacementBlock != null) 220.dp else 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayClassesSorted, key = { it.id }) { cls ->
                        val overlapping = cls.getOverlappingClasses(scheduledClasses)
                        val startTotal = cls.startHour * 60 + cls.startMinute
                        val endTotal = startTotal + cls.durationMinutes
                        val isNow = selectedDay == todayMonFirst && currentTimeMinutes in startTotal until endTotal

                        TimelineClassCard(
                            classBlock = cls,
                            overlappingClasses = overlapping,
                            isNowInSession = isNow,
                            onClick = { onClassClick(cls) },
                            onUnschedule = { onUnscheduleBlock(cls) }
                        )
                    }
                }
            }

            // Soft-clay Placement Quick Slot Tray
            if (activePlacementBlock != null) {
                val safeStart = settings.startHour.coerceIn(0, 22)
                val safeEnd = settings.endHour.coerceIn(safeStart + 1, 23)
                val blockColor = remember(activePlacementBlock.colorHex) {
                    parseColorSafe(activePlacementBlock.colorHex, planner.sagePrimary)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .testTag("timeline_placement_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = planner.claySurface,
                    border = BorderStroke(1.5.dp, planner.sagePrimary),
                    shadowElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(blockColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = activePlacementBlock.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = planner.inkPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${ScheduleLayoutHelper.formatDuration(activePlacementBlock.durationMinutes)} · ${ScheduleLayoutHelper.dayFullName(selectedDay)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = planner.sagePrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            PaperOutlinedButton(
                                onClick = onCancelPlacement,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("cancel_placement_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Choose start time on ${ScheduleLayoutHelper.dayFullName(selectedDay)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = planner.inkSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (h in safeStart until safeEnd) {
                                val hourLabel = ScheduleLayoutHelper.formatHourShort(h)
                                PaperOutlinedButton(
                                    onClick = { onPlaceBlock(activePlacementBlock, selectedDay, h) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .testTag("place_slot_${selectedDay}_$h")
                                        .semantics {
                                            contentDescription = "Select start time $hourLabel on ${ScheduleLayoutHelper.dayFullName(selectedDay)}"
                                        }
                                ) {
                                    Text(
                                        text = hourLabel,
                                        style = MaterialTheme.typography.labelLarge
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

@Composable
private fun DaySelectorChip(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    classesCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planner = ScheduleTheme.colors
    val fullName = ScheduleLayoutHelper.dayFullName(day)
    val shortName = ScheduleLayoutHelper.dayShortName(day)
    val countDescription = if (classesCount == 0) "no classes"
    else "$classesCount ${if (classesCount == 1) "class" else "classes"}"
    val todayDescription = if (isToday) ", today" else ""

    ClaySelectableSurface(
        selected = isSelected,
        isHighlighted = isToday,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        role = Role.Tab,
        modifier = modifier
            .widthIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "$fullName$todayDescription, $countDescription"
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = shortName,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) planner.sageOnPrimary else planner.inkPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .background(planner.sageOnPrimary, RoundedCornerShape(2.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (classesCount > 0) planner.terracotta else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineClassCard(
    classBlock: ClassBlock,
    overlappingClasses: List<ClassBlock> = emptyList(),
    isNowInSession: Boolean = false,
    onClick: () -> Unit,
    onUnschedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planner = ScheduleTheme.colors
    val cardColor = remember(classBlock.colorHex) {
        parseColorSafe(classBlock.colorHex, Color(0xFF2E5A4F))
    }
    val isOverlapping = overlappingClasses.isNotEmpty()
    val timeRangeText = remember(classBlock.startHour, classBlock.startMinute, classBlock.durationMinutes) {
        ScheduleLayoutHelper.formatTimeRange(
            classBlock.startHour,
            classBlock.startMinute,
            classBlock.durationMinutes
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit ${classBlock.name}", onClick = onClick)
            .testTag("timeline_card_${classBlock.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverlapping) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            } else {
                planner.paperSheet
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isOverlapping || isNowInSession) 1.5.dp else 1.dp,
            color = when {
                isOverlapping -> MaterialTheme.colorScheme.error
                isNowInSession -> planner.terracotta
                else -> planner.ruleFine
            }
        ),
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
                    .width(6.dp)
                    .background(if (isOverlapping) MaterialTheme.colorScheme.error else cardColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // 1. Course name first in reading & TalkBack traversal order
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = classBlock.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else planner.inkPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    ConflictIndicator(overlappingClasses = overlappingClasses, iconSize = 18.dp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Time range & duration immediately second
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else planner.inkSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeRangeText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else planner.inkSecondary
                        )
                    }
                    Surface(
                        color = planner.clayInsetTrack,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = ScheduleLayoutHelper.formatDuration(classBlock.durationMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = planner.inkSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isNowInSession) {
                        Surface(
                            color = planner.terracottaContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "IN SESSION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = planner.onTerracottaContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 3. Room and instructor when present
                if (classBlock.room.isNotBlank() || classBlock.professor.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (classBlock.room.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = planner.inkSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = classBlock.room,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = planner.inkSecondary
                                )
                            }
                        }
                        if (classBlock.professor.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = planner.inkSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = classBlock.professor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = planner.inkSecondary
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit ${classBlock.name}",
                        tint = planner.sagePrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onUnschedule) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "Move ${classBlock.name} to unscheduled pool",
                        tint = planner.inkSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

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
    val planner = ScheduleTheme.colors
    var activePlacementDay by remember { mutableStateOf<Int?>(null) }
    var activePlacementHour by remember { mutableStateOf<Int?>(null) }
    var draggingBlockId by remember { mutableStateOf<Int?>(null) }

    val safeStartHour = settings.startHour.coerceIn(0, 22)
    val safeEndHour = settings.endHour.coerceIn(safeStartHour + 1, 23)
    val numHours = safeEndHour - safeStartHour
    val rowHeightDp = 84.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val timeGutterWidth = if (LocalDensity.current.fontScale > 1.3f) 60.dp else 44.dp

    val scheduledClasses = remember(classes) { classes.filter { it.isScheduled } }
    val numDays = if (settings.showWeekends) 7 else 6

    val laneByDay = remember(scheduledClasses, numDays) {
        (1..numDays).associateWith { day ->
            ScheduleLayoutHelper.computeDayLanes(scheduledClasses.filter { it.dayOfWeek == day })
        }
    }

    var anchorDay by rememberSaveable { mutableIntStateOf(1) }
    BoxWithConstraints(Modifier.fillMaxSize().background(planner.paperSheet).testTag("weekly_grid_view")) {
        val minDayWidth = 132.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val capacity = ((maxWidth - timeGutterWidth) / minDayWidth).toInt().coerceIn(1, numDays)
        val firstDay = 1 + (anchorDay.coerceIn(1, numDays) - 1) / capacity * capacity
        val visibleDays = firstDay..minOf(numDays, firstDay + capacity - 1)
        Column {
            if (capacity < numDays) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val range = if (visibleDays.first == visibleDays.last) {
                        ScheduleLayoutHelper.dayFullName(firstDay)
                    } else {
                        "${ScheduleLayoutHelper.dayShortName(firstDay)} — ${ScheduleLayoutHelper.dayShortName(visibleDays.last)}"
                    }
                    Text(range, Modifier.weight(1f), color = planner.inkPrimary,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif))
                    IconButton(onClick = { anchorDay = firstDay - capacity }, enabled = firstDay > 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous days")
                    }
                    IconButton(onClick = { anchorDay = visibleDays.last + 1 }, enabled = visibleDays.last < numDays) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next days")
                    }
                }
            }
            // Day Column Headers on flat paper sheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(planner.paperBackground)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(timeGutterWidth))
                for (day in visibleDays) {
                    Text(
                        text = ScheduleLayoutHelper.dayShortName(day).uppercase(),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = ScheduleLayoutHelper.dayFullName(day) },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = planner.inkPrimary
                    )
                }
            }
            HorizontalDivider(color = planner.ruleStrong.copy(alpha = 0.6f))

            // Scrollable Sheet Body
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(planner.paperSheet)
                    .verticalScroll(rememberScrollState())
            ) {
                val density = LocalDensity.current
                val totalSheetWidthPx = with(density) { maxWidth.toPx() }
                val gutterPx = with(density) { timeGutterWidth.toPx() }
                val dayColumnWidthPx = ((totalSheetWidthPx - gutterPx) / visibleDays.count()).coerceAtLeast(1f)

                // Hour Rows & Drop Targets (flat paper sheet, fine rules, zero cell shadows)
                Column {
                    for (h in 0 until numHours) {
                        val currentHour = safeStartHour + h
                        val hourLabel = ScheduleLayoutHelper.formatHourShort(currentHour)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightDp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(timeGutterWidth)
                                    .height(rowHeightDp)
                                    .padding(top = 4.dp, end = 6.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = hourLabel,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = planner.inkSecondary
                                )
                            }

                            Row(modifier = Modifier.weight(1f)) {
                                for (day in visibleDays) {
                                    val placement = activePlacementBlock
                                    val isPlacementActive = placement != null
                                    val isHovered = isPlacementActive &&
                                        activePlacementDay == day &&
                                        activePlacementHour == currentHour

                                    val cellBg by animateColorAsState(
                                        targetValue = when {
                                            isHovered -> planner.sageContainer
                                            isPlacementActive -> planner.sageContainer.copy(alpha = 0.3f)
                                            else -> planner.paperSheet
                                        },
                                        animationSpec = tween(120),
                                        label = "cellBg"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(rowHeightDp)
                                            .background(cellBg)
                                            .border(
                                                width = if (isHovered) 1.5.dp else 0.5.dp,
                                                color = if (isHovered) planner.sagePrimary else planner.ruleFine
                                            )
                                            .then(
                                                if (placement != null) {
                                                    Modifier
                                                        .testTag("grid_slot_${day}_$currentHour")
                                                        .clickable {
                                                            onPlaceBlock(placement, day, currentHour)
                                                            activePlacementDay = null
                                                            activePlacementHour = null
                                                        }
                                                        .semantics {
                                                            contentDescription = "Place ${placement.name} on ${ScheduleLayoutHelper.dayFullName(day)} at $hourLabel"
                                                        }
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPlacementActive) {
                                            Icon(
                                                imageVector = if (isHovered) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                                                contentDescription = null,
                                                tint = if (isHovered) planner.sagePrimary
                                                else planner.sagePrimary.copy(alpha = 0.55f),
                                                modifier = Modifier.size(if (isHovered) 24.dp else 18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Positioned Course Cards Overlay (flat blocks with narrow course-color strips & side-by-side overlap lanes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = timeGutterWidth)
                ) {
                    for (day in visibleDays) {
                        val dayClasses = scheduledClasses.filter { it.dayOfWeek == day }
                        val dayLanes = laneByDay[day].orEmpty()
                        val columnBeingDragged = dayClasses.any { it.id == draggingBlockId }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeightDp * numHours)
                                .zIndex(if (columnBeingDragged) 20f else 1f)
                        ) {
                            val colWidthDp = maxWidth
                            for (cls in dayClasses) {
                                val startFraction = cls.startHour + cls.startMinute / 60f
                                val endFraction = startFraction + cls.durationMinutes.coerceAtLeast(15) / 60f
                                if (endFraction <= safeStartHour || startFraction >= safeEndHour) continue

                                val clampedStart = maxOf(startFraction, safeStartHour.toFloat())
                                val clampedEnd = minOf(endFraction, safeEndHour.toFloat())
                                val offsetY = ((clampedStart - safeStartHour) * rowHeightDp.value).dp
                                val cardHeight = ((clampedEnd - clampedStart) * rowHeightDp.value).dp.coerceAtLeast(28.dp)

                                val lane = dayLanes[cls.id] ?: LanePlacement(0, 1)
                                val laneWidth = colWidthDp / lane.totalLanes
                                val laneOffsetX = laneWidth * lane.laneIndex

                                val overlapping = cls.getOverlappingClasses(scheduledClasses)

                                ClassGridCard(
                                    classBlock = cls,
                                    overlappingClasses = overlapping,
                                    lanePlacement = lane,
                                    gridStyle = gridStyle,
                                    visibleDays = visibleDays,
                                    safeStartHour = safeStartHour,
                                    safeEndHour = safeEndHour,
                                    dayColumnWidthPx = dayColumnWidthPx,
                                    rowHeightDp = rowHeightDp,
                                    modifier = Modifier
                                        .offset(x = laneOffsetX, y = offsetY)
                                        .width(laneWidth)
                                        .height(cardHeight)
                                        .padding(
                                            horizontal = if (lane.totalLanes > 1) 1.dp else 1.5.dp,
                                            vertical = 1.dp
                                        ),
                                    onClick = { onClassClick(cls) },
                                    onMoveRequest = {
                                        onStartPlacement(cls)
                                        activePlacementDay = cls.dayOfWeek
                                        activePlacementHour = cls.startHour.coerceIn(safeStartHour, safeEndHour - 1)
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
                                    onDragStateChange = { isDragging ->
                                        draggingBlockId = if (isDragging) cls.id else null
                                        if (!isDragging) {
                                            activePlacementDay = null
                                            activePlacementHour = null
                                        }
                                    },
                                    onDragTargetChange = { targetDay, targetHour ->
                                        activePlacementDay = targetDay
                                        activePlacementHour = targetHour
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

@Composable
fun ClassGridCard(
    classBlock: ClassBlock,
    overlappingClasses: List<ClassBlock> = emptyList(),
    lanePlacement: LanePlacement = LanePlacement(0, 1),
    gridStyle: String = "classic",
    visibleDays: IntRange = 1..6,
    safeStartHour: Int = 8,
    safeEndHour: Int = 18,
    dayColumnWidthPx: Float = 160f,
    rowHeightDp: Dp = 76.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoveRequest: () -> Unit,
    onUnscheduleRequest: () -> Unit,
    onPlaceBlock: (ClassBlock, Int, Int) -> Unit,
    onCancelPlacement: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onDragTargetChange: (Int, Int) -> Unit
) {
    val planner = ScheduleTheme.colors
    val cardColor = remember(classBlock.colorHex) {
        parseColorSafe(classBlock.colorHex, Color(0xFF2E5A4F))
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeightDp.toPx() }

    var isDragging by remember(classBlock.id) { mutableStateOf(false) }
    var dragOffset by remember(classBlock.id) { mutableStateOf(Offset.Zero) }
    val isOverlapping = overlappingClasses.isNotEmpty()
    val isNarrowLane = lanePlacement.totalLanes > 1
    val isShortBlock = classBlock.durationMinutes <= 45

    val maxStartHour = (safeEndHour - 1).coerceAtLeast(safeStartHour)

    val dragGestureModifier = Modifier.pointerInput(
        classBlock.id,
        classBlock.dayOfWeek,
        classBlock.startHour,
        classBlock.startMinute,
        visibleDays,
        safeStartHour,
        safeEndHour,
        dayColumnWidthPx
    ) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                isDragging = true
                dragOffset = Offset.Zero
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDragStateChange(true)
                onMoveRequest()
                onDragTargetChange(
                    classBlock.dayOfWeek.coerceIn(visibleDays),
                    classBlock.startHour.coerceIn(safeStartHour, maxStartHour)
                )
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                val deltaDays = (dragOffset.x / dayColumnWidthPx).roundToInt()
                val deltaHours = (dragOffset.y / rowHeightPx).roundToInt()
                val targetDay = (classBlock.dayOfWeek + deltaDays).coerceIn(visibleDays)
                val targetHour = (classBlock.startHour + deltaHours).coerceIn(safeStartHour, maxStartHour)
                onDragTargetChange(targetDay, targetHour)
            },
            onDragEnd = {
                isDragging = false
                val deltaDays = (dragOffset.x / dayColumnWidthPx).roundToInt()
                val deltaHours = (dragOffset.y / rowHeightPx).roundToInt()
                val targetDay = (classBlock.dayOfWeek + deltaDays).coerceIn(visibleDays)
                val targetHour = (classBlock.startHour + deltaHours).coerceIn(safeStartHour, maxStartHour)
                dragOffset = Offset.Zero
                onDragStateChange(false)
                onPlaceBlock(classBlock, targetDay, targetHour)
            },
            onDragCancel = {
                isDragging = false
                dragOffset = Offset.Zero
                onDragStateChange(false)
                onCancelPlacement()
            }
        )
    }

    // Flat paper-friendly surfaces so adjacent overlap lanes stay distinct without shadows
    val containerColor = when {
        isOverlapping -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (planner.isDark) 0.72f else 0.78f)
        gridStyle == "minimal" -> planner.claySurface
        else -> cardColor.copy(alpha = if (planner.isDark) 0.24f else 0.13f)
    }
    val borderColor = when {
        isOverlapping -> MaterialTheme.colorScheme.error
        gridStyle == "minimal" -> planner.ruleStrong
        else -> cardColor.copy(alpha = if (planner.isDark) 0.65f else 0.45f)
    }
    val primaryTextColor = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer else planner.inkPrimary
    val secondaryTextColor = if (isOverlapping) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else planner.inkSecondary

    val timeRange = remember(classBlock.startHour, classBlock.startMinute, classBlock.durationMinutes) {
        ScheduleLayoutHelper.formatTimeRange(
            classBlock.startHour,
            classBlock.startMinute,
            classBlock.durationMinutes
        )
    }
    val accessibleDesc = buildString {
        append(classBlock.name)
        append(", ")
        append(ScheduleLayoutHelper.dayFullName(classBlock.dayOfWeek))
        append(", ")
        append(timeRange)
        if (classBlock.room.isNotBlank()) append(", ${classBlock.room}")
        if (classBlock.professor.isNotBlank()) append(", ${classBlock.professor}")
        if (isOverlapping) append(", conflicts with ${overlappingClasses.joinToString { it.name }}")
    }

    Box(modifier = modifier.zIndex(if (isDragging) 30f else 1f)) {
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = cardColor.copy(alpha = 0.55f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            ),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .defaultMinSize(minHeight = 28.dp)
                .then(dragGestureModifier)
                .graphicsLayer {
                    scaleX = if (isDragging) 1.04f else 1f
                    scaleY = if (isDragging) 1.04f else 1f
                    alpha = if (isDragging) 0.92f else 1f
                    translationX = if (isDragging) dragOffset.x else 0f
                    translationY = if (isDragging) dragOffset.y else 0f
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDesc
                    onClick(label = "View details and class actions") {
                        onClick()
                        true
                    }
                }
                .clickable(onClickLabel = "View details and class actions", onClick = onClick)
                .testTag("class_card_${classBlock.id}"),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(if (isOverlapping || isDragging) 1.5.dp else 1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Narrow course-color strip (keeps user-assigned color even on conflicts via inner stripe or border)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isNarrowLane) 3.dp else 4.dp)
                        .background(cardColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (isNarrowLane) 3.dp else 5.dp,
                            top = if (isShortBlock || isNarrowLane) 2.dp else 4.dp,
                            end = if (isNarrowLane) 2.dp else 4.dp,
                            bottom = 2.dp
                        )
                ) {
                    Text(
                        text = classBlock.name,
                        style = if (isNarrowLane || isShortBlock) {
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                letterSpacing = 0.sp
                            )
                        } else {
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            )
                        },
                        color = primaryTextColor,
                        maxLines = if (isShortBlock) 2 else if (isNarrowLane) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!isNarrowLane && !isShortBlock) {
                        if (classBlock.room.isNotBlank()) {
                            Text(
                                text = classBlock.room,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (classBlock.professor.isNotBlank() && classBlock.durationMinutes >= 75) {
                            Text(
                                text = classBlock.professor,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = secondaryTextColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (isOverlapping) {
                        Text(
                            text = "Conflict",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun parseColorSafe(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(hex)))
} catch (_: Exception) {
    fallback
}
