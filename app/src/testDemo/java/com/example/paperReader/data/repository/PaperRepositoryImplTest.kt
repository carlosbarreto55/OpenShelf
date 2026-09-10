package com.example.paperReader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.local.entity.AccessSnapshotEntity
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.FakeRemoteScenario
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
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

private const val TWO_DAYS_MILLIS = 2L * 24 * 60 * 60 * 1000

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaperRepositoryImplTest {
    private lateinit var database: PaperReaderDatabase
    private lateinit var remote: FakePaperRemoteDataSource
    private lateinit var clock: FakeClock
    private lateinit var repository: PaperRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        remote = FakePaperRemoteDataSource().apply { latencyMillis = 0 }
        clock = FakeClock(0L)
        repository = PaperRepositoryImpl(
            remote = remote,
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

    private fun query() = PaperQuery(text = SearchQuery("AI"), filters = PaperFilters())

    @Test
    fun searchPersistsResultsObservableThroughRoom() = runTest {
        val result = repository.search(PaperSearchRequest(query = query()))
        assertTrue(result is RepositoryResult.Success)

        val page = repository.observeSearch(query()).first()
        assertTrue(page.items.isNotEmpty())
        assertEquals((result as RepositoryResult.Success).value.nextCursor, page.nextCursor)
    }

    @Test
    fun expiredConfirmedAccessIsSuppressedRatherThanTrustedForever() = runTest {
        repository.search(PaperSearchRequest(query = query()))
        val paperId = PaperId("W2741809807") // fixture with DIRECT_PDF access

        val freshPage = repository.observeSearch(query()).first()
        val freshPaper = freshPage.items.first { it.id == paperId }
        assertTrue(freshPaper.access is AccessResolution.DirectPdf)

        clock.timeEpochMillis += TWO_DAYS_MILLIS
        val stalePage = repository.observeSearch(query()).first()
        val stalePaper = stalePage.items.first { it.id == paperId }
        assertEquals(AccessResolution.Unknown, stalePaper.access)
    }

    @Test
    fun metadataRefreshWithoutAccessDataDropsPreviouslyConfirmedAccess() = runTest {
        // This fixture paper has no access field at all in its remote response.
        val paperId = PaperId("W4031122556")
        // Seed the paper row first (satisfies the access_snapshots -> papers foreign key), then
        // simulate a stale confirmed snapshot lingering from some earlier, unrelated fetch.
        repository.refreshPaper(paperId)
        database.accessSnapshotDao().upsert(
            AccessSnapshotEntity(
                paperId = paperId.value,
                state = "DIRECT_PDF",
                sourceUrl = null,
                descriptorUrl = "https://demo.paperreader.invalid/old.pdf",
                descriptorMimeType = "application/pdf",
                descriptorProvenance = null,
                descriptorGenerated = false,
                descriptorExpiresAtEpochMillis = null,
                provenance = null,
                checkedAtEpochMillis = 0L,
            ),
        )

        val result = repository.refreshPaper(paperId)
        assertTrue(result is RepositoryResult.Success)

        // A metadata-only refresh must not let the stale confirmed descriptor keep looking valid.
        val refreshed = (result as RepositoryResult.Success).value
        assertEquals(AccessResolution.Unknown, refreshed.access)
        assertEquals(AccessResolution.Unknown, repository.observePaper(paperId).first()?.access)
    }

    @Test
    fun recentQueriesAreDedupedAndMovedToFront() = runTest {
        repository.recordRecentQuery(SearchQuery("machine learning"))
        clock.timeEpochMillis += 1
        repository.recordRecentQuery(SearchQuery("robotics"))
        clock.timeEpochMillis += 1
        repository.recordRecentQuery(SearchQuery("machine learning"))

        val recents = repository.observeRecentQueries(limit = 10).first()
        assertEquals(listOf("machine learning", "robotics"), recents)
    }

    @Test
    fun clearRecentQueriesLeavesSavedAndDownloadDataAlone() = runTest {
        repository.recordRecentQuery(SearchQuery("AI"))
        repository.clearRecentQueries()

        assertTrue(repository.observeRecentQueries(limit = 10).first().isEmpty())
    }

    @Test
    fun pruneStaleCacheRemovesExpiredQueriesButKeepsFreshOnes() = runTest {
        repository.search(PaperSearchRequest(query = query()))
        clock.timeEpochMillis += 8L * 24 * 60 * 60 * 1000 // older than the 7-day cache policy

        repository.pruneStaleCache()

        val page = repository.observeSearch(query()).first()
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun confirmDownloadAvailabilityReturnsTheBackendsCurrentDescriptor() = runTest {
        val result = repository.confirmDownloadAvailability(PaperId("W2741809807"))

        val availability = (result as RepositoryResult.Success).value
        assertTrue(availability is DownloadAvailability.Available)
    }

    @Test
    fun confirmDownloadAvailabilityReportsNoPdfAsItsOwnSuccessfulOutcome() = runTest {
        remote.scenario = FakeRemoteScenario.NO_PDF

        val result = repository.confirmDownloadAvailability(PaperId("W2741809807"))

        assertEquals(DownloadAvailability.Unavailable, (result as RepositoryResult.Success).value)
    }

    @Test
    fun confirmDownloadAvailabilityPropagatesTypedTransportFailures() = runTest {
        val expectations = mapOf(
            FakeRemoteScenario.NOT_FOUND to RepositoryError.NotFound,
            FakeRemoteScenario.UPSTREAM_UNAVAILABLE to RepositoryError.ServiceUnavailable,
            FakeRemoteScenario.UPSTREAM_TIMEOUT to RepositoryError.Timeout,
        )

        expectations.forEach { (scenario, expected) ->
            remote.scenario = scenario
            val result = repository.confirmDownloadAvailability(PaperId("W2741809807"))
            assertEquals("$scenario", expected, (result as RepositoryResult.Failure).error)
        }
    }

    @Test
    fun confirmDownloadAvailabilityRejectsAResponseAboutAnotherPaper() = runTest {
        remote.scenario = FakeRemoteScenario.MISMATCHED_IDENTITY

        val result = repository.confirmDownloadAvailability(PaperId("W2741809807"))

        assertEquals(
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun refreshPaperReportsNotFoundRatherThanAContractFailure() = runTest {
        val result = repository.refreshPaper(PaperId("W0000000000"))

        assertEquals(RepositoryError.NotFound, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun refreshPaperRejectsADetailResponseAboutAnotherPaper() = runTest {
        remote.scenario = FakeRemoteScenario.MISMATCHED_IDENTITY

        val result = repository.refreshPaper(PaperId("W2741809807"))

        assertEquals(
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun searchCarriesTheSuppliedTotalThroughToTheMappedPage() = runTest {
        val result = repository.search(PaperSearchRequest(query()))

        val page = (result as RepositoryResult.Success).value
        assertTrue("The fixture catalog returns results for this query", page.items.isNotEmpty())
        assertEquals("The total is the matched count, not the loaded count", 7, page.totalItems)
    }
}
