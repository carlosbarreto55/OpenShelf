package com.example.paperReader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryImplTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var clock: FakeClock
    private lateinit var libraryRepository: LibraryRepositoryImpl
    private lateinit var paperRepository: PaperRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        clock = FakeClock(0L)
        libraryRepository = LibraryRepositoryImpl(
            savedPaperDao = database.savedPaperDao(),
            paperDao = database.paperDao(),
            accessSnapshotDao = database.accessSnapshotDao(),
            clock = clock,
        )
        paperRepository = PaperRepositoryImpl(
            remote = FakePaperRemoteDataSource().apply { latencyMillis = 0 },
            paperDao = database.paperDao(),
            searchCacheDao = database.searchCacheDao(),
            accessSnapshotDao = database.accessSnapshotDao(),
            recentSearchDao = database.recentSearchDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingAPaperMakesItObservableInTheLibrary() = runTest {
        libraryRepository.save(TestFixtures.paper("W1", access = TestFixtures.directPdf()))

        val saved = libraryRepository.observeSavedPapers().first()
        assertEquals(listOf("W1"), saved.map { it.id.value })
        assertTrue(saved.first().access is AccessResolution.DirectPdf)
    }

    @Test
    fun unsavingRemovesFromLibraryButLeavesDownloadRecordIntact() = runTest {
        libraryRepository.save(TestFixtures.paper("W1"))
        database.downloadDao().upsert(
            DownloadEntity(
                id = "d1",
                paperId = "W1",
                title = "Demo title",
                state = DownloadState.COMPLETED,
                sourceUrl = "https://demo.paperreader.invalid/papers/W1.pdf",
                mimeType = "application/pdf",
                bytesDownloaded = 100L,
                totalBytes = 100L,
                localContentUri = "content://demo/1",
                errorMessage = null,
                updatedAtEpochMillis = 0L,
            ),
        )

        libraryRepository.unsave(PaperId("W1"))

        assertTrue(libraryRepository.observeSavedPapers().first().isEmpty())
        assertEquals(DownloadState.COMPLETED, database.downloadDao().get("d1")?.state)
    }

    @Test
    fun savedStateSurvivesAFreshSearchRecachingTheSamePaper() = runTest {
        libraryRepository.save(TestFixtures.paper("W2741809807"))

        paperRepository.search(
            PaperSearchRequest(query = PaperQuery(text = SearchQuery("AI"), filters = PaperFilters())),
        )

        val saved = libraryRepository.observeSavedPapers().first()
        assertEquals(listOf("W2741809807"), saved.map { it.id.value })
    }
}
