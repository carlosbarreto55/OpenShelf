package com.example.paperReader.data.reader

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Open-time classification and page failures (design.md decision 5), against real documents.
 *
 * The structural verdicts — not a PDF, cut short — are made before the platform renderer is
 * reached, so these tests assert it is never reached for them. The verdicts only the renderer can
 * give are covered twice: here through [FakePlatformDocuments], which throws what the renderer is
 * documented to throw, and on a device against the real renderer in `PdfRendererPageSourceDeviceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfRendererPageSourceTest {
    private lateinit var dir: File
    private val platform = FakePlatformDocuments()
    private val dispatcher = StandardTestDispatcher()
    private val source = PdfRendererPageSource(ReaderFixtures.pathOpener, platform, dispatcher)

    @Before
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("reader-source-test").toFile()
    }

    @After
    fun tearDown() {
        source.close()
        dir.deleteRecursively()
    }

    @Test
    fun aValidMultiPageDocumentOpensWithEveryPageSize() = runReaderTest {
        val result = source.open(fixture(ReaderFixtures.THREE_PAGES))

        assertEquals(PdfOpenResult.Readable(List(3) { PageSize(612, 792) }), result)
    }

    @Test
    fun aDocumentNeedingAPasswordIsReportedEncrypted() = runReaderTest {
        platform.openFailure = SecurityException("password required")

        assertEquals(PdfOpenResult.Encrypted, source.open(fixture(ReaderFixtures.ENCRYPTED_USER_PASSWORD)))
    }

    @Test
    fun anEncryptDictionaryAloneIsNotARefusal() = runReaderTest {
        // Owner-password-only: restricted, but it opens without a password. The classifier must
        // pass it on, or the reader would refuse papers the renderer reads fine.
        val result = source.open(fixture(ReaderFixtures.ENCRYPTED_OWNER_ONLY))

        assertEquals(1, platform.opened)
        assertTrue(result is PdfOpenResult.Readable)
    }

    @Test
    fun aCorruptFileIsMalformedAndNeverReachesTheRenderer() = runReaderTest {
        assertEquals(PdfOpenResult.Malformed, source.open(fixture(ReaderFixtures.TRUNCATED)))
        assertEquals(0, platform.opened)
    }

    @Test
    fun aFileTheRendererCannotParseIsMalformed() = runReaderTest {
        platform.openFailure = IOException("not a valid PDF")

        assertEquals(PdfOpenResult.Malformed, source.open(fixture(ReaderFixtures.THREE_PAGES)))
    }

    @Test
    fun aDocumentWithNoPagesIsMalformed() = runReaderTest {
        platform.pageSizes = emptyList()

        assertEquals(PdfOpenResult.Malformed, source.open(fixture(ReaderFixtures.THREE_PAGES)))
        assertEquals("the empty document is released, not kept open", 1, platform.closed)
    }

    @Test
    fun aFileWithoutThePdfSignatureIsNotAPdf() = runReaderTest {
        val html = File(dir, "login.html").apply { writeText("<html>Please sign in</html>") }

        assertEquals(PdfOpenResult.NotPdf, source.open(html.path))
        assertEquals(0, platform.opened)
    }

    @Test
    fun anEmptyFileIsNotAPdf() = runReaderTest {
        val empty = File(dir, "empty.pdf").apply { writeBytes(ByteArray(0)) }

        assertEquals(PdfOpenResult.NotPdf, source.open(empty.path))
    }

    @Test
    fun aMissingFileIsMissing() = runReaderTest {
        assertEquals(PdfOpenResult.Missing, source.open(File(dir, "gone.pdf").path))
        assertEquals(0, platform.opened)
    }

    @Test
    fun aPageThatFailsToRenderIsUnavailableNotBlank() = runReaderTest {
        platform.failingPages[1] = IllegalStateException("page content stream is damaged")
        source.open(fixture(ReaderFixtures.THREE_PAGES))

        assertTrue(source.render(0, 600) is PageRender.Rendered)
        assertEquals(PageRender.Unavailable, source.render(1, 600))
        assertTrue("the rest of the document still renders", source.render(2, 600) is PageRender.Rendered)
        assertEquals(listOf(0, 2), platform.renderedPages)
    }

    @Test
    fun aPageTooLargeForMemoryIsUnavailable() = runReaderTest {
        platform.failingPages[0] = OutOfMemoryError()
        source.open(fixture(ReaderFixtures.THREE_PAGES))

        assertEquals(PageRender.Unavailable, source.render(0, 600))
    }

    @Test
    fun aRenderKeepsThePageAspectRatioAtTheRequestedWidth() = runReaderTest {
        source.open(fixture(ReaderFixtures.THREE_PAGES))

        val bitmap = (source.render(0, 612) as PageRender.Rendered).bitmap

        assertEquals(612, bitmap.width)
        assertEquals(792, bitmap.height)
    }

    @Test
    fun nothingRendersBeforeOrAfterADocumentIsOpen() = runReaderTest {
        assertEquals(PageRender.Unavailable, source.render(0, 600))

        source.open(fixture(ReaderFixtures.THREE_PAGES))
        assertEquals(PageRender.Unavailable, source.render(3, 600))

        source.close()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, platform.closed)
        assertEquals(PageRender.Unavailable, source.render(0, 600))
    }

    @Test
    fun openingAnotherDocumentReleasesTheFirst() = runReaderTest {
        source.open(fixture(ReaderFixtures.THREE_PAGES))
        source.open(fixture(ReaderFixtures.ENCRYPTED_OWNER_ONLY))

        assertEquals(1, platform.closed)
    }

    private fun fixture(name: String): String = ReaderFixtures.copy(name, dir).path

    private fun runReaderTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }
}
