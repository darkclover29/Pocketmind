package com.pocketmind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmind.ui.theme.*

// ── Data model ────────────────────────────────────────────────────────────────

enum class MessageRole { USER, ASSISTANT }

data class ChatMessageModel(
    val id: String,
    val role: MessageRole,
    val content: String,        // Raw text (may contain markdown)
    val isStreaming: Boolean = false
)

// ── Bubble layout ─────────────────────────────────────────────────────────────

@Composable
fun ChatMessageBubble(message: ChatMessageModel) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = if (isUser) 18.dp else 4.dp,
                        topEnd      = if (isUser) 4.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd   = 18.dp
                    )
                )
                .background(
                    if (isUser) NeonVioletContainer else Charcoal
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isUser) {
                // User messages: plain text only
                Text(
                    text  = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(color = OffWhite)
                )
            } else {
                // AI messages: markdown-aware renderer
                MarkdownContent(
                    raw         = message.content,
                    isStreaming = message.isStreaming
                )
            }
        }
    }
}

// ── Markdown renderer ─────────────────────────────────────────────────────────
//
// Supported tokens (no external library needed for these common cases):
//   **bold**   *italic*   `inline code`
//   ```lang\n...\n```  — fenced code blocks
//
// For a fully-featured renderer, swap this with a library such as
// "io.noties.markwon:markwon-core" or "com.mikepenz:multiplatform-markdown-renderer"

@Composable
fun MarkdownContent(raw: String, isStreaming: Boolean = false) {
    val segments = remember(raw) { parseMarkdownSegments(raw) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.FencedCode -> CodeBlock(
                    code     = segment.code,
                    language = segment.language
                )
                is MarkdownSegment.Paragraph  -> InlineMarkdownText(segment.text)
            }
        }

        // Blinking cursor while streaming
        if (isStreaming) {
            Text(
                text  = "▍",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = NeonViolet,
                    fontSize = 14.sp
                )
            )
        }
    }
}

// ── Code block ────────────────────────────────────────────────────────────────

@Composable
fun CodeBlock(code: String, language: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = CodeBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(CodeSurface)
    ) {
        // Header bar
        if (language.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalLight)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = language.lowercase(),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Scrollable code body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text  = code.trimEnd(),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ── Inline markdown (bold / italic / inline-code) ─────────────────────────────

@Composable
fun InlineMarkdownText(text: String) {
    val annotated = remember(text) { buildInlineAnnotated(text) }
    Text(
        text  = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = OffWhite)
    )
}

private fun buildInlineAnnotated(raw: String) = buildAnnotatedString {
    // Tokenise left-to-right for **bold**, *italic*, `code`
    val regex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")
    var cursor = 0

    regex.findAll(raw).forEach { match ->
        // Append plain text before this match
        if (match.range.first > cursor) {
            append(raw.substring(cursor, match.range.first))
        }
        when {
            match.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            match.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(match.groupValues[2])
            }
            match.groupValues[3].isNotEmpty() -> withStyle(
                SpanStyle(
                    fontFamily      = CodeFontFamily,
                    background      = CodeSurface,
                    color           = NeonViolet,
                    fontSize        = 13.sp
                )
            ) {
                append(" ${match.groupValues[3]} ")
            }
        }
        cursor = match.range.last + 1
    }
    // Remaining plain text
    if (cursor < raw.length) append(raw.substring(cursor))
}

// ── Segment parser ────────────────────────────────────────────────────────────

sealed class MarkdownSegment {
    data class FencedCode(val language: String, val code: String) : MarkdownSegment()
    data class Paragraph(val text: String) : MarkdownSegment()
}

private fun parseMarkdownSegments(raw: String): List<MarkdownSegment> {
    val result  = mutableListOf<MarkdownSegment>()
    val fence   = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var cursor  = 0

    fence.findAll(raw).forEach { match ->
        // Text before the fence
        val before = raw.substring(cursor, match.range.first).trim()
        if (before.isNotEmpty()) result.add(MarkdownSegment.Paragraph(before))

        result.add(
            MarkdownSegment.FencedCode(
                language = match.groupValues[1],
                code     = match.groupValues[2]
            )
        )
        cursor = match.range.last + 1
    }
    // Trailing text after last fence
    val tail = raw.substring(cursor).trim()
    if (tail.isNotEmpty()) result.add(MarkdownSegment.Paragraph(tail))

    return result.ifEmpty { listOf(MarkdownSegment.Paragraph(raw)) }
}
