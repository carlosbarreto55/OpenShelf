package com.example.paperReader.ui.reader

import android.graphics.Bitmap
import com.example.paperReader.data.reader.PageRender
import com.example.paperReader.data.reader.PageSize
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen can see of the document right now. */
data class ReaderViewport(
    val firstVisiblePage: Int,
    val lastVisiblePage: Int,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    /** 1 is a page exactly as wide as the viewport. */
    val zoom: Float,
) {
    val pageWidthPx: Int get() = (viewportWidthPx * zoom).roundToInt()
}

/** One page as the screen draws it. */
sealed interface PageImage {
    class Ready(val bitmap: Bitmap, val renderedWidthPx: Int) : PageImage

    /** The page failed to render; it is shown as unavailable, never as a blank page. */
    data class Unavailable(val requestedWidthPx: Int) : PageImage
}

/**
 * Keeps only the pages the current view needs rendered (design.md decision 2).
 *
 * The window is the visible pages plus [prefetchPages] either side. A page that leaves it is
 * dropped and its bitmap recycled; a render still waiting for a page that left is cancelled, so it
 * never runs. Memory is bounded by [SCREENS_OF_PIXELS] screens' worth of pixels, derived from the
 * viewport rather than a page count, so a tablet and a phone behave alike: the prefetch margin goes
 * first when the window would not fit, and past that the visible pages render narrower than they
 * are drawn rather than not at all.
 *
 * Zoom changes the width a page is laid out at, and a page whose bitmap was rendered at another
 * width renders again at the new one. The old bitmap is drawn until the new one arrives, so the
 * page never flashes empty — but it is replaced, never kept and scaled up.
 *
 * Not thread-safe: [update] and the renders it launches must share one thread, which a ViewModel's
 * main-thread scope as [scope] guarantees. Rendering itself happens off it, inside [render].
 */
class ReaderPageWindow(
    private val pages: List<PageSize>,
    private val scope: CoroutineScope,
    private val render: suspend (pageIndex: Int, widthPx: Int) -> PageRender,
    private val recycle: (Bitmap) -> Unit = Bitmap::recycle,
    private val prefetchPages: Int = DEFAULT_PREFETCH_PAGES,
) {
    private val _images = MutableStateFlow<Map<Int, PageImage>>(emptyMap())
    val images: StateFlow<Map<Int, PageImage>> = _images.asStateFlow()

    private val renders = mutableMapOf<Int, PendingRender>()

    private class PendingRender(val widthPx: Int, val job: Job)

    fun update(viewport: ReaderViewport) {
        if (pages.isEmpty() || viewport.viewportWidthPx <= 0 || viewport.viewportHeightPx <= 0) return
        val targets = targetWidths(viewport)

        // Pages that left the window: their pending renders never start, their bitmaps go.
        renders.keys.filterNot { it in targets }.forEach { renders.remove(it)?.job?.cancel() }
        val evicted = _images.value.filterKeys { it !in targets }
        if (evicted.isNotEmpty()) {
            _images.update { current -> current - evicted.keys }
            evicted.values.forEach(::release)
        }

        for ((page, width) in targets) {
            val shown = _images.value[page]
            val alreadyAtWidth = when (shown) {
                is PageImage.Ready -> shown.renderedWidthPx == width
                // A page that failed at this width would fail again; it is retried only when the
                // width changes.
                is PageImage.Unavailable -> shown.requestedWidthPx == width
                null -> false
            }
            if (alreadyAtWidth) {
                renders.remove(page)?.job?.cancel()
                continue
            }
            if (renders[page]?.widthPx == width) continue
            renders.remove(page)?.job?.cancel()
            renders[page] = PendingRender(width, scope.launch { renderInto(page, width) })
        }
    }

    /** Drops everything at once; for when the screen is gone and nothing can still be drawing. */
    fun clear() {
        renders.values.forEach { it.job.cancel() }
        renders.clear()
        val all = _images.value.values
        _images.value = emptyMap()
        all.forEach { (it as? PageImage.Ready)?.bitmap?.let(recycle) }
    }

    private suspend fun renderInto(page: Int, width: Int) {
        val image = when (val result = render(page, width)) {
            is PageRender.Rendered -> PageImage.Ready(result.bitmap, width)
            PageRender.Unavailable -> PageImage.Unavailable(width)
        }
        val replaced = _images.value[page]
        _images.update { it + (page to image) }
        renders.remove(page)
        replaced?.let(::release)
    }

    /**
     * Recycled after a grace period rather than at once: the frame being drawn when a page is
     * dropped may still hold its bitmap, and drawing a recycled bitmap is a crash.
     */
    private fun release(image: PageImage) {
        val bitmap = (image as? PageImage.Ready)?.bitmap ?: return
        scope.launch {
            delay(RECYCLE_GRACE_MILLIS)
            recycle(bitmap)
        }
    }

    /** Page index to the width it should be rendered at, for every page in the window. */
    private fun targetWidths(viewport: ReaderViewport): Map<Int, Int> {
        val last = pages.lastIndex
        val first = viewport.firstVisiblePage.coerceIn(0, last)
        val lastVisible = viewport.lastVisiblePage.coerceIn(first, last)
        val visible = first..lastVisible
        val width = viewport.pageWidthPx.coerceAtLeast(1)
        val budget = viewport.viewportWidthPx.toLong() * viewport.viewportHeightPx * BYTES_PER_PIXEL * SCREENS_OF_PIXELS

        val withPrefetch = (first - prefetchPages).coerceAtLeast(0)..(lastVisible + prefetchPages).coerceAtMost(last)
        if (withPrefetch.sumOf { bytesAt(it, width) } <= budget) return withPrefetch.associateWith { width }

        val visibleBytes = visible.sumOf { bytesAt(it, width) }
        if (visibleBytes <= budget) return visible.associateWith { width }

        // Pixels grow with the square of the width, so the width shrinks by the square root.
        val scaled = (width * sqrt(budget.toDouble() / visibleBytes)).toInt().coerceAtLeast(1)
        return visible.associateWith { scaled }
    }

    private fun bytesAt(page: Int, width: Int): Long {
        val height = (width * pages[page].heightPerWidth).toLong()
        return width.toLong() * height * BYTES_PER_PIXEL
    }

    companion object {
        const val DEFAULT_PREFETCH_PAGES = 1
        const val SCREENS_OF_PIXELS = 4L
        const val RECYCLE_GRACE_MILLIS = 500L
        private const val BYTES_PER_PIXEL = 4L
    }
}
