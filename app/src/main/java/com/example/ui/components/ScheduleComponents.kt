package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.BuildConfig
import com.example.data.model.ClassBlock
import com.example.ui.theme.ScheduleTheme
import com.example.updater.ReleaseInfo
import com.example.util.ScheduleLayoutHelper
import kotlin.math.roundToInt

@Composable
fun ConflictIndicator(
    overlappingClasses: List<ClassBlock>,
    iconSize: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    if (overlappingClasses.isEmpty()) return
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Schedule conflict",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(iconSize)
                .clickable(onClickLabel = "View schedule conflicts") { showPopup = true }
        )

        if (showPopup) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .widthIn(max = 260.dp)
                        .shadow(6.dp, RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Schedule Conflict",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        overlappingClasses.forEach { other ->
                            Text(
                                text = "• ${other.name} (${ScheduleLayoutHelper.formatTimeRange(other.startHour, other.startMinute, other.durationMinutes)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDangerDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(14.dp)
    )
}

internal fun formatChangelogItems(markdown: String): List<String> {
    return markdown
        .lines()
        .mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            if (trimmed.startsWith("#")) return@mapNotNull null
            trimmed.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                .takeIf { it.isNotBlank() }
        }
        .ifEmpty { listOf("Bug fixes, improvements, and updated build.") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateAvailableDialog(
    release: ReleaseInfo,
    latestVersion: String,
    needsInstallPermission: Boolean,
    onInstallClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleasePage: () -> Unit = {}
) {
    val planner = ScheduleTheme.colors
    val hasApk = release.asset != null
    val changelogItems = remember(release.body) { formatChangelogItems(release.body) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("update_available_dialog"),
            shape = RoundedCornerShape(18.dp),
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = planner.sageOnContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update v$latestVersion Available",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            color = planner.inkPrimary
                        )
                        Text(
                            text = "Installed: v${BuildConfig.VERSION_NAME} → Latest: v$latestVersion",
                            style = MaterialTheme.typography.labelMedium,
                            color = planner.sagePrimary
                        )
                    }
                }

                release.asset?.let { asset ->
                    val sizeMb = if (asset.sizeBytes > 0) {
                        String.format("%.1f MB", asset.sizeBytes / (1024f * 1024f))
                    } else {
                        "APK package"
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${asset.name} ($sizeMb)",
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.inkSecondary
                    )
                }

                HorizontalDivider(
                    color = planner.ruleFine,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Text(
                    text = "WHAT'S NEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.0.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = planner.sagePrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    changelogItems.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = planner.sagePrimary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                            ) {}
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = planner.inkPrimary
                            )
                        }
                    }
                }

                if (!hasApk) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = planner.terracottaContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, planner.terracotta.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "This release has no APK file attached yet. Open the GitHub release page or check back later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = planner.onTerracottaContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (needsInstallPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = planner.terracottaContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, planner.terracotta.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Android requires allowing \"Install unknown apps\" before confirming installation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = planner.onTerracottaContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            PaperOutlinedButton(
                                onClick = onGrantPermissionClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("grant_install_permission_button")
                            ) {
                                Text("Allow Installs in Settings", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ClayPrimaryButton(
                    onClick = {
                        when {
                            !hasApk -> onOpenReleasePage()
                            needsInstallPermission -> onGrantPermissionClick()
                            else -> onInstallClick()
                        }
                    },
                    enabled = hasApk || release.htmlUrl.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_now_button")
                ) {
                    Text(
                        text = if (hasApk) "Update now" else "Open on GitHub",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasApk && release.htmlUrl.isNotBlank()) {
                        PaperOutlinedButton(
                            onClick = onOpenReleasePage,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("open_release_page_button")
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GitHub", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    PaperOutlinedButton(
                        onClick = onSkipVersion,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("skip_version_button")
                    ) {
                        Text("Don't show again", style = MaterialTheme.typography.labelLarge)
                    }
                    PaperOutlinedButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text("Later", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsNewDialog(
    versionName: String,
    notes: List<String>,
    onDontShowAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val planner = ScheduleTheme.colors
    val items = remember(notes) {
        notes.ifEmpty { listOf("Updated build with layout improvements and stability fixes.") }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("whats_new_dialog"),
            shape = RoundedCornerShape(18.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkedLogoMark(size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WHAT'S NEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = planner.sagePrimary
                        )
                        Text(
                            text = "Sked v$versionName",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                            color = planner.inkPrimary
                        )
                    }
                    Surface(
                        color = planner.sageContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = planner.sageOnContainer,
                            modifier = Modifier
                                .padding(7.dp)
                                .size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = planner.ruleFine,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { note ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = planner.terracotta,
                                shape = CircleShape,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                            ) {}
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = planner.inkPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperOutlinedButton(
                        onClick = onDontShowAgain,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("whats_new_dont_show_again_button")
                    ) {
                        Text("Don't show again", style = MaterialTheme.typography.labelLarge)
                    }
                    ClayPrimaryButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("whats_new_close_button")
                    ) {
                        Text("Got it", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateErrorDialog(
    message: String,
    hasReleaseUrl: Boolean,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    val planner = ScheduleTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = planner.paperSheet,
        modifier = Modifier.testTag("update_error_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = planner.terracotta)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Could Not Complete", color = planner.inkPrimary)
            }
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = planner.inkPrimary
            )
        },
        confirmButton = {
            if (hasReleaseUrl) {
                ClayPrimaryButton(
                    onClick = onOpenReleasePage,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open Releases", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                ClayPrimaryButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (hasReleaseUrl) {
                PaperOutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun UpdateDownloadDialog(
    versionName: String,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val planner = ScheduleTheme.colors
    val fraction = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null

    AlertDialog(
        onDismissRequest = {},
        containerColor = planner.paperSheet,
        title = {
            Text(
                text = "Downloading v$versionName",
                style = MaterialTheme.typography.titleMedium,
                color = planner.inkPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = planner.sagePrimary,
                        trackColor = planner.clayInsetTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val pct = (fraction * 100f).roundToInt().coerceIn(0, 100)
                    val downloadedMb = downloadedBytes / (1024f * 1024f)
                    val totalMb = totalBytes / (1024f * 1024f)
                    Text(
                        text = String.format("%d%% (%.1f / %.1f MB)", pct, downloadedMb, totalMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.inkSecondary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    LinearProgressIndicator(
                        color = planner.sagePrimary,
                        trackColor = planner.clayInsetTrack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Connecting to GitHub Release…",
                        style = MaterialTheme.typography.bodySmall,
                        color = planner.inkSecondary
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
