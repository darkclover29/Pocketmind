package com.pocketmind.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmind.ui.theme.*

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
    val isStreaming : Boolean = false
)

// ── Bubble ────────────────────────────────────────────────────────────────────

private val CORNER_FULL  = 20.dp
private val CORNER_SHARP = 4.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message       : Message,
    modifier      : Modifier      = Modifier,
    hapticEnabled : Boolean       = true,
    onRetry       : (() -> Unit)? = null,
    onReadAloud   : (() -> Unit)? = null   // null for user bubbles
) {
    val isUser           = message.role == Role.USER
    val clipboardManager = LocalClipboardManager.current
    val haptic           = LocalHapticFeedback.current
    var showContextMenu  by remember { mutableStateOf(false) }
    var showCopiedHint   by remember { mutableStateOf(false) }

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
    val textColor    = if (isUser) MaterialTheme.colorScheme.onPrimary
                       else        MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // Outer Box is the DropdownMenu anchor
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .then(if (!isUser) Modifier.border(1.dp, outlineColor, shape) else Modifier)
                    .clip(shape)
                    .background(bubbleBg)
                    .combinedClickable(
                        onClick     = {},
                        onLongClick = {
                            if (hapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            showContextMenu = true
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                            isStreaming = message.isStreaming
                        )
                    }

                    if (onRetry != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = outlineColor, thickness = 0.5.dp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick        = onRetry,
                                colors         = ButtonDefaults.textButtonColors(contentColor = ElectricViolet),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.Refresh,
                                    contentDescription = "Retry",
                                    modifier           = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Retry", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

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
            }
        }

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

// ── Markdown renderer ─────────────────────────────────────────────────────────
//
// Handles:
//   ```lang\n…\n```    fenced code blocks → CodeBlock
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
                }
            }
        } else {
            // ── Incremental streaming render ──────────────────────────────
            // Re-parsing the ENTIRE response per token is O(n²) over a long
            // generation and janks scrolling. Instead, split at the last
            // completed paragraph: the head is parsed only when a paragraph
            // finishes (remember(head) hit rate is ~100%), and only the short
            // live tail is re-rendered per token.
            val split = remember(raw) { splitStreamingMarkdown(raw) }
            val head  = split.first
            val tail  = split.second

            if (head.isNotEmpty()) {
                val headSegments = remember(head) { parseSegments(head) }
                headSegments.forEach { seg ->
                    when (seg) {
                        is Segment.Code      -> CodeBlock(seg.code, seg.language)
                        is Segment.Paragraph -> ParagraphContent(seg.text)
                    }
                }
            }

            val trimmedTail = tail.trim()
            if (trimmedTail.isNotEmpty()) {
                if (trimmedTail.startsWith("```")) {
                    // Code block still being streamed → cheap monospace box
                    // (no header/copy/horizontal scroll). The full CodeBlock
                    // renders once the fence closes and it moves to the head.
                    val newline = trimmedTail.indexOf('\n')
                    val body    = if (newline > 0) trimmedTail.substring(newline + 1) else ""
                    if (body.isNotBlank()) StreamingCodeTail(body)
                } else {
                    ParagraphContent(trimmedTail)
                }
            }
        }
        if (isStreaming) {
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
            Text(
                text  = "▍",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = ElectricViolet.copy(alpha = caretAlpha)
                )
            )
        }
    }
}

// ── Paragraph content with heading + list support ─────────────────────────────

@Composable
private fun ParagraphContent(text: String) {
    val textColor         = MaterialTheme.colorScheme.onSurface
    val lines             = text.split('\n')
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("# ")   -> {
                    Text(
                        text  = line.removePrefix("# "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = textColor
                        )
                    )
                    i++
                }
                line.startsWith("## ")  -> {
                    Text(
                        text  = line.removePrefix("## "),
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
                        baseColor  = textColor
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
                    BulletList(items, textColor)
                }
                line.matches(ORDERED_ITEM_REGEX) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].matches(ORDERED_ITEM_REGEX)) {
                        items += lines[i].replace(ORDERED_PREFIX_REGEX, "")
                        i++
                    }
                    NumberedList(items, textColor)
                }
                line.isBlank() -> { Spacer(Modifier.height(2.dp)); i++ }
                else           -> { InlineText(line, baseColor = textColor); i++ }
            }
        }
    }
}

// ── List composables ──────────────────────────────────────────────────────────

@Composable
private fun BulletList(items: List<String>, textColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text     = "•",
                    style    = MaterialTheme.typography.bodyLarge.copy(color = ElectricViolet),
                    modifier = Modifier.padding(top = 1.dp)
                )
                InlineText(item, baseColor = textColor)
            }
        }
    }
}

@Composable
private fun NumberedList(items: List<String>, textColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text     = "${index + 1}.",
                    style    = MaterialTheme.typography.bodyLarge.copy(color = ElectricViolet),
                    modifier = Modifier.padding(top = 1.dp)
                )
                InlineText(item, baseColor = textColor)
            }
        }
    }
}

// ── Lightweight in-progress code block (streaming only) ──────────────────────

@Composable
private fun StreamingCodeTail(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text  = code.trimEnd(),
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

        // Scrollable body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text     = code.trimEnd(),
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

@Composable
fun InlineText(
    text      : String,
    extraStyle: SpanStyle = SpanStyle(),
    baseColor : Color     = MaterialTheme.colorScheme.onSurface
) {
    val inlineCodeBg   = MaterialTheme.colorScheme.surfaceContainerHigh
    val inlineCodeText = InlineCodeAccent

    val annotated = remember(text, extraStyle, baseColor) {
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
    var cursor = 0

    FENCE_REGEX.findAll(raw).forEach { m ->
        val before = raw.substring(cursor, m.range.first).trim()
        if (before.isNotEmpty()) result += Segment.Paragraph(before)
        result += Segment.Code(m.groupValues[1], m.groupValues[2])
        cursor = m.range.last + 1
    }
    val tail = raw.substring(cursor).trim()
    if (tail.isNotEmpty()) result += Segment.Paragraph(tail)
    return result.ifEmpty { listOf(Segment.Paragraph(raw)) }
}
