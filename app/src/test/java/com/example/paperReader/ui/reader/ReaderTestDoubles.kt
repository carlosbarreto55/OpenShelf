package com.example.paperReader.ui.reader

import android.graphics.Bitmap
import com.example.paperReader.data.reader.PageRender
import com.example.paperReader.data.reader.PageSize
import com.example.paperReader.data.reader.PdfOpenResult
import com.example.paperReader.data.reader.PdfPageSource
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.domain.repository.ReadingPositionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Answers each content URI with a fixed verdict; every page renders unless listed as failing. */
class FakePdfPageSource(
    private val results: MutableMap<String, PdfOpenResult> = mutableMapOf(),
) : PdfPageSource {
    val opened = mutableListOf<String>()
    val rendered = mutableListOf<Int>()
    var closed = 0
        private set

    fun answer(contentUri: String, result: PdfOpenResult) {
        results[contentUri] = result
    }

    override suspend fun open(contentUri: String): PdfOpenResult {
        opened += contentUri
        return results[contentUri] ?: PdfOpenResult.Missing
    }

    override suspend fun render(pageIndex: Int, targetWidthPx: Int): PageRender {
        rendered += pageIndex
        return PageRender.Rendered(Bitmap.createBitmap(targetWidthPx, targetWidthPx * 4 / 3, Bitmap.Config.ARGB_8888))
    }

    override fun close() {
        closed++
    }

    companion object {
        fun readable(pageCount: Int) = PdfOpenResult.Readable(List(pageCount) { PageSize(600, 800) })
    }
}

class FakeReadingPositionRepository : ReadingPositionRepository {
    val stored = mutableMapOf<PaperId, ReadingPosition>()
    val saves = mutableListOf<Pair<PaperId, ReadingPosition>>()
    val resumable = MutableStateFlow<List<ResumablePaper>>(emptyList())

    override suspend fun position(paperId: PaperId): ReadingPosition? = stored[paperId]

    override suspend fun save(paperId: PaperId, position: ReadingPosition) {
        saves += paperId to position
        stored[paperId] = position
    }

    override fun observeResumable(): Flow<List<ResumablePaper>> = resumable
}
