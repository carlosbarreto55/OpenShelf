package com.example.paperReader.domain.repository

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>

    data class Failure(val error: RepositoryError) : RepositoryResult<Nothing>
}

sealed interface RepositoryError {
    val userMessage: String
    val isRecoverable: Boolean

    data object Offline : RepositoryError {
        override val userMessage = "You're offline. Check your connection or try cached results."
        override val isRecoverable = true
    }

    data object Timeout : RepositoryError {
        override val userMessage = "The request took too long. Try again."
        override val isRecoverable = true
    }

    /** The backend confirmed the resource does not exist (HTTP 404). Not recoverable by retrying,
     * and deliberately distinct from a malformed success body, which stays [Contract]. */
    data object NotFound : RepositoryError {
        override val userMessage = "We couldn't find that paper."
        override val isRecoverable = false
    }

    /** The paper exists, but the backend currently has no downloadable PDF for it (HTTP 200 with
     * a null descriptor). A normal answer, deliberately distinct from a malformed descriptor and
     * from a missing paper; retrying now would not change it. */
    data object NoDownloadableContent : RepositoryError {
        override val userMessage = "No PDF is available for this paper right now."
        override val isRecoverable = false
    }

    /**
     * The backend is reachable but cannot serve the request right now: its upstream failed
     * (HTTP 502 `OPENALEX_UNAVAILABLE`) or it refused to start another outbound call
     * (HTTP 503 `OUTBOUND_CAPACITY_EXHAUSTED`). Both say the same thing to a user, and both are
     * worth retrying.
     */
    data object ServiceUnavailable : RepositoryError {
        override val userMessage = "The service is temporarily unavailable. Try again shortly."
        override val isRecoverable = true
    }

    /**
     * The backend rejected the request itself (HTTP 400 `INVALID_REQUEST`). The client validates
     * its own inputs, so reaching this means the two sides disagree about what v1 accepts —
     * retrying the same request cannot help, and it is deliberately not a [Contract] failure,
     * which is about a response we could not read.
     */
    data object InvalidRequest : RepositoryError {
        override val userMessage = "We couldn't run that request. Try changing your search."
        override val isRecoverable = false
    }

    data class Http(val statusCode: Int? = null) : RepositoryError {
        override val userMessage = "The service is unavailable right now. Try again later."
        override val isRecoverable = statusCode == null || statusCode >= 500
    }

    data class Contract(val failure: ContractFailure) : RepositoryError {
        override val userMessage = "The service returned data we could not read."
        override val isRecoverable = false
    }

    data class AccessValidation(val failure: AccessFailure) : RepositoryError {
        override val userMessage = "Paper access could not be verified."
        override val isRecoverable = false
    }

    data object Unexpected : RepositoryError {
        override val userMessage = "Something went wrong. Try again."
        override val isRecoverable = true
    }

    enum class ContractFailure {
        INVALID_PAPER_ID,
        MISSING_TITLE,
        INVALID_RESPONSE,
        ALL_ITEMS_INVALID,
        UNIMPLEMENTED,
        CURSOR_DID_NOT_ADVANCE,
    }

    enum class AccessFailure {
        INVALID_DESCRIPTOR,
        INVALID_SOURCE_URL,
        INVALID_PAPER_ID,
        EXPIRED_DESCRIPTOR,
    }
}
