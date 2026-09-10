package com.example.paperReader.domain.usecase

import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.domain.repository.LibraryRepository
import com.example.paperReader.domain.repository.RepositoryResult
import javax.inject.Inject

/** Shared by every screen with a save affordance (search, paper detail, library) so the
 * saved/unsaved branch lives in one place. Safe to call without any local optimistic state: Room
 * is the sole source of truth, and every screen observes it reactively, so the UI updates once the
 * write lands rather than by mutating view state directly. */
class ToggleSavedPaperUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(paper: Paper): RepositoryResult<Unit> {
        return when (paper.savedState) {
            SavedState.SAVED -> libraryRepository.unsave(paper.id)
            SavedState.NOT_SAVED -> libraryRepository.save(paper)
        }
    }
}
