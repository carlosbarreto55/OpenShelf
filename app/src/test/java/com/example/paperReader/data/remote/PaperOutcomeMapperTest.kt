package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.ConfirmedPdfDownloadDescriptorDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.domain.model.DownloadAvailability
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outcomes AND-005/AND-006/AND-007/AND-008 require to stay distinct from one another:
 * malformed data, mismatched identity, no-PDF, an expired descriptor, an absent total, and every
 * work-type spelling.
 */
class PaperOutcomeMapperTest {

    private val requested = PaperId("W2001")

    // --- detail -------------------------------------------------------------------------------

    @Test
    fun aDetailResponseWithNoPaperIsMalformedDataNotNotFound() {
        val result = PaperMappers.mapDetailResponse(PaperDetailResponseDto(paper = null), requested)

        val error = (result as RepositoryResult.Failure).error
        assertEquals(RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE), error)
        assertTrue("Not-found is a 404, never a 200 body", error != RepositoryError.NotFound)
    }

    @Test
    fun aDetailResponseAboutAnotherPaperIsRejected() {
        val response = PaperDetailResponseDto(paper = PaperDto(id = "W9999", title = "Another paper"))

        val result = PaperMappers.mapDetailResponse(response, requested)

        assertEquals(
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun aMatchingDetailResponseMaps() {
        val response = PaperDetailResponseDto(paper = PaperDto(id = "W2001", title = "Attention"))

        val result = PaperMappers.mapDetailResponse(response, requested)

        assertEquals(requested, (result as RepositoryResult.Success).value.id)
    }

    // --- download availability ----------------------------------------------------------------

    @Test
    fun aNullDescriptorForTheRequestedPaperIsTheNormalNoPdfAnswer() {
        val response = DownloadDescriptorResponseDto(paperId = "W2001", descriptor = null)

        val result = PaperMappers.mapDownloadAvailability(response, requested, nowEpochMillis = 0L)

        assertEquals(DownloadAvailability.Unavailable, (result as RepositoryResult.Success).value)
    }

    @Test
    fun aValidUnexpiredDescriptorAuthorizesATransfer() {
        val response = DownloadDescriptorResponseDto(
            paperId = "W2001",
            descriptor = ConfirmedPdfDownloadDescriptorDto(
                url = "https://cdn.example.org/W2001.pdf",
                mimeType = "application/pdf",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        val result = PaperMappers.mapDownloadAvailability(response, requested, nowEpochMillis = 1_000L)

        val available = (result as RepositoryResult.Success).value as DownloadAvailability.Available
        assertEquals("https://cdn.example.org/W2001.pdf", available.descriptor.url)
    }

    @Test
    fun anAlreadyExpiredDescriptorFailsClosedAgainstTheInjectedClock() {
        val response = DownloadDescriptorResponseDto(
            paperId = "W2001",
            descriptor = ConfirmedPdfDownloadDescriptorDto(
                url = "https://cdn.example.org/W2001.pdf",
                expiresAtEpochMillis = 1_000L,
            ),
        )

        val result = PaperMappers.mapDownloadAvailability(response, requested, nowEpochMillis = 1_000L)

        assertEquals(
            RepositoryError.AccessValidation(RepositoryError.AccessFailure.EXPIRED_DESCRIPTOR),
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun aCleartextDescriptorFailsClosedRatherThanBecomingNoPdf() {
        val response = DownloadDescriptorResponseDto(
            paperId = "W2001",
            descriptor = ConfirmedPdfDownloadDescriptorDto(url = "http://cdn.example.org/W2001.pdf"),
        )

        val result = PaperMappers.mapDownloadAvailability(response, requested, nowEpochMillis = 0L)

        assertEquals(
            RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR),
            (result as RepositoryResult.Failure).error,
        )
    }

    // --- totals -------------------------------------------------------------------------------

    @Test
    fun aSuppliedTotalIsCarriedThroughUnchanged() {
        val page = PaperMappers.mapSearchResponse(
            PaperSearchResponseDto(items = listOf(PaperDto(id = "W2001", title = "A")), totalCount = 125),
        )

        assertEquals(125, (page as RepositoryResult.Success).value.totalItems)
    }

    @Test
    fun anAbsentOrInvalidTotalStaysAbsentRatherThanBecomingTheLoadedCount() {
        val absent = PaperMappers.mapSearchResponse(
            PaperSearchResponseDto(items = listOf(PaperDto(id = "W2001", title = "A")), totalCount = null),
        )
        val negative = PaperMappers.mapSearchResponse(
            PaperSearchResponseDto(items = listOf(PaperDto(id = "W2001", title = "A")), totalCount = -3),
        )

        assertNull((absent as RepositoryResult.Success).value.totalItems)
        assertNull((negative as RepositoryResult.Success).value.totalItems)
    }

    // --- work types ---------------------------------------------------------------------------

    @Test
    fun canonicalLegacyAndUnknownWorkTypesAllResolveWithoutLosingThePaper() {
        val expectations = mapOf(
            "book_chapter" to PaperType.BOOK_CHAPTER,
            "book-chapter" to PaperType.BOOK_CHAPTER,
            "BOOK-CHAPTER" to PaperType.BOOK_CHAPTER,
            "Book_Chapter" to PaperType.BOOK_CHAPTER,
            "article" to PaperType.ARTICLE,
            "journal_article" to PaperType.ARTICLE,
            "REVIEW" to PaperType.REVIEW,
            "preprint" to PaperType.PREPRINT,
            "dataset" to PaperType.DATASET,
            "monograph" to PaperType.OTHER,
            "" to PaperType.UNKNOWN,
            "   " to PaperType.UNKNOWN,
        )

        expectations.forEach { (wire, expected) ->
            assertEquals("\"$wire\"", expected, PaperType.fromWireValue(wire))
        }
        assertEquals(PaperType.UNKNOWN, PaperType.fromWireValue(null))
    }

    @Test
    fun onlySupportedTypesHaveACanonicalOutboundValue() {
        assertEquals("book_chapter", PaperType.BOOK_CHAPTER.wireValue)
        assertNull("OTHER must never be emitted as a filter", PaperType.OTHER.wireValue)
        assertNull("UNKNOWN must never be emitted as a filter", PaperType.UNKNOWN.wireValue)
        assertEquals(
            listOf("article", "review", "preprint", "book_chapter", "dataset"),
            PaperType.SUPPORTED_FILTER_TYPES.map { it.wireValue },
        )
    }
}
