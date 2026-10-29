package com.example.paperReader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.data.local.entity.ReadingPositionEntity
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.repository.DownloadedFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingPositionRepositoryImplTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var repository: ReadingPositionRepositoryImpl
    private val clock = FakeClock(0L)
    private val presentFiles = mutableSetOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReadingPositionRepositoryImpl(database.readingPositionDao(), DownloadedFiles { it in presentFiles }, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aSavedPositionIsReadBackForItsPaperOnly() = runTest {
        repository.save(PaperId("W1"), ReadingPosition(pageIndex = 5, pageOffsetFraction = 0.4f))

        assertEquals(ReadingPosition(5, 0.4f), repository.position(PaperId("W1")))
        assertNull(repository.position(PaperId("W2")))
    }

    @Test
    fun anOutOfRangeStoredRowStartsAtTheTopRatherThanFailing() = runTest {
        database.readingPositionDao().upsert(ReadingPositionEntity("W1", pageIndex = -1, pageOffsetFraction = 3f, updatedAtEpochMillis = 0))

        assertNull(repository.position(PaperId("W1")))
    }

    @Test
    fun aPaperWhoseFileWasRemovedNeverAppearsAsResumable() = runTest {
        listOf("W1", "W2").forEach { completedDownload(it) }
        presentFiles += "content://app/W1.pdf" // W2's record says completed, but its file is gone
        repository.save(PaperId("W1"), ReadingPosition.START)
        repository.save(PaperId("W2"), ReadingPosition.START)

        assertEquals(listOf(PaperId("W1")), repository.observeResumable().first().map { it.paperId })
    }

    @Test
    fun resumablePapersAreOrderedByMostRecentlyRead() = runTest {
        listOf("W1", "W2", "W3").forEach {
            completedDownload(it)
            presentFiles += "content://app/$it.pdf"
        }
        for ((at, id) in listOf(100L to "W2", 200L to "W1", 300L to "W3")) {
            clock.timeEpochMillis = at
            repository.save(PaperId(id), ReadingPosition(pageIndex = 1, pageOffsetFraction = 0f))
        }

        val resumable = repository.observeResumable().first()

        assertEquals(listOf("W3", "W1", "W2"), resumable.map { it.paperId.value })
        assertEquals(300L, resumable.first().lastReadAtEpochMillis)
    }

    private suspend fun completedDownload(paperId: String) {
        database.downloadDao().upsert(
            DownloadEntity(
                id = paperId,
                paperId = paperId,
                title = "Title $paperId",
                state = DownloadState.COMPLETED,
                sourceUrl = "https://cdn.example.org/$paperId.pdf",
                mimeType = "application/pdf",
                bytesDownloaded = 10,
                totalBytes = 10,
                localContentUri = "content://app/$paperId.pdf",
                errorMessage = null,
                updatedAtEpochMillis = 0,
            ),
        )
    }
}
