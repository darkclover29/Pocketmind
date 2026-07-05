package com.pocketmind.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmind.ui.theme.*

// ── Quick action definitions ──────────────────────────────────────────────────

private data class QuickAction(
    val label    : String,
    val icon     : ImageVector,
    val transform: (currentInput: String) -> String
)

private val QUICK_ACTIONS = listOf(
    QuickAction("Summarize",   Icons.Rounded.Summarize) { input ->
        if (input.isBlank()) "Summarize your previous response in 3 clear bullet points."
        else                 "Summarize the following in 3 clear bullet points:\n\n$input"
    },
    QuickAction("Rewrite",     Icons.Rounded.Edit) { input ->
        if (input.isBlank()) "Rewrite your last response to be clearer and more concise."
        else                 "Rewrite this to be clear and professional:\n\n$input"
    },
    QuickAction("Fix Grammar", Icons.Rounded.Spellcheck) { input ->
        if (input.isBlank()) "Fix the grammar and spelling in your last response."
        else                 "Fix the grammar and spelling in this text:\n\n$input"
    },
    QuickAction("Translate",   Icons.Rounded.Translate) { input ->
        if (input.isBlank()) "Translate your last response to English."
        else                 "Translate this text to English:\n\n$input"
    },
    QuickAction("Explain",     Icons.Rounded.Lightbulb) { input ->
        if (input.isBlank()) "Explain your last response in simple, easy-to-understand terms."
        else                 "Explain this in simple, easy-to-understand terms:\n\n$input"
    },
    QuickAction("Bullets",     Icons.Rounded.FormatListBulleted) { input ->
        if (input.isBlank()) "Convert your last response into a clear bullet-point list."
        else                 "Convert this into a clear bullet-point list:\n\n$input"
    },
    QuickAction("Shorter",     Icons.Rounded.Remove) { input ->
        if (input.isBlank()) "Make your last response shorter while keeping the key points."
        else                 "Make this shorter while keeping the key points:\n\n$input"
    },
    QuickAction("Expand",      Icons.Rounded.Add) { input ->
        if (input.isBlank()) "Expand your last response with more detail and examples."
        else                 "Expand on this with more detail and examples:\n\n$input"
    }
)

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun ChatInputArea(
    value          : String,
    onValueChange  : (String) -> Unit,
    isGenerating   : Boolean,
    onSend         : () -> Unit,
    onStop         : () -> Unit,
    // Document mode
    documentActive : Boolean    = false,
    documentWords  : Int        = 0,
    onDocumentClear: () -> Unit = {},
    onDocumentClick: () -> Unit = {},
    isEmpty        : Boolean    = false,
    modifier       : Modifier   = Modifier
) {
    Surface(
        modifier       = modifier.fillMaxWidth().imePadding(),
        color          = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // ── Quick action chips (only shown when not empty and not generating) ────
            if (!isEmpty && !isGenerating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Paste-document chip (first in row)
                    SuggestionChip(
                        onClick = onDocumentClick,
                        label   = {
                            Text(
                                if (documentActive) "Change doc" else "Paste doc",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        icon    = {
                            Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(14.dp))
                        },
                        colors  = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (documentActive) VioletGlow
                                             else MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor     = if (documentActive) ElectricViolet
                                             else MaterialTheme.colorScheme.onSurfaceVariant,
                            iconContentColor = if (documentActive) ElectricViolet
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border  = SuggestionChipDefaults.suggestionChipBorder(
                            enabled     = true,
                            borderColor = if (documentActive) ElectricViolet.copy(alpha = 0.4f)
                                          else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            borderWidth = if (documentActive) 1.dp else 0.5.dp
                        )
                    )

                    // Divider between doc chip and action chips
                    Box(
                        Modifier
                            .height(20.dp)
                            .width(0.5.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )

                    // Action chips
                    QUICK_ACTIONS.forEach { action ->
                        SuggestionChip(
                            onClick = {
                                val newText = action.transform(value)
                                onValueChange(newText)
                            },
                            label   = {
                                Text(action.label, style = MaterialTheme.typography.labelSmall)
                            },
                            icon    = {
                                Icon(action.icon, null, modifier = Modifier.size(14.dp))
                            },
                            colors  = SuggestionChipDefaults.suggestionChipColors(
                                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border  = SuggestionChipDefaults.suggestionChipBorder(
                                enabled     = true,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                borderWidth = 0.5.dp
                            )
                        )
                    }
                }
            }

            // ── Text field row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hintColor   = MaterialTheme.colorScheme.onSurfaceVariant
                val borderColor = MaterialTheme.colorScheme.outline
                val containerBg = MaterialTheme.colorScheme.surfaceContainer
                val textColor   = MaterialTheme.colorScheme.onSurface

                // Stays enabled while generating so the keyboard isn't dismissed
                // and the user can compose their next message; send is gated below.
                OutlinedTextField(
                    value         = value,
                    onValueChange = onValueChange,
                    placeholder   = {
                        Text(
                            text  = when {
                                isGenerating   -> "Generating…"
                                documentActive -> "Ask about the document ($documentWords words)…"
                                else           -> "Message PocketMind"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(color = hintColor)
                        )
                    },
                    modifier  = Modifier.weight(1f),
                    shape     = RoundedCornerShape(22.dp),
                    maxLines  = 6,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDocumentClick,
                                colors  = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (documentActive) ElectricViolet
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector        = if (documentActive) Icons.Rounded.Article else Icons.Rounded.AttachFile,
                                    contentDescription = "Attach Document",
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                            if (documentActive) {
                                IconButton(
                                    onClick  = onDocumentClear,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector        = Icons.Rounded.Close,
                                        contentDescription = "Clear Document",
                                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier           = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors    = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = ElectricViolet,
                        unfocusedBorderColor    = borderColor,
                        disabledBorderColor     = borderColor.copy(alpha = 0.3f),
                        focusedTextColor        = textColor,
                        unfocusedTextColor      = textColor,
                        disabledTextColor       = hintColor,
                        cursorColor             = ElectricViolet,
                        focusedContainerColor   = containerBg,
                        unfocusedContainerColor = containerBg,
                        disabledContainerColor  = containerBg.copy(alpha = 0.4f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction      = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (value.isNotBlank() && !isGenerating) onSend() }
                    )
                )

                ActionButton(
                    isGenerating = isGenerating,
                    hasText      = value.isNotBlank(),
                    onSend       = onSend,
                    onStop       = onStop
                )
            }
        }
    }
}


// ── Action button (send / stop) ───────────────────────────────────────────────

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
            (scaleIn(initialScale = 0.6f, animationSpec = tween(200)) +
                fadeIn(tween(200))) togetherWith
            (scaleOut(targetScale = 0.6f, animationSpec = tween(150)) +
                fadeOut(tween(150)))
        },
        label = "action_btn"
    ) { generating ->
        if (generating) StopButton(onClick = onStop)
        else            SendButton(enabled = hasText, onClick = onSend)
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
            .background(if (enabled) ElectricViolet else MaterialTheme.colorScheme.outline)
    ) {
        Icon(
            imageVector        = Icons.Rounded.ArrowUpward,
            contentDescription = "Send",
            tint               = if (enabled) White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "stop_pulse")
    val scale by pulse.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "stop_scale"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size((48 * scale).dp).clip(CircleShape).background(StopSurface))
        IconButton(
            onClick  = onClick,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(StopSurface)
        ) {
            Icon(Icons.Rounded.Stop, "Stop",
                tint     = StopRed,
                modifier = Modifier.size(20.dp))
        }
    }
}
