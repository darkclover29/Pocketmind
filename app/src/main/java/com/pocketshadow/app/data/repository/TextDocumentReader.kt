package com.pocketshadow.app.data.repository

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded, strict UTF-8 decoding shared by the picker and its regression tests. */
object TextDocumentReader {
    const val MAX_BYTES = 1_048_576

    fun read(input: InputStream): String {
        val bytes = input.takeBytes(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "Choose a text document smaller than 1 MB." }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("This file is not UTF-8 text. Export it as TXT or Markdown first.", e)
        }
        require(!text.contains('\u0000')) { "This is not a supported text document." }
        require(text.isNotBlank()) { "The document is empty. Choose a file containing text." }
        return text
    }

    private fun InputStream.takeBytes(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
