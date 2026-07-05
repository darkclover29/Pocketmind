package com.pocketmind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmind.ui.theme.*

// ── Domain model ──────────────────────────────────────────────────────────────

enum class Role { USER, AI }

data class Message(
    val id         : String = java.util.UUID.randomUUID().toString(),
    val role       : Role,
    val content    : String,
    val isStreaming: Boolean = false
)

// ── Bubble ────────────────────────────────────────────────────────────────────

private val CORNER_FULL  = 20.dp
private val CORNER_SHARP = 4.dp

@Composable
fun ChatMessageBubble(
    message : Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Role.USER

    // Asymmetric corners: sharp at the "tail" corner, round everywhere else
    val shape = RoundedCornerShape(
        topStart    = CORNER_FULL,
        topEnd      = CORNER_FULL,
        bottomStart = if (isUser) CORNER_FULL else CORNER_SHARP,
        bottomEnd   = if (isUser) CORNER_SHARP else CORNER_FULL
    )

    Row(
        modifier            = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    // AI bubbles get a subtle border; user bubbles don't need one
                    if (!isUser) Modifier.border(1.dp, AiChatBorder, shape) else Modifier
                )
                .clip(shape)
                .background(if (isUser) ElectricViolet else AiChatSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isUser) {
                // Plain text only — users don't send markdown
                Text(
                    text  = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(color = White)
                )
            } else {
                MarkdownBody(
                    raw         = message.content,
                    isStreaming = message.isStreaming
                )
            }
        }
    }
}

// ── Markdown renderer ─────────────────────────────────────────────────────────
//
// Handles:
//   ```lang\n…\n```   fenced code blocks  → CodeBlock composable
//   **bold**           inline bold
//   *italic*           inline italic
//   `inline code`      inline monospace
//
// For a richer renderer (tables, lists, links) drop in:
//   "com.github.jeziellago:compose-markdown:<version>"

@Composable
fun MarkdownBody(raw: String, isStreaming: Boolean = false) {
    val segments = remember(raw) { parseSegments(raw) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is Segment.Code      -> CodeBlock(seg.code, seg.language)
                is Segment.Paragraph -> InlineText(seg.text)
            }
        }
        // Blinking caret while generating
        if (isStreaming) {
            Text(
                text  = "▍",
                style = MaterialTheme.typography.bodyLarge.copy(color = ElectricViolet)
            )
        }
    }
}

// ── Code block ────────────────────────────────────────────────────────────────

@Composable
fun CodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    val codeShape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(codeShape)
            .border(1.dp, AiChatBorder, codeShape)
            .background(CodeSurface)
    ) {
        // ── Header bar ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElevatedSurface)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelMedium
            )
        }

        // ── Scrollable body ───────────────────────────────────────────────────
        // ✅ horizontalScroll prevents long lines from breaking bubble layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text     = code.trimEnd(),
                style    = MaterialTheme.typography.labelSmall,
                softWrap = false   // disable line-wrap so horizontal scroll is meaningful
            )
        }
    }
}

// ── Inline markdown text ──────────────────────────────────────────────────────

@Composable
fun InlineText(text: String) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val regex  = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`""")
            var cursor = 0

            regex.findAll(text).forEach { match ->
                if (match.range.first > cursor) append(text.substring(cursor, match.range.first))

                val (bold, italic, code) = match.destructured
                when {
                    bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(bold)
                    }
                    italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italic)
                    }
                    code.isNotEmpty() -> withStyle(
                        SpanStyle(
                            fontFamily  = CodeFontFamily,
                            background  = CodeSurface,
                            color       = InlineCodeAccent,
                            fontSize    = 13.sp
                        )
                    ) { append(" $code ") }
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(
        text  = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = White)
    )
}

// ── Segment parser ────────────────────────────────────────────────────────────

sealed class Segment {
    data class Code(val language: String, val code: String) : Segment()
    data class Paragraph(val text: String) : Segment()
}

internal fun parseSegments(raw: String): List<Segment> {
    val result = mutableListOf<Segment>()
    val fence  = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var cursor = 0

    fence.findAll(raw).forEach { m ->
        val before = raw.substring(cursor, m.range.first).trim()
        if (before.isNotEmpty()) result += Segment.Paragraph(before)
        result += Segment.Code(m.groupValues[1], m.groupValues[2])
        cursor = m.range.last + 1
    }
    val tail = raw.substring(cursor).trim()
    if (tail.isNotEmpty()) result += Segment.Paragraph(tail)
    return result.ifEmpty { listOf(Segment.Paragraph(raw)) }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewUserBubble() {
    PocketMindTheme {
        ChatMessageBubble(
            Message(role = Role.USER, content = "How do I reverse a list in Python?")
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAiBubble() {
    PocketMindTheme {
        ChatMessageBubble(
            Message(
                role    = Role.AI,
                content = """
You can use **`reversed()`** or slicing:

```python
my_list = [1, 2, 3, 4, 5]
# Method 1 — in-place
my_list.reverse()

# Method 2 — new list via slicing
reversed_list = my_list[::-1]
print(reversed_list)
```

Both are *O(n)* and idiomatic Python.
""".trimIndent()
            )
        )
    }
}
