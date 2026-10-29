package com.example.paperReader.ui

import androidx.lifecycle.SavedStateHandle
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.repository.testDownloadRecord
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.ReadPaperUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.components.toAccessAction
import com.example.paperReader.ui.downloads.DownloadsContent
import com.example.paperReader.ui.downloads.DownloadsViewModel
import com.example.paperReader.ui.library.LibraryContent
import com.example.paperReader.ui.library.LibraryViewModel
import com.example.paperReader.ui.paperdetail.FakePaperDetailRepository
import com.example.paperReader.ui.paperdetail.PaperDetailContent
import com.example.paperReader.ui.paperdetail.PaperDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A local copy is not an access decision: once a paper's access stops being a confirmed PDF, Read
 * PDF leaves its card and its detail even though the completed file is still on the device, and
 * the file stays readable from Downloads, which answers for the file rather than for access.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadPdfAfterAccessDropTest {
    private val dispatcher = StandardTestDispatcher()
    private val confirmed = testPaper("W1", access = TestFixtures.directPdf(), savedState = SavedState.SAVED)
    private val uncertain = confirmed.copy(access = AccessResolution.Unknown)
    private val completed = testDownloadRecord("W1", state = DownloadState.COMPLETED)
        .copy(localContentUri = "content://app/W1.pdf")
    private val downloads = FakeDownloadsRepository().apply { setDownloads(listOf(completed)) }
    private val readPaper = ReadPaperUseCase(downloads, DownloadedFiles { it == completed.localContentUri })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theCardLosesReadPdfAndReadingItIsRefused() = runTest(dispatcher) {
        val library = FakeLibraryRepository().apply { save(confirmed) }
        val viewModel = LibraryViewModel(library, ToggleSavedPaperUseCase(library), readPaper)
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(cardAccessAction(viewModel))

        library.save(uncertain)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(cardAccessAction(viewModel))

        // Even a stale tap — from a frame drawn before the drop — opens nothing.
        val requests = mutableListOf<String>()
        viewModel.openReaderRequestsFlow.onEach { requests += it }.launchIn(CoroutineScope(dispatcher))
        viewModel.readPdf(uncertain)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertNotNull(viewModel.uiState.value.downloadFeedback)
        assertTrue(downloads.startDownloadCalls.isEmpty())
    }

    @Test
    fun theDetailLosesReadPdf() = runTest(dispatcher) {
        val papers = FakePaperDetailRepository(initialPaper = confirmed)
        val viewModel = PaperDetailViewModel(
            SavedStateHandle(mapOf("paperId" to "W1")),
            papers,
            ToggleSavedPaperUseCase(FakeLibraryRepository()),
            readPaper,
        )
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(detailAccessAction(viewModel))

        papers.setPaper(uncertain)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(detailAccessAction(viewModel))
    }

    @Test
    fun theFileStaysReadableFromDownloads() = runTest(dispatcher) {
        val viewModel = DownloadsViewModel(downloads)
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as DownloadsContent.Loaded
        val record = content.sections.flatMap { it.records }.single()
        viewModel.open(record)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(completed.id, viewModel.openReaderRequestsFlow.first())
        assertEquals(RepositoryResult.Failure::class, readPaper(uncertain)::class)
    }

    private fun cardAccessAction(viewModel: LibraryViewModel) =
        (viewModel.uiState.value.content as LibraryContent.Loaded).papers.single().access.toAccessAction()

    private fun detailAccessAction(viewModel: PaperDetailViewModel) =
        (viewModel.uiState.value.content as PaperDetailContent.Loaded).paper.access.toAccessAction()
}
