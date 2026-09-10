package com.example.paperReader.ui.downloads

import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.FakeDownloadsRepository
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
        assertEquals(listOf(record), (content as DownloadsContent.Loaded).downloads)
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
    fun openEmitsAFileRequestForACompletedDownload() = runTest(dispatcher) {
        val record = downloadRecord(state = DownloadState.COMPLETED, localContentUri = "content://demo/1", mimeType = "text/plain")
        val requests = mutableListOf<OpenFileRequest>()
        viewModel.openFileRequestsFlow.onEach { requests.add(it) }.launchIn(CoroutineScope(dispatcher))

        viewModel.open(record)
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

    private fun downloadRecord(
        id: String = "d1",
        state: DownloadState,
        localContentUri: String? = null,
        mimeType: String? = null,
    ) = DownloadRecord(
        id = id,
        paperId = PaperId("W1"),
        title = "Demo title",
        state = state,
        localContentUri = localContentUri,
        mimeType = mimeType,
        updatedAtEpochMillis = 0L,
    )
}
