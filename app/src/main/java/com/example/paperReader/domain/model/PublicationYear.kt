package com.example.paperReader.domain.model

/**
 * The one publication-year rule in the app.
 *
 * Mapping already discards an out-of-range year from a response ([PaperMappers.mapPaper]); a filter
 * control has to apply exactly the same range, or the UI could issue a request for a year the
 * client would refuse to display back.
 */
object PublicationYear {
    /** The documented contract range, matching what response mapping accepts. */
    val SUPPORTED_RANGE = 1500..2100

    sealed interface Validation {
        data class Valid(val year: Int) : Validation

        /** Nothing entered: not an error, just no year filter. */
        data object Empty : Validation

        data object NotANumber : Validation

        data object OutOfRange : Validation
    }

    fun validate(rawInput: String?): Validation {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) return Validation.Empty
        val year = trimmed.toIntOrNull() ?: return Validation.NotANumber
        return if (year in SUPPORTED_RANGE) Validation.Valid(year) else Validation.OutOfRange
    }
}
