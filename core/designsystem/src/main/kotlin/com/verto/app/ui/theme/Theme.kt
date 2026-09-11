package com.verto.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.verto.app.utils.AppFontSize
import com.verto.app.utils.ThemeMode

private val VertoDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = DarkVertoColors.onPrimary,
    primaryContainer = AccentDim,
    onPrimaryContainer = DarkVertoColors.onPrimary,
    secondary = AccentBlue,
    onSecondary = DarkVertoColors.onSecondary,
    secondaryContainer = DarkVertoColors.infoContainer,
    onSecondaryContainer = DarkVertoColors.onInfoContainer,
    background = DarkVertoColors.bgDeep,
    onBackground = DarkVertoColors.textPrimary,
    surface = DarkVertoColors.bgSurface,
    onSurface = DarkVertoColors.textPrimary,
    surfaceVariant = DarkVertoColors.bgCardAlt,
    onSurfaceVariant = DarkVertoColors.textSecondary,
    outline = DarkVertoColors.borderColor,
    error = DarkVertoColors.danger,
    onError = DarkVertoColors.onDanger,
    errorContainer = DarkVertoColors.dangerContainer,
    onErrorContainer = DarkVertoColors.onDangerContainer,
)

private val VertoLightColorScheme = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = LightVertoColors.onPrimary,
    primaryContainer = AccentLight,
    onPrimaryContainer = LightVertoColors.textPrimary,
    secondary = AccentBlue,
    onSecondary = LightVertoColors.onSecondary,
    secondaryContainer = LightVertoColors.infoContainer,
    onSecondaryContainer = LightVertoColors.onInfoContainer,
    background = LightVertoColors.bgDeep,
    onBackground = LightVertoColors.textPrimary,
    surface = LightVertoColors.bgSurface,
    onSurface = LightVertoColors.textPrimary,
    surfaceVariant = LightVertoColors.bgCardAlt,
    onSurfaceVariant = LightVertoColors.textSecondary,
    outline = LightVertoColors.borderColor,
    error = LightVertoColors.danger,
    onError = LightVertoColors.onDanger,
    errorContainer = LightVertoColors.dangerContainer,
    onErrorContainer = LightVertoColors.onDangerContainer,
)

val VertoShapes = Shapes(
    extraSmall = RoundedCornerShape(VertoRadius.xs),
    small = RoundedCornerShape(VertoRadius.sm),
    medium = RoundedCornerShape(VertoRadius.md),
    large = RoundedCornerShape(VertoRadius.lg),
    extraLarge = RoundedCornerShape(VertoRadius.xxl),
)

// Kept for source compatibility.
val Shapes = VertoShapes

@Composable
fun VertoTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    appFontSize: AppFontSize = AppFontSize.MEDIUM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    val fontScaleMultiplier = when (appFontSize) {
        AppFontSize.SMALL -> 0.875f
        AppFontSize.MEDIUM -> 1f
        AppFontSize.LARGE -> 1.125f
    }

    val vertoColors = if (useDark) DarkVertoColors else LightVertoColors
    val colorScheme = if (useDark) VertoDarkColorScheme else VertoLightColorScheme
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalVertoColors provides vertoColors,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScaleMultiplier,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VertoTypography,
            shapes = VertoShapes,
            content = content,
        )
    }
}
