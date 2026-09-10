package com.example.paperReader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.ui.components.toAccessAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/** Proves the offline-paper-library requirement that saved state (and access state) is the same
 * fact seen from every read path — search results, paper detail, and the library list — because
 * they all join the same Room tables rather than keeping independent copies. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedStateConsistencyTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var paperRepository: PaperRepositoryImpl
    private lateinit var libraryRepository: LibraryRepositoryImpl

    private val query = PaperQuery(text = SearchQuery("AI"), filters = PaperFilters())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        val clock = FakeClock(0L)
        paperRepository = PaperRepositoryImpl(
            remote = FakePaperRemoteDataSource().apply { latencyMillis = 0 },
            paperDao = database.paperDao(),
            searchCacheDao = database.searchCacheDao(),
            accessSnapshotDao = database.accessSnapshotDao(),
            recentSearchDao = database.recentSearchDao(),
            clock = clock,
        )
        libraryRepository = LibraryRepositoryImpl(
            savedPaperDao = database.savedPaperDao(),
            paperDao = database.paperDao(),
            accessSnapshotDao = database.accessSnapshotDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingAConfirmedPdfPaperIsReflectedInSearchDetailAndLibrary() = runTest {
        paperRepository.search(PaperSearchRequest(query = query))
        val paperId = PaperId("W2741809807") // fixture with DIRECT_PDF access

        val searched = paperRepository.observeSearch(query).first().items.first { it.id == paperId }
        libraryRepository.save(searched)

        val fromSearch = paperRepository.observeSearch(query).first().items.first { it.id == paperId }
        val fromDetail = paperRepository.observePaper(paperId).first()
        val fromLibrary = libraryRepository.observeSavedPapers().first().first { it.id == paperId }

        assertEquals(SavedState.SAVED, fromSearch.savedState)
        assertEquals(SavedState.SAVED, fromDetail?.savedState)
        assertEquals(SavedState.SAVED, fromLibrary.savedState)
        assertTrue(fromSearch.access is AccessResolution.DirectPdf)
        assertTrue(fromDetail?.access is AccessResolution.DirectPdf)
        assertTrue(fromLibrary.access is AccessResolution.DirectPdf)
    }

    @Test
    fun unsavingIsReflectedEverywhereTooNotJustInTheLibrary() = runTest {
        paperRepository.search(PaperSearchRequest(query = query))
        val paperId = PaperId("W2741809807")
        libraryRepository.save(paperRepository.observeSearch(query).first().items.first { it.id == paperId })

        libraryRepository.unsave(paperId)

        val fromSearch = paperRepository.observeSearch(query).first().items.first { it.id == paperId }
        val fromDetail = paperRepository.observePaper(paperId).first()
        assertEquals(SavedState.NOT_SAVED, fromSearch.savedState)
        assertEquals(SavedState.NOT_SAVED, fromDetail?.savedState)
        assertTrue(libraryRepository.observeSavedPapers().first().none { it.id == paperId })
    }

    @Test
    fun savingAnInaccessiblePaperExposesNoAccessActionAnywhere() = runTest {
        paperRepository.search(PaperSearchRequest(query = query))
        val paperId = PaperId("W3987654321") // fixture with NO_FREE_FULL_TEXT access

        libraryRepository.save(paperRepository.observeSearch(query).first().items.first { it.id == paperId })

        val fromSearch = paperRepository.observeSearch(query).first().items.first { it.id == paperId }
        val fromDetail = paperRepository.observePaper(paperId).first()
        val fromLibrary = libraryRepository.observeSavedPapers().first().first { it.id == paperId }

        assertEquals(AccessResolution.NoFreeFullText, fromSearch.access)
        assertNull(fromSearch.access.toAccessAction())
        assertNull(fromDetail?.access?.toAccessAction())
        assertNull(fromLibrary.access.toAccessAction())
    }
}
