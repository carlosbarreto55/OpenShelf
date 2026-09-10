package com.example.paperReader.data.download

import java.io.File
import javax.inject.Inject

/** App-managed storage for downloaded files, kept under one directory so `res/xml/file_paths.xml`
 * can expose exactly that directory (and nothing else) through `FileProvider`. Takes a plain
 * [File] rather than a `Context` so the write/delete logic is testable without Robolectric. */
class DownloadFileStorage @Inject constructor(
    private val baseDir: File,
) {
    /** [extension] is caller-supplied (not hard-coded to `.pdf`) so a placeholder implementation
     * can never accidentally hand out a `.pdf`-named file it did not actually validate. */
    fun fileFor(downloadId: String, extension: String): File = File(baseDir, "$downloadId.$extension")

    fun write(downloadId: String, extension: String, bytes: ByteArray): File {
        val file = fileFor(downloadId, extension)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    /** Removes any file for [downloadId] regardless of extension (a canceled/failed transfer may
     * leave behind either a completed-shaped or placeholder-shaped file). */
    fun delete(downloadId: String) {
        baseDir.listFiles { file -> file.nameWithoutExtension == downloadId }?.forEach { it.delete() }
    }
}
