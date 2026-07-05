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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketmind.ui.theme.*

// ── Input area ────────────────────────────────────────────────────────────────

@Composable
fun ChatInputArea(
    value        : String,
    onValueChange: (String) -> Unit,
    isGenerating : Boolean,
    onSend       : () -> Unit,
    onStop       : () -> Unit,
    modifier     : Modifier = Modifier
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
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Text field ────────────────────────────────────────────────────
            OutlinedTextField(
                value         = value,
                onValueChange = onValueChange,
                enabled       = !isGenerating,
                placeholder   = {
                    Text(
                        text  = if (isGenerating) "Generating response…" else "Message PocketMind",
                        style = MaterialTheme.typography.bodyLarge.copy(color = HintGray)
                    )
                },
                modifier  = Modifier.weight(1f),
                shape     = RoundedCornerShape(24.dp),
                maxLines  = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors    = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor     = ElectricViolet,
                    unfocusedBorderColor   = AiChatBorder,
                    disabledBorderColor    = AiChatBorder.copy(alpha = 0.4f),
                    focusedTextColor       = White,
                    unfocusedTextColor     = White,
                    disabledTextColor      = HintGray,
                    cursorColor            = ElectricViolet,
                    focusedContainerColor  = ElevatedSurface,
                    unfocusedContainerColor= ElevatedSurface,
                    disabledContainerColor = ElevatedSurface.copy(alpha = 0.5f),
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (value.isNotBlank() && !isGenerating) onSend() }
                )
            )

            // ── Dynamic action button ─────────────────────────────────────────
            ActionButton(
                isGenerating = isGenerating,
                hasText      = value.isNotBlank(),
                onSend       = onSend,
                onStop       = onStop
            )
        }
    }
}

// ── Send / Stop button ────────────────────────────────────────────────────────

@Composable
private fun ActionButton(
    isGenerating: Boolean,
    hasText     : Boolean,
    onSend      : () -> Unit,
    onStop      : () -> Unit
) {
    AnimatedContent(
        targetState    = isGenerating,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = tween(200)) + fadeIn(tween(200))) togetherWith
            (scaleOut(targetScale = 0.6f, animationSpec = tween(150)) + fadeOut(tween(150)))
        },
        label = "action_btn"
    ) { generating ->
        if (generating) {
            StopButton(onClick = onStop)
        } else {
            SendButton(enabled = hasText, onClick = onSend)
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) ElectricViolet else AiChatBorder)
    ) {
        Icon(
            imageVector        = Icons.Rounded.ArrowUpward,
            contentDescription = "Send message",
            tint               = if (enabled) White else HintGray,
            modifier           = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    // Pulsing scale animation while engine is running
    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stop_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size((48 * pulseScale).dp)
                .clip(CircleShape)
                .background(StopSurface)
        )
        // Inner stop icon button
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(StopSurface)
        ) {
            Icon(
                imageVector        = Icons.Rounded.Stop,
                contentDescription = "Stop generation",
                tint               = StopRed,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewIdle() {
    PocketMindTheme {
        ChatInputArea(
            value         = "How do transformers work?",
            onValueChange = {},
            isGenerating  = false,
            onSend        = {},
            onStop        = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewGenerating() {
    PocketMindTheme {
        ChatInputArea(
            value         = "",
            onValueChange = {},
            isGenerating  = true,
            onSend        = {},
            onStop        = {}
        )
    }
}
