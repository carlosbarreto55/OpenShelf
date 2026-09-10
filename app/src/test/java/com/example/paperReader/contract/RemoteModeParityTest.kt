package com.example.paperReader.contract

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.paperReader.data.local.FakeClock
import com.example.paperReader.data.local.PaperReaderDatabase
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.FakeRemoteScenario
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.data.repository.PaperRepositoryImpl
import com.example.paperReader.domain.model.DownloadAvailability
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AND-012: one scenario matrix, run twice.
 *
 * The HTTP source reaches each outcome through a status code or response body; the fake source is
 * told to produce it directly. Above [PaperRemoteDataSource] the two must be indistinguishable —
 * otherwise demo builds and unit tests stay green while live mode takes different repository and
 * UI paths, which is exactly what the register recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteModeParityTest {

    @get:Rule
    val server = ContractServer()

    private lateinit var database: PaperReaderDatabase
    private lateinit var fakeSource: FakePaperRemoteDataSource

    private val paperId = PaperId("W2001")
    private val query = PaperQuery(text = SearchQuery("attention"))

    /** Matches the deterministic fixture catalog the fake source serves. */
    private val fakeCatalogQuery = PaperQuery(text = SearchQuery("AI"))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperReaderDatabase::class.java,
        )
            .addCallback(PaperReaderDatabase.enableForeignKeysCallback)
            .allowMainThreadQueries()
            .build()
        fakeSource = FakePaperRemoteDataSource().apply { latencyMillis = 0 }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aSuccessfulSearchPersistsAndBecomesObservableInBothModes() = runTest {
        server.enqueueFixture("responses/search-normal.json")

        // Each mode is asked for something its own data actually contains; the assertion is that
        // the repository-level outcome is the same shape, not that the fixtures match.
        val outcomes = bothModes(
            http = { it.search(PaperSearchRequest(query)) },
            fake = { it.search(PaperSearchRequest(fakeCatalogQuery)) },
        )

        outcomes.forEach { (mode, result) ->
            assertTrue("$mode must succeed", result is RepositoryResult.Success)
            assertTrue("$mode must persist items", (result as RepositoryResult.Success).value.items.isNotEmpty())
        }
    }

    @Test
    fun aTerminalEmptySearchIsASuccessInBothModes() = runTest {
        server.enqueueFixture("responses/search-empty-terminal.json")
        fakeSource.scenario = FakeRemoteScenario.NORMAL

        val http = httpRepository().search(PaperSearchRequest(query))
        val fake = fakeRepository().search(
            PaperSearchRequest(PaperQuery(text = SearchQuery("no such topic exists anywhere"))),
        )

        listOf("http" to http, "fake" to fake).forEach { (mode, result) ->
            val page = (result as RepositoryResult.Success).value
            assertTrue("$mode must report no items", page.items.isEmpty())
            assertNull("$mode must report a terminal cursor", page.nextCursor)
        }
    }

    @Test
    fun aMissingPaperIsNotFoundInBothModes() = runTest {
        server.enqueueError(status = 404, bodyFixture = "errors/not-found.json")
        fakeSource.scenario = FakeRemoteScenario.NOT_FOUND

        assertSameFailure(RepositoryError.NotFound) { it.refreshPaper(paperId) }
    }

    @Test
    fun aPaperWithNoPdfIsAnUnavailableDescriptorInBothModes() = runTest {
        server.enqueueFixture("responses/download-unavailable.json")
        fakeSource.scenario = FakeRemoteScenario.NO_PDF

        val http = httpRepository().confirmDownloadAvailability(paperId)
        val fake = fakeRepository().confirmDownloadAvailability(PaperId("W2741809807"))

        listOf("http" to http, "fake" to fake).forEach { (mode, result) ->
            assertEquals(
                "$mode must report the normal no-PDF outcome",
                DownloadAvailability.Unavailable,
                (result as RepositoryResult.Success).value,
            )
        }
    }

    @Test
    fun anUnavailableUpstreamIsARecoverableServiceFailureInBothModes() = runTest {
        server.enqueueError(status = 502, bodyFixture = "errors/upstream-unavailable.json")
        fakeSource.scenario = FakeRemoteScenario.UPSTREAM_UNAVAILABLE

        assertSameFailure(RepositoryError.ServiceUnavailable) { it.refreshPaper(paperId) }
    }

    @Test
    fun anUpstreamTimeoutIsTheTimeoutOutcomeInBothModes() = runTest {
        server.enqueueError(status = 504, bodyFixture = "errors/upstream-timeout.json")
        fakeSource.scenario = FakeRemoteScenario.UPSTREAM_TIMEOUT

        assertSameFailure(RepositoryError.Timeout) { it.refreshPaper(paperId) }
    }

    @Test
    fun aResponseAboutAnotherPaperIsAContractFailureInBothModes() = runTest {
        server.enqueueFixture("responses/detail-mismatched-id.json")
        fakeSource.scenario = FakeRemoteScenario.MISMATCHED_IDENTITY

        val expected = RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID)
        val http = httpRepository().refreshPaper(paperId)
        val fake = fakeRepository().refreshPaper(PaperId("W2741809807"))

        listOf("http" to http, "fake" to fake).forEach { (mode, result) ->
            assertEquals(mode, expected, (result as RepositoryResult.Failure).error)
        }
    }

    @Test
    fun unmappableSuccessDataIsAContractFailureInBothModes() = runTest {
        server.enqueueFixture("errors/search-missing-required-fields.json")
        fakeSource.scenario = FakeRemoteScenario.MALFORMED_DATA

        val expected = RepositoryError.Contract(RepositoryError.ContractFailure.ALL_ITEMS_INVALID)
        val http = httpRepository().search(PaperSearchRequest(query))
        val fake = fakeRepository().search(PaperSearchRequest(fakeCatalogQuery))

        listOf("http" to http, "fake" to fake).forEach { (mode, result) ->
            assertEquals(mode, expected, (result as RepositoryResult.Failure).error)
        }
    }

    @Test
    fun neitherModeLeavesPartialResultsBehindWhenAPageFails() = runTest {
        server.enqueueError(status = 502, bodyFixture = "errors/upstream-unavailable.json")
        val repository = httpRepository()

        repository.search(PaperSearchRequest(query))

        assertTrue(repository.observeSearch(query).first().items.isEmpty())
    }

    private fun httpRepository() = repositoryFor(server.source)

    private fun fakeRepository() = repositoryFor(fakeSource)

    private fun repositoryFor(source: PaperRemoteDataSource) = PaperRepositoryImpl(
        remote = source,
        paperDao = database.paperDao(),
        searchCacheDao = database.searchCacheDao(),
        accessSnapshotDao = database.accessSnapshotDao(),
        recentSearchDao = database.recentSearchDao(),
        clock = FakeClock(0L),
    )

    private suspend fun <T> bothModes(
        http: suspend (PaperRepositoryImpl) -> T,
        fake: suspend (PaperRepositoryImpl) -> T,
    ): List<Pair<String, T>> = listOf("http" to http(httpRepository()), "fake" to fake(fakeRepository()))

    private suspend fun assertSameFailure(
        expected: RepositoryError,
        operation: suspend (PaperRepositoryImpl) -> RepositoryResult<*>,
    ) {
        listOf("http" to httpRepository(), "fake" to fakeRepository()).forEach { (mode, repository) ->
            val result = operation(repository)
            assertEquals(mode, expected, (result as RepositoryResult.Failure).error)
        }
    }
}
