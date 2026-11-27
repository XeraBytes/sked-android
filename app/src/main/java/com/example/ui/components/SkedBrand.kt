package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ScheduleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BrandSpruce = Color(0xFF163560)
private val BrandPine = Color(0xFF1E4E8C)
private val BrandTerracotta = Color(0xFF0D9488)
private val BrandPrussian = Color(0xFF4338CA)
private val BrandPaper = Color(0xFFF8FAFC)
private val BrandRule = Color(0xFFCBD5E1)

/**
 * Geometric Sked folio emblem matching the adaptive launcher icon,
 * supporting staggered reveal progress for the launch intro animation.
 */
@Composable
fun SkedLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tabProgress: Float = 1f,
    block1Progress: Float = 1f,
    block2Progress: Float = 1f,
    block3Progress: Float = 1f
) {
    val planner = ScheduleTheme.colors
    val sheetBorder = if (planner.isDark) planner.ruleStrong else BrandSpruce.copy(alpha = 0.22f)

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Sked logo" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Binding tabs along the top spine
        val tabW = w * 0.095f
        val tabH = h * 0.16f
        val tabDrop = (1f - tabProgress.coerceIn(0f, 1f)) * (h * 0.14f)
        val tabPositions = listOf(0.24f, 0.4525f, 0.665f)
        if (tabProgress > 0f) {
            tabPositions.forEach { xRatio ->
                drawRoundRect(
                    color = BrandTerracotta.copy(alpha = tabProgress.coerceIn(0f, 1f)),
                    topLeft = Offset(w * xRatio, h * 0.05f - tabDrop),
                    size = Size(tabW, tabH),
                    cornerRadius = CornerRadius(w * 0.025f, w * 0.025f)
                )
            }
        }

        // Paper folio body
        val sheetLeft = w * 0.10f
        val sheetTop = h * 0.15f
        val sheetW = w * 0.80f
        val sheetH = h * 0.77f
        val radius = CornerRadius(w * 0.09f, w * 0.09f)

        drawRoundRect(
            color = BrandPaper,
            topLeft = Offset(sheetLeft, sheetTop),
            size = Size(sheetW, sheetH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = sheetBorder,
            topLeft = Offset(sheetLeft, sheetTop),
            size = Size(sheetW, sheetH),
            cornerRadius = radius,
            style = Stroke(width = (w * 0.022f).coerceAtLeast(1f))
        )

        // Deep spruce header band inside folio
        val bandH = sheetH * 0.22f
        drawRoundRect(
            color = BrandSpruce,
            topLeft = Offset(sheetLeft, sheetTop),
            size = Size(sheetW, bandH),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        drawRect(
            color = BrandSpruce,
            topLeft = Offset(sheetLeft, sheetTop + bandH * 0.45f),
            size = Size(sheetW, bandH * 0.55f)
        )

        // Subtle interior ruled dividers
        val gridTop = sheetTop + bandH + sheetH * 0.08f
        val colSplit = sheetLeft + sheetW * 0.50f
        drawLine(
            color = BrandRule.copy(alpha = 0.75f),
            start = Offset(colSplit, gridTop),
            end = Offset(colSplit, sheetTop + sheetH - sheetH * 0.08f),
            strokeWidth = (w * 0.015f).coerceAtLeast(1f)
        )

        val cellRadius = CornerRadius(w * 0.035f, w * 0.035f)
        val leftX = sheetLeft + sheetW * 0.09f
        val rightX = colSplit + sheetW * 0.06f
        val cellW = sheetW * 0.35f

        // Block 1: Left Pine block
        if (block1Progress > 0f) {
            val p1 = block1Progress.coerceIn(0f, 1f)
            drawRoundRect(
                color = BrandPine.copy(alpha = p1),
                topLeft = Offset(leftX, gridTop + sheetH * 0.04f),
                size = Size(cellW * p1, sheetH * 0.24f),
                cornerRadius = cellRadius
            )
        }

        // Block 2: Right Terracotta block
        if (block2Progress > 0f) {
            val p2 = block2Progress.coerceIn(0f, 1f)
            drawRoundRect(
                color = BrandTerracotta.copy(alpha = p2),
                topLeft = Offset(rightX, gridTop + sheetH * 0.04f),
                size = Size(cellW * p2, sheetH * 0.17f),
                cornerRadius = cellRadius
            )
        }

        // Block 3: Lower cells (Prussian & warm paper slate)
        if (block3Progress > 0f) {
            val p3 = block3Progress.coerceIn(0f, 1f)
            drawRoundRect(
                color = BrandRule.copy(alpha = p3),
                topLeft = Offset(leftX, gridTop + sheetH * 0.35f),
                size = Size(cellW * p3, sheetH * 0.18f),
                cornerRadius = cellRadius
            )
            drawRoundRect(
                color = BrandPrussian.copy(alpha = p3),
                topLeft = Offset(rightX, gridTop + sheetH * 0.27f),
                size = Size(cellW * p3, sheetH * 0.26f),
                cornerRadius = cellRadius
            )
        }
    }
}

/**
 * Compact header brand badge anchoring Sked identity in the TopAppBar.
 */
@Composable
fun SkedTopBarBrand(
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planner = ScheduleTheme.colors
    Row(
        modifier = modifier
            .clickable(onClickLabel = "Go to Home", onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkedLogoMark(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sked",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = planner.inkPrimary
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = planner.sageContainer
                ) {
                    Text(
                        text = "FOLIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = planner.sageOnContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = planner.inkSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Full-screen animated editorial folio intro played when opening Sked.
 * Tap anywhere to enter early, or wait ~1.45s for auto-dismissal.
 */
@Composable
fun SkedLaunchIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planner = ScheduleTheme.colors
    var visible by remember { mutableStateOf(true) }

    val folioScale = remember { Animatable(0.82f) }
    val folioAlpha = remember { Animatable(0f) }
    val tabProgress = remember { Animatable(0f) }
    val block1 = remember { Animatable(0f) }
    val block2 = remember { Animatable(0f) }
    val block3 = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetPx = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        launch {
            folioAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
        launch {
            folioScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        delay(120)
        launch {
            tabProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
        delay(110)
        launch {
            block1.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
        delay(90)
        launch {
            block2.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
        delay(90)
        launch {
            block3.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        }
        launch {
            titleAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetPx.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        }
        delay(780)
        visible = false
        delay(260)
        onFinished()
    }

    val ruleColor = planner.ruleFine.copy(alpha = 0.6f)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(260))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(planner.paperBackground)
                .drawBehind {
                    val step = 32.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(ruleColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Skip intro"
                ) {
                    visible = false
                    onFinished()
                }
                .testTag("sked_launch_intro")
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = folioScale.value
                        scaleY = folioScale.value
                        alpha = folioAlpha.value
                    }
                ) {
                    SkedLogoMark(
                        size = 116.dp,
                        tabProgress = tabProgress.value,
                        block1Progress = block1.value,
                        block2Progress = block2.value,
                        block3Progress = block3.value
                    )
                }

                Spacer(Modifier.height(22.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetPx.value
                    }
                ) {
                    Text(
                        text = "ACADEMIC TIMETABLE STUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.0.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = planner.sagePrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sked",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = planner.inkPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.width(64.dp),
                        thickness = 2.dp,
                        color = BrandTerracotta
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Compose your week · Sync CCC e-GURO · Zero overlaps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = planner.inkSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = "Tap anywhere to enter",
                style = MaterialTheme.typography.labelMedium,
                color = planner.inkSecondary.copy(alpha = 0.78f * titleAlpha.value),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }
}
