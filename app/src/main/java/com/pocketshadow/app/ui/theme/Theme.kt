package com.pocketshadow.app.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

// ── Theme mode ────────────────────────────────────────────────────────────────
//
// SYSTEM follows Android's dark/light preference via isSystemInDarkTheme(),
// useful for users who run scheduled bedtime / dark mode.

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
    SYSTEM ("System", 0xFF1F1F2AL),
}

// ── Color schemes ─────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    background           = DarkBg,
    surface              = DarkSurface,
    surfaceContainer     = DarkBubble,
    surfaceContainerHigh = DarkElevated,
    surfaceContainerLow  = DarkCode,
    primary              = ElectricViolet,
    onPrimary            = OnAmber,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = OnAmber,
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
    onPrimary            = OnAmber,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = OnAmber,
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
    onPrimary            = OnAmber,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = OnAmber,
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
    onPrimary            = OnAmber,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = OnAmber,
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
    onPrimary            = OnAmber,
    primaryContainer     = ElectricVioletDim,
    onPrimaryContainer   = OnAmber,
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

/**
 * Resolves [ThemeMode.SYSTEM] to either DARK or LIGHT based on the current
 * Android configuration. Other modes pass through unchanged.
 */
@Composable
fun ThemeMode.resolved(): ThemeMode = when (this) {
    ThemeMode.SYSTEM -> {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
    }
    else -> this
}

@Composable
fun PocketShadowTheme(
    themeMode : ThemeMode = ThemeMode.DARK,
    /** 0.85 = Small, 1.0 = Normal, 1.15 = Large. Scales all sp values. */
    fontScale : Float     = 1f,
    content   : @Composable () -> Unit
) {
    val resolved = themeMode.resolved()
    val isDark = resolved in setOf(ThemeMode.DARK, ThemeMode.NAVY, ThemeMode.FOREST)

    val colorScheme = when (resolved) {
        ThemeMode.DARK   -> DarkColorScheme
        ThemeMode.NAVY   -> NavyColorScheme
        ThemeMode.FOREST -> ForestColorScheme
        ThemeMode.LIGHT  -> LightColorScheme
        ThemeMode.WARM   -> WarmColorScheme
        ThemeMode.SYSTEM -> DarkColorScheme   // unreachable; resolved() above
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

    // Layer the app preference on top of Android's accessibility setting instead
    // of replacing it. A user who chooses a larger system font must keep it.
    val currentDensity = LocalDensity.current
    val scaledDensity  = if (fontScale == 1f) currentDensity
                         else Density(
                             density = currentDensity.density,
                             fontScale = currentDensity.fontScale * fontScale
                         )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = PocketShadowTypography,
            shapes      = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            content     = content
        )
    }
}
