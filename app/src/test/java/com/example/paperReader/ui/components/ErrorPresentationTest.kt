package com.example.paperReader.ui.components

import com.example.paperReader.domain.repository.RepositoryError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The taxonomy exists so failures can be told apart; these assert that the presentation layer
 * actually tells them apart, and that nothing but the safe user message escapes.
 */
class ErrorPresentationTest {

    /** Every cause the repositories can produce. Kept explicit rather than reflected so adding a
     * variant fails here as well as at the `when`. */
    private val allCauses = listOf(
        RepositoryError.Offline,
        RepositoryError.Timeout,
        RepositoryError.NotFound,
        RepositoryError.NoDownloadableContent,
        RepositoryError.ServiceUnavailable,
        RepositoryError.InvalidRequest,
        RepositoryError.Http(503),
        RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
        RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR),
        RepositoryError.Unexpected,
    )

    @Test
    fun eachCauseGetsItsOwnHeadlineRatherThanOneGenericMessage() {
        val headlines = allCauses.map { it.toPresentation().headlineRes }

        assertEquals(
            "Two causes share a headline, which is the generic-message defect returning",
            headlines.size,
            headlines.toSet().size,
        )
    }

    @Test
    fun retryIsOfferedExactlyWhenTheCauseSaysRetryingCanSucceed() {
        allCauses.forEach { error ->
            assertEquals(
                "${error::class.simpleName} disagrees with its own isRecoverable",
                error.isRecoverable,
                error.toPresentation().canRetry,
            )
        }
    }

    @Test
    fun nonRecoverableCausesOfferNoRetry() {
        listOf(
            RepositoryError.NotFound,
            RepositoryError.NoDownloadableContent,
            RepositoryError.InvalidRequest,
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
            RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
        ).forEach { error ->
            assertFalse(
                "${error::class.simpleName} would offer a retry that cannot work",
                error.toPresentation().canRetry,
            )
        }
    }

    @Test
    fun offlineRoutesToTheLibraryBecauseItReadsFromTheDevice() {
        assertEquals(ErrorRoute.LIBRARY, RepositoryError.Offline.toPresentation().secondaryRoute)
    }

    @Test
    fun everyCauseNamesASecondRouteThatWorksUnderIt() {
        allCauses.forEach { error ->
            val presentation = error.toPresentation()
            // A route is only useful if it does not depend on the thing that just failed. Nothing
            // maps to a route that needs the backend it could not reach.
            assertTrue(
                "${error::class.simpleName} offers no working alternative",
                presentation.secondaryRoute in ErrorRoute.entries,
            )
        }

        // The two causes a different query can actually resolve send the user back to search;
        // everything else offers content already on the device.
        assertEquals(ErrorRoute.SEARCH, RepositoryError.NotFound.toPresentation().secondaryRoute)
        assertEquals(ErrorRoute.SEARCH, RepositoryError.InvalidRequest.toPresentation().secondaryRoute)
        assertEquals(
            ErrorRoute.DOWNLOADS,
            RepositoryError.NoDownloadableContent.toPresentation().secondaryRoute,
        )
    }

    @Test
    fun onlyTheSafeUserMessageReachesThePresentation() {
        allCauses.forEach { error ->
            assertEquals(
                "${error::class.simpleName} explained itself with something other than userMessage",
                error.userMessage,
                error.toPresentation().explanation,
            )
        }
    }

    @Test
    fun structuredFailureDetailChoosesCopyButIsNeverShown() {
        // A status code and a contract-failure name are diagnostics, not user-facing text.
        val http = RepositoryError.Http(502).toPresentation().explanation
        val contract = RepositoryError.Contract(RepositoryError.ContractFailure.ALL_ITEMS_INVALID)
            .toPresentation().explanation
        val access = RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR)
            .toPresentation().explanation

        assertFalse(http.contains("502"))
        assertFalse(contract.contains("ALL_ITEMS_INVALID", ignoreCase = true))
        assertFalse(access.contains("EXPIRED_DESCRIPTOR", ignoreCase = true))
        listOf(http, contract, access).forEach { message ->
            assertFalse("A URL leaked into error copy", message.contains("http", ignoreCase = true))
        }
    }

    @Test
    fun twoHttpStatusesStillReadTheSameToAUser() {
        // Http carries a code so policy can branch on it; the copy must not, or the status becomes
        // user-facing by accident.
        assertEquals(
            RepositoryError.Http(500).toPresentation().explanation,
            RepositoryError.Http(418).toPresentation().explanation,
        )
        assertNotEquals(
            RepositoryError.Http(500).isRecoverable,
            RepositoryError.Http(418).isRecoverable,
        )
    }
}
