package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ScheduleTheme

/**
 * Raised soft-clay primary action button with sage fill, tonal top/bottom bevel,
 * modest elevation that settles on press, visible focus indicator, and >= 48 dp touch target.
 */
@Composable
fun ClayPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ScheduleTheme.colors.sagePrimary,
    contentColor: Color = ScheduleTheme.colors.sageOnPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val planner = ScheduleTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isPressed -> 1.dp
            else -> 4.dp
        },
        animationSpec = tween(90),
        label = "clayElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.985f else 1f,
        animationSpec = tween(90),
        label = "clayScale"
    )

    val effectiveBg = if (enabled) {
        containerColor
    } else {
        planner.clayInsetTrack
    }
    val effectiveFg = if (enabled) {
        contentColor
    } else {
        planner.inkSecondary
    }
    val borderColor = when {
        isFocused -> planner.terracotta
        !enabled -> planner.ruleStrong
        isPressed -> containerColor.copy(alpha = 0.85f)
        else -> planner.clayHighlight.copy(alpha = if (planner.isDark) 0.25f else 0.35f)
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = shape,
        color = effectiveBg,
        contentColor = effectiveFg,
        shadowElevation = elevation,
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor)
    ) {
        Box(
            propagateMinConstraints = true,
            modifier = Modifier.drawWithContent {
                drawContent()
                if (enabled && !isPressed) {
                    drawLine(
                        color = Color.White.copy(alpha = if (planner.isDark) 0.16f else 0.24f),
                        start = Offset(12.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 12.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalContentColor provides effectiveFg) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

/**
 * Flatter outlined control for secondary actions (choosing files, saving reference photos,
 * cancel/later controls) with >= 48 dp touch target and clear focus outline.
 */
@Composable
fun PaperOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val planner = ScheduleTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isActive -> planner.sageContainer
        else -> planner.paperSheet
    }
    val fg = when {
        !enabled -> planner.inkSecondary.copy(alpha = 0.6f)
        isActive -> planner.sageOnContainer
        else -> contentColor
    }
    val stroke = when {
        isFocused -> planner.terracotta
        borderColor != null -> borderColor
        isActive -> planner.sagePrimary
        else -> planner.ruleStrong
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = shape,
        color = bg,
        contentColor = fg,
        shadowElevation = 0.dp,
        border = BorderStroke(if (isFocused || isActive) 1.5.dp else 1.dp, stroke)
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Soft-clay selectable pill: subtle raised edge when idle, inset appearance with
 * bold fill/border and selection semantics when selected.
 */
@Composable
fun ClaySelectableSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    shape: Shape = RoundedCornerShape(14.dp),
    role: Role = Role.Tab,
    content: @Composable () -> Unit
) {
    val planner = ScheduleTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val elevation by animateDpAsState(
        targetValue = if (selected || isPressed) 0.dp else 2.dp,
        animationSpec = tween(100),
        label = "clayChipElevation"
    )

    val bgColor = when {
        selected -> planner.sagePrimary
        isHighlighted -> planner.sageContainer.copy(alpha = 0.65f)
        else -> planner.claySurface
    }
    val borderColor = when {
        isFocused -> planner.terracotta
        selected -> planner.sagePrimary
        isHighlighted -> planner.sagePrimary.copy(alpha = 0.55f)
        else -> planner.ruleStrong.copy(alpha = 0.7f)
    }

    val insetBrush = if (selected) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = if (planner.isDark) 0.28f else 0.16f),
                Color.Transparent,
                Color.White.copy(alpha = if (planner.isDark) 0.12f else 0.18f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                planner.clayHighlight.copy(alpha = if (planner.isDark) 0.35f else 0.85f),
                Color.Transparent,
                planner.clayShadow.copy(alpha = if (planner.isDark) 0.45f else 0.35f)
            )
        )
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(bgColor, shape)
            .border(
                width = if (isFocused || selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = insetBrush,
                    style = Stroke(width = 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(14.dp.toPx())
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = role,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
