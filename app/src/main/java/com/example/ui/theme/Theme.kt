package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class SchedulePlannerColors(
    val isDark: Boolean,
    val paperBackground: Color,
    val paperSheet: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val ruleFine: Color,
    val ruleStrong: Color,
    val sagePrimary: Color,
    val sageOnPrimary: Color,
    val sageContainer: Color,
    val sageOnContainer: Color,
    val terracotta: Color,
    val onTerracotta: Color,
    val terracottaContainer: Color,
    val onTerracottaContainer: Color,
    val claySurface: Color,
    val clayHighlight: Color,
    val clayShadow: Color,
    val clayInsetTrack: Color
)

private val LightPlannerColors = SchedulePlannerColors(
    isDark = false,
    paperBackground = PaperBackground,
    paperSheet = PaperSurface,
    inkPrimary = PaperOnSurface,
    inkSecondary = PaperOnSurfaceVariant,
    ruleFine = PaperOutlineVariant,
    ruleStrong = PaperOutline,
    sagePrimary = PaperPrimary,
    sageOnPrimary = PaperOnPrimary,
    sageContainer = PaperPrimaryContainer,
    sageOnContainer = PaperOnPrimaryContainer,
    terracotta = PaperSecondary,
    onTerracotta = PaperOnSecondary,
    terracottaContainer = PaperSecondaryContainer,
    onTerracottaContainer = PaperOnSecondaryContainer,
    claySurface = PaperClaySurface,
    clayHighlight = PaperClayHighlight,
    clayShadow = PaperClayShadow,
    clayInsetTrack = PaperClayInsetTrack
)

private val DarkPlannerColors = SchedulePlannerColors(
    isDark = true,
    paperBackground = DeskBackground,
    paperSheet = DeskSurface,
    inkPrimary = DeskOnSurface,
    inkSecondary = DeskOnSurfaceVariant,
    ruleFine = DeskOutlineVariant,
    ruleStrong = DeskOutline,
    sagePrimary = DeskPrimary,
    sageOnPrimary = DeskOnPrimary,
    sageContainer = DeskPrimaryContainer,
    sageOnContainer = DeskOnPrimaryContainer,
    terracotta = DeskSecondary,
    onTerracotta = DeskOnSecondary,
    terracottaContainer = DeskSecondaryContainer,
    onTerracottaContainer = DeskOnSecondaryContainer,
    claySurface = DeskClaySurface,
    clayHighlight = DeskClayHighlight,
    clayShadow = DeskClayShadow,
    clayInsetTrack = DeskClayInsetTrack
)

val LocalScheduleColors = staticCompositionLocalOf { LightPlannerColors }

object ScheduleTheme {
    val colors: SchedulePlannerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScheduleColors.current
}

private val DarkColorScheme =
    darkColorScheme(
        primary = DeskPrimary,
        onPrimary = DeskOnPrimary,
        primaryContainer = DeskPrimaryContainer,
        onPrimaryContainer = DeskOnPrimaryContainer,
        secondary = DeskSecondary,
        onSecondary = DeskOnSecondary,
        secondaryContainer = DeskSecondaryContainer,
        onSecondaryContainer = DeskOnSecondaryContainer,
        tertiary = DeskTertiary,
        tertiaryContainer = DeskTertiaryContainer,
        onTertiaryContainer = DeskOnTertiaryContainer,
        background = DeskBackground,
        surface = DeskSurface,
        surfaceVariant = DeskSurfaceVariant,
        onBackground = DeskOnSurface,
        onSurface = DeskOnSurface,
        onSurfaceVariant = DeskOnSurfaceVariant,
        outline = DeskOutline,
        outlineVariant = DeskOutlineVariant,
        error = DeskError,
        onError = DeskOnError,
        errorContainer = DeskErrorContainer,
        onErrorContainer = DeskOnErrorContainer
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PaperPrimary,
        onPrimary = PaperOnPrimary,
        primaryContainer = PaperPrimaryContainer,
        onPrimaryContainer = PaperOnPrimaryContainer,
        secondary = PaperSecondary,
        onSecondary = PaperOnSecondary,
        secondaryContainer = PaperSecondaryContainer,
        onSecondaryContainer = PaperOnSecondaryContainer,
        tertiary = PaperTertiary,
        tertiaryContainer = PaperTertiaryContainer,
        onTertiaryContainer = PaperOnTertiaryContainer,
        background = PaperBackground,
        surface = PaperSurface,
        surfaceVariant = PaperSurfaceVariant,
        onBackground = PaperOnSurface,
        onSurface = PaperOnSurface,
        onSurfaceVariant = PaperOnSurfaceVariant,
        outline = PaperOutline,
        outlineVariant = PaperOutlineVariant,
        error = PaperError,
        onError = PaperOnError,
        errorContainer = PaperErrorContainer,
        onErrorContainer = PaperOnErrorContainer
    )

@Composable
fun ScheduleMakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val plannerColors = if (darkTheme) DarkPlannerColors else LightPlannerColors

    CompositionLocalProvider(LocalScheduleColors provides plannerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = ScheduleMakerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
