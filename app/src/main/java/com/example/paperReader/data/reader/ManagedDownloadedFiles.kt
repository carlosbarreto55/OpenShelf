package com.example.paperReader.data.reader

import com.example.paperReader.di.IoDispatcher
import com.example.paperReader.domain.repository.DownloadedFiles
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Answers through the same content URI the reader opens, so "present" means "the reader can open
 * it", not merely "a file of that name exists somewhere". */
class ManagedDownloadedFiles @Inject constructor(
    private val fileOpener: ManagedFileOpener,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DownloadedFiles {
    override suspend fun isPresent(contentUri: String): Boolean = withContext(ioDispatcher) {
        runCatching { fileOpener.open(contentUri).close() }.isSuccess
    }
}
