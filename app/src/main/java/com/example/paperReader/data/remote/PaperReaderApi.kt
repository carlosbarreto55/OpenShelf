package com.example.paperReader.data.remote

import com.example.paperReader.data.remote.dto.AccessRefreshResponseDto
import com.example.paperReader.data.remote.dto.DownloadDescriptorResponseDto
import com.example.paperReader.data.remote.dto.PaperDetailResponseDto
import com.example.paperReader.data.remote.dto.PaperSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PaperReaderApi {
    @GET("v1/papers/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("cursor") cursor: String?,
        @Query("sort") sort: String,
        @Query("open_access") openAccessOnly: Boolean,
        @Query("pdf_available") pdfAvailableOnly: Boolean,
        @Query("publication_year") publicationYear: Int?,
        @Query("work_type") workType: String?,
        @Query("topic") topic: String?,
    ): PaperSearchResponseDto

    @GET("v1/papers/{paperId}")
    suspend fun getPaper(@Path("paperId") paperId: String): PaperDetailResponseDto

    @POST("v1/papers/{paperId}/access:refresh")
    suspend fun refreshAccess(@Path("paperId") paperId: String): AccessRefreshResponseDto

    @GET("v1/papers/{paperId}/download")
    suspend fun getDownloadDescriptor(@Path("paperId") paperId: String): DownloadDescriptorResponseDto
}
