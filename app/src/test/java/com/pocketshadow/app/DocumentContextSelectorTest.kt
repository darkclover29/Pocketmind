package com.pocketshadow.app

import com.pocketshadow.app.data.repository.DocumentContextSelector
import org.junit.Assert.*
import org.junit.Test

class DocumentContextSelectorTest {
    @Test fun followUpSelectsADifferentPartOfTheSameDocument() {
        val document = "Orchard harvest. ".repeat(100) + "Neutral material. ".repeat(1000) + "Volcano eruption. ".repeat(100)
        val first = DocumentContextSelector.select(document, "Orchard")
        val followUp = DocumentContextSelector.select(document, "Volcano")
        assertTrue(first.contains("Orchard"))
        assertTrue(followUp.contains("Volcano"))
        assertFalse(followUp.contains("Orchard"))
        assertNotEquals(first, followUp)
        assertTrue(followUp.contains("[Excerpt "))
    }

    @Test fun emptyDocumentIsHandled() {
        assertEquals("", DocumentContextSelector.select("", "question"))
    }

    @Test fun selectionIsBoundedAndDeterministic() {
        val document = "Repeated text. ".repeat(10000)
        val result = DocumentContextSelector.select(document, "Repeated")
        assertTrue(result.length < 7200)
        assertEquals(result, DocumentContextSelector.select(document, "Repeated"))
    }
}
