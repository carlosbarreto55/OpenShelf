package com.example.paperReader.ui.reader

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.paperReader.data.reader.AndroidPdfDocumentFactory
import com.example.paperReader.data.reader.PdfOpenResult
import com.example.paperReader.data.reader.PdfRendererPageSource
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 7.1: a long, large-format document read from start to end, at the zoom bounds, through the
 * real renderer and the real window. Rendering every page it passes at full zoom would need many
 * gigabytes; the window must hold the pixels it keeps within its budget at every step, and leave
 * nothing behind once it is cleared, on the device's ordinary app heap.
 */
@RunWith(AndroidJUnit4::class)
class ReaderMemoryDeviceTest {
    private lateinit var dir: File
    private val source = PdfRendererPageSource(
        fileOpener = { path -> ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) },
        documentFactory = AndroidPdfDocumentFactory(),
        ioDispatcher = Dispatchers.IO,
    )

    /** The window is not thread-safe, so it and its renders share this one thread, as on main. */
    private val windowThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reader-memory-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        source.close()
        windowThread.close()
        dir.deleteRecursively()
    }

    @Test
    fun readingALongLargeFormatDocumentStaysWithinTheWindowBudget() = runBlocking {
        val opened = source.open(longDocument(pageCount = PAGE_COUNT).path)
        val pages = (opened as PdfOpenResult.Readable).pages
        val nativeBefore = settledNativeHeap()

        withContext(windowThread) {
            val scope = CoroutineScope(SupervisorJob() + windowThread)
            val window = ReaderPageWindow(pages, scope, source::render)
            var largestHeld = 0L

            for (zoom in listOf(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)) {
                // Full zoom reads fewer pages: each step re-renders the visible pages at the
                // width the budget allows, which is the expensive case, not the long one.
                val lastPage = if (zoom == ReaderViewModel.MIN_ZOOM) pages.lastIndex else ZOOMED_PAGES
                for (page in 0..lastPage) {
                    val viewport = ReaderViewport(page, (page + 1).coerceAtMost(pages.lastIndex), WIDTH, HEIGHT, zoom)
                    window.update(viewport)
                    withTimeout(RENDER_TIMEOUT_MILLIS) {
                        while (window.images.value[page] == null) delay(5)
                    }
                    val held = window.images.value.values.sumOf { image ->
                        (image as? PageImage.Ready)?.bitmap?.takeUnless { it.isRecycled }?.allocationByteCount?.toLong() ?: 0L
                    }
                    largestHeld = maxOf(largestHeld, held)
                    assertTrue(
                        "page $page at zoom $zoom holds $held bytes, over the ${BUDGET_BYTES}-byte budget",
                        held <= BUDGET_BYTES,
                    )
                }
            }

            window.clear()
            delay(ReaderPageWindow.RECYCLE_GRACE_MILLIS * 2)
            scope.cancel()
            assertTrue("the window held pixels at all", largestHeld > 0)
        }

        val growth = settledNativeHeap() - nativeBefore
        assertTrue(
            "native heap grew by $growth bytes after the window was cleared " +
                "(heap limit ${Runtime.getRuntime().maxMemory()} bytes)",
            growth < BUDGET_BYTES,
        )
    }

    /** A generated document, so its size is chosen here rather than checked in: twice US Letter,
     * with enough drawn on each page that rendering it does real work. */
    private fun longDocument(pageCount: Int): File {
        val file = File(dir, "long.pdf")
        val document = PdfDocument()
        val ink = Paint().apply { color = Color.BLACK; textSize = 28f }
        repeat(pageCount) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, index + 1).create())
            for (line in 0 until PAGE_HEIGHT_PT / 40) {
                page.canvas.drawText("Page ${index + 1}, line $line — The quick brown fox jumps over the lazy dog.", 48f, 48f + line * 40f, ink)
            }
            page.canvas.drawRect(48f, 48f, PAGE_WIDTH_PT - 48f, PAGE_HEIGHT_PT - 48f, Paint().apply { style = Paint.Style.STROKE })
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    private fun settledNativeHeap(): Long {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        return Debug.getNativeHeapAllocatedSize()
    }

    private companion object {
        const val PAGE_COUNT = 150
        const val ZOOMED_PAGES = 20
        const val PAGE_WIDTH_PT = 1224
        const val PAGE_HEIGHT_PT = 1584

        /** A 1080 × 2400 phone, the shape the budget is sized from. */
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val BUDGET_BYTES = WIDTH.toLong() * HEIGHT * 4 * ReaderPageWindow.SCREENS_OF_PIXELS

        const val RENDER_TIMEOUT_MILLIS = 10_000L
    }
}
