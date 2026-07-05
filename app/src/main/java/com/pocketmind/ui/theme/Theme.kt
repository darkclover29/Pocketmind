package com.pocketmind.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

// ── Theme mode ────────────────────────────────────────────────────────────────
//
// SYSTEM is intentionally absent — the app defaults to DARK, and the user
// explicitly picks from the 5 options. The system dark/light preference is
// only used to choose the initial default (see SettingsRepository).

enum class ThemeMode(
    val label: String,
    /** Used to paint the swatch dot in the Settings picker. */
    val swatchColor: Long
) {
    DARK   ("Dark",   0xFF000000L),
    NAVY   ("Navy",   0xFF060C1EL),
    FOREST ("Forest", 0xFF050E08L),
    LIGHT  ("Light",  0xFFF7F7F8L),
    WARM   ("Warm",   0xFFFDF8F0L),
}

// ── Color schemes ─────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    background           = DarkBg,
    surface              = DarkSurface,
    surfaceContainer     = DarkBubble,
    surfaceContainerHigh = DarkElevated,
    surfaceContainerLow  = DarkCode,
    primary              = ElectricViolet,
    onPrimary            = White,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = White,
    secondary            = DarkHint,
    onSecondary          = DarkBg,
    onBackground         = White,
    onSurface            = White,
    onSurfaceVariant     = DarkHint,
    outline              = DarkBorder,
    outlineVariant       = DarkBorder,
    error                = ErrorRed,
    onError              = White,
)

private val NavyColorScheme = darkColorScheme(
    background           = NavyBg,
    surface              = NavySurface,
    surfaceContainer     = NavyBubble,
    surfaceContainerHigh = NavyElevated,
    surfaceContainerLow  = NavyCode,
    primary              = ElectricViolet,
    onPrimary            = White,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = White,
    secondary            = NavyHint,
    onSecondary          = NavyBg,
    onBackground         = White,
    onSurface            = White,
    onSurfaceVariant     = NavyHint,
    outline              = NavyBorder,
    outlineVariant       = NavyBorder,
    error                = ErrorRed,
    onError              = White,
)

private val ForestColorScheme = darkColorScheme(
    background           = ForestBg,
    surface              = ForestSurface,
    surfaceContainer     = ForestBubble,
    surfaceContainerHigh = ForestElevated,
    surfaceContainerLow  = ForestCode,
    primary              = ElectricViolet,
    onPrimary            = White,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = White,
    secondary            = ForestHint,
    onSecondary          = ForestBg,
    onBackground         = White,
    onSurface            = White,
    onSurfaceVariant     = ForestHint,
    outline              = ForestBorder,
    outlineVariant       = ForestBorder,
    error                = ErrorRed,
    onError              = White,
)

private val LightColorScheme = lightColorScheme(
    background           = LightBg,
    surface              = LightSurface,
    surfaceContainer     = LightBubble,
    surfaceContainerHigh = LightElevated,
    surfaceContainerLow  = LightCode,
    primary              = ElectricViolet,
    onPrimary            = White,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = White,
    secondary            = LightHint,
    onSecondary          = White,
    onBackground         = NearBlack,
    onSurface            = NearBlack,
    onSurfaceVariant     = LightHint,
    outline              = LightBorder,
    outlineVariant       = LightBorder,
    error                = ErrorRed,
    onError              = White,
)

private val WarmColorScheme = lightColorScheme(
    background           = WarmBg,
    surface              = WarmSurface,
    surfaceContainer     = WarmBubble,
    surfaceContainerHigh = WarmElevated,
    surfaceContainerLow  = WarmCode,
    primary              = ElectricViolet,
    onPrimary            = White,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = White,
    secondary            = WarmHint,
    onSecondary          = White,
    onBackground         = WarmText,
    onSurface            = WarmText,
    onSurfaceVariant     = WarmHint,
    outline              = WarmBorder,
    outlineVariant       = WarmBorder,
    error                = ErrorRed,
    onError              = White,
)

// ── Theme composable ──────────────────────────────────────────────────────────

@Composable
fun PocketMindTheme(
    themeMode : ThemeMode = ThemeMode.DARK,
    /** 0.85 = Small, 1.0 = Normal, 1.15 = Large. Scales all sp values. */
    fontScale : Float     = 1f,
    content   : @Composable () -> Unit
) {
    val isDark = themeMode in setOf(ThemeMode.DARK, ThemeMode.NAVY, ThemeMode.FOREST)

    val colorScheme = when (themeMode) {
        ThemeMode.DARK   -> DarkColorScheme
        ThemeMode.NAVY   -> NavyColorScheme
        ThemeMode.FOREST -> ForestColorScheme
        ThemeMode.LIGHT  -> LightColorScheme
        ThemeMode.WARM   -> WarmColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgArgb = colorScheme.background.toArgb()
            window.statusBarColor     = bgArgb
            window.navigationBarColor = bgArgb
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    // ✅ Font scaling: override LocalDensity.fontScale so all `sp` values scale
    //    proportionally. This is the same mechanism Android uses for system font size.
    val currentDensity = LocalDensity.current
    val scaledDensity  = if (fontScale == 1f) currentDensity
                         else Density(density = currentDensity.density, fontScale = fontScale)

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = PocketMindTypography,
            content     = content
        )
    }
}
