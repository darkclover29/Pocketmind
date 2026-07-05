package com.pocketmind.ui.theme

import androidx.compose.ui.graphics.Color

// ── OLED Foundation ───────────────────────────────────────────────────────────
val TrueBlack           = Color(0xFF000000)   // background / surface (OLED saver)
val AiChatSurface       = Color(0xFF121212)   // AI bubble background
val AiChatBorder        = Color(0xFF2A2A2A)   // AI bubble 1dp border
val CodeSurface         = Color(0xFF1E1E1E)   // Code block background
val ElevatedSurface     = Color(0xFF1A1A1A)   // Input bar / dialogs

// ── Accent ────────────────────────────────────────────────────────────────────
val ElectricViolet      = Color(0xFF8A2BE2)   // User bubble / primary
val ElectricVioletDim   = Color(0xFF6A1DB0)   // Pressed state
val VioletGlow          = Color(0x338A2BE2)   // Pill bg for Local model indicator

// ── Text ──────────────────────────────────────────────────────────────────────
val White               = Color.White
val HintGray            = Color(0xFFAAAAAA)   // Timestamps, placeholders
val CodeText            = Color(0xFFD4D4D4)   // VS Code-style code text
val InlineCodeAccent    = Color(0xFFCE9178)   // Inline code highlight (warm amber)

// ── Engine Status ─────────────────────────────────────────────────────────────
val AiCoreGreen         = Color(0xFF00E676)   // AICore active dot
val AiCoreGreenGlow     = Color(0x3300E676)   // AICore pill background
val LocalViolet         = ElectricViolet      // Local model active dot
val ErrorRed            = Color(0xFFCF6679)

// ── Stop Button ───────────────────────────────────────────────────────────────
val StopRed             = Color(0xFFEF5350)
val StopSurface         = Color(0xFF3B1111)
