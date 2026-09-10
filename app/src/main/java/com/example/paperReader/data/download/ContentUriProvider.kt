package com.example.paperReader.data.download

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Isolates the one `FileProvider` call a completed download makes so [FakeDownloadManager] and
 * [PdfDownloadWorker] are testable without a real, manifest-resolved `FileProvider` (Robolectric
 * does not reliably resolve one in a plain unit test). */
fun interface ContentUriProvider {
    fun uriFor(file: File): String
}

class FileProviderContentUriProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ContentUriProvider {
    override fun uriFor(file: File): String {
        return FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX, file).toString()
    }

    private companion object {
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }
}
