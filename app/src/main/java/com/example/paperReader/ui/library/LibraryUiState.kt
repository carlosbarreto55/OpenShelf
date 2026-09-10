package com.example.paperReader.ui.library

import com.example.paperReader.domain.model.Paper

sealed interface LibraryContent {
    data object Loading : LibraryContent

    data object Empty : LibraryContent

    data class Loaded(val papers: List<Paper>) : LibraryContent
}

data class LibraryUiState(
    val content: LibraryContent = LibraryContent.Loading,
)
