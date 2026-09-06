package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessRefreshResponseDto
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

    override suspend fun refreshAccess(paperId: PaperId): RepositoryResult<AccessRefreshResponseDto> {
        return execute { api.refreshAccess(paperId.value) }
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
            RepositoryResult.Failure(RepositoryError.Http(error.code()))
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
}
