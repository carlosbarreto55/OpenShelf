package com.example.paperReader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.data.reader.PdfOpenResult
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadProgress
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.repository.DownloadedFiles
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.ReadPaperUseCase
import com.example.paperReader.ui.components.OpenFileRequest
import com.example.paperReader.ui.paperdetail.FakePaperDetailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val downloads = FakeDownloadsRepository()
    private val papers = FakePaperDetailRepository(TestFixtures.paper("W1", access = TestFixtures.directPdf()))
    private val positions = FakeReadingPositionRepository()
    private val source = FakePdfPageSource()
    private val presentFiles = mutableSetOf<String>()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: Map<String, Any> = emptyMap()): ReaderViewModel {
        return ReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("downloadId" to "W1") + saved),
            downloadsRepository = downloads,
            paperRepository = papers,
            readingPositions = positions,
            readPaper = ReadPaperUseCase(downloads, DownloadedFiles { it in presentFiles }),
            pageSource = source,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        ).also { store.put("reader", it) }
    }

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    private fun record(
        state: DownloadState,
        bytes: Long = 0,
        total: Long? = null,
        uri: String? = null,
        mimeType: String? = "application/pdf",
        error: String? = null,
    ) = DownloadRecord(
        id = "W1",
        paperId = PaperId("W1"),
        title = "Demo title",
        state = state,
        progress = DownloadProgress(bytes, total),
        localContentUri = uri,
        mimeType = mimeType,
        errorMessage = error,
        updatedAtEpochMillis = 0,
    )

    private fun completedAndReadable(pages: Int = 3) {
        source.answer(URI, FakePdfPageSource.readable(pages))
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI)))
    }

    @Test
    fun aTransferIsFollowedToTheDocumentWithoutAnyUserAction() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.QUEUED)))
        settle()
        assertEquals(ReaderContent.Transferring(queued = true, progress = null), vm.uiState.value.content)

        downloads.setDownloads(listOf(record(DownloadState.RUNNING, bytes = 256, total = 512)))
        settle()
        assertEquals(ReaderContent.Transferring(queued = false, progress = 0.5f), vm.uiState.value.content)

        completedAndReadable()
        settle()
        val content = vm.uiState.value.content as ReaderContent.Reading
        assertEquals(3, content.pages.size)
        assertEquals(listOf(URI), source.opened)
        assertEquals("Demo title", vm.uiState.value.title)
    }

    @Test
    fun progressWithoutAKnownSizeIsIndeterminate() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.RUNNING, bytes = 100, total = null)))
        settle()

        assertEquals(ReaderContent.Transferring(queued = false, progress = null), vm.uiState.value.content)
    }

    @Test
    fun aTransferFailingWhileOpenShowsTheCauseAndRetriesThroughConfirmation() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.RUNNING, bytes = 1, total = 2)))
        settle()
        downloads.setDownloads(listOf(record(DownloadState.FAILED, error = "The server did not return a valid PDF.")))
        settle()
        assertEquals(ReaderContent.TransferFailed("The server did not return a valid PDF."), vm.uiState.value.content)
        assertTrue("no partial content was opened", source.opened.isEmpty())

        downloads.retryResult = RepositoryResult.Failure(RepositoryError.Offline)
        vm.retryTransfer()
        settle()

        assertEquals(listOf("W1"), downloads.retryCalls)
        assertEquals(RepositoryError.Offline, vm.uiState.value.downloadFeedback?.error)
    }

    @Test
    fun leavingMidTransferLeavesTheDownloadRunning() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.RUNNING, bytes = 1, total = 2)))
        settle()

        vm.onLeave()
        store.clear()
        settle()

        assertTrue(downloads.cancelCalls.isEmpty())
    }

    @Test
    fun cancelIsTheUsersExplicitChoice() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.RUNNING, bytes = 1, total = 2)))
        settle()

        vm.cancelTransfer()
        settle()
        downloads.setDownloads(listOf(record(DownloadState.CANCELED)))
        settle()

        assertEquals(listOf("W1"), downloads.cancelCalls)
        assertEquals(ReaderContent.Canceled, vm.uiState.value.content)
    }

    @Test
    fun aMultiPageDocumentRendersTheVisiblePagesAndRemembersWhereTheUserIs() = runTest(dispatcher) {
        val vm = viewModel()
        completedAndReadable(pages = 10)
        settle()

        vm.onViewport(ReaderViewport(firstVisiblePage = 4, lastVisiblePage = 4, viewportWidthPx = 1000, viewportHeightPx = 2000, zoom = 1f))
        settle()
        assertEquals(setOf(3, 4, 5), vm.pageImages.value.keys)

        vm.onPositionChanged(ReadingPosition(pageIndex = 4, pageOffsetFraction = 0.3f))
        dispatcher.scheduler.advanceTimeBy(ReaderViewModel.POSITION_DEBOUNCE_MILLIS + 1)
        settle()
        assertEquals(listOf(PaperId("W1") to ReadingPosition(4, 0.3f)), positions.saves)
    }

    @Test
    fun scrollingWritesOnlyOnceTheReaderPauses() = runTest(dispatcher) {
        val vm = viewModel()
        completedAndReadable(pages = 10)
        settle()

        repeat(5) { page ->
            vm.onPositionChanged(ReadingPosition(page, 0f))
            dispatcher.scheduler.advanceTimeBy(ReaderViewModel.POSITION_DEBOUNCE_MILLIS / 2)
        }
        settle()

        assertEquals(listOf(PaperId("W1") to ReadingPosition(4, 0f)), positions.saves)
    }

    @Test
    fun reopeningResumesAtTheStoredPosition() = runTest(dispatcher) {
        positions.stored[PaperId("W1")] = ReadingPosition(pageIndex = 2, pageOffsetFraction = 0.25f)
        val vm = viewModel()
        completedAndReadable()
        settle()

        assertEquals(ReadingPosition(2, 0.25f), (vm.uiState.value.content as ReaderContent.Reading).initialPosition)
    }

    @Test
    fun aFirstOpenStartsAtTheFirstPage() = runTest(dispatcher) {
        val vm = viewModel()
        completedAndReadable()
        settle()

        assertEquals(ReadingPosition.START, (vm.uiState.value.content as ReaderContent.Reading).initialPosition)
    }

    @Test
    fun afterProcessDeathThePageAndZoomComeBackFromSavedState() = runTest(dispatcher) {
        // Room holds an older visit; saved state holds this one, which is what was on screen.
        positions.stored[PaperId("W1")] = ReadingPosition(pageIndex = 0, pageOffsetFraction = 0f)
        val vm = viewModel(saved = mapOf("readerPage" to 1, "readerPageOffset" to 0.5f, "readerZoom" to 2f))
        completedAndReadable()
        settle()

        val content = vm.uiState.value.content as ReaderContent.Reading
        assertEquals(ReadingPosition(1, 0.5f), content.initialPosition)
        assertEquals(2f, content.initialZoom)
    }

    @Test
    fun aPositionPastTheEndOfTheDocumentStartsAtTheTop() = runTest(dispatcher) {
        positions.stored[PaperId("W1")] = ReadingPosition(pageIndex = 9, pageOffsetFraction = 0f)
        val vm = viewModel()
        completedAndReadable(pages = 3)
        settle()

        assertEquals(ReadingPosition.START, (vm.uiState.value.content as ReaderContent.Reading).initialPosition)
    }

    @Test
    fun leavingWritesTheLastPositionAtOnce() = runTest(dispatcher) {
        val vm = viewModel()
        completedAndReadable()
        settle()

        vm.onPositionChanged(ReadingPosition(2, 0.9f))
        vm.onLeave()
        store.clear()
        dispatcher.scheduler.runCurrent()

        assertEquals(PaperId("W1") to ReadingPosition(2, 0.9f), positions.saves.single())
    }

    @Test
    fun theDemoPlaceholderIsRefusedAsDemoOutput() = runTest(dispatcher) {
        val vm = viewModel()
        source.answer(URI, PdfOpenResult.NotPdf)
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI, mimeType = "text/plain")))
        settle()

        assertEquals(
            ReaderContent.Refused(ReaderRefusal.DEMO_PLACEHOLDER, canReadAgain = false, canOpenExternally = true),
            vm.uiState.value.content,
        )
        assertTrue(vm.pageImages.value.isEmpty())
    }

    @Test
    fun eachUnreadableFileIsRefusedWithItsOwnCauseAndTheHandoffOffered() = runTest(dispatcher) {
        val cases = mapOf(
            PdfOpenResult.NotPdf to ReaderRefusal.NOT_A_PDF,
            PdfOpenResult.Encrypted to ReaderRefusal.ENCRYPTED,
            PdfOpenResult.Malformed to ReaderRefusal.MALFORMED,
        )
        for ((result, refusal) in cases) {
            store.clear()
            val vm = viewModel()
            source.answer(URI, result)
            downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI)))
            settle()

            assertEquals(
                ReaderContent.Refused(refusal, canReadAgain = false, canOpenExternally = true),
                vm.uiState.value.content,
            )
            downloads.setDownloads(emptyList())
            settle()
        }
    }

    @Test
    fun aMissingFileOffersReadingAgainOnlyWhileAccessIsConfirmed() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI))) // source answers Missing
        settle()
        assertEquals(
            ReaderContent.Refused(ReaderRefusal.FILE_MISSING, canReadAgain = true, canOpenExternally = false),
            vm.uiState.value.content,
        )

        papers.setPaper(TestFixtures.paper("W1", access = AccessResolution.Unknown))
        settle()

        assertEquals(
            ReaderContent.Refused(ReaderRefusal.FILE_MISSING, canReadAgain = false, canOpenExternally = false),
            vm.uiState.value.content,
        )
    }

    @Test
    fun noRecordAtAllIsAMissingFileNotAWait() = runTest(dispatcher) {
        val vm = viewModel()
        settle()

        assertEquals(ReaderRefusal.FILE_MISSING, (vm.uiState.value.content as ReaderContent.Refused).refusal)
    }

    @Test
    fun readingAgainStartsAConfirmedDownloadThatTheReaderFollows() = runTest(dispatcher) {
        val vm = viewModel()
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI)))
        downloads.startResult = RepositoryResult.Success(record(DownloadState.QUEUED))
        settle()

        vm.readAgain()
        settle()
        downloads.setDownloads(listOf(record(DownloadState.QUEUED)))
        settle()

        assertEquals(1, downloads.startDownloadCalls.size)
        assertEquals(ReaderContent.Transferring(queued = true, progress = null), vm.uiState.value.content)
    }

    @Test
    fun theExternalHandoffCarriesTheContentUriAndReportsNoViewer() = runTest(dispatcher) {
        val vm = viewModel()
        source.answer(URI, PdfOpenResult.Malformed)
        downloads.setDownloads(listOf(record(DownloadState.COMPLETED, uri = URI)))
        settle()

        vm.openInAnotherApp()
        settle()
        assertEquals(OpenFileRequest("W1", URI, "application/pdf"), vm.openFileRequestsFlow.first())

        vm.onNoViewerAvailable()
        settle()
        assertTrue(vm.uiState.value.noViewerAvailable)
        vm.dismissNoViewerNotice()
        settle()
        assertFalse(vm.uiState.value.noViewerAvailable)
    }

    @Test
    fun leavingReleasesTheDocument() = runTest(dispatcher) {
        viewModel()
        completedAndReadable()
        settle()

        store.clear()

        assertEquals(1, source.closed)
    }

    private companion object {
        const val URI = "content://com.example.paperReader.fileprovider/downloads/W1.pdf"
    }
}
