package com.example.paperReader.domain.model

/**
 * Where a reader was in a paper: a page, and how far down it the top of the screen was, from 0 to
 * 1. A fraction rather than pixels, so it means the same thing after rotation, on another display,
 * and at another zoom.
 */
data class ReadingPosition(val pageIndex: Int, val pageOffsetFraction: Float) {
    init {
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(pageOffsetFraction in 0f..1f) { "pageOffsetFraction must be within 0..1" }
    }

    companion object {
        /** A paper opened for the first time starts here. */
        val START = ReadingPosition(pageIndex = 0, pageOffsetFraction = 0f)
    }
}

/** A paper that can be picked up where it was left: its position, and the download to read. */
data class ResumablePaper(
    val paperId: PaperId,
    val downloadId: String,
    val title: String,
    val position: ReadingPosition,
    val lastReadAtEpochMillis: Long,
)
