package com.example.paperReader.ui.downloads

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.FakeDownloadsRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.ui.components.OpenFileRequest
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDownloadsRepository
    private lateinit var viewModel: DownloadsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDownloadsRepository()
        viewModel = DownloadsViewModel(repository)
        viewModel.uiState.onEach { }.launchIn(CoroutineScope(dispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noDownloadsIsEmpty() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DownloadsContent.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun downloadsAreObservedAsLoaded() = runTest(dispatcher) {
        val record = downloadRecord(state = DownloadState.RUNNING)
        repository.setDownloads(listOf(record))
        dispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content
        assertTrue(content is DownloadsContent.Loaded)
        assertEquals(
            listOf(DownloadSection(DownloadGroup.IN_PROGRESS, listOf(record))),
            (content as DownloadsContent.Loaded).sections,
        )
    }

    @Test
    fun retryDelegatesToRepository() = runTest(dispatcher) {
        viewModel.retry("d1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("d1"), repository.retryCalls)
    }

    @Test
    fun cancelDelegatesToRepository() = runTest(dispatcher) {
        viewModel.cancel("d1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("d1"), repository.cancelCalls)
    }

    @Test
    fun openReadsACompletedDownloadInTheAppNotAnotherApp() = runTest(dispatcher) {
        val record = downloadRecord(state = DownloadState.COMPLETED, localContentUri = "content://demo/1", mimeType = "text/plain")
        val readerRequests = mutableListOf<String>()
        val fileRequests = mutableListOf<OpenFileRequest>()
        viewModel.openReaderRequestsFlow.onEach { readerRequests.add(it) }.launchIn(CoroutineScope(dispatcher))
        viewModel.openFileRequestsFlow.onEach { fileRequests.add(it) }.launchIn(CoroutineScope(dispatcher))

        viewModel.open(record)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(record.id), readerRequests)
        assertTrue(fileRequests.isEmpty())
    }

    @Test
    fun onlyACompletedDownloadOpensTheReader() = runTest(dispatcher) {
        val readerRequests = mutableListOf<String>()
        viewModel.openReaderRequestsFlow.onEach { readerRequests.add(it) }.launchIn(CoroutineScope(dispatcher))

        viewModel.open(downloadRecord(state = DownloadState.FAILED))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(readerRequests.isEmpty())
    }

    @Test
    fun openingInAnotherAppHandsOverTheContentUriOnly() = runTest(dispatcher) {
        val record = downloadRecord(state = DownloadState.COMPLETED, localContentUri = "content://demo/1", mimeType = "text/plain")
        val requests = mutableListOf<OpenFileRequest>()
        viewModel.openFileRequestsFlow.onEach { requests.add(it) }.launchIn(CoroutineScope(dispatcher))

        viewModel.openInAnotherApp(record)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(OpenFileRequest(record.id, "content://demo/1", "text/plain")), requests)
    }

    @Test
    fun noViewerAvailableIsSurfacedAndDismissible() = runTest(dispatcher) {
        viewModel.onNoViewerAvailable("d1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("d1", viewModel.uiState.value.noViewerAvailableForDownloadId)

        viewModel.dismissNoViewerNotice()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.noViewerAvailableForDownloadId)
    }

    @Test
    fun recordsAreGroupedByWhatTheUserHasToDoWithThem() = runTest(dispatcher) {
        repository.setDownloads(
            listOf(
                downloadRecord(id = "done", state = DownloadState.COMPLETED, updatedAt = 30L),
                downloadRecord(id = "failed", state = DownloadState.FAILED, updatedAt = 20L),
                downloadRecord(id = "running", state = DownloadState.RUNNING, updatedAt = 10L),
                downloadRecord(id = "queued", state = DownloadState.QUEUED, updatedAt = 5L),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val sections = (viewModel.uiState.value.content as DownloadsContent.Loaded).sections

        // In-progress and needing-attention records come before files that are simply present, and
        // the order does not depend on what the DAO happened to return.
        assertEquals(
            listOf(DownloadGroup.IN_PROGRESS, DownloadGroup.NEEDS_ATTENTION, DownloadGroup.ON_THIS_DEVICE),
            sections.map { it.group },
        )
        assertEquals(listOf("running", "queued"), sections[0].records.map { it.id })
        assertEquals(listOf("failed"), sections[1].records.map { it.id })
        assertEquals(listOf("done"), sections[2].records.map { it.id })
    }

    @Test
    fun aCanceledRecordLeavesTheListRatherThanBecomingAGroup() = runTest(dispatcher) {
        repository.setDownloads(
            listOf(
                downloadRecord(id = "canceled", state = DownloadState.CANCELED),
                downloadRecord(id = "running", state = DownloadState.RUNNING),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val sections = (viewModel.uiState.value.content as DownloadsContent.Loaded).sections

        assertEquals(listOf(DownloadGroup.IN_PROGRESS), sections.map { it.group })
        assertEquals(listOf("running"), sections.single().records.map { it.id })
    }

    @Test
    fun onlyCanceledRecordsLeavesNothingToShow() = runTest(dispatcher) {
        repository.setDownloads(listOf(downloadRecord(id = "canceled", state = DownloadState.CANCELED)))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DownloadsContent.Empty, viewModel.uiState.value.content)
    }

    @Test
    fun whenEveryRecordSharesAStateOnlyThatGroupIsEmitted() = runTest(dispatcher) {
        repository.setDownloads(
            listOf(
                downloadRecord(id = "a", state = DownloadState.COMPLETED, updatedAt = 2L),
                downloadRecord(id = "b", state = DownloadState.COMPLETED, updatedAt = 1L),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val sections = (viewModel.uiState.value.content as DownloadsContent.Loaded).sections

        // Exactly one section, so no screen has to decide what to do with an empty heading.
        assertEquals(1, sections.size)
        assertEquals(DownloadGroup.ON_THIS_DEVICE, sections.single().group)
    }

    @Test
    fun clearCompletedRemovesCompletedRecordsAndLeavesTheRest() = runTest(dispatcher) {
        repository.setDownloads(
            listOf(
                downloadRecord(id = "done", state = DownloadState.COMPLETED),
                downloadRecord(id = "failed", state = DownloadState.FAILED),
                downloadRecord(id = "running", state = DownloadState.RUNNING),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasCompletedDownloads)

        viewModel.clearCompleted()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.clearCompletedCalls)
        val sections = (viewModel.uiState.value.content as DownloadsContent.Loaded).sections
        assertEquals(
            listOf(DownloadGroup.IN_PROGRESS, DownloadGroup.NEEDS_ATTENTION),
            sections.map { it.group },
        )
        assertEquals(false, viewModel.uiState.value.hasCompletedDownloads)
    }

    @Test
    fun aFailedClearCompletedIsReportedRatherThanDropped() = runTest(dispatcher) {
        repository.clearCompletedResult = RepositoryResult.Failure(RepositoryError.Unexpected)

        viewModel.clearCompleted()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RepositoryError.Unexpected, viewModel.uiState.value.downloadFeedback?.error)
    }

    private fun downloadRecord(
        id: String = "d1",
        state: DownloadState,
        localContentUri: String? = null,
        mimeType: String? = null,
        updatedAt: Long = 0L,
    ) = DownloadRecord(
        id = id,
        paperId = PaperId("W1"),
        title = "Demo title",
        state = state,
        localContentUri = localContentUri,
        mimeType = mimeType,
        updatedAtEpochMillis = updatedAt,
    )
}
