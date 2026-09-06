package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessResolutionDto
import com.example.paperReader.data.remote.dto.ConfirmedPdfDownloadDescriptorDto
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperMapperTest {
    @Test
    fun unknownOptionalFieldsAreIgnoredByRemoteJson() {
        val dto = RemoteJson.decodeFromString<PaperDto>(
            """{"id":"W123","title":"A paper","future_field":"ignored"}""",
        )

        assertEquals("W123", dto.id)
        assertEquals("A paper", dto.title)
    }

    @Test
    fun confirmedPdfMapsToActionableDomainAccess() {
        val result = PaperMappers.mapPaper(
            PaperDto(
                id = "W123",
                title = "A paper",
                access = AccessResolutionDto(
                    state = "DIRECT_PDF",
                    descriptor = ConfirmedPdfDownloadDescriptorDto(
                        url = "https://papers.example/download.pdf",
                        provenance = "Institutional repository",
                    ),
                ),
            ),
        )

        val paper = (result as RepositoryResult.Success).value
        assertTrue(paper.access is AccessResolution.DirectPdf)
    }

    @Test
    fun malformedPdfDescriptorFailsClosedWithoutRejectingMetadata() {
        val accessResult = PaperMappers.mapAccessResolution(
            AccessResolutionDto(
                state = "DIRECT_PDF",
                descriptor = ConfirmedPdfDownloadDescriptorDto(
                    url = "http://not-https.example/paper.pdf",
                ),
            ),
        )
        assertTrue(accessResult is RepositoryResult.Failure)
        assertTrue((accessResult as RepositoryResult.Failure).error is RepositoryError.AccessValidation)

        val paperResult = PaperMappers.mapPaper(
            PaperDto(
                id = "W123",
                title = "Metadata survives",
                access = AccessResolutionDto(
                    state = "DIRECT_PDF",
                    descriptor = ConfirmedPdfDownloadDescriptorDto(
                        url = "http://not-https.example/paper.pdf",
                    ),
                ),
            ),
        )
        val paper = (paperResult as RepositoryResult.Success).value
        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun unknownAccessStateMapsToUnknown() {
        val result = PaperMappers.mapAccessResolution(AccessResolutionDto(state = "FUTURE_STATE"))

        assertEquals(RepositoryResult.Success(AccessResolution.Unknown), result)
    }

    @Test
    fun invalidPaperIdentityIsRejected() {
        val result = PaperMappers.mapPaper(PaperDto(id = "not-stable", title = "A paper"))

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
            (result as RepositoryResult.Failure).error,
        )
    }

    @Test
    fun jsonConfigurationCanDecodeUnknownAccessFields() {
        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<AccessResolutionDto>(
            """{"state":"UNKNOWN","unexpected":true}""",
        )

        assertEquals("UNKNOWN", dto.state)
    }
}
