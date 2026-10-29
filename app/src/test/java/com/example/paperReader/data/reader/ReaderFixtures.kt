package com.example.paperReader.data.reader

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

/** The checked-in documents under `test/resources/reader`, written by
 * `tools/generate-reader-fixtures.py`. */
object ReaderFixtures {
    const val THREE_PAGES = "three-pages.pdf"
    const val ENCRYPTED_USER_PASSWORD = "encrypted-user-password.pdf"
    const val ENCRYPTED_OWNER_ONLY = "encrypted-owner-only.pdf"
    const val TRUNCATED = "truncated.pdf"

    /** Copies a fixture to [dir], because the code under test opens real files by descriptor. */
    fun copy(name: String, dir: File): File {
        val bytes = checkNotNull(ReaderFixtures::class.java.getResourceAsStream("/reader/$name")) {
            "missing reader fixture $name"
        }.use { it.readBytes() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    /** Treats the "content URI" as a plain path, the way a test hands one out. */
    val pathOpener = ManagedFileOpener { path -> ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) }
}

/**
 * Stands in for the platform renderer, which has no JVM implementation. It behaves as the real one
 * is documented to — [SecurityException] for a password, [IOException] for a file it cannot parse —
 * and records whether it was reached at all, which is what the classification tests are about.
 */
class FakePlatformDocuments(
    var pageSizes: List<PageSize> = List(3) { PageSize(612, 792) },
) : PlatformPdfDocument.Factory {
    var openFailure: Throwable? = null
    val failingPages = mutableMapOf<Int, Throwable>()
    var opened = 0
        private set
    val renderedPages = mutableListOf<Int>()
    var closed = 0
        private set

    override fun open(descriptor: ParcelFileDescriptor): PlatformPdfDocument {
        opened++
        openFailure?.let { throw it }
        return object : PlatformPdfDocument {
            override val pageCount: Int get() = pageSizes.size

            override fun pageSize(index: Int): PageSize = pageSizes[index]

            override fun renderPage(index: Int, bitmap: Bitmap) {
                failingPages[index]?.let { throw it }
                renderedPages += index
            }

            override fun close() {
                closed++
                descriptor.close()
            }
        }
    }
}
