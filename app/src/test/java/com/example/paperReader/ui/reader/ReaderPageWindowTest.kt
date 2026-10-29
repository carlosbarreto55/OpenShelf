package com.example.paperReader.ui.reader

import android.graphics.Bitmap
import com.example.paperReader.data.reader.PageRender
import com.example.paperReader.data.reader.PageSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderPageWindowTest {
    private val pages = List(20) { PageSize(width = 600, height = 800) }
    private val requested = mutableListOf<Pair<Int, Int>>()
    private val recycled = mutableListOf<Bitmap>()

    /** A phone-sized viewport looking at page [first]..[last] at [zoom]. */
    private fun viewport(first: Int, last: Int = first, zoom: Float = 1f) =
        ReaderViewport(first, last, viewportWidthPx = 1000, viewportHeightPx = 2000, zoom = zoom)

    /** On the test scope itself, not `backgroundScope`, whose work `advanceUntilIdle` ignores. */
    private fun TestScope.window(render: suspend (Int, Int) -> PageRender = ::renderNow) =
        ReaderPageWindow(pages, this, render, recycle = { recycled += it })

    private fun renderNow(page: Int, width: Int): PageRender {
        requested += page to width
        return PageRender.Rendered(Bitmap.createBitmap(width, width * 4 / 3, Bitmap.Config.ARGB_8888))
    }

    @Test
    fun onlyTheVisiblePagesAndTheirPrefetchMarginRender() = runTest {
        val window = window()

        window.update(viewport(first = 5, last = 6))
        advanceUntilIdle()

        assertEquals(setOf(4, 5, 6, 7), window.images.value.keys)
        assertEquals(setOf(4, 5, 6, 7), requested.map { it.first }.toSet())
    }

    @Test
    fun pagesThatLeaveTheWindowAreDroppedAndRecycledAfterTheGracePeriod() = runTest {
        val window = window()
        window.update(viewport(first = 0))
        advanceUntilIdle()
        val first = (window.images.value[0] as PageImage.Ready).bitmap

        window.update(viewport(first = 10))
        runCurrent()

        assertFalse("dropped from what the screen draws at once", 0 in window.images.value)
        assertFalse("but not recycled while a frame may still hold it", first in recycled)
        advanceTimeBy(ReaderPageWindow.RECYCLE_GRACE_MILLIS + 1)
        assertTrue(first in recycled)
        assertEquals(setOf(9, 10, 11), window.images.value.keys)
    }

    @Test
    fun aRenderForAPageThatLeftTheWindowIsCancelledNotQueued() = runTest {
        val gate = CompletableDeferred<Unit>()
        val window = window { page, width ->
            if (page == 0) gate.await()
            renderNow(page, width)
        }
        window.update(viewport(first = 0))
        runCurrent()

        window.update(viewport(first = 10))
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("page 0's render never completed into the window", 0 in window.images.value)
        assertFalse(requested.any { it.first == 0 })
    }

    @Test
    fun zoomingReRendersTheVisiblePageWiderAndRecyclesTheOldBitmap() = runTest {
        val window = window()
        window.update(viewport(first = 3))
        advanceUntilIdle()
        val before = window.images.value[3] as PageImage.Ready

        window.update(viewport(first = 3, zoom = 1.5f))
        runCurrent()
        val after = window.images.value[3] as PageImage.Ready
        advanceTimeBy(ReaderPageWindow.RECYCLE_GRACE_MILLIS + 1)

        assertEquals(1000, before.renderedWidthPx)
        assertEquals("rendered at the zoomed width, not scaled up", 1500, after.renderedWidthPx)
        assertTrue(before.bitmap in recycled)
    }

    @Test
    fun scrollingWithinTheWindowRendersNothingAgain() = runTest {
        val window = window()
        window.update(viewport(first = 3, last = 4))
        advanceUntilIdle()
        val rendered = requested.size

        window.update(viewport(first = 4))
        advanceUntilIdle()

        assertEquals(rendered, requested.size)
    }

    @Test
    fun memoryStaysWithinTheDisplayDerivedBudgetAtHighZoom() = runTest {
        val window = window()

        // At 3x one page is 3000 x 4000 px = 48 MB; the budget is four 1000 x 2000 screens, 32 MB.
        window.update(viewport(first = 2, zoom = 3f))
        advanceUntilIdle()

        assertEquals("the prefetch margin goes first", setOf(2), window.images.value.keys)
        val image = window.images.value.getValue(2) as PageImage.Ready
        val bytes = image.renderedWidthPx.toLong() * (image.renderedWidthPx * 4 / 3) * 4
        assertTrue("$bytes bytes for one page", bytes <= 1000L * 2000 * 4 * ReaderPageWindow.SCREENS_OF_PIXELS)
    }

    @Test
    fun aPageThatFailedIsNotRetriedOnEveryScrollButIsAfterAZoom() = runTest {
        val window = window { page, width ->
            requested += page to width
            if (page == 1) PageRender.Unavailable else renderNow(page, width).also { requested.removeAt(requested.lastIndex) }
        }
        window.update(viewport(first = 1))
        advanceUntilIdle()
        window.update(viewport(first = 1))
        advanceUntilIdle()

        assertEquals(PageImage.Unavailable(1000), window.images.value[1])
        assertEquals(1, requested.count { it.first == 1 })

        window.update(viewport(first = 1, zoom = 2f))
        advanceUntilIdle()
        assertEquals(2, requested.count { it.first == 1 })
    }

    @Test
    fun clearingRecyclesEveryBitmapAtOnce() = runTest {
        val window = window()
        window.update(viewport(first = 0, last = 1))
        advanceUntilIdle()

        window.clear()

        assertTrue(window.images.value.isEmpty())
        assertEquals(3, recycled.size)
    }
}
