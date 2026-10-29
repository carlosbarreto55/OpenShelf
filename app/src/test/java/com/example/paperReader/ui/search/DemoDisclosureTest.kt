package com.example.paperReader.ui.search

import com.example.paperReader.domain.usecase.ReadPaperUseCase
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.R
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.SearchPapersUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.ui.downloads.DownloadGroup
import com.example.paperReader.ui.downloads.DownloadSection
import com.example.paperReader.ui.downloads.DownloadsContent
import com.example.paperReader.ui.downloads.DownloadsScreen
import com.example.paperReader.ui.downloads.DownloadsUiState
import com.example.paperReader.ui.theme.PaperReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Disclosing that a build runs on placeholder data: owed once in full, never fully withdrawn, and
 * never owed at all by a build that talks to a real backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp")
class DemoDisclosureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSearchPaperRepository()
    private val preferences = FakeDemoDisclosurePreferences()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aDemoBuildOwesTheFullExplanationOnFirstLaunch() = runTest(dispatcher) {
        val viewModel = collectedViewModel()

        assertEquals(DemoDisclosureState.FirstRun, viewModel.uiState.value.demoDisclosure)
    }

    @Test
    fun dismissalSurvivesTheViewModelItWasMadeOn() = runTest(dispatcher) {
        collectedViewModel().dismissDemoExplanation()
        dispatcher.scheduler.advanceUntilIdle()

        // A fresh ViewModel over the same stored flag stands in for a process restart.
        val recreated = collectedViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DemoDisclosureState.IndicatorOnly, recreated.uiState.value.demoDisclosure)
    }

    @Test
    fun theIndicatorOutlivesTheExplanationAndCanReopenIt() = runTest(dispatcher) {
        val viewModel = collectedViewModel()
        viewModel.dismissDemoExplanation()
        dispatcher.scheduler.advanceUntilIdle()
        // IndicatorOnly is still a demo build: the marker is there, the explanation is not.
        assertEquals(DemoDisclosureState.IndicatorOnly, viewModel.uiState.value.demoDisclosure)

        viewModel.openDemoExplanation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DemoDisclosureState.FirstRun, viewModel.uiState.value.demoDisclosure)
    }

    @Test
    fun aRealBackendOwesNoDisclosureAtAll() = runTest(dispatcher) {
        val viewModel = collectedViewModel(mode = BackendMode.HTTP)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DemoDisclosureState.Hidden, viewModel.uiState.value.demoDisclosure)
    }

    @Test
    fun theExplanationAndTheIndicatorBothRenderOnFirstRun() {
        setSearchContent(DemoDisclosureState.FirstRun)

        composeRule.onNodeWithText(context.getString(R.string.demo_explanation_title)).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.demo_indicator_action))
            .assertIsDisplayed()
    }

    @Test
    fun onlyTheIndicatorRendersOnceAcknowledged() {
        setSearchContent(DemoDisclosureState.IndicatorOnly)

        composeRule.onNodeWithText(context.getString(R.string.demo_explanation_title)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.demo_indicator_action))
            .assertIsDisplayed()
    }

    @Test
    fun aNonDemoBuildRendersNeither() {
        setSearchContent(DemoDisclosureState.Hidden)

        composeRule.onNodeWithText(context.getString(R.string.demo_explanation_title)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.demo_indicator_action))
            .assertDoesNotExist()
    }

    @Test
    fun dismissingTheExplanationReportsIt() {
        var dismissed = 0
        setSearchContent(DemoDisclosureState.FirstRun, onDismissDemoExplanation = { dismissed++ })

        composeRule.onNodeWithText(context.getString(R.string.demo_explanation_dismiss)).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun theDisclaimerInsideASimulatedDownloadIsNotTiedToTheAcknowledgement() {
        // The strongest claim is made where the consequence is — on a file that is not the PDF it
        // stands in for. `DownloadsScreen` takes no disclosure state at all, so there is nothing
        // for an acknowledgement to switch off.
        composeRule.setContent {
            PaperReaderTheme {
                DownloadsScreen(
                    state = DownloadsUiState(
                        content = DownloadsContent.Loaded(
                            sections = listOf(
                                DownloadSection(
                                    group = DownloadGroup.ON_THIS_DEVICE,
                                    records = listOf(placeholderDownload()),
                                ),
                            ),
                        ),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onOpen = {},
                    onDismissNoViewerNotice = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.downloads_demo_placeholder_notice))
            .assertIsDisplayed()
    }

    private fun placeholderDownload() = DownloadRecord(
        id = "D1",
        paperId = PaperId("W1"),
        title = "Harnessing Generative AI",
        state = DownloadState.COMPLETED,
        mimeType = "text/plain",
        updatedAtEpochMillis = 0L,
    )

    private fun collectedViewModel(mode: BackendMode = BackendMode.FAKE): SearchViewModel {
        val viewModel = SearchViewModel(
            repository,
            SearchPapersUseCase(repository),
            ToggleSavedPaperUseCase(FakeLibraryRepository()),
            ReadPaperUseCase(FakeDownloadsRepository(), DownloadedFiles { false }),
            preferences,
            BackendConfiguration(
                mode = mode,
                baseUrl = if (mode == BackendMode.HTTP) "https://paperreader.invalid/" else null,
            ),
        )
        // `uiState` only runs its upstream while collected.
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun setSearchContent(
        demoDisclosure: DemoDisclosureState,
        onDismissDemoExplanation: () -> Unit = {},
    ) {
        composeRule.setContent {
            PaperReaderTheme {
                SearchScreen(
                    state = SearchUiState(demoDisclosure = demoDisclosure),
                    onQueryTextChanged = {},
                    onFiltersChanged = {},
                    onClearFilters = {},
                    onSortOrderChanged = {},
                    onRetry = {},
                    onLoadNextPage = {},
                    onToggleSave = {},
                    onOpenPaper = {},
                    onReadPdf = {},
                    onDismissDemoExplanation = onDismissDemoExplanation,
                    onReadOnline = {},
                )
            }
        }
    }
}
