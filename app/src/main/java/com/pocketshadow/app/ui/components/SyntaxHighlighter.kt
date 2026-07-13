package com.pocketshadow.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight regex-based syntax highlighter for the most common languages
 * LLMs emit. Stays under ~150 LOC and adds zero dependencies.
 *
 * Supported: Kotlin, Python, JSON, JavaScript/TypeScript, Shell/Bash, Java,
 * Go, Rust, SQL, YAML, generic fallback.
 *
 * Each language gets keyword/string/number/comment colour spans.
 */
object SyntaxHighlighter {

    // ── Keywords ──────────────────────────────────────────────────────────────

    private val KOTLIN_KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
        "companion", "override", "private", "public", "protected", "internal", "abstract",
        "open", "final", "lateinit", "const", "vararg", "suspend", "inline", "operator",
        "in", "out", "by", "as", "is", "when", "if", "else", "for", "while", "do", "return",
        "break", "continue", "throw", "try", "catch", "finally", "import", "package",
        "typealias", "where", "init", "this", "super", "null", "true", "false", "it"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "return",
        "yield", "lambda", "with", "try", "except", "finally", "raise", "assert", "pass",
        "break", "continue", "global", "nonlocal", "in", "is", "not", "and", "or", "None",
        "True", "False", "self", "cls", "async", "await", "del", "print"
    )

    private val JS_KEYWORDS = setOf(
        "function", "const", "let", "var", "class", "extends", "super", "this", "new",
        "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "throw", "try", "catch", "finally", "import", "export", "from", "as", "default",
        "async", "await", "yield", "typeof", "instanceof", "in", "of", "void", "delete",
        "null", "undefined", "true", "false", "NaN", "Infinity"
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case",
        "esac", "function", "return", "echo", "export", "local", "readonly", "unset",
        "cd", "pwd", "ls", "mkdir", "rm", "cp", "mv", "cat", "grep", "find", "sed", "awk"
    )

    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
        "TABLE", "INDEX", "VIEW", "JOIN", "INNER", "OUTER", "LEFT", "RIGHT", "FULL", "ON",
        "AS", "AND", "OR", "NOT", "NULL", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
        "DISTINCT", "UNION", "ALL", "INTO", "VALUES", "SET", "PRIMARY", "KEY", "FOREIGN",
        "REFERENCES", "DEFAULT", "UNIQUE", "CHECK", "CONSTRAINT", "CASCADE"
    )

    private val GO_KEYWORDS = setOf(
        "func", "var", "const", "type", "struct", "interface", "package", "import", "return",
        "if", "else", "for", "range", "switch", "case", "default", "break", "continue",
        "defer", "go", "chan", "select", "map", "make", "new", "nil", "true", "false"
    )

    private val RUST_KEYWORDS = setOf(
        "fn", "let", "mut", "const", "struct", "enum", "trait", "impl", "pub", "use", "mod",
        "crate", "self", "super", "as", "in", "ref", "match", "if", "else", "for", "while",
        "loop", "break", "continue", "return", "unsafe", "async", "await", "move", "box",
        "Some", "None", "Ok", "Err", "true", "false"
    )

    // ── Colour palette (works on dark code backgrounds) ──────────────────────

    private val ColorKeyword    = Color(0xFFC586C0)   // purple
    private val ColorString     = Color(0xFFCE9178)   // orange/amber
    private val ColorNumber     = Color(0xFFB5CEA8)   // light green
    private val ColorComment    = Color(0xFF6A9955)   // green
    private val ColorFunc       = Color(0xFFDCDCAA)   // yellow
    private val ColorType       = Color(0xFF4EC9B0)   // teal
    private val ColorPunct      = Color(0xFFD4D4D4)   // light grey

    fun languageOf(id: String): String = when (id.lowercase().trim()) {
        "kt", "kotlin" -> "kotlin"
        "py", "python" -> "python"
        "json" -> "json"
        "js", "javascript", "ts", "typescript", "jsx", "tsx" -> "js"
        "sh", "bash", "shell", "zsh" -> "shell"
        "java" -> "java"
        "go", "golang" -> "go"
        "rs", "rust" -> "rust"
        "sql" -> "sql"
        "yaml", "yml" -> "yaml"
        "xml", "html" -> "markup"
        else -> "generic"
    }

    /** Returns an [AnnotatedString] with colour spans matching the language. */
    fun highlight(code: String, language: String): AnnotatedString = buildAnnotatedString {
        when (language) {
            "kotlin"  -> highlightGeneric(code, KOTLIN_KEYWORDS)
            "python"  -> highlightPython(code)
            "json"    -> highlightJson(code)
            "js"      -> highlightGeneric(code, JS_KEYWORDS)
            "shell"   -> highlightShell(code)
            "java"    -> highlightGeneric(code, KOTLIN_KEYWORDS.intersect(JS_KEYWORDS) + setOf("void", "extends", "implements", "throws", "new", "this", "super"))
            "go"      -> highlightGeneric(code, GO_KEYWORDS)
            "rust"    -> highlightRust(code)
            "sql"     -> highlightSql(code)
            "yaml"    -> highlightYaml(code)
            else      -> highlightGeneric(code, emptySet())
        }
    }

    // ── Generic C-like highlighter ────────────────────────────────────────────

    private fun AnnotatedString.Builder.highlightGeneric(code: String, keywords: Set<String>) {
        val regex = Regex(
            """(?<comment>//[^\n]*|/\*[\s\S]*?\*/|#[^\n]*)|""" +              // comments
            """(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`)|""" + // strings
            """(?<number>\b\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?[fFlLdDuU]?\b)|""" +  // numbers
            """(?<word>[A-Za-z_][A-Za-z0-9_]*)"""                               // words
        )

        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorString)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                else -> {
                    val word = m.value
                    when {
                        word in keywords -> withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Medium)) { append(word) }
                        word.first().isUpperCase() -> withStyle(SpanStyle(color = ColorType)) { append(word) }
                        word.endsWith("(") || (m.range.last + 1 < code.length && code[m.range.last + 1] == '(') ->
                            withStyle(SpanStyle(color = ColorFunc)) { append(word) }
                        else -> withStyle(SpanStyle(color = ColorPunct)) { append(word) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightPython(code: String) {
        val regex = Regex(
            """(?<comment>#[^\n]*)|""" +
            """(?<string>(?:"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\"\"\"[\s\S]*?\"\"\"|'''[\s\S]*?'''))|""" +
            """(?<number>\b\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?[jJ]?\b)|""" +
            """(?<decorator>@[A-Za-z_][A-Za-z0-9_.]*)|""" +
            """(?<word>[A-Za-z_][A-Za-z0-9_]*)"""
        )
        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorString)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                m.groups[4] != null -> withStyle(SpanStyle(color = ColorKeyword)) { append(m.value) }
                else -> {
                    val word = m.value
                    when {
                        word in PYTHON_KEYWORDS -> withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Medium)) { append(word) }
                        word.first().isUpperCase() -> withStyle(SpanStyle(color = ColorType)) { append(word) }
                        word == "def" || (m.range.last + 1 < code.length && code[m.range.last + 1] == '(') ->
                            withStyle(SpanStyle(color = ColorFunc)) { append(word) }
                        else -> withStyle(SpanStyle(color = ColorPunct)) { append(word) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightJson(code: String) {
        val regex = Regex(
            """(?<string>"(?:[^"\\]|\\.)*")|""" +
            """(?<number>-?\b\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?\b)|""" +
            """(?<bool>true|false|null)|""" +
            """(?<punct>[{}\[\]:,])"""
        )
        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> {
                    val s = m.value
                    // key (followed by colon) vs string value
                    val after = code.indexOfAny(charArrayOf(':'), m.range.last + 1).let { idx ->
                        idx in (m.range.last + 1)..(m.range.last + 5)
                    }
                    withStyle(SpanStyle(color = if (after) ColorKeyword else ColorString)) { append(s) }
                }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorKeyword)) { append(m.value) }
                else -> withStyle(SpanStyle(color = ColorPunct)) { append(m.value) }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightShell(code: String) {
        val regex = Regex(
            """(?<comment>#[^\n]*)|""" +
            """(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|""" +
            """(?<number>\b\d+\b)|""" +
            """(?<var>\$[A-Za-z_][A-Za-z0-9_]*)|""" +
            """(?<word>[A-Za-z_][A-Za-z0-9_-]*)"""
        )
        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorString)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                m.groups[4] != null -> withStyle(SpanStyle(color = ColorType)) { append(m.value) }
                else -> {
                    val word = m.value
                    if (word in SHELL_KEYWORDS) {
                        withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Medium)) { append(word) }
                    } else {
                        withStyle(SpanStyle(color = ColorPunct)) { append(word) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightRust(code: String) {
        val regex = Regex(
            """(?<comment>//[^\n]*|/\*[\s\S]*?\*/)|""" +
            """(?<string>"(?:[^"\\]|\\.)*")|""" +
            """(?<lifetime>'[A-Za-z_][A-Za-z0-9_]*)|""" +
            """(?<number>\b\d[\d_]*\.?\d*(?:[eE][+-]?\d+)?[fFiIuU]?\b)|""" +
            """(?<word>[A-Za-z_][A-Za-z0-9_]*)"""
        )
        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorString)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorKeyword)) { append(m.value) }
                m.groups[4] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                else -> {
                    val word = m.value
                    when {
                        word in RUST_KEYWORDS -> withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Medium)) { append(word) }
                        word.startsWith("Vec") || word.startsWith("Option") || word.startsWith("Result") || word.first().isUpperCase() ->
                            withStyle(SpanStyle(color = ColorType)) { append(word) }
                        else -> withStyle(SpanStyle(color = ColorPunct)) { append(word) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightSql(code: String) {
        // SQL is case-insensitive — uppercase comparison
        val upper = code.uppercase()
        val regex = Regex(
            """(?<comment>--[^\n]*|/\*[\s\S]*?\*/)|""" +
            """(?<string>'(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*")|""" +
            """(?<number>\b\d+\.?\d*\b)|""" +
            """(?<word>[A-Za-z_][A-Za-z0-9_]*)"""
        )
        var cursor = 0
        regex.findAll(code).forEach { m ->
            if (m.range.first > cursor) {
                withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor, m.range.first)) }
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(m.value) }
                m.groups[2] != null -> withStyle(SpanStyle(color = ColorString)) { append(m.value) }
                m.groups[3] != null -> withStyle(SpanStyle(color = ColorNumber)) { append(m.value) }
                else -> {
                    val word = m.value
                    val upperWord = word.uppercase()
                    if (upperWord in SQL_KEYWORDS) {
                        withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Medium)) { append(word) }
                    } else if (word.first().isUpperCase()) {
                        withStyle(SpanStyle(color = ColorType)) { append(word) }
                    } else {
                        withStyle(SpanStyle(color = ColorPunct)) { append(word) }
                    }
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = ColorPunct)) { append(code.substring(cursor)) }
        }
    }

    private fun AnnotatedString.Builder.highlightYaml(code: String) {
        val lines = code.split('\n')
        for ((idx, line) in lines.withIndex()) {
            val match = Regex("""^(\s*)([A-Za-z0-9_-]+)(\s*:)(.*)$""").find(line)
            if (match != null) {
                withStyle(SpanStyle(color = ColorPunct)) { append(match.groupValues[1]) }
                withStyle(SpanStyle(color = ColorKeyword)) { append(match.groupValues[2]) }
                withStyle(SpanStyle(color = ColorPunct)) { append(match.groupValues[3]) }
                val rest = match.groupValues[4].trim()
                if (rest.isNotEmpty()) {
                    if (rest.startsWith('"') || rest.startsWith("'")) {
                        withStyle(SpanStyle(color = ColorString)) { append(match.groupValues[4]) }
                    } else if (rest.matches(Regex("-?\\d+\\.?\\d*"))) {
                        withStyle(SpanStyle(color = ColorNumber)) { append(match.groupValues[4]) }
                    } else {
                        withStyle(SpanStyle(color = ColorString)) { append(match.groupValues[4]) }
                    }
                }
            } else if (line.startsWith("#") || line.trimStart().startsWith("#")) {
                withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) { append(line) }
            } else {
                withStyle(SpanStyle(color = ColorPunct)) { append(line) }
            }
            if (idx < lines.lastIndex) append('\n')
        }
    }
}
