package com.example.paperReader.data.local

import com.example.paperReader.data.local.entity.PaperEntity
import com.example.paperReader.data.local.entity.PersistedAuthor
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperType

object TestFixtures {
    fun paperEntity(
        id: String,
        title: String = "Demo title",
        updatedAtEpochMillis: Long = 0L,
    ): PaperEntity = PaperEntity(
        id = id,
        title = title,
        authors = listOf(PersistedAuthor(name = "A. Author")),
        venue = "Demo Venue",
        publicationYear = 2024,
        type = PaperType.ARTICLE,
        topics = emptyList(),
        citationCount = 10,
        abstractText = null,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun paper(
        id: String,
        title: String = "Demo title",
        access: AccessResolution = AccessResolution.Unknown,
    ): Paper = Paper(
        id = PaperId(id),
        title = title,
        authors = listOf(com.example.paperReader.domain.model.Author(name = "A. Author")),
        venue = "Demo Venue",
        publicationYear = 2024,
        type = PaperType.ARTICLE,
        topics = emptyList(),
        citationCount = 10,
        abstractText = null,
        access = access,
    )

    fun directPdf(url: String = "https://demo.paperreader.invalid/papers/W1.pdf"): AccessResolution.DirectPdf =
        AccessResolution.DirectPdf(ConfirmedPdfDescriptor(url = url, mimeType = "application/pdf"))
}
