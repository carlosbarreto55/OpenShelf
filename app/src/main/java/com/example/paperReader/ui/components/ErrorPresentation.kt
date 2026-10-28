package com.example.paperReader.ui.components

import androidx.annotation.StringRes
import com.example.paperReader.R
import com.example.paperReader.domain.repository.RepositoryError

/**
 * A destination that still works while the failure being reported persists, so a screen is never
 * left offering only an action that cannot succeed. Deliberately an enum in the UI layer rather
 * than a navigation route: `ui/navigation` maps it to a destination, so nothing below navigation
 * has to know the graph exists.
 */
enum class ErrorRoute(@param:StringRes val labelRes: Int) {
    /** Reads from Room, so it works with no connectivity and no reachable backend. */
    LIBRARY(R.string.error_route_library),

    /** A different query is the only thing that can change the outcome. */
    SEARCH(R.string.error_route_search),

    /** Files already on the device. */
    DOWNLOADS(R.string.error_route_downloads),
}

/**
 * What one [RepositoryError] looks like on screen: its own headline, its own explanation, whether
 * a retry is offered at all, and the route that still works under it.
 *
 * [explanation] is always the error's own [RepositoryError.userMessage] — the single safe string
 * the domain layer publishes. No server payload, descriptor URL, status code, stack trace or
 * credential is ever carried here, and the taxonomy's own variants (an [RepositoryError.Http]
 * status code, a [RepositoryError.ContractFailure]) are used to *choose* copy, never shown.
 */
data class ErrorPresentation(
    @param:StringRes val headlineRes: Int,
    val explanation: String,
    val canRetry: Boolean,
    val secondaryRoute: ErrorRoute,
)

/**
 * The single cause → presentation mapping, exhaustive over the sealed taxonomy: a new
 * [RepositoryError] variant is a compile error here rather than a silent fall-through to a generic
 * message, which is exactly the defect this replaces.
 */
fun RepositoryError.toPresentation(): ErrorPresentation = when (this) {
    // Retrying is offered (connectivity can come back mid-screen) but it is not the only way out:
    // the library reads from Room and needs no network at all.
    RepositoryError.Offline -> present(R.string.error_headline_offline, ErrorRoute.LIBRARY)

    RepositoryError.Timeout -> present(R.string.error_headline_timeout, ErrorRoute.LIBRARY)

    // Terminal: this paper is gone, so the only thing that can change the answer is a new query.
    RepositoryError.NotFound -> present(R.string.error_headline_not_found, ErrorRoute.SEARCH)

    // The paper exists and has no PDF right now; what the user already has is on the device.
    RepositoryError.NoDownloadableContent ->
        present(R.string.error_headline_no_downloadable_content, ErrorRoute.DOWNLOADS)

    RepositoryError.ServiceUnavailable ->
        present(R.string.error_headline_service_unavailable, ErrorRoute.LIBRARY)

    // The two sides disagree about what v1 accepts, so the same request cannot help — a different
    // search can.
    RepositoryError.InvalidRequest -> present(R.string.error_headline_invalid_request, ErrorRoute.SEARCH)

    is RepositoryError.Http -> present(R.string.error_headline_http, ErrorRoute.LIBRARY)

    is RepositoryError.Contract -> present(R.string.error_headline_contract, ErrorRoute.LIBRARY)

    is RepositoryError.AccessValidation ->
        present(R.string.error_headline_access_validation, ErrorRoute.LIBRARY)

    RepositoryError.Unexpected -> present(R.string.error_headline_unexpected, ErrorRoute.LIBRARY)
}

private fun RepositoryError.present(@StringRes headlineRes: Int, route: ErrorRoute) = ErrorPresentation(
    headlineRes = headlineRes,
    explanation = userMessage,
    // The taxonomy already decides this; the UI must not second-guess it by offering a retry
    // "just in case".
    canRetry = isRecoverable,
    secondaryRoute = route,
)
