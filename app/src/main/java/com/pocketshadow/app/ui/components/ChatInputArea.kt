package com.pocketshadow.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketshadow.app.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun ChatInputArea(
    /**
     * Composer text as a flow, collected HERE (not at the screen level) so
     * per-keystroke recomposition is scoped to the input area only.
     */
    textFlow       : StateFlow<String>,
    onValueChange  : (String) -> Unit,
    isGenerating   : Boolean,
    onSend         : () -> Unit,
    onStop         : () -> Unit,
    // Document mode
    documentActive : Boolean    = false,
    documentWords  : Int        = 0,
    documentName   : String?    = null,
    onDocumentClear: () -> Unit = {},
    onDocumentClick: () -> Unit = {},
    isEmpty        : Boolean    = false,
    // Input history (MRU) for swipe-up recall
    inputHistory   : List<String> = emptyList(),
    /** Lets the parent focus the composer (e.g. prompt cards, Edit message). */
    focusRequester : FocusRequester? = null,
    // Voice input (dictation)
    voiceAvailable : Boolean    = false,
    isListening    : Boolean    = false,
    onMicClick     : () -> Unit = {},
    modifier       : Modifier   = Modifier
) {
    val value by textFlow.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var historyIndex     by remember { mutableStateOf(-1) }   // -1 = not browsing history
    var dragAccumulator  by remember { mutableFloatStateOf(0f) }
    var composerFocused  by remember { mutableStateOf(false) }
    val composerScale by animateFloatAsState(
        targetValue = if (composerFocused) 1f else 0.995f,
        animationSpec = PocketShadowMotion.pressSpring,
        label = "composer_focus_scale"
    )

    Surface(
        modifier       = modifier.fillMaxWidth().imePadding(),
        color          = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column {
            AnimatedVisibility(
                visible = documentActive,
                enter = fadeIn(tween(PocketShadowMotion.microMs)) + expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                ),
                exit = fadeOut(tween(PocketShadowMotion.exitMs)) + shrinkVertically(
                    animationSpec = tween(PocketShadowMotion.exitMs)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = VioletGlow,
                        onClick = onDocumentClick
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Article, null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
                            Text(
                                "${documentName ?: "Document"} · $documentWords words",
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ElectricViolet,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            IconButton(onClick = onDocumentClear, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, "Remove document", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            // ── Text field row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val hintColor   = MaterialTheme.colorScheme.onSurfaceVariant
                val borderColor = MaterialTheme.colorScheme.outline
                val containerBg = MaterialTheme.colorScheme.surfaceContainer
                val textColor   = MaterialTheme.colorScheme.onSurface

                // Stays enabled while generating so the keyboard isn't dismissed
                // and the user can compose their next message; send is gated below.
                //
                // Swipe-up gesture cycles through input history (MRU).
                OutlinedTextField(
                    value         = value,
                    onValueChange = { newValue ->
                        historyIndex = -1   // reset history browsing on manual edit
                        onValueChange(newValue)
                    },
                    placeholder   = {
                        Text(
                            text  = when {
                                isListening    -> "Listening…"
                                isGenerating   -> "Generating…"
                                documentActive -> "Ask about this document…"
                                else           -> "Message PocketShadow…"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(color = hintColor),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    modifier  = Modifier
                        .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester)
                              else Modifier)
                        .onFocusChanged { composerFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = composerScale
                            scaleY = composerScale
                        }
                        .pointerInput(inputHistory) {
                            if (inputHistory.isEmpty()) return@pointerInput
                            detectVerticalDragGestures(
                                onDragStart = { dragAccumulator = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    // dragAmount is negative when swiping up
                                    dragAccumulator += dragAmount
                                },
                                onDragEnd = {
                                    if (dragAccumulator < -60f) {
                                        // Swipe up — cycle to next history item
                                        val nextIndex = if (historyIndex + 1 >= inputHistory.size) 0
                                                        else historyIndex + 1
                                        historyIndex = nextIndex
                                        onValueChange(inputHistory[nextIndex])
                                    } else if (dragAccumulator > 60f && historyIndex > 0) {
                                        // Swipe down — cycle back
                                        val prevIndex = historyIndex - 1
                                        historyIndex = prevIndex
                                        onValueChange(inputHistory[prevIndex])
                                    }
                                    dragAccumulator = 0f
                                }
                            )
                        },
                    shape     = RoundedCornerShape(20.dp),
                    maxLines  = 6,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(
                                onClick = onDocumentClick,
                                modifier = Modifier.size(48.dp),
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
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector        = Icons.Rounded.Close,
                                        contentDescription = "Clear Document",
                                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier           = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    },
                    trailingIcon = if (voiceAvailable) ({
                        // Mic pulses red while dictating; tap toggles.
                        val micScale = if (isListening) {
                            val pulse = rememberInfiniteTransition(label = "mic_pulse")
                            pulse.animateFloat(
                                initialValue  = 1f,
                                targetValue   = 1.18f,
                                animationSpec = infiniteRepeatable(
                                    tween(500, easing = FastOutSlowInEasing),
                                    RepeatMode.Reverse
                                ),
                                label = "mic_scale"
                            ).value
                        } else 1f
                        IconButton(
                            onClick  = onMicClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.Mic,
                                contentDescription = if (isListening) "Stop voice input"
                                                     else "Start voice input",
                                tint               = if (isListening) StopRed
                                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(20.dp).scale(micScale)
                            )
                        }
                    }) else null,
                    colors    = OutlinedTextFieldDefaults.colors(
                        // Filled-pill look: hairline border only, soft amber on focus —
                        // a full-strength outline reads harsh against OLED black.
                        focusedBorderColor      = ElectricViolet.copy(alpha = if (composerFocused) 0.72f else 0.45f),
                        unfocusedBorderColor    = borderColor.copy(alpha = 0.5f),
                        disabledBorderColor     = borderColor.copy(alpha = 0.25f),
                        focusedTextColor        = textColor,
                        unfocusedTextColor      = textColor,
                        disabledTextColor       = hintColor,
                        cursorColor             = ElectricViolet,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    // Matches the user-bubble gradient; disabled state is a quiet surface, not
    // a full-strength outline blob.
    val bg: Brush = if (enabled) {
        Brush.linearGradient(colors = listOf(AmberBright, ElectricViolet, ElectricVioletDim))
    } else {
        SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
    ) {
        Icon(
            imageVector        = Icons.Rounded.ArrowUpward,
            contentDescription = "Send",
            tint               = if (enabled) OnAmber
                                 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
