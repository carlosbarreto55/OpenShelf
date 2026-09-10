package com.example.paperReader.data.download

/**
 * Before a production download can be marked completed, it must look like an actual PDF response
 * — not an HTML error/login page a misconfigured or expired descriptor could return. Both checks
 * are required; a 200 response with an HTML body must still fail.
 */
object PdfValidation {
    private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)

    /** A confirmed download response must succeed and carry a body. */
    fun isSuccessfulPdfResponse(statusCode: Int, contentLength: Long): Boolean {
        return statusCode in 200..299 && contentLength > 0
    }

    /** PDF files begin with the `%PDF-` magic signature; anything else (HTML, JSON, empty) is
     * rejected regardless of the response's declared content type, since that header is easy for
     * a misbehaving server to get wrong. */
    fun hasPdfSignature(bytes: ByteArray): Boolean {
        if (bytes.size < PDF_SIGNATURE.size) return false
        return PDF_SIGNATURE.indices.all { bytes[it] == PDF_SIGNATURE[it] }
    }

    fun isValidPdf(statusCode: Int, contentLength: Long, bytes: ByteArray): Boolean {
        return isSuccessfulPdfResponse(statusCode, contentLength) && hasPdfSignature(bytes)
    }
}
