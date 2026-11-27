package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClassBlock
import com.example.data.model.ScheduleSettings
import com.example.ui.components.ClayPrimaryButton
import com.example.ui.components.PaperOutlinedButton
import com.example.ui.components.SkedLogoMark
import com.example.ui.theme.ScheduleTheme
import com.example.util.ScheduleLayoutHelper
import java.util.Calendar
import java.util.Locale

private fun homeDayOfWeekMonFirst(): Int {
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

private fun timeOfDayGreeting(hourOfDay: Int): String = when (hourOfDay) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    classes: List<ClassBlock>,
    settings: ScheduleSettings,
    onOpenScheduleGrid: () -> Unit,
    onOpenDailyAgenda: () -> Unit,
    onOpenEguroSync: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenPool: () -> Unit,
    onAutoArrangePool: () -> Unit,
    onAddClassClick: () -> Unit,
    onClassClick: (ClassBlock) -> Unit,
    onReplayIntro: () -> Unit
) {
    val planner = ScheduleTheme.colors
    var enterVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { enterVisible = true }

    val scheduled = remember(classes) { classes.filter { it.isScheduled } }
    val unscheduled = remember(classes) { classes.filter { !it.isScheduled } }
    val distinctSubjects = remember(classes) {
        classes.distinctBy { it.name.trim().lowercase(Locale.US) }.size
    }
    val totalWeeklyMinutes = remember(scheduled) { scheduled.sumOf { it.durationMinutes } }

    val calendar = remember { Calendar.getInstance() }
    val today = remember { homeDayOfWeekMonFirst() }
    val nowMinutes = remember {
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    val todayClasses = remember(scheduled, today) {
        scheduled
            .filter { it.dayOfWeek == today }
            .sortedWith(compareBy<ClassBlock> { it.startHour * 60 + it.startMinute }.thenBy { it.id })
    }

    val liveNowClass = remember(todayClasses, nowMinutes) {
        todayClasses.firstOrNull { block ->
            val start = block.startHour * 60 + block.startMinute
            nowMinutes in start until (start + block.durationMinutes)
        }
    }
    val nextTodayClass = remember(todayClasses, nowMinutes) {
        todayClasses.firstOrNull { block ->
            (block.startHour * 60 + block.startMinute) > nowMinutes
        }
    }
    val nextWeekClass = remember(scheduled, today) {
        (1..7)
            .map { offset -> ((today - 1 + offset) % 7) + 1 }
            .firstNotNullOfOrNull { day ->
                scheduled
                    .filter { it.dayOfWeek == day }
                    .minByOrNull { it.startHour * 60 + it.startMinute }
            }
    }

    val spotlightClass = liveNowClass ?: nextTodayClass ?: nextWeekClass
    val spotlightLabel = when {
        liveNowClass != null -> "IN SESSION NOW"
        nextTodayClass != null -> "UP NEXT TODAY"
        nextWeekClass != null -> "NEXT ON ${ScheduleLayoutHelper.dayFullName(nextWeekClass.dayOfWeek).uppercase(Locale.US)}"
        else -> "START YOUR FOLIO"
    }

    val pulseTransition = rememberInfiniteTransition(label = "HomeLogoPulse")
    val liveDotAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivePulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(planner.paperBackground)
            .testTag("home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedVisibility(
                visible = enterVisible,
                enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 10 }
            ) {
                // Hero Folio Welcome Card with Sked Logo + Live Spotlight
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_hero_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                    border = BorderStroke(1.dp, planner.ruleStrong)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = planner.clayInsetTrack,
                                border = BorderStroke(1.dp, planner.ruleFine),
                                modifier = Modifier
                                    .clickable(onClickLabel = "Replay Sked intro", onClick = onReplayIntro)
                                    .testTag("home_logo_replay_button")
                            ) {
                                Box(
                                    modifier = Modifier.padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SkedLogoMark(size = 48.dp)
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = timeOfDayGreeting(calendar.get(Calendar.HOUR_OF_DAY)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = planner.sagePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onReplayIntro)
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = planner.inkSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            text = "Intro",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = planner.inkSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = settings.schoolName,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = planner.inkPrimary,
                                    maxLines = 2,
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
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = planner.ruleFine)
                        Spacer(Modifier.height(14.dp))

                        // Summary stats strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeMetricPill(
                                value = distinctSubjects.toString(),
                                label = if (distinctSubjects == 1) "Subject" else "Subjects",
                                modifier = Modifier.weight(1f)
                            )
                            HomeMetricPill(
                                value = ScheduleLayoutHelper.formatDuration(totalWeeklyMinutes).ifBlank { "0m" },
                                label = "Scheduled",
                                modifier = Modifier.weight(1f)
                            )
                            HomeMetricPill(
                                value = unscheduled.size.toString(),
                                label = "In Pool",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenPool
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Up Next / Live class spotlight banner
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (spotlightClass != null) onClassClick(spotlightClass)
                                    else onOpenEguroSync()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = planner.claySurface,
                            border = BorderStroke(1.dp, planner.ruleStrong)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .graphicsLayer { alpha = if (liveNowClass != null) liveDotAlpha else 1f }
                                        .background(
                                            color = if (liveNowClass != null) planner.terracotta else planner.sagePrimary,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = spotlightLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 0.8.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (liveNowClass != null) planner.terracotta else planner.sagePrimary
                                    )
                                    if (spotlightClass != null) {
                                        Text(
                                            text = spotlightClass.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                                            color = planner.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = buildString {
                                            append(
                                                ScheduleLayoutHelper.formatTimeRange(
                                                    spotlightClass.startHour,
                                                    spotlightClass.startMinute,
                                                    spotlightClass.durationMinutes
                                                )
                                            )
                                            if (spotlightClass.room.isNotBlank()) {
                                                append(" · ")
                                                append(spotlightClass.room)
                                            }
                                        }
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = planner.inkSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "Connect CCC e-GURO or add your classes",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = planner.inkPrimary
                                        )
                                        Text(
                                            text = "Tap to auto-detect subjects and build your weekly sheet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = planner.inkSecondary
                                        )
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = planner.inkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Launch Actions
            Text(
                text = "QUICK WORKSPACE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.0.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = planner.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                HomeQuickActionTile(
                    icon = Icons.Filled.CalendarViewWeek,
                    title = "Weekly Sheet",
                    subtitle = "${scheduled.size} placed",
                    accent = planner.sagePrimary,
                    onClick = onOpenScheduleGrid,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_open_weekly_button")
                )
                HomeQuickActionTile(
                    icon = Icons.Filled.School,
                    title = "CCC e-GURO",
                    subtitle = "Sync classes",
                    accent = planner.terracotta,
                    onClick = onOpenEguroSync,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_open_eguro_button")
                )
                HomeQuickActionTile(
                    icon = Icons.Filled.Add,
                    title = "New Class",
                    subtitle = "Manual slot",
                    accent = planner.sagePrimary,
                    onClick = onAddClassClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_add_class_button")
                )
                if (unscheduled.isNotEmpty()) {
                    HomeQuickActionTile(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Auto-Arrange",
                        subtitle = "${unscheduled.size} in pool",
                        accent = planner.terracotta,
                        onClick = onAutoArrangePool,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_auto_arrange_button")
                    )
                } else {
                    HomeQuickActionTile(
                        icon = Icons.Filled.UploadFile,
                        title = "AI Import",
                        subtitle = "PDF or image",
                        accent = Color(0xFF38598B),
                        onClick = onOpenImport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_open_import_button")
                    )
                }
            }

            // Today's classes preview card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_today_preview"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
                border = BorderStroke(1.dp, planner.ruleFine)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${ScheduleLayoutHelper.dayFullName(today)} Agenda",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                                color = planner.inkPrimary
                            )
                            Text(
                                text = if (todayClasses.isEmpty()) "No classes scheduled today"
                                else "${todayClasses.size} ${if (todayClasses.size == 1) "class" else "classes"} on deck",
                                style = MaterialTheme.typography.bodySmall,
                                color = planner.inkSecondary
                            )
                        }
                        PaperOutlinedButton(
                            onClick = onOpenDailyAgenda,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Full Agenda", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (todayClasses.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ClayPrimaryButton(
                                onClick = onOpenScheduleGrid,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text("Browse Week")
                            }
                            if (unscheduled.isNotEmpty()) {
                                PaperOutlinedButton(
                                    onClick = onOpenPool,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text("Place from Pool")
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        todayClasses.take(4).forEachIndexed { idx, block ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = planner.ruleFine,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            val swatchColor = try {
                                Color(android.graphics.Color.parseColor(ScheduleLayoutHelper.normalizeColorHex(block.colorHex)))
                            } catch (_: Exception) {
                                planner.sagePrimary
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onClassClick(block) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(34.dp)
                                        .background(swatchColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = planner.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = planner.inkSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = ScheduleLayoutHelper.formatTimeRange(
                                                    block.startHour,
                                                    block.startMinute,
                                                    block.durationMinutes
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = planner.inkSecondary
                                            )
                                        }
                                        if (block.room.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Outlined.LocationOn,
                                                    contentDescription = null,
                                                    tint = planner.inkSecondary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = block.room,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = planner.inkSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeMetricPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val planner = ScheduleTheme.colors
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        color = planner.clayInsetTrack,
        border = BorderStroke(1.dp, planner.ruleFine)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                ),
                color = planner.inkPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = planner.inkSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeQuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planner = ScheduleTheme.colors
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = planner.paperSheet),
        border = BorderStroke(1.dp, planner.ruleStrong)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = planner.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = planner.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
