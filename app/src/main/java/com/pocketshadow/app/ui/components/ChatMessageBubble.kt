package com.pocketshadow.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.rounded.AutoAwesome
import com.pocketshadow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Domain model ──────────────────────────────────────────────────────────────

enum class Role { USER, AI }

/**
 * @Immutable tells the Compose compiler this class never changes after creation.
 * This allows the compiler to skip stability checks and avoid unnecessary
 * recomposition of composables that receive a [Message] as a parameter.
 */
@Immutable
data class Message(
    val id         : String  = java.util.UUID.randomUUID().toString(),
    val role       : Role,
    val content    : String,
    val isStreaming : Boolean = false,
    /** Epoch millis, nullable so legacy callers compile without it. */
    val timestamp  : Long?   = null,
    /** AI-only: generation stats, e.g. "12.4 tok/s · 256 tokens · first token 0.8s". */
    val statsLine  : String? = null
)

// ── Bubble ────────────────────────────────────────────────────────────────────

private val CORNER_FULL  = 22.dp
private val CORNER_SHARP = 8.dp

private val DATE_FMT = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
fun GlowingAiAvatar(modifier: Modifier = Modifier, animated: Boolean = false) {
    // Pulse ONLY while streaming: an infinite animation on every visible
    // avatar keeps the whole list recomposing at 60 fps while the app idles
    // (battery drain + jank), and ignores reduced-motion preferences.
    val scale = if (animated) {
        val infiniteTransition = rememberInfiniteTransition(label = "avatar_glow")
        infiniteTransition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        ).value
    } else 1f

    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glowing background ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricViolet.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Inner core with gradient
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricViolet, ElectricVioletDim)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message       : Message,
    modifier      : Modifier      = Modifier,
    hapticEnabled : Boolean       = true,
    onRetry       : (() -> Unit)? = null,
    onRegenerate  : (() -> Unit)? = null,   // AI-only: re-run generation for the previous turn
    onReadAloud   : (() -> Unit)? = null,   // null for user bubbles
    onQuickAction : ((String) -> Unit)? = null,
    /** Show the visible copy/regenerate/read-aloud row (newest AI reply only). */
    showActions   : Boolean       = false,
    /** USER-only: long-press → Edit puts the text back in the composer. */
    onEdit        : ((String) -> Unit)? = null
) {
    val isUser           = message.role == Role.USER
    val clipboardManager = LocalClipboardManager.current
    val haptic           = LocalHapticFeedback.current
    var showContextMenu  by remember { mutableStateOf(false) }
    var showCopiedHint   by remember { mutableStateOf(false) }
    // Timestamps/stats are noise 99% of the time — hidden until the bubble is tapped.
    var showMeta         by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedHint) {
        if (showCopiedHint) { kotlinx.coroutines.delay(1500); showCopiedHint = false }
    }

    val shape = RoundedCornerShape(
        topStart    = CORNER_FULL,
        topEnd      = CORNER_FULL,
        bottomStart = if (isUser) CORNER_FULL else CORNER_SHARP,
        bottomEnd   = if (isUser) CORNER_SHARP else CORNER_FULL
    )

    val bubbleBg     = if (isUser) MaterialTheme.colorScheme.primary
                       else        MaterialTheme.colorScheme.surfaceContainer
    // Deep warm brown on amber — white-on-amber fails contrast and looks washed out.
    val textColor    = if (isUser) OnAmber
                       else        MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    val bubbleBrush = if (isUser) {
        // Diagonal highlight→base gradient reads as depth without extra elevation
        Brush.linearGradient(colors = listOf(AmberBright, ElectricViolet, ElectricVioletDim))
    } else {
        SolidColor(bubbleBg)
    }

    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom
    ) {
        // ── AI avatar (logo mark in a tinted circle) ──────────────────────────
        if (!isUser) {
            GlowingAiAvatar(animated = message.isStreaming)
            Spacer(Modifier.width(6.dp))
        }

        // Bubble max width adapts to the screen: a hard 300.dp cap leaves a
        // skinny ribbon on tablets / landscape / foldables.
        val maxBubbleWidth =
            (LocalConfiguration.current.screenWidthDp * 0.85f).dp.coerceAtLeast(300.dp)

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        // Outer Box is the DropdownMenu anchor
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    // Hairline at half-alpha: separates the bubble from an OLED-black
                    // background without the boxed-in look a full 1dp border gives.
                    .then(if (!isUser) Modifier.border(
                        0.5.dp, outlineColor.copy(alpha = 0.6f), shape) else Modifier)
                    .clip(shape)
                    .background(bubbleBrush)
                    .combinedClickable(
                        onClick     = { showMeta = !showMeta },
                        onLongClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            showContextMenu = true
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Column {
                    if (isUser) {
                        Text(
                            text  = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                        )
                    } else {
                        MarkdownBody(
                            raw         = message.content,
                            isStreaming  = message.isStreaming
                        )
                    }

                    // ── Footer: generation stats (AI) + timestamp — tap to reveal ──
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showMeta &&
                                  (message.timestamp != null || message.statsLine != null),
                        enter   = androidx.compose.animation.fadeIn(tween(150)) +
                                  androidx.compose.animation.expandVertically(tween(150)),
                        exit    = androidx.compose.animation.fadeOut(tween(120)) +
                                  androidx.compose.animation.shrinkVertically(tween(120)),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Row(
                            // wrap-content so short bubbles don't stretch to max width
                            modifier              = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            if (!isUser && message.statsLine != null) {
                                Text(
                                    text  = "⚡ ${message.statsLine}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            message.timestamp?.let { ts ->
                                Text(
                                    text  = DATE_FMT.format(Date(ts)),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color    = if (isUser) textColor.copy(alpha = 0.7f)
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                } // Column
            } // inner Box

            // Context menu (long-press)
            DropdownMenu(
                expanded         = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                DropdownMenuItem(
                    text         = { Text("Copy message", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon  = {
                        Icon(Icons.Rounded.ContentCopy, null,
                            modifier = Modifier.size(18.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        showContextMenu = false
                        showCopiedHint  = true
                    }
                )
                if (isUser && onEdit != null) {
                    DropdownMenuItem(
                        text        = { Text("Edit message", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        onClick = {
                            showContextMenu = false
                            onEdit(message.content)
                        }
                    )
                }
                if (!isUser && onRegenerate != null && !message.isStreaming) {
                    DropdownMenuItem(
                        text        = { Text("Regenerate response", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Refresh, null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        onClick = {
                            showContextMenu = false
                            onRegenerate()
                        }
                    )
                }
                if (onReadAloud != null) {
                    DropdownMenuItem(
                        text        = { Text("Read aloud", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Rounded.VolumeUp, null,
                                modifier = Modifier.size(18.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        onClick = {
                            showContextMenu = false
                            onReadAloud()
                        }
                    )
                }
                if (!isUser && onQuickAction != null && !message.isStreaming) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text        = { Text("Summarize message", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Rounded.Summarize, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick     = {
                            showContextMenu = false
                            onQuickAction("Summarize this message in 3 clear bullet points:\n\n${message.content}")
                        }
                    )
                    DropdownMenuItem(
                        text        = { Text("Explain message", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick     = {
                            showContextMenu = false
                            onQuickAction("Explain this message in simple, easy-to-understand terms:\n\n${message.content}")
                        }
                    )
                    DropdownMenuItem(
                        text        = { Text("Rewrite message", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick     = {
                            showContextMenu = false
                            onQuickAction("Rewrite this message to be clear and professional:\n\n${message.content}")
                        }
                    )
                    DropdownMenuItem(
                        text        = { Text("Translate to English", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Rounded.Translate, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick     = {
                            showContextMenu = false
                            onQuickAction("Translate this message to English:\n\n${message.content}")
                        }
                    )
                }
            }
        } // outer Box

        // ── Visible action row (newest AI reply only) ─────────────────────
        // Long-press menus have zero discoverability; surface the three most
        // used actions directly under the latest response.
        if (!isUser && showActions && !message.isStreaming) {
            Row(
                modifier              = Modifier.padding(start = 2.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                BubbleActionIcon(Icons.Rounded.ContentCopy, "Copy response") {
                    clipboardManager.setText(AnnotatedString(message.content))
                    showCopiedHint = true
                }
                if (onRegenerate != null) {
                    BubbleActionIcon(Icons.Rounded.Refresh, "Regenerate response", onRegenerate)
                }
                if (onReadAloud != null) {
                    BubbleActionIcon(Icons.Rounded.VolumeUp, "Read aloud", onReadAloud)
                }
            }
        }
        } // wrapper Column

        // Floating "Copied" chip
        if (showCopiedHint) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, outlineColor, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text  = "Copied",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

// ── Compact action icon under the newest AI reply ─────────────────────────────

@Composable
private fun BubbleActionIcon(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            modifier           = Modifier.size(17.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Markdown renderer ─────────────────────────────────────────────────────────
//
// Handles:
//   ```lang\n…\n```    fenced code blocks → CodeBlock (syntax highlighted)
//   | header |         markdown tables → TableBlock
//   | ---- |
//   | cell  |
//   # / ## / ###       headings
//   - item / * item    bullet lists
//   1. item            numbered lists
//   **bold**           inline bold
//   *italic*           inline italic
//   `inline code`      inline monospace highlight

@Composable
fun MarkdownBody(raw: String, isStreaming: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!isStreaming) {
            val segments = remember(raw) { parseSegments(raw) }
            segments.forEach { seg ->
                when (seg) {
                    is Segment.Code      -> CodeBlock(seg.code, seg.language)
                    is Segment.Paragraph -> ParagraphContent(seg.text)
                    is Segment.Table     -> TableBlock(seg.headers, seg.rows)
                }
            }
        } else {
            // ── Incremental streaming render ──────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "caret_blink")
            val caretAlpha by infiniteTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = 0f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "caret"
            )
            val caretColor = ElectricViolet.copy(alpha = caretAlpha)

            val split = remember(raw) { splitStreamingMarkdown(raw) }
            val head  = split.first
            val tail  = split.second

            val trimmedTail = tail.trim()
            val isTailEmpty = trimmedTail.isEmpty()

            if (head.isNotEmpty()) {
                val headSegments = remember(head) { parseSegments(head) }
                headSegments.forEachIndexed { index, seg ->
                    val isLastSegment = (index == headSegments.size - 1)
                    val segmentCaretColor = if (isTailEmpty && isLastSegment) caretColor else null
                    when (seg) {
                        is Segment.Code      -> CodeBlock(seg.code, seg.language)
                        is Segment.Paragraph -> ParagraphContent(seg.text, segmentCaretColor)
                        is Segment.Table     -> TableBlock(seg.headers, seg.rows)
                    }
                }
            }

            if (!isTailEmpty) {
                if (trimmedTail.startsWith("```")) {
                    val newline = trimmedTail.indexOf('\n')
                    val body    = if (newline > 0) trimmedTail.substring(newline + 1) else ""
                    StreamingCodeTail(body, caretColor)
                } else {
                    ParagraphContent(trimmedTail, caretColor)
                }
            } else if (head.isEmpty()) {
                ParagraphContent("", caretColor)
            }
        }
    }
}

// ── Markdown table renderer ───────────────────────────────────────────────────

@Composable
private fun TableBlock(headers: List<String>, rows: List<List<String>>) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val headerBg    = MaterialTheme.colorScheme.surfaceContainerHigh
    val cellBg      = MaterialTheme.colorScheme.surfaceContainerLow
    val textColor   = MaterialTheme.colorScheme.onSurface
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth().background(headerBg)) {
            for (c in 0 until colCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(0.5.dp, borderColor)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    InlineText(
                        text       = headers.getOrNull(c) ?: "",
                        extraStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
                        baseColor  = headerColor
                    )
                }
            }
        }
        // Data rows
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().background(cellBg)) {
                for (c in 0 until colCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        InlineText(
                            text      = row.getOrNull(c) ?: "",
                            baseColor = textColor
                        )
                    }
                }
            }
        }
    }
}

// ── Paragraph content with heading + list support ─────────────────────────────

@Composable
private fun ParagraphContent(text: String, caretColor: Color? = null) {
    val textColor         = MaterialTheme.colorScheme.onSurface
    val lines             = text.split('\n')
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val isLastLine = (i == lines.size - 1)
            val lineCaretColor = if (isLastLine) caretColor else null
            when {
                line.startsWith("# ")   -> {
                    val rawText = line.removePrefix("# ")
                    val annotated = if (lineCaretColor != null) {
                        buildAnnotatedString {
                            append(rawText)
                            withStyle(SpanStyle(color = lineCaretColor)) {
                                append("▍")
                            }
                        }
                    } else {
                        AnnotatedString(rawText)
                    }
                    Text(
                        text  = annotated,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = textColor
                        )
                    )
                    i++
                }
                line.startsWith("## ")  -> {
                    val rawText = line.removePrefix("## ")
                    val annotated = if (lineCaretColor != null) {
                        buildAnnotatedString {
                            append(rawText)
                            withStyle(SpanStyle(color = lineCaretColor)) {
                                append("▍")
                            }
                        }
                    } else {
                        AnnotatedString(rawText)
                    }
                    Text(
                        text  = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color      = textColor
                        )
                    )
                    i++
                }
                line.startsWith("### ") -> {
                    InlineText(
                        text       = line.removePrefix("### "),
                        extraStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
                        baseColor  = textColor,
                        caretColor = lineCaretColor
                    )
                    i++
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size &&
                        (lines[i].startsWith("- ") || lines[i].startsWith("* "))) {
                        items += lines[i].removePrefix("- ").removePrefix("* ")
                        i++
                    }
                    val isLastSection = (i == lines.size)
                    BulletList(items, textColor, if (isLastSection) caretColor else null)
                }
                line.matches(ORDERED_ITEM_REGEX) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].matches(ORDERED_ITEM_REGEX)) {
                        items += lines[i].replace(ORDERED_PREFIX_REGEX, "")
                        i++
                    }
                    val isLastSection = (i == lines.size)
                    NumberedList(items, textColor, if (isLastSection) caretColor else null)
                }
                line.isBlank() -> {
                    if (lineCaretColor != null) {
                        InlineText("", baseColor = textColor, caretColor = lineCaretColor)
                    } else {
                        Spacer(Modifier.height(2.dp))
                      }
                      i++
                }
                else -> {
                    InlineText(line, baseColor = textColor, caretColor = lineCaretColor)
                    i++
                }
            }
        }
    }
}

// ── List composables ──────────────────────────────────────────────────────────

@Composable
private fun BulletList(items: List<String>, textColor: Color, caretColor: Color? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            val isLastItem = (index == items.size - 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text     = "•",
                    style    = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    modifier = Modifier.padding(start = 4.dp)
                )
                InlineText(
                    text       = item,
                    baseColor  = textColor,
                    caretColor = if (isLastItem) caretColor else null
                )
            }
        }
    }
}

@Composable
private fun NumberedList(items: List<String>, textColor: Color, caretColor: Color? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            val isLastItem = (index == items.size - 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text     = "${index + 1}.",
                    style    = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    modifier = Modifier.padding(start = 4.dp)
                )
                InlineText(
                    text       = item,
                    baseColor  = textColor,
                    caretColor = if (isLastItem) caretColor else null
                )
            }
        }
    }
}

// ── Lightweight in-progress code block (streaming only) ──────────────────────

@Composable
private fun StreamingCodeTail(code: String, caretColor: Color? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        val trimmed = code.trimEnd()
        val textVal = if (caretColor != null) {
            buildAnnotatedString {
                append(trimmed)
                withStyle(SpanStyle(color = caretColor)) {
                    append("▍")
                }
            }
        } else {
            AnnotatedString(trimmed)
        }
        Text(
            text  = textVal,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = MaterialTheme.colorScheme.onSurface,
                fontFamily = CodeFontFamily
            )
        )
    }
}

// ── Code block ────────────────────────────────────────────────────────────────

@Composable
fun CodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied       by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) { kotlinx.coroutines.delay(1500); showCopied = false }
    }

    val codeShape   = RoundedCornerShape(10.dp)
    val codeBg      = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg    = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outline

    // Syntax-highlighted body
    val langId    = SyntaxHighlighter.languageOf(language)
    val annotated = remember(code, langId) { SyntaxHighlighter.highlight(code.trimEnd(), langId) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(codeShape)
            .border(1.dp, borderColor, codeShape)
            .background(codeBg)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            IconButton(
                onClick  = { clipboardManager.setText(AnnotatedString(code.trimEnd())); showCopied = true },
                modifier = Modifier.size(30.dp)
            ) {
                if (showCopied) {
                    Text(
                        text  = "✓",
                        style = MaterialTheme.typography.bodySmall.copy(color = ElectricViolet, fontSize = 11.sp)
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Scrollable body (highlighted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text     = annotated,
                style    = MaterialTheme.typography.labelSmall.copy(
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontFamily = CodeFontFamily
                ),
                softWrap = false
            )
        }
    }
}

// ── Inline markdown text ──────────────────────────────────────────────────────

// Compiled once — InlineText/parseSegments run per streamed token, so compiling
// these regexes inside the composable was measurable overhead during streaming.
private val INLINE_MD_REGEX      = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`""")
private val FENCE_REGEX          = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
private val ORDERED_ITEM_REGEX   = Regex("^\\d+\\.\\s.*")
private val ORDERED_PREFIX_REGEX = Regex("^\\d+\\.\\s")

// ── Markdown table detection ──────────────────────────────────────────────────
private val TABLE_ROW_REGEX = Regex("""^\s*\|.*\|\s*$""")
private val TABLE_SEP_REGEX = Regex("""^\s*\|?[\s:]*[-]{2,}[\s:|-]*\|?\s*$""")

@Composable
fun InlineText(
    text      : String,
    extraStyle: SpanStyle = SpanStyle(),
    baseColor : Color     = MaterialTheme.colorScheme.onSurface,
    caretColor: Color?    = null
) {
    val inlineCodeBg   = MaterialTheme.colorScheme.surfaceContainerHigh
    val inlineCodeText = InlineCodeAccent

    val annotated = remember(text, extraStyle, baseColor, caretColor) {
        buildAnnotatedString {
            withStyle(extraStyle) {
                var cursor = 0

                INLINE_MD_REGEX.findAll(text).forEach { match ->
                    if (match.range.first > cursor)
                        append(text.substring(cursor, match.range.first))

                    val (bold, italic, code) = match.destructured
                    when {
                        bold.isNotEmpty()   -> withStyle(SpanStyle(fontWeight = FontWeight.Bold))   { append(bold)   }
                        italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle  = FontStyle.Italic))  { append(italic) }
                        code.isNotEmpty()   -> withStyle(
                            SpanStyle(
                                fontFamily = CodeFontFamily,
                                background = inlineCodeBg,
                                color      = inlineCodeText,
                                fontSize   = 13.sp
                            )
                        ) { append(" $code ") }
                    }
                    cursor = match.range.last + 1
                }
                if (cursor < text.length) append(text.substring(cursor))

                if (caretColor != null) {
                    withStyle(SpanStyle(color = caretColor)) {
                        append("▍")
                    }
                }
            }
        }
    }
    Text(
        text  = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = baseColor)
    )
}

// ── Segment parser ────────────────────────────────────────────────────────────

sealed class Segment {
    data class Code(val language: String, val code: String) : Segment()
    data class Paragraph(val text: String)                  : Segment()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Segment()
}

/**
 * Splits streaming text into (stable head, live tail) at the last blank line.
 * If that split would land inside an unclosed code fence, the split moves back
 * to the fence opening so the whole open block stays in the live tail.
 * Cheap: a couple of linear scans, no regex, no allocation beyond substrings.
 */
internal fun splitStreamingMarkdown(raw: String): Pair<String, String> {
    var split = raw.lastIndexOf("\n\n")
    if (split <= 0) return "" to raw

    // Count fences in the head; odd count = split is inside an open code block
    val head = raw.substring(0, split)
    var count = 0
    var lastFence = -1
    var idx = head.indexOf("```")
    while (idx >= 0) {
        count++
        lastFence = idx
        idx = head.indexOf("```", idx + 3)
    }
    if (count % 2 == 1) split = lastFence
    if (split <= 0) return "" to raw

    return raw.substring(0, split) to raw.substring(split)
}

internal fun parseSegments(raw: String): List<Segment> {
    val result = mutableListOf<Segment>()
    val lines  = raw.split('\n')
    var i      = 0

    while (i < lines.size) {
        val line = lines[i]

        // ── Fenced code block ──────────────────────────────────────────────
        if (line.startsWith("```")) {
            val language = line.removePrefix("```").trim()
            val codeEnd  = (i + 1 until lines.size).firstOrNull { lines[it].startsWith("```") }
            if (codeEnd != null) {
                val code = lines.subList(i + 1, codeEnd).joinToString("\n")
                result += Segment.Code(language, code)
                i = codeEnd + 1
                continue
            }
            // Unclosed fence — treat rest as code tail
            val code = lines.subList(i + 1, lines.size).joinToString("\n")
            result += Segment.Code(language, code)
            break
        }

        // ── Markdown table ─────────────────────────────────────────────────
        if (TABLE_ROW_REGEX.containsMatchIn(line) && i + 1 < lines.size && TABLE_SEP_REGEX.containsMatchIn(lines[i + 1])) {
            val tableLines = mutableListOf(line)
            var j = i + 1
            tableLines += lines[j]; j++  // separator
            while (j < lines.size && TABLE_ROW_REGEX.containsMatchIn(lines[j])) {
                tableLines += lines[j]; j++
            }
            val parsed = parseTable(tableLines)
            if (parsed != null) {
                result += Segment.Table(parsed.first, parsed.second)
                i = j
                continue
            }
        }

        // ── Paragraph (collect consecutive non-empty, non-special lines) ──
        val para = StringBuilder()
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("```")) break
            if (TABLE_ROW_REGEX.containsMatchIn(l) && i + 1 < lines.size && TABLE_SEP_REGEX.containsMatchIn(lines[i + 1])) break
            if (para.isNotEmpty()) para.append('\n')
            para.append(l)
            i++
        }
        val trimmed = para.toString().trim()
        if (trimmed.isNotEmpty()) result += Segment.Paragraph(trimmed)
    }

    return result.ifEmpty { listOf(Segment.Paragraph(raw)) }
}

private fun parseTable(tableLines: List<String>): Pair<List<String>, List<List<String>>>? {
    if (tableLines.size < 2) return null
    val header = splitTableRow(tableLines[0]) ?: return null
    val rows = tableLines.drop(2).mapNotNull { splitTableRow(it) }
    return header to rows
}

private fun splitTableRow(line: String): List<String>? {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    if (trimmed.isEmpty()) return null
    return trimmed.split("|").map { it.trim() }
}
