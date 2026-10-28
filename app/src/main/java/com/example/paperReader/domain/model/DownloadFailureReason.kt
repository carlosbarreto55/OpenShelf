package com.example.paperReader.domain.model

/**
 * The failures a download can be recorded with, named once so the side that writes
 * [DownloadRecord.errorMessage] and the side that explains it to the user agree.
 *
 * [message] is the exact text persisted with the record, which is why this is a shared constant
 * rather than a string the UI tries to recognise: matching on ad-hoc prose would silently stop
 * working the first time a producer reworded itself.
 *
 * A transport failure carries the underlying I/O message instead and resolves to `null` here — it
 * has no cause we can name beyond "it did not finish", and inventing one would be a claim the
 * client cannot support.
 */
enum class DownloadFailureReason(val message: String) {
    /** The response was not a PDF — an HTML error or login page, JSON, or an empty body. Nothing
     * is written to storage until this check passes, so no partial file survives it. */
    INVALID_PDF("The server did not return a valid PDF."),

    /** The process died mid-transfer and no live work entry remained to resume. */
    INTERRUPTED("Interrupted before it could finish. Try again."),
    ;

    companion object {
        fun fromMessage(message: String?): DownloadFailureReason? =
            entries.firstOrNull { it.message == message }
    }
}
