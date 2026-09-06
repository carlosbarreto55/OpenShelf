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
    }

    enum class AccessFailure {
        INVALID_DESCRIPTOR,
        INVALID_SOURCE_URL,
        INVALID_PAPER_ID,
    }
}
