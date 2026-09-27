package com.pocketshadow.app.data.repository

/** Local excerpt selection; stable excerpt numbers let readers verify model citations. */
object DocumentContextSelector {
    fun select(document: String, question: String): String {
        val chunks = chunkDocument(document)
        if (chunks.isEmpty()) return document.take(DOCUMENT_CONTEXT_CHAR_BUDGET)

        val queryTerms = question
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()

        val ranked = if (queryTerms.isEmpty()) {
            chunks.take(DOCUMENT_CONTEXT_MAX_CHUNKS).mapIndexed { index, chunk -> index to chunk }
        } else {
            chunks
                .mapIndexed { index, chunk ->
                    val lower = chunk.lowercase()
                    val score = queryTerms.sumOf { term ->
                        Regex("\\b${Regex.escape(term)}\\b").findAll(lower).count()
                    }
                    Triple(index, chunk, score)
                }
                .filter { it.third > 0 }
                .sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
                .take(DOCUMENT_CONTEXT_MAX_CHUNKS)
                .sortedBy { it.first }
                .map { it.first to it.second }
                .ifEmpty { chunks.take(DOCUMENT_CONTEXT_MAX_CHUNKS).mapIndexed { index, chunk -> index to chunk } }
        }

        val builder = StringBuilder()
        for ((index, chunk) in ranked) {
            val block = "[Excerpt ${index + 1}]\n${chunk.trim()}\n\n"
            if (builder.length + block.length > DOCUMENT_CONTEXT_CHAR_BUDGET) break
            builder.append(block)
        }
        if (document.length > DOCUMENT_CONTEXT_CHAR_BUDGET) {
            builder.append("[Note: Selected from a longer document using local keyword search.]")
        }
        return builder.toString().trim()
    }

    private fun chunkDocument(document: String): List<String> {
        val clean = document.replace("\r\n", "\n").trim()
        if (clean.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            val targetEnd = (start + DOCUMENT_CHUNK_CHARS).coerceAtMost(clean.length)
            val end = if (targetEnd < clean.length) {
                clean.lastIndexOf('\n', targetEnd)
                    .takeIf { it > start + DOCUMENT_CHUNK_CHARS / 2 }
                    ?: targetEnd
            } else targetEnd
            chunks += clean.substring(start, end)
            if (end >= clean.length) break
            start = (end - DOCUMENT_CHUNK_OVERLAP)
                .coerceAtLeast(start + 1)
                .coerceAtMost(clean.length)
        }
        return chunks
    }

    private const val DOCUMENT_CHUNK_CHARS = 1_400
    private const val DOCUMENT_CHUNK_OVERLAP = 180
    private const val DOCUMENT_CONTEXT_MAX_CHUNKS = 5
    private const val DOCUMENT_CONTEXT_CHAR_BUDGET = 7_000
}
