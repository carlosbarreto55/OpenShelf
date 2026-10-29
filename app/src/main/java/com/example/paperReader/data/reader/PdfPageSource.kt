package com.example.paperReader.data.reader

import android.graphics.Bitmap

/**
 * The pages of one managed PDF, rendered on demand (design.md decision 1). It isolates rendering
 * only: what may be opened, and what the user is told when it cannot be, is decided above it.
 *
 * One instance holds at most one open document. Opening another closes the first.
 */
interface PdfPageSource {
    /** Classifies the file behind [contentUri] and opens it only when it is [PdfOpenResult.Readable].
     * No page is rendered before this returns, so an unreadable file never reaches the screen. */
    suspend fun open(contentUri: String): PdfOpenResult

    /** Renders [pageIndex] [targetWidthPx] wide, keeping the page's aspect ratio. A page that fails
     * is [PageRender.Unavailable], never a blank bitmap that would pass for a blank page. */
    suspend fun render(pageIndex: Int, targetWidthPx: Int): PageRender

    /** Releases the document after any render already running. Safe to call more than once. */
    fun close()
}

/** What opening found, before any page was rendered (design.md decision 5). */
sealed interface PdfOpenResult {
    data class Readable(val pages: List<PageSize>) : PdfOpenResult {
        val pageCount: Int get() = pages.size
    }

    /** Nothing to open: the managed file is gone. */
    data object Missing : PdfOpenResult

    /** The file does not begin with the PDF signature — the demo placeholder is one of these. */
    data object NotPdf : PdfOpenResult

    /** The document needs a password to open. */
    data object Encrypted : PdfOpenResult

    /** It claims to be a PDF but is cut short or cannot be parsed. */
    data object Malformed : PdfOpenResult
}

/** A page's size in PDF points, which is all a layout needs before the page is rendered. */
data class PageSize(val width: Int, val height: Int) {
    /** Height over width, so a page [width] pixels wide is `width * heightPerWidth` tall. */
    val heightPerWidth: Float get() = height.toFloat() / width
}

sealed interface PageRender {
    /** Owned by the caller from here on, including recycling it. */
    class Rendered(val bitmap: Bitmap) : PageRender

    data object Unavailable : PageRender
}
