package com.example.paperReader.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfValidationTest {
    private val pdfBytes = "%PDF-1.4\n...".toByteArray(Charsets.US_ASCII)
    private val htmlBytes = "<html><body>404 Not Found</body></html>".toByteArray(Charsets.US_ASCII)

    @Test
    fun validPdfResponsePasses() {
        assertTrue(PdfValidation.isValidPdf(200, pdfBytes.size.toLong(), pdfBytes))
    }

    @Test
    fun htmlErrorPageWithSuccessStatusIsRejected() {
        // A 200 status alone must never be trusted — this is exactly the "server returns HTML
        // instead of PDF" scenario from the paper-downloads spec.
        assertFalse(PdfValidation.isValidPdf(200, htmlBytes.size.toLong(), htmlBytes))
    }

    @Test
    fun nonSuccessStatusIsRejectedEvenWithAPdfBody() {
        assertFalse(PdfValidation.isValidPdf(404, pdfBytes.size.toLong(), pdfBytes))
    }

    @Test
    fun emptyBodyIsRejected() {
        assertFalse(PdfValidation.isValidPdf(200, 0L, ByteArray(0)))
    }

    @Test
    fun bodyShorterThanTheSignatureIsRejected() {
        val truncated = "%PD".toByteArray(Charsets.US_ASCII)
        assertFalse(PdfValidation.hasPdfSignature(truncated))
    }
}
