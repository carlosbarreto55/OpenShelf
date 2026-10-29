package com.example.paperReader.data.reader

import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The verdicts only the platform renderer can give, from the real renderer. The JVM tests cover the
 * same cases through a stand-in that throws what the renderer is documented to throw; this is the
 * check that it actually does, on the same checked-in fixtures (`test/resources/reader`, which the
 * instrumentation resources include).
 */
@RunWith(AndroidJUnit4::class)
class PdfRendererPageSourceDeviceTest {
    private lateinit var dir: File
    private val source = PdfRendererPageSource(
        fileOpener = { path -> ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) },
        documentFactory = AndroidPdfDocumentFactory(),
        ioDispatcher = Dispatchers.IO,
    )

    @Before
    fun setUp() {
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reader-device-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        source.close()
        dir.deleteRecursively()
    }

    @Test
    fun aValidMultiPageDocumentOpensAndRendersItsText() = runBlocking {
        val result = source.open(fixture("three-pages.pdf"))

        assertEquals(PdfOpenResult.Readable(List(3) { PageSize(612, 792) }), result)
        val bitmap = (source.render(0, 612) as PageRender.Rendered).bitmap
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        assertTrue("the page's text was drawn, so this is not a blank bitmap", pixels.any { it != Color.WHITE })
    }

    @Test
    fun aDocumentNeedingAPasswordIsEncrypted() = runBlocking {
        assertEquals(PdfOpenResult.Encrypted, source.open(fixture("encrypted-user-password.pdf")))
    }

    @Test
    fun anOwnerPasswordOnlyDocumentIsReadable() = runBlocking {
        assertTrue(source.open(fixture("encrypted-owner-only.pdf")) is PdfOpenResult.Readable)
    }

    @Test
    fun aTruncatedDocumentIsMalformed() = runBlocking {
        assertEquals(PdfOpenResult.Malformed, source.open(fixture("truncated.pdf")))
    }

    @Test
    fun aMissingFileIsMissing() = runBlocking {
        assertEquals(PdfOpenResult.Missing, source.open(File(dir, "gone.pdf").path))
    }

    private fun fixture(name: String): String {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/reader/$name")) { "missing reader fixture $name" }
            .use { it.readBytes() }
        return File(dir, name).apply { writeBytes(bytes) }.path
    }
}
