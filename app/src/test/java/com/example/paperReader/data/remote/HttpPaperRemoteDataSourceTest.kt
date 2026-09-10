package com.example.paperReader.data.remote

import com.example.paperReader.contract.ContractServer
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Transport outcomes, asserted against a real HTTP server rather than an in-process
 * [PaperReaderApi] double.
 *
 * SHR-001 recorded that both repositories stayed green while disagreeing, because Android's HTTP
 * tests never exercised status handling or serialization. Everything here goes through Retrofit,
 * OkHttp, and the production [RemoteJson] converter.
 */
class HttpPaperRemoteDataSourceTest {

    @get:Rule
    val server = ContractServer()

    private val paperId = PaperId("W2001")

    @Test
    fun successfulResponseIsWrappedAsSuccess() = runBlocking {
        server.enqueueFixture("responses/search-normal.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        assertTrue(result is RepositoryResult.Success)
    }

    @Test
    fun notFoundBecomesTheFirstClassNotFoundOutcome() = runBlocking {
        server.enqueueError(status = 404, bodyFixture = "errors/not-found.json")

        val result = server.source.getPaper(paperId)

        assertEquals(RepositoryError.NotFound, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun notFoundIsNotRecoverable() = runBlocking {
        server.enqueueError(status = 404, bodyFixture = "errors/not-found.json")

        val result = server.source.getDownloadDescriptor(paperId)

        assertEquals(false, (result as RepositoryResult.Failure).error.isRecoverable)
    }

    @Test
    fun upstreamUnavailableBecomesARecoverableServiceFailure() = runBlocking {
        server.enqueueError(status = 502, bodyFixture = "errors/upstream-unavailable.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        val error = (result as RepositoryResult.Failure).error
        assertEquals(RepositoryError.ServiceUnavailable, error)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun upstreamTimeoutBecomesTheRecoverableTimeoutOutcome() = runBlocking {
        server.enqueueError(status = 504, bodyFixture = "errors/upstream-timeout.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        assertEquals(RepositoryError.Timeout, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun outboundCapacityRejectionJoinsTheRecoverableServiceFailure() = runBlocking {
        // 503 OUTBOUND_CAPACITY_EXHAUSTED means "retry shortly", which is what 502 already says to
        // a user. It must not fall through to the generic Http outcome and its outage wording.
        server.enqueueError(status = 503, bodyFixture = "errors/outbound-capacity.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        val error = (result as RepositoryResult.Failure).error
        assertEquals(RepositoryError.ServiceUnavailable, error)
        assertTrue(error.isRecoverable)
    }

    @Test
    fun anInvalidRequestIsNotReportedAsAnOutage() = runBlocking {
        // 400 INVALID_REQUEST is the two sides disagreeing about what v1 accepts. Retrying the same
        // request cannot help, and telling the user the service is down would be a lie.
        server.enqueueError(status = 400, bodyFixture = "errors/invalid-request.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        val error = (result as RepositoryResult.Failure).error
        assertEquals(RepositoryError.InvalidRequest, error)
        assertEquals(false, error.isRecoverable)
    }

    @Test
    fun otherStatusesKeepTheirCodeWithoutExposingTheBody() = runBlocking {
        // 429 has no policy of its own in v1, so it is the generic outcome: the code survives, the
        // server's words do not.
        server.enqueueRaw(status = 429, body = """{"error":"maintenance","detail":"secret internals"}""")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        val error = (result as RepositoryResult.Failure).error
        assertEquals(RepositoryError.Http(429), error)
        assertTrue("The server's words must never reach the user", "secret" !in error.userMessage)
    }

    @Test
    fun malformedSuccessBodyStaysAContractFailureRatherThanNotFound() = runBlocking {
        server.enqueueFixture("errors/malformed-success.json")

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        val error = (result as RepositoryResult.Failure).error as RepositoryError.Contract
        assertEquals(RepositoryError.ContractFailure.INVALID_RESPONSE, error.failure)
    }

    @Test
    fun aResponseMissingRequiredFieldsIsAContractFailureNotAnEmptyResult() = runBlocking {
        server.enqueueFixture("errors/search-missing-required-fields.json")

        val response = server.source.search(PaperSearchRequestDto(query = "AI"))

        // Deserialization succeeds — the failure is a mapping decision, made once, in the mapper.
        val mapped = PaperMappers.mapSearchResponse((response as RepositoryResult.Success).value)
        val error = (mapped as RepositoryResult.Failure).error as RepositoryError.Contract
        assertEquals(RepositoryError.ContractFailure.ALL_ITEMS_INVALID, error.failure)
    }

    @Test
    fun aDetailResponseAboutADifferentPaperIsRejected() = runBlocking {
        server.enqueueFixture("responses/detail-mismatched-id.json")

        val response = server.source.getPaper(paperId)

        val mapped = PaperMappers.mapDetailResponse((response as RepositoryResult.Success).value, paperId)
        val error = (mapped as RepositoryResult.Failure).error as RepositoryError.Contract
        assertEquals(RepositoryError.ContractFailure.INVALID_PAPER_ID, error.failure)
    }

    @Test
    fun aDescriptorResponseAboutADifferentPaperIsRejected() = runBlocking {
        server.enqueueFixture("responses/download-mismatched-id.json")

        val response = server.source.getDownloadDescriptor(paperId)

        val mapped = PaperMappers.mapDownloadAvailability(
            dto = (response as RepositoryResult.Success).value,
            expectedPaperId = paperId,
            nowEpochMillis = 0L,
        )
        val error = (mapped as RepositoryResult.Failure).error as RepositoryError.Contract
        assertEquals(RepositoryError.ContractFailure.INVALID_PAPER_ID, error.failure)
    }

    @Test
    fun anUnreachableServerBecomesTheOfflineOutcome() = runBlocking {
        server.shutdownBeforeRequest()

        val result = server.source.search(PaperSearchRequestDto(query = "AI"))

        assertEquals(RepositoryError.Offline, (result as RepositoryResult.Failure).error)
    }
}
