package com.example.paperReader.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.entity.DownloadEntity
import com.example.paperReader.data.local.entity.ReadingPositionEntity
import com.example.paperReader.domain.model.DownloadState
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
class ReadingPositionDaoTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var positions: ReadingPositionDao
    private lateinit var downloads: DownloadDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        ).addCallback(PaperReaderDatabase.enableForeignKeysCallback).allowMainThreadQueries().build()
        positions = database.readingPositionDao()
        downloads = database.downloadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertInsertsThenReplacesTheOnePositionPerPaper() = runTest {
        positions.upsert(position("W1", page = 2, at = 100))
        positions.upsert(position("W1", page = 7, offset = 0.5f, at = 200))

        assertEquals(position("W1", page = 7, offset = 0.5f, at = 200), positions.get("W1"))
        assertNull(positions.get("W2"))
    }

    @Test
    fun removingADownloadRemovesItsPosition() = runTest {
        downloads.upsert(download("W1"))
        downloads.upsert(download("W2"))
        positions.upsert(position("W1", at = 100))
        positions.upsert(position("W2", at = 100))

        downloads.delete("W1")

        assertNull(positions.get("W1"))
        assertEquals("another paper's position is untouched", 2, positions.get("W2")?.pageIndex)
    }

    @Test
    fun rewritingADownloadRecordKeepsItsPosition() = runTest {
        // The record is rewritten with REPLACE on every progress step and state change. Were the
        // position tied to it by a cascading foreign key, this is where it would silently vanish.
        downloads.upsert(download("W1", state = DownloadState.RUNNING, uri = null))
        positions.upsert(position("W1", at = 100))

        downloads.upsert(download("W1", state = DownloadState.COMPLETED))

        assertEquals(2, positions.get("W1")?.pageIndex)
    }

    @Test
    fun resumablePapersAreTheMostRecentlyReadFirst() = runTest {
        listOf("W1", "W2", "W3").forEach { downloads.upsert(download(it)) }
        positions.upsert(position("W1", at = 100))
        positions.upsert(position("W2", at = 300))
        positions.upsert(position("W3", at = 200))

        assertEquals(listOf("W2", "W3", "W1"), positions.observeResumable().first().map { it.paperId })
    }

    @Test
    fun onlyACompletedDownloadThatNamesAFileIsResumable() = runTest {
        downloads.upsert(download("W1"))
        downloads.upsert(download("W2", state = DownloadState.RUNNING, uri = null))
        downloads.upsert(download("W3", state = DownloadState.FAILED, uri = null))
        listOf("W1", "W2", "W3", "W4").forEach { positions.upsert(position(it, at = 100)) }

        val resumable = positions.observeResumable().first()

        assertEquals(listOf("W1"), resumable.map { it.paperId })
        assertEquals("content://app/W1.pdf", resumable.single().localContentUri)
    }

    private fun position(paperId: String, page: Int = 2, offset: Float = 0f, at: Long) =
        ReadingPositionEntity(paperId = paperId, pageIndex = page, pageOffsetFraction = offset, updatedAtEpochMillis = at)

    private fun download(
        paperId: String,
        state: DownloadState = DownloadState.COMPLETED,
        uri: String? = "content://app/$paperId.pdf",
    ) = DownloadEntity(
        id = paperId,
        paperId = paperId,
        title = "Title $paperId",
        state = state,
        sourceUrl = "https://cdn.example.org/$paperId.pdf",
        mimeType = "application/pdf",
        bytesDownloaded = 10,
        totalBytes = 10,
        localContentUri = uri,
        errorMessage = null,
        updatedAtEpochMillis = 0,
    )
}
