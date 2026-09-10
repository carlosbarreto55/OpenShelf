package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class HttpPaperRemoteDataSource @Inject constructor(
    private val api: PaperReaderApi,
) : PaperRemoteDataSource {
    override suspend fun search(request: PaperSearchRequestDto): RepositoryResult<PaperSearchResponseDto> {
        return execute {
            api.search(
                query = request.query,
                cursor = request.cursor,
                sort = request.sort,
                openAccessOnly = request.filters.openAccessOnly,
                pdfAvailableOnly = request.filters.pdfAvailableOnly,
                publicationYear = request.filters.publicationYear,
                workType = request.filters.workType,
                topic = request.filters.topic,
            )
        }
    }

    override suspend fun getPaper(paperId: PaperId): RepositoryResult<PaperDetailResponseDto> {
        return execute { api.getPaper(paperId.value) }
    }

    override suspend fun getDownloadDescriptor(paperId: PaperId): RepositoryResult<DownloadDescriptorResponseDto> {
        return execute { api.getDownloadDescriptor(paperId.value) }
    }

    private suspend fun <T> execute(block: suspend () -> T): RepositoryResult<T> {
        return try {
            RepositoryResult.Success(block())
        } catch (_: SocketTimeoutException) {
            RepositoryResult.Failure(RepositoryError.Timeout)
        } catch (error: HttpException) {
            // The response body is never read: an upstream error document must not reach UI state.
            RepositoryResult.Failure(error.code().toRepositoryError())
        } catch (_: SerializationException) {
            RepositoryResult.Failure(
                RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE),
            )
        } catch (_: UnknownHostException) {
            RepositoryResult.Failure(RepositoryError.Offline)
        } catch (_: ConnectException) {
            RepositoryResult.Failure(RepositoryError.Offline)
        } catch (_: IOException) {
            RepositoryResult.Failure(RepositoryError.Offline)
        } catch (_: Throwable) {
            RepositoryResult.Failure(RepositoryError.Unexpected)
        }
    }

    /** The status policy recorded as business decision 3 in TECHNICAL_DEBT.md, extended to the two
     * further statuses the canonical `paperreader-v1.0.0` contract declares. Any other status keeps
     * its code without exposing what the server said about it. */
    private fun Int.toRepositoryError(): RepositoryError = when (this) {
        HTTP_BAD_REQUEST -> RepositoryError.InvalidRequest
        HTTP_NOT_FOUND -> RepositoryError.NotFound
        HTTP_BAD_GATEWAY -> RepositoryError.ServiceUnavailable
        HTTP_SERVICE_UNAVAILABLE -> RepositoryError.ServiceUnavailable
        HTTP_GATEWAY_TIMEOUT -> RepositoryError.Timeout
        else -> RepositoryError.Http(this)
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val HTTP_GATEWAY_TIMEOUT = 504
    }
}
