package com.example.paperReader.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.TestFixtures
import com.example.paperReader.data.local.entity.SavedPaperEntity
import com.example.paperReader.data.local.entity.SearchQueryEntity
import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchCacheDaoTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var searchCacheDao: SearchCacheDao
    private lateinit var paperDao: PaperDao
    private lateinit var savedPaperDao: SavedPaperDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        searchCacheDao = database.searchCacheDao()
        paperDao = database.paperDao()
        savedPaperDao = database.savedPaperDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun query(key: String = "ai|relevance|false|false|||", cursor: String? = "3") = SearchQueryEntity(
        queryKey = key,
        queryText = "ai",
        sortOrder = "relevance",
        openAccessOnly = false,
        pdfAvailableOnly = false,
        publicationYear = null,
        workType = null,
        topic = null,
        nextCursor = cursor,
        totalCount = null,
        fetchedAtEpochMillis = 1_000L,
    )

    /** Persists one page the way [com.example.paperReader.data.repository.PaperRepositoryImpl] does. */
    private suspend fun persist(
        key: String = query().queryKey,
        paperIds: List<String>,
        cursor: String? = null,
        total: Int? = null,
        isFirstPage: Boolean,
        fetchedAt: Long = 1_000L,
    ) {
        searchCacheDao.persistPage(
            query = query(key = key, cursor = cursor).copy(totalCount = total, fetchedAtEpochMillis = fetchedAt),
            papers = paperIds.distinct().map { TestFixtures.paperEntity(it) },
            accessSnapshots = emptyList(),
            orderedPaperIds = paperIds,
            isFirstPage = isFirstPage,
        )
    }

    @Test
    fun firstPageReplacesThePriorOrderRatherThanAppendingToIt() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2"), cursor = "3", isFirstPage = true)

        persist(paperIds = listOf("W3"), cursor = null, isFirstPage = true)

        assertEquals(listOf("W3"), searchCacheDao.observeResults(key).first().map { it.id })
        assertNull(searchCacheDao.getQuery(key)?.nextCursor)
    }

    @Test
    fun appendPreservesExistingPositionsAndExtendsTheOrder() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2"), cursor = "2", isFirstPage = true)

        persist(paperIds = listOf("W3"), cursor = null, isFirstPage = false)

        assertEquals(listOf("W1", "W2", "W3"), searchCacheDao.observeResults(key).first().map { it.id })
    }

    @Test
    fun onePageContainingTheSameIdTwiceStoresOnlyItsFirstOccurrence() = runTest {
        val key = query().queryKey

        persist(paperIds = listOf("W1", "W2", "W1"), isFirstPage = true)

        assertEquals(listOf("W1", "W2"), searchCacheDao.observeResults(key).first().map { it.id })
    }

    @Test
    fun anOverlappingAppendKeepsTheOriginalPositionAndAppendsOnlyUnseenIds() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2"), cursor = "2", isFirstPage = true)

        persist(paperIds = listOf("W2", "W3", "W1", "W4"), cursor = "4", isFirstPage = false)

        assertEquals(
            listOf("W1", "W2", "W3", "W4"),
            searchCacheDao.observeResults(key).first().map { it.id },
        )
    }

    @Test
    fun anAppendMadeEntirelyOfAlreadySeenIdsAddsNothingButStillAdvancesTheCursor() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2"), cursor = "2", isFirstPage = true)

        persist(paperIds = listOf("W1", "W2"), cursor = "4", isFirstPage = false)

        assertEquals(listOf("W1", "W2"), searchCacheDao.observeResults(key).first().map { it.id })
        assertEquals("4", searchCacheDao.getQuery(key)?.nextCursor)
    }

    @Test
    fun repeatedAppendsOfTheSamePageStayIdempotent() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1"), cursor = "1", isFirstPage = true)

        repeat(3) { persist(paperIds = listOf("W2"), cursor = "2", isFirstPage = false) }

        assertEquals(listOf("W1", "W2"), searchCacheDao.observeResults(key).first().map { it.id })
    }

    @Test
    fun firstSeenOrderSurvivesReopeningTheCache() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2"), cursor = "2", isFirstPage = true)
        persist(paperIds = listOf("W2", "W3"), cursor = null, isFirstPage = false)
        val beforeRestart = searchCacheDao.observeResults(key).first().map { it.id }

        // The Flow is re-collected from a freshly opened DAO handle, which is what a restart does.
        val afterRestart = database.searchCacheDao().observeResults(key).first().map { it.id }

        assertEquals(listOf("W1", "W2", "W3"), beforeRestart)
        assertEquals(beforeRestart, afterRestart)
    }

    @Test
    fun appendsStartAfterTheHighestPositionNotTheRowCount() = runTest {
        val key = query().queryKey
        persist(paperIds = listOf("W1", "W2", "W3"), cursor = "3", isFirstPage = true)
        // Simulates a migrated cache with a gap: one association was removed as a duplicate.
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM search_results WHERE queryKey = '$key' AND position = 1",
        )

        persist(paperIds = listOf("W4"), cursor = null, isFirstPage = false)

        assertEquals(listOf("W1", "W3", "W4"), searchCacheDao.observeResults(key).first().map { it.id })
    }

    @Test
    fun aFirstPageClearsOnlyItsOwnQuerysAssociations() = runTest {
        persist(key = "query-a", paperIds = listOf("W1", "W2"), isFirstPage = true)
        persist(key = "query-b", paperIds = listOf("W1", "W3"), isFirstPage = true)

        persist(key = "query-a", paperIds = listOf("W9"), isFirstPage = true)

        assertEquals(listOf("W9"), searchCacheDao.observeResults("query-a").first().map { it.id })
        assertEquals(listOf("W1", "W3"), searchCacheDao.observeResults("query-b").first().map { it.id })
    }

    @Test
    fun persistingAPageAlsoStoresItsAccessSnapshotsInTheSameTransaction() = runTest {
        val key = query().queryKey

        searchCacheDao.persistPage(
            query = query(cursor = null),
            papers = listOf(TestFixtures.paperEntity("W1")),
            accessSnapshots = listOf(
                AccessSnapshotEntity(
                    paperId = "W1",
                    state = "DIRECT_PDF",
                    sourceUrl = null,
                    descriptorUrl = "https://cdn.example.org/W1.pdf",
                    descriptorMimeType = "application/pdf",
                    descriptorProvenance = null,
                    descriptorGenerated = false,
                    descriptorExpiresAtEpochMillis = null,
                    provenance = null,
                    checkedAtEpochMillis = 1_000L,
                ),
            ),
            orderedPaperIds = listOf("W1"),
            isFirstPage = true,
        )

        assertEquals(1, searchCacheDao.observeResults(key).first().size)
        assertNotNull(database.accessSnapshotDao().get("W1"))
    }

    @Test
    fun samePaperReusedAcrossTwoDifferentQueriesIsNotDuplicated() = runTest {
        searchCacheDao.persistPage(
            query = query(key = "query-a", cursor = null),
            papers = listOf(TestFixtures.paperEntity("W1", title = "Original title")),
            accessSnapshots = emptyList(),
            orderedPaperIds = listOf("W1"),
            isFirstPage = true,
        )
        searchCacheDao.persistPage(
            query = query(key = "query-b", cursor = null),
            papers = listOf(TestFixtures.paperEntity("W1", title = "Updated title")),
            accessSnapshots = emptyList(),
            orderedPaperIds = listOf("W1"),
            isFirstPage = true,
        )

        assertEquals("Updated title", paperDao.get("W1")?.title)
        assertEquals(1, searchCacheDao.observeResults("query-a").first().size)
        assertEquals(1, searchCacheDao.observeResults("query-b").first().size)
    }

    @Test
    fun deletingStaleQueryCascadesToItsResultsOnly() = runTest {
        persist(key = "stale", paperIds = listOf("W1"), isFirstPage = true, fetchedAt = 1_000L)
        persist(key = "fresh", paperIds = listOf("W2"), isFirstPage = true, fetchedAt = 9_000L)

        searchCacheDao.deleteStaleQueries(olderThanEpochMillis = 5_000L)

        assertTrue(searchCacheDao.observeResults("stale").first().isEmpty())
        assertEquals(1, searchCacheDao.observeResults("fresh").first().size)
    }

    @Test
    fun pruningLeavesSavedPapersInPlaceAfterCrossRefsAreGone() = runTest {
        persist(key = "q", paperIds = listOf("W1", "W2"), isFirstPage = true)
        savedPaperDao.upsert(SavedPaperEntity(paperId = "W1", savedAtEpochMillis = 500L))

        searchCacheDao.deleteStaleQueries(olderThanEpochMillis = Long.MAX_VALUE)
        val deleted = paperDao.deleteOrphanedPapers()

        assertEquals(1, deleted) // only W2 was unreferenced
        assertTrue(paperDao.get("W1") != null)
        assertNull(paperDao.get("W2"))
    }
}
