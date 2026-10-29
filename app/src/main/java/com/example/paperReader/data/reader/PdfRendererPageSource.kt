package com.example.paperReader.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.example.paperReader.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [PdfPageSource] over the platform's [PdfRenderer].
 *
 * The renderer allows one open page at a time and is not thread-safe. Both constraints are kept
 * here, once, rather than at every call site: every call runs on [serial], a view of the I/O pool
 * that never runs two tasks at once, and each render opens, draws and closes its page within one
 * uninterrupted block. A render whose caller was cancelled while it waited never starts, which is
 * how pages that scrolled away stop costing anything.
 */
class PdfRendererPageSource @Inject constructor(
    private val fileOpener: ManagedFileOpener,
    private val documentFactory: PlatformPdfDocument.Factory,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : PdfPageSource {

    private val serial = ioDispatcher.limitedParallelism(1)

    /** Only [close] uses this, so a ViewModel can release the document from `onCleared`, after its
     * own scope is already cancelled. */
    private val closeScope = CoroutineScope(SupervisorJob() + serial)

    private var document: PlatformPdfDocument? = null
    private var pages: List<PageSize> = emptyList()

    override suspend fun open(contentUri: String): PdfOpenResult = withContext(serial) {
        closeDocument()

        val descriptor = try {
            fileOpener.open(contentUri)
        } catch (_: FileNotFoundException) {
            return@withContext PdfOpenResult.Missing
        }

        // Positional reads only, and the stream is deliberately not closed: closing it would close
        // the descriptor the renderer is about to be given.
        val channel = FileInputStream(descriptor.fileDescriptor).channel
        val verdict = try {
            PdfFileClassifier.classify(channel)
        } catch (_: IOException) {
            PdfFileClassifier.Verdict.MALFORMED
        }
        when (verdict) {
            PdfFileClassifier.Verdict.NOT_PDF -> return@withContext descriptor.closeWith(PdfOpenResult.NotPdf)
            PdfFileClassifier.Verdict.MALFORMED -> return@withContext descriptor.closeWith(PdfOpenResult.Malformed)
            PdfFileClassifier.Verdict.CANDIDATE -> Unit
        }

        // From here the renderer owns the descriptor and closes it with itself.
        val opened = try {
            documentFactory.open(descriptor)
        } catch (_: SecurityException) {
            return@withContext descriptor.closeWith(PdfOpenResult.Encrypted)
        } catch (_: IOException) {
            return@withContext descriptor.closeWith(PdfOpenResult.Malformed)
        } catch (_: RuntimeException) {
            return@withContext descriptor.closeWith(PdfOpenResult.Malformed)
        }

        val sizes = try {
            (0 until opened.pageCount).map(opened::pageSize)
        } catch (_: RuntimeException) {
            null
        }
        if (sizes.isNullOrEmpty() || sizes.any { it.width <= 0 || it.height <= 0 }) {
            opened.close()
            return@withContext PdfOpenResult.Malformed
        }

        document = opened
        pages = sizes
        PdfOpenResult.Readable(sizes)
    }

    override suspend fun render(pageIndex: Int, targetWidthPx: Int): PageRender = withContext(serial) {
        val open = document ?: return@withContext PageRender.Unavailable
        val size = pages.getOrNull(pageIndex) ?: return@withContext PageRender.Unavailable
        // A render that waited behind others for a page that has since scrolled away stops here,
        // before it allocates anything.
        currentCoroutineContext().ensureActive()

        val width = targetWidthPx.coerceIn(1, MAX_BITMAP_EDGE_PX)
        val height = (width * size.heightPerWidth).toInt().coerceIn(1, MAX_BITMAP_EDGE_PX)
        var bitmap: Bitmap? = null
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // A PDF page has no background of its own; without this it would render onto
            // transparency and show whatever the screen has behind it.
            bitmap.eraseColor(Color.WHITE)
            open.renderPage(pageIndex, bitmap)
            PageRender.Rendered(bitmap)
        } catch (_: RuntimeException) {
            bitmap?.recycle()
            PageRender.Unavailable
        } catch (_: OutOfMemoryError) {
            // One page that does not fit is that page unavailable, not the reader gone.
            bitmap?.recycle()
            PageRender.Unavailable
        }
    }

    override fun close() {
        closeScope.launch { closeDocument() }
    }

    private fun closeDocument() {
        document?.close()
        document = null
        pages = emptyList()
    }

    private fun ParcelFileDescriptor.closeWith(result: PdfOpenResult): PdfOpenResult {
        runCatching { close() }
        return result
    }

    private companion object {
        /** The largest edge one page bitmap may have, whatever the zoom; above this a single page
         * costs more memory than the whole window it belongs to should. */
        const val MAX_BITMAP_EDGE_PX = 4096
    }
}

/** Opens a download's managed file for reading. Throws [FileNotFoundException] when it is gone. */
fun interface ManagedFileOpener {
    fun open(contentUri: String): ParcelFileDescriptor
}

/**
 * Goes through the content URI the download record already holds, never a filesystem path, so the
 * reader sees exactly what the external handoff would and nothing outside the managed directory.
 */
class ContentResolverFileOpener @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ManagedFileOpener {
    override fun open(contentUri: String): ParcelFileDescriptor {
        return try {
            context.contentResolver.openFileDescriptor(contentUri.toUri(), "r")
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: throw FileNotFoundException(contentUri)
    }
}

/**
 * The few renderer operations [PdfRendererPageSource] needs, so its classification and failure
 * handling can be tested on the JVM, where the platform renderer has no implementation.
 */
interface PlatformPdfDocument : Closeable {
    val pageCount: Int

    fun pageSize(index: Int): PageSize

    /** Draws page [index] scaled to fill [bitmap]. */
    fun renderPage(index: Int, bitmap: Bitmap)

    fun interface Factory {
        /** Throws [SecurityException] when a password is needed and [IOException] when the file
         * cannot be parsed — the platform renderer's own contract. */
        fun open(descriptor: ParcelFileDescriptor): PlatformPdfDocument
    }
}

class AndroidPdfDocumentFactory @Inject constructor() : PlatformPdfDocument.Factory {
    override fun open(descriptor: ParcelFileDescriptor): PlatformPdfDocument = AndroidPdfDocument(PdfRenderer(descriptor))
}

private class AndroidPdfDocument(private val renderer: PdfRenderer) : PlatformPdfDocument {
    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(index: Int): PageSize = renderer.openPage(index).use { PageSize(it.width, it.height) }

    override fun renderPage(index: Int, bitmap: Bitmap) {
        renderer.openPage(index).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override fun close() = renderer.close()
}
