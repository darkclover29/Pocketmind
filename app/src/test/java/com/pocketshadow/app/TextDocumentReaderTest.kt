package com.pocketshadow.app

import com.pocketshadow.app.data.repository.TextDocumentReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextDocumentReaderTest {
    @Test fun readsUnicodeAndRemovesBom() {
        assertEquals("नमस्ते\nHello", TextDocumentReader.read("\uFEFFनमस्ते\nHello".byteInputStream()))
    }

    @Test fun rejectsOversizeDocumentsWithoutReadingWholeStream() {
        assertThrows(IllegalArgumentException::class.java) {
            TextDocumentReader.read(ByteArray(TextDocumentReader.MAX_BYTES + 1) { 65 }.inputStream())
        }
    }

    @Test fun acceptsExactLimit() {
        assertEquals(TextDocumentReader.MAX_BYTES,
            TextDocumentReader.read(ByteArray(TextDocumentReader.MAX_BYTES) { 65 }.inputStream()).length)
    }

    @Test fun rejectsEmptyAndBinaryDocuments() {
        listOf(byteArrayOf(), " \n".toByteArray(), byteArrayOf(0, 65), byteArrayOf(0xC3.toByte(), 0x28)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { TextDocumentReader.read(bytes.inputStream()) }
        }
    }
}
