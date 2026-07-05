package com.pocketmind.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PocketMindColorScheme = darkColorScheme(
    // ── Core canvas ───────────────────────────────────────────────────────────
    background              = TrueBlack,
    surface                 = TrueBlack,
    surfaceContainer        = AiChatSurface,        // AI bubbles
    surfaceContainerHigh    = ElevatedSurface,       // Input bar

    // ── Brand accent ─────────────────────────────────────────────────────────
    primary                 = ElectricViolet,        // User bubbles, FABs
    onPrimary               = White,
    primaryContainer        = ElectricVioletDim,
    onPrimaryContainer      = White,

    // ── Secondary (engine chip borders, tags) ─────────────────────────────────
    secondary               = HintGray,
    onSecondary             = TrueBlack,

    // ── Text ─────────────────────────────────────────────────────────────────
    onBackground            = White,
    onSurface               = White,
    onSurfaceVariant        = HintGray,

    // ── Error ─────────────────────────────────────────────────────────────────
    error                   = ErrorRed,
    onError                 = White,
)

@Composable
fun PocketMindTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = TrueBlack.toArgb()
            window.navigationBarColor = TrueBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = PocketMindColorScheme,
        typography  = PocketMindTypography,
        content     = content
    )
}
