package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The PaperReader backend's v1 surface, and nothing else. Nullable query parameters are omitted by
 * Retrofit, which is how an inactive optional restriction is expressed.
 *
 * There is deliberately no `access:refresh` operation: the register (AND-011) found it had no
 * production call site, and full detail refresh plus transfer-time download confirmation already
 * cover every freshness need. The server may still expose the endpoint.
 */
interface PaperReaderApi {
    @GET("v1/papers/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("cursor") cursor: String?,
        @Query("sort") sort: String,
        @Query("open_access") openAccessOnly: Boolean?,
        @Query("pdf_available") pdfAvailableOnly: Boolean?,
        @Query("publication_year") publicationYear: Int?,
        @Query("work_type") workType: String?,
        @Query("topic") topic: String?,
    ): PaperSearchResponseDto

    @GET("v1/papers/{paperId}")
    suspend fun getPaper(@Path("paperId") paperId: String): PaperDetailResponseDto

    @GET("v1/papers/{paperId}/download")
    suspend fun getDownloadDescriptor(@Path("paperId") paperId: String): DownloadDescriptorResponseDto
}
