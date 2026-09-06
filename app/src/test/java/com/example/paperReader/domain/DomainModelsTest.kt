package com.example.paperReader.domain

import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainModelsTest {
    @Test
    fun searchQueryNormalizesWhitespace() {
        assertEquals("AI harness", SearchQuery.normalize("  AI   harness ")?.value)
        assertNull(SearchQuery.normalize("   "))
    }

    @Test
    fun paperIdAcceptsStableWorkIdsOnly() {
        assertEquals(PaperId("W123"), PaperId.from(" W123 "))
        assertNull(PaperId.from(""))
        assertNull(PaperId.from("publisher-paper"))
    }
}
