package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.data.remote.dto.SearchFiltersDto
import com.example.paperReader.data.remote.fixtures.PaperFixtures
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Canonical outcomes the fake source can reproduce on demand.
 *
 * These are the transport-independent results of the HTTP cases the register lists under AND-012.
 * The mechanism differs — HTTP uses a status code, this source injects the outcome directly — but
 * everything above [PaperRemoteDataSource] must see the same thing either way.
 */
enum class FakeRemoteScenario {
    /** Serve the fixture catalog normally. */
    NORMAL,

    /** Every operation reports the paper does not exist, as HTTP 404 would. */
    NOT_FOUND,

    /** The paper exists but the download endpoint has no descriptor for it (HTTP 200, null). */
    NO_PDF,

    /** Search returns no items but keeps handing back a new cursor. */
    EMPTY_PAGES_WITH_CURSOR,

    /** The backend's upstream is unavailable, as HTTP 502 would report. */
    UPSTREAM_UNAVAILABLE,

    /** The backend's upstream timed out, as HTTP 504 would report. */
    UPSTREAM_TIMEOUT,

    /** A successful response that describes a different paper than the one requested. */
    MISMATCHED_IDENTITY,

    /** A 2xx body that cannot be mapped: present, but without usable identity or title. */
    MALFORMED_DATA,
}

/**
 * Default offline binding until a real PaperReader backend exists. Fixture papers are loaded from
 * [PaperFixtures] and served through the same cursor-page, filter/sort, and error-mapping behavior
 * an HTTP backend would use, so this is demo data, not a demo code path.
 */
class FakePaperRemoteDataSource @Inject constructor() : PaperRemoteDataSource {

    /** Simulated network delay. Set to `0` in tests for fast, deterministic execution. */
    var latencyMillis: Long = DEFAULT_LATENCY_MILLIS

    /** When set, every subsequent call fails with this error until cleared. Lets callers
     * demonstrate or test offline/timeout/contract/error UI without a real backend. */
    var forcedFailure: RepositoryError? = null

    /** Selects a canonical edge case; see [FakeRemoteScenario]. */
    var scenario: FakeRemoteScenario = FakeRemoteScenario.NORMAL

    private val catalog: List<PaperDto> = PaperFixtures.catalog

    override suspend fun search(request: PaperSearchRequestDto): RepositoryResult<PaperSearchResponseDto> {
        return respond {
            when (scenario) {
                FakeRemoteScenario.EMPTY_PAGES_WITH_CURSOR -> {
                    // A valid contract-conforming sequence that carries no visible items: the
                    // client must keep the cursor rather than call the query terminal.
                    val page = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    RepositoryResult.Success(
                        PaperSearchResponseDto(
                            items = emptyList(),
                            nextCursor = (page + PAGE_SIZE).toString(),
                            totalCount = null,
                        ),
                    )
                }

                FakeRemoteScenario.MALFORMED_DATA -> RepositoryResult.Success(
                    PaperSearchResponseDto(items = listOf(MALFORMED_ITEM), nextCursor = null),
                )

                else -> {
                    val matches = catalog
                        .filter { it.matchesQuery(request.query) && it.matchesFilters(request.filters) }
                        .sortedWith(request.sort.toComparator())
                    val offset = request.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val page = matches.drop(offset).take(PAGE_SIZE)
                    val nextCursor = (offset + PAGE_SIZE).takeIf { it < matches.size }?.toString()
                    RepositoryResult.Success(
                        PaperSearchResponseDto(
                            items = page,
                            nextCursor = nextCursor,
                            totalCount = matches.size,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun getPaper(paperId: PaperId): RepositoryResult<PaperDetailResponseDto> {
        return respond {
            when (scenario) {
                FakeRemoteScenario.MISMATCHED_IDENTITY -> RepositoryResult.Success(
                    PaperDetailResponseDto(paper = catalog.first { it.id != paperId.value }),
                )

                FakeRemoteScenario.MALFORMED_DATA -> RepositoryResult.Success(
                    PaperDetailResponseDto(paper = MALFORMED_ITEM),
                )

                else -> findPaper(paperId)
                    ?.let { RepositoryResult.Success(PaperDetailResponseDto(paper = it)) }
                    ?: notFound()
            }
        }
    }

    override suspend fun getDownloadDescriptor(paperId: PaperId): RepositoryResult<DownloadDescriptorResponseDto> {
        return respond {
            when (scenario) {
                FakeRemoteScenario.NO_PDF -> RepositoryResult.Success(
                    DownloadDescriptorResponseDto(paperId = paperId.value, descriptor = null),
                )

                FakeRemoteScenario.MISMATCHED_IDENTITY -> RepositoryResult.Success(
                    DownloadDescriptorResponseDto(
                        paperId = catalog.first { it.id != paperId.value }.id,
                        descriptor = catalog.firstNotNullOf { it.access?.descriptor },
                    ),
                )

                FakeRemoteScenario.MALFORMED_DATA -> RepositoryResult.Success(
                    DownloadDescriptorResponseDto(paperId = null, descriptor = null),
                )

                else -> findPaper(paperId)?.let {
                    RepositoryResult.Success(
                        DownloadDescriptorResponseDto(paperId = paperId.value, descriptor = it.access?.descriptor),
                    )
                } ?: notFound()
            }
        }
    }

    private suspend fun <T> respond(block: () -> RepositoryResult<T>): RepositoryResult<T> {
        if (latencyMillis > 0) delay(latencyMillis)
        forcedFailure?.let { return RepositoryResult.Failure(it) }
        scenarioFailure()?.let { return RepositoryResult.Failure(it) }
        return block()
    }

    /** The scenarios whose HTTP counterpart is a status code rather than a response body. */
    private fun scenarioFailure(): RepositoryError? = when (scenario) {
        FakeRemoteScenario.NOT_FOUND -> RepositoryError.NotFound
        FakeRemoteScenario.UPSTREAM_UNAVAILABLE -> RepositoryError.ServiceUnavailable
        FakeRemoteScenario.UPSTREAM_TIMEOUT -> RepositoryError.Timeout
        else -> null
    }

    private fun findPaper(paperId: PaperId): PaperDto? {
        return catalog.firstOrNull { it.id == paperId.value }
    }

    /** An unknown paper is HTTP 404 on the wire, so it is [RepositoryError.NotFound] here too —
     * not the client-side contract failure the register recorded as AND-012 drift. */
    private fun <T> notFound(): RepositoryResult<T> = RepositoryResult.Failure(RepositoryError.NotFound)

    private fun PaperDto.matchesQuery(query: String): Boolean {
        val needle = query.trim().lowercase(Locale.US)
        if (needle.isEmpty()) return true
        if (title?.lowercase(Locale.US)?.contains(needle) == true) return true
        if (topics.any { it.name?.lowercase(Locale.US)?.contains(needle) == true }) return true
        if (authors.any { it.name?.lowercase(Locale.US)?.contains(needle) == true }) return true
        return false
    }

    private fun PaperDto.matchesFilters(filters: SearchFiltersDto): Boolean {
        val state = access?.state?.uppercase(Locale.US)
        // Null and false both mean "unrestricted", exactly as on the wire.
        if (filters.openAccessOnly == true && state !in OPEN_ACCESS_STATES) return false
        if (filters.pdfAvailableOnly == true && state != "DIRECT_PDF") return false
        if (filters.publicationYear != null && year != filters.publicationYear) return false
        if (!filters.workType.isNullOrBlank() &&
            com.example.paperReader.domain.model.PaperType.fromWireValue(type) !=
            com.example.paperReader.domain.model.PaperType.fromWireValue(filters.workType)
        ) {
            return false
        }
        val topicFilter = filters.topic
        if (!topicFilter.isNullOrBlank()) {
            val needle = topicFilter.lowercase(Locale.US)
            if (topics.none { it.name?.lowercase(Locale.US)?.contains(needle) == true }) return false
        }
        return true
    }

    private fun String.toComparator(): Comparator<PaperDto> {
        return when (lowercase(Locale.US)) {
            "newest" -> compareByDescending { it.year ?: Int.MIN_VALUE }
            "citations" -> compareByDescending { it.citationCount ?: 0 }
            else -> Comparator { _, _ -> 0 }
        }
    }

    private companion object {
        const val DEFAULT_LATENCY_MILLIS = 350L
        const val PAGE_SIZE = 3
        val OPEN_ACCESS_STATES = setOf("DIRECT_PDF", "OPEN_FULL_TEXT")

        /** Present in the response but unmappable: no stable ID and no title. */
        val MALFORMED_ITEM = PaperDto(id = null, title = null)
    }
}
