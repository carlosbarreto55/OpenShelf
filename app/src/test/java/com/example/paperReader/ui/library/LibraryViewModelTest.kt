package com.example.paperReader.ui.library

import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.FakeDownloadsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var downloadsRepository: FakeDownloadsRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        libraryRepository = FakeLibraryRepository()
        downloadsRepository = FakeDownloadsRepository()
        viewModel = LibraryViewModel(libraryRepository, ToggleSavedPaperUseCase(libraryRepository), downloadsRepository)
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
    fun startDownloadDelegatesToDownloadsRepository() = runTest(dispatcher) {
        val paper = testPaper("W1", savedState = SavedState.SAVED)
        viewModel.startDownload(paper)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(paper), downloadsRepository.startDownloadCalls)
    }
}
