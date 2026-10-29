package com.example.paperReader.ui.components

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessPresentationTest {
    private val descriptor = ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf")

    @Test
    fun directPdfMapsToReadPdfAction() {
        val action = AccessResolution.DirectPdf(descriptor).toAccessAction()
        assertEquals(AccessAction.ReadPdf(descriptor), action)
    }

    @Test
    fun openFullTextMapsToReadOnlineAction() {
        val action = AccessResolution.OpenFullText(sourceUrl = "https://demo.paperreader.invalid/papers/W1", provenance = "demo")
            .toAccessAction()
        assertEquals(AccessAction.ReadOnline("https://demo.paperreader.invalid/papers/W1", "demo"), action)
    }

    @Test
    fun everyOtherStateExposesNoAction() {
        val silentStates = listOf(
            AccessResolution.NoFreeFullText,
            AccessResolution.Paywalled,
            AccessResolution.Unknown,
            AccessResolution.Checking,
            AccessResolution.Error,
        )
        silentStates.forEach { access ->
            assertNull("expected no action for $access", access.toAccessAction())
        }
    }
}
