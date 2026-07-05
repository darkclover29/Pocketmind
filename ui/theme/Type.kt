package com.pocketmind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Swap FontFamily.Default for a bundled Inter/Roboto via downloadable fonts if desired
val SansFontFamily = FontFamily.Default
val CodeFontFamily = FontFamily.Monospace

val PocketMindTypography = Typography(
    // App bar title
    titleMedium = TextStyle(
        fontFamily  = SansFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 17.sp,
        lineHeight  = 24.sp,
        color       = White
    ),
    // Chat bubble body
    bodyLarge = TextStyle(
        fontFamily  = SansFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,
        color       = White
    ),
    // Timestamps, hints
    bodySmall = TextStyle(
        fontFamily  = SansFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 11.sp,
        lineHeight  = 15.sp,
        color       = HintGray
    ),
    // Code block body
    labelSmall = TextStyle(
        fontFamily  = CodeFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.5.sp,
        lineHeight  = 19.sp,
        color       = CodeText
    ),
    // Language tag in code block header
    labelMedium = TextStyle(
        fontFamily  = CodeFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        color       = HintGray
    )
)
