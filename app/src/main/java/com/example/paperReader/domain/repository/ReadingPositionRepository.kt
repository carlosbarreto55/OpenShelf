package com.example.paperReader.domain.repository

import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.model.ResumablePaper
import kotlinx.coroutines.flow.Flow

/** Reading positions, kept locally per paper with no account (in-app-reading spec). */
interface ReadingPositionRepository {
    suspend fun position(paperId: PaperId): ReadingPosition?

    suspend fun save(paperId: PaperId, position: ReadingPosition)

    /** Papers with a stored position whose completed file is still on the device, most recently
     * read first. A paper whose file is gone never appears, whatever its record says. */
    fun observeResumable(): Flow<List<ResumablePaper>>
}

/** Whether a completed download's managed file is still on the device. A record can outlive its
 * file — the user can clear app storage, or the file can be removed underneath it — so a record's
 * state alone never answers this. */
fun interface DownloadedFiles {
    suspend fun isPresent(contentUri: String): Boolean
}
