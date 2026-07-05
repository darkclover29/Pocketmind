package com.pocketmind.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pocketmind.ui.theme.*

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
fun MessageInputBar(
    value       : String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend      : () -> Unit,
    onStop      : () -> Unit,
    modifier    : Modifier = Modifier
) {
    Surface(
        modifier      = modifier.fillMaxWidth(),
        color         = TrueBlack,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Text field ────────────────────────────────────────────────────
            OutlinedTextField(
                value        = value,
                onValueChange = onValueChange,
                enabled      = !isGenerating,
                placeholder  = {
                    Text(
                        text  = if (isGenerating) "Generating…" else "Ask PocketMind…",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MutedGray)
                    )
                },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(24.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = NeonViolet,
                    unfocusedBorderColor = CharcoalLight,
                    disabledBorderColor  = CharcoalMid,
                    focusedTextColor     = OffWhite,
                    unfocusedTextColor   = OffWhite,
                    disabledTextColor    = MutedGray,
                    cursorColor          = NeonViolet,
                    focusedContainerColor   = Charcoal,
                    unfocusedContainerColor = Charcoal,
                    disabledContainerColor  = CharcoalMid,
                ),
                textStyle    = MaterialTheme.typography.bodyLarge,
                maxLines     = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (value.isNotBlank() && !isGenerating) onSend() }
                )
            )

            // ── Send / Stop button ────────────────────────────────────────────
            ActionButton(
                isGenerating = isGenerating,
                canSend      = value.isNotBlank(),
                onSend       = onSend,
                onStop       = onStop
            )
        }
    }
}

// ── Animated action button ────────────────────────────────────────────────────

@Composable
private fun ActionButton(
    isGenerating: Boolean,
    canSend     : Boolean,
    onSend      : () -> Unit,
    onStop      : () -> Unit
) {
    // Pulse animation while generating
    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.12f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    AnimatedContent(
        targetState    = isGenerating,
        transitionSpec = {
            (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
            (scaleOut(targetScale = 0.7f) + fadeOut())
        },
        label = "action_btn_transition"
    ) { generating ->
        if (generating) {
            // Stop button
            IconButton(
                onClick  = onStop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StopRedContainer)
                    // subtle scale pulse
                    .then(
                        Modifier // can't use graphicsLayer inside AnimatedContent easily,
                                 // so we rely on the container colour pulse instead
                    )
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Stop,
                    contentDescription = "Stop generation",
                    tint               = StopRed,
                    modifier           = Modifier.size(22.dp)
                )
            }
        } else {
            // Send button
            IconButton(
                onClick  = { if (canSend) onSend() },
                enabled  = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) NeonViolet else CharcoalLight
                    )
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Send,
                    contentDescription = "Send message",
                    tint               = if (canSend) TrueBlack else MutedGray,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}
