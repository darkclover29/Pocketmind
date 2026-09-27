package com.pocketshadow.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Shared motion rhythm for the app. Keep transitions quick, calm, and interruptible. */
object PocketShadowMotion {
    const val enterMs = 240
    const val exitMs = 180
    const val microMs = 180
    val pressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
    val enter = tween<Float>(enterMs)
    val exit = tween<Float>(exitMs)
}
