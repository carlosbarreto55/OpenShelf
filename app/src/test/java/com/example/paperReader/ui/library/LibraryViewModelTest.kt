package com.example.paperReader.ui.library

import com.example.paperReader.data.local.TestFixtures
import kotlinx.coroutines.flow.first
import com.example.paperReader.domain.repository.testDownloadRecord
import com.example.paperReader.domain.usecase.ReadPaperUseCase
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.FakeLibraryRepository
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.domain.usecase.testPaper
import com.example.paperReader.ui.components.toAccessAction
import com.example.paperReader.ui.reader.FakeReadingPositionRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var downloadsRepository: FakeDownloadsRepository
    private val readingPositions = FakeReadingPositionRepository()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
        downloadsRepository = FakeDownloadsRepository()
        viewModel = LibraryViewModel(
            libraryRepository,
            ToggleSavedPaperUseCase(libraryRepository),
            ReadPaperUseCase(downloadsRepository, DownloadedFiles { false }),
            readingPositions,
        )
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noSavedPapersIsEmpty() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LibraryContent.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun savedPapersAreObservedAsLoaded() = runTest(dispatcher) {
        val paper = testPaper("W1", savedState = SavedState.SAVED)
        libraryRepository.save(paper)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is LibraryContent.Loaded)
        assertEquals(listOf(paper), (content as LibraryContent.Loaded).papers)
    }

    @Test
    fun togglingASavedPaperUnsavesIt() = runTest(dispatcher) {
        val paper = testPaper("W1", savedState = SavedState.SAVED)
        libraryRepository.save(paper)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSaved(paper)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper.id), libraryRepository.unsavedCalls)
        assertEquals(LibraryContent.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun inaccessibleSavedPaperExposesNoAccessAction() = runTest(dispatcher) {
        val paper = testPaper("W1", access = AccessResolution.NoFreeFullText, savedState = SavedState.SAVED)
        libraryRepository.save(paper)
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as LibraryContent.Loaded
        assertNull(content.papers.single().access.toAccessAction())
    }

    @Test
    fun readPdfOpensTheReaderOnTheConfirmedDownload() = runTest(dispatcher) {
        val paper = testPaper("W1", access = TestFixtures.directPdf(), savedState = SavedState.SAVED)
        downloadsRepository.startResult = RepositoryResult.Success(testDownloadRecord("W1"))

        viewModel.readPdf(paper)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper), downloadsRepository.startDownloadCalls)
        assertEquals("download-W1", viewModel.openReaderRequestsFlow.first())
    }

    @Test
    fun aFailedDownloadConfirmationIsSurfacedRatherThanDiscarded() = runTest(dispatcher) {
        // Downloads started from a saved paper go through the same transfer-time confirmation as
        // any other, so they can fail — and the library used to drop that outcome on the floor.
        downloadsRepository.startResult = RepositoryResult.Failure(RepositoryError.NoDownloadableContent)

        val readerRequests = mutableListOf<String>()
        viewModel.openReaderRequestsFlow.onEach { readerRequests += it }.launchIn(CoroutineScope(dispatcher))
        viewModel.readPdf(testPaper("W1", access = TestFixtures.directPdf(), savedState = SavedState.SAVED))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            RepositoryError.NoDownloadableContent,
            viewModel.uiState.value.downloadFeedback?.error,
        )
        assertTrue("a failed confirmation opens no reader", readerRequests.isEmpty())

        viewModel.dismissDownloadFeedback()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.downloadFeedback)
    }

    @Test
    fun continueReadingListsWhatTheRepositoryOffersInItsOrder() = runTest(dispatcher) {
        // Most recently read first, and only papers whose file is present: the repository owns
        // both rules (ReadingPositionRepositoryImplTest); the library must not reorder or refilter.
        val offered = listOf(resumable("W2", page = 7, readAt = 200), resumable("W1", page = 0, readAt = 100))
        readingPositions.resumable.value = offered
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(offered, viewModel.uiState.value.continueReading)
    }

    @Test
    fun aPaperWhoseFileIsRemovedLeavesContinueReading() = runTest(dispatcher) {
        readingPositions.resumable.value = listOf(resumable("W1", page = 3, readAt = 100))
        dispatcher.scheduler.advanceUntilIdle()

        readingPositions.resumable.value = emptyList()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.continueReading.isEmpty())
    }

    @Test
    fun continuingAPaperOpensTheReaderOnItsDownloadWithoutAConfirmation() = runTest(dispatcher) {
        viewModel.continueReading(resumable("W1", page = 3, readAt = 100))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("download-W1", viewModel.openReaderRequestsFlow.first())
        assertTrue(downloadsRepository.startDownloadCalls.isEmpty())
    }

    private fun resumable(id: String, page: Int, readAt: Long) = ResumablePaper(
        paperId = PaperId(id),
        downloadId = "download-$id",
        title = "Paper $id",
        position = ReadingPosition(pageIndex = page, pageOffsetFraction = 0f),
        lastReadAtEpochMillis = readAt,
    )
}
