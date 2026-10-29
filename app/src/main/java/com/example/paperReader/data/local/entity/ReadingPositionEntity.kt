package com.example.paperReader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Where the user last was in a paper (design.md decision 3). Keyed by paper rather than by
 * download, since a paper has at most one download record and the position is about the paper.
 *
 * [pageOffsetFraction] is how far down [pageIndex] the top of the screen was, from 0 to 1. A
 * fraction rather than pixels, so the position means the same thing after a rotation, on another
 * display, and at another zoom.
 *
 * There is deliberately no foreign key to `downloads`: that table is written with
 * `OnConflictStrategy.REPLACE`, which SQLite carries out as a delete and an insert, so an
 * `ON DELETE CASCADE` would erase the position on every progress update. Removal is explicit
 * instead, in `DownloadDao.delete`.
 */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val paperId: String,
    val pageIndex: Int,
    val pageOffsetFraction: Float,
    val updatedAtEpochMillis: Long,
)

/** A stored position joined to the completed download it can be resumed from. */
data class ResumableReadingRow(
    val paperId: String,
    val downloadId: String,
    val title: String,
    val localContentUri: String,
    val pageIndex: Int,
    val pageOffsetFraction: Float,
    val updatedAtEpochMillis: Long,
)
