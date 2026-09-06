package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessRefreshResponseDto
import com.example.paperReader.data.remote.dto.AccessResolutionDto
import com.example.paperReader.data.remote.dto.AuthorDto
import com.example.paperReader.data.remote.dto.ConfirmedPdfDownloadDescriptorDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperDto
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.data.remote.dto.TopicDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject

/** Default offline binding until the production-shaped fixture source is added. */
class FakePaperRemoteDataSource @Inject constructor() : PaperRemoteDataSource {
    // Temporary demo-only data. Keep this list intentionally small because it stays in memory
    // until the backend and production fixture flow replace it.
    private val placeholderPapers = listOf(
        PaperDto(
            id = "W2741809807",
            title = "Harnessing Generative AI for Automated Feedback in Higher Education",
            authors = listOf(
                AuthorDto(name = "R. M. Mahoney"),
                AuthorDto(name = "A. Chen"),
            ),
            venue = "Online Learning",
            year = 2024,
            type = "REVIEW",
            topics = listOf(TopicDto(name = "Education")),
            citationCount = 151,
            access = AccessResolutionDto(
                state = "DIRECT_PDF",
                descriptor = ConfirmedPdfDownloadDescriptorDto(
                    url = "https://demo.paperreader.invalid/papers/W2741809807.pdf",
                    mimeType = "application/pdf",
                    provenance = "Demo repository descriptor",
                ),
            ),
        ),
        PaperDto(
            id = "W3123456789",
            title = "Towards Sustainable Agriculture: Harnessing AI for Global Food Security",
            authors = listOf(AuthorDto(name = "A. Javaid")),
            venue = "Artificial Intelligence in Agriculture",
            year = 2024,
            type = "ARTICLE",
            topics = listOf(TopicDto(name = "Agriculture")),
            citationCount = 171,
            access = AccessResolutionDto(
                state = "OPEN_FULL_TEXT",
                sourceUrl = "https://demo.paperreader.invalid/papers/W3123456789",
                provenance = "Demo open HTML descriptor",
            ),
        ),
        PaperDto(
            id = "W3987654321",
            title = "Empowering SMEs with Generative AI for Resilience",
            authors = listOf(AuthorDto(name = "P. Chatterjee")),
            venue = "Management Research Review",
            year = 2024,
            type = "ARTICLE",
            topics = listOf(TopicDto(name = "Business")),
            citationCount = 82,
            access = AccessResolutionDto(state = "NO_FREE_FULL_TEXT"),
        ),
    )

    override suspend fun search(request: PaperSearchRequestDto): RepositoryResult<PaperSearchResponseDto> {
        return RepositoryResult.Success(PaperSearchResponseDto(items = placeholderPapers))
    }

    override suspend fun getPaper(paperId: PaperId): RepositoryResult<PaperDetailResponseDto> {
        val paper = findPaper(paperId) ?: return missingPaper()
        return RepositoryResult.Success(PaperDetailResponseDto(paper = paper))
    }

    override suspend fun refreshAccess(paperId: PaperId): RepositoryResult<AccessRefreshResponseDto> {
        val paper = findPaper(paperId) ?: return missingPaper()
        return RepositoryResult.Success(
            AccessRefreshResponseDto(
                paperId = paperId.value,
                access = paper.access,
            ),
        )
    }

    override suspend fun getDownloadDescriptor(paperId: PaperId): RepositoryResult<DownloadDescriptorResponseDto> {
        val paper = findPaper(paperId) ?: return missingPaper()
        return RepositoryResult.Success(
            DownloadDescriptorResponseDto(
                paperId = paperId.value,
                descriptor = paper.access?.descriptor,
            ),
        )
    }

    private fun findPaper(paperId: PaperId): PaperDto? {
        return placeholderPapers.firstOrNull { it.id == paperId.value }
    }

    private fun <T> missingPaper(): RepositoryResult<T> {
        return RepositoryResult.Failure(
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_PAPER_ID),
        )
    }
}
