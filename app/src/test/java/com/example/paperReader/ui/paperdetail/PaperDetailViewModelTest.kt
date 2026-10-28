package com.example.paperReader.ui.paperdetail

import androidx.lifecycle.SavedStateHandle
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.components.toAccessAction
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaperDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var downloadsRepository: FakeDownloadsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
        downloadsRepository = FakeDownloadsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakePaperDetailRepository): PaperDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("paperId" to "W1"))
        return PaperDetailViewModel(
            savedStateHandle,
            repository,
            ToggleSavedPaperUseCase(libraryRepository),
            downloadsRepository,
        ).also {
            it.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
        }
    }

    @Test
    fun successfulRefreshShowsLoadedPaper() = runTest(dispatcher) {
        val paper = testPaper("W1")
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Success(paper) }

        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is PaperDetailContent.Loaded)
        assertEquals(paper, (content as PaperDetailContent.Loaded).paper)
        assertTrue(!content.isStale)
    }

    @Test
    fun refreshFailureWithoutCacheShowsRecoverableError() = runTest(dispatcher) {
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Failure(RepositoryError.Offline) }

        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is PaperDetailContent.Error)
        assertTrue((content as PaperDetailContent.Error).error.isRecoverable)
    }

    @Test
    fun refreshFailureWithCachedPaperKeepsItVisibleAndMarksStale() = runTest(dispatcher) {
        val cached = testPaper("W1")
        val repository = FakePaperDetailRepository(initialPaper = cached)
        repository.refreshHandler = { RepositoryResult.Failure(RepositoryError.Offline) }

        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is PaperDetailContent.Loaded)
        assertEquals(cached, (content as PaperDetailContent.Loaded).paper)
        assertTrue(content.isStale)
    }

    @Test
    fun retryReissuesRefresh() = runTest(dispatcher) {
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Failure(RepositoryError.Offline) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, repository.refreshCalls.size)
    }

    @Test
    fun toggleSavedTogglesTheCurrentlyLoadedPaper() = runTest(dispatcher) {
        val paper = testPaper("W1", savedState = SavedState.NOT_SAVED)
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Success(paper) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSaved()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper), libraryRepository.savedCalls)
    }

    @Test
    fun inaccessibleLoadedPaperExposesNoAccessAction() = runTest(dispatcher) {
        val paper = testPaper("W1", access = AccessResolution.Paywalled)
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Success(paper) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as PaperDetailContent.Loaded
        assertEquals(AccessResolution.Paywalled, content.paper.access)
        assertNull(content.paper.access.toAccessAction())
    }

    @Test
    fun startDownloadDelegatesToDownloadsRepositoryForTheLoadedPaper() = runTest(dispatcher) {
        val paper = testPaper("W1", access = TestFixtures.directPdf())
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Success(paper) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startDownload()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper), downloadsRepository.startDownloadCalls)
    }

    @Test
    fun aConfirmedNotFoundGetsItsOwnStableStateRatherThanARetryScreen() = runTest(dispatcher) {
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Failure(RepositoryError.NotFound) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaperDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun notFoundSuppressesACachedPaperAndItsAccessActions() = runTest(dispatcher) {
        val cached = testPaper("W1", access = TestFixtures.directPdf())
        val repository = FakePaperDetailRepository(initialPaper = cached)
        repository.refreshHandler = { RepositoryResult.Failure(RepositoryError.NotFound) }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PaperDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun aMalformedDetailBodyStaysARecoverableStyleErrorNotNotFound() = runTest(dispatcher) {
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = {
            RepositoryResult.Failure(RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE))
        }
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is PaperDetailContent.Error)
        assertNotEquals(PaperDetailContent.NotFound, content)
    }

    @Test
    fun upstreamFailuresRemainRecoverableWithACachedCopyShownAsStale() = runTest(dispatcher) {
        listOf(RepositoryError.ServiceUnavailable, RepositoryError.Timeout).forEach { error ->
            val cached = testPaper("W1", access = TestFixtures.directPdf())
            val repository = FakePaperDetailRepository(initialPaper = cached)
            repository.refreshHandler = { RepositoryResult.Failure(error) }
            val viewModel = viewModel(repository)
            dispatcher.scheduler.advanceUntilIdle()

            val content = viewModel.uiState.value.content as PaperDetailContent.Loaded
            assertTrue("$error must stay recoverable", error.isRecoverable)
            assertTrue("$error must mark the cached copy stale", content.isStale)
        }
    }

    @Test
    fun aFailedDownloadConfirmationIsSurfacedRatherThanDiscarded() = runTest(dispatcher) {
        val paper = testPaper("W1", access = TestFixtures.directPdf())
        val repository = FakePaperDetailRepository()
        repository.refreshHandler = { RepositoryResult.Success(paper) }
        downloadsRepository.startResult = RepositoryResult.Failure(RepositoryError.NoDownloadableContent)
        val viewModel = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startDownload()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            RepositoryError.NoDownloadableContent,
            viewModel.uiState.value.downloadFeedback?.error,
        )

        viewModel.dismissDownloadFeedback()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.downloadFeedback)
    }
}
