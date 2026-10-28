package com.example.paperReader.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.paperReader.R
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One case per cause, asserting what the user actually sees: the headline that names the failure,
 * whether a retry is offered at all, and the second route the screen falls back on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecoverableErrorStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val retryLabel = context.getString(R.string.error_action_retry)

    @Test
    fun offlineIsNamedAndOffersTheLibraryAlongsideItsRetry() {
        var route: ErrorRoute? = null
        setContent(RepositoryError.Offline) { route = it }

        composeRule.onNodeWithText(context.getString(R.string.error_headline_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(RepositoryError.Offline.userMessage).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(ErrorRoute.LIBRARY.labelRes)).performClick()
        assertEquals(ErrorRoute.LIBRARY, route)
    }

    @Test
    fun timeoutIsNamedSeparatelyFromOffline() {
        setContent(RepositoryError.Timeout)

        composeRule.onNodeWithText(context.getString(R.string.error_headline_timeout)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.error_headline_offline)).assertDoesNotExist()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }

    @Test
    fun serviceUnavailableIsNamedAndRetryable() {
        setContent(RepositoryError.ServiceUnavailable)

        composeRule.onNodeWithText(context.getString(R.string.error_headline_service_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(ErrorRoute.LIBRARY.labelRes)).assertIsDisplayed()
    }

    @Test
    fun notFoundOffersNoRetryAndSendsTheUserBackToSearch() {
        var route: ErrorRoute? = null
        setContent(RepositoryError.NotFound) { route = it }

        composeRule.onNodeWithText(context.getString(R.string.error_headline_not_found)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(ErrorRoute.SEARCH.labelRes)).performClick()
        assertEquals(ErrorRoute.SEARCH, route)
    }

    @Test
    fun noDownloadableContentOffersNoRetryAndPointsAtFilesAlreadyHere() {
        var route: ErrorRoute? = null
        setContent(RepositoryError.NoDownloadableContent) { route = it }

        composeRule.onNodeWithText(context.getString(R.string.error_headline_no_downloadable_content)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(ErrorRoute.DOWNLOADS.labelRes)).performClick()
        assertEquals(ErrorRoute.DOWNLOADS, route)
    }

    @Test
    fun invalidRequestOffersNoRetryAndSendsTheUserBackToSearch() {
        setContent(RepositoryError.InvalidRequest)

        composeRule.onNodeWithText(context.getString(R.string.error_headline_invalid_request)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(ErrorRoute.SEARCH.labelRes)).assertIsDisplayed()
    }

    @Test
    fun contractFailureIsNamedWithoutRetryAndWithoutLeakingTheFailureName() {
        setContent(RepositoryError.Contract(RepositoryError.ContractFailure.ALL_ITEMS_INVALID))

        composeRule.onNodeWithText(context.getString(R.string.error_headline_contract)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
        composeRule.onNodeWithText("ALL_ITEMS_INVALID", substring = true).assertDoesNotExist()
    }

    @Test
    fun accessValidationFailureIsNamedWithoutRetry() {
        setContent(RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR))

        composeRule.onNodeWithText(context.getString(R.string.error_headline_access_validation)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertDoesNotExist()
    }

    @Test
    fun aServerStatusIsNeverPutOnScreen() {
        setContent(RepositoryError.Http(502))

        composeRule.onNodeWithText(context.getString(R.string.error_headline_http)).assertIsDisplayed()
        composeRule.onNodeWithText("502", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }

    @Test
    fun unexpectedKeepsTheGenericHeadlineAndARetry() {
        setContent(RepositoryError.Unexpected)

        composeRule.onNodeWithText(context.getString(R.string.error_headline_unexpected)).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
    }

    @Test
    fun retryInvokesTheRetryActionRatherThanTheRoute() {
        var retries = 0
        var route: ErrorRoute? = null
        composeRule.setContent {
            PaperReaderTheme {
                RecoverableErrorState(
                    error = RepositoryError.Offline,
                    onRetry = { retries++ },
                    onNavigateTo = { route = it },
                )
            }
        }

        composeRule.onNodeWithText(retryLabel).performClick()

        assertEquals(1, retries)
        assertEquals(null, route)
    }

    private fun setContent(error: RepositoryError, onNavigateTo: (ErrorRoute) -> Unit = {}) {
        composeRule.setContent {
            PaperReaderTheme {
                RecoverableErrorState(error = error, onRetry = {}, onNavigateTo = onNavigateTo)
            }
        }
    }
}
