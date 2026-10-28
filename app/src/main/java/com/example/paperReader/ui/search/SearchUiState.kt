package com.example.paperReader.ui.search

import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.components.DownloadFeedback

/**
 * How much the screen still owes the user about this build running on placeholder data.
 *
 * [Hidden] is the only state a non-fake build ever reaches. The other two differ *only* in whether
 * the full explanation is currently open: the compact indicator is present for both, because it is
 * never dismissible (backend-ready-data-layer: "Demo state remains discoverable").
 */
enum class DemoDisclosureState {
    /** Not a demo build: no explanation, no indicator, anywhere. */
    Hidden,

    /** The explanation is owed — never acknowledged, or reopened from the indicator. */
    FirstRun,

    /** Acknowledged: the explanation is gone, the indicator that reopens it is not. */
    IndicatorOnly,
    ;

    val isDemoBuild: Boolean get() = this != Hidden
}

enum class AppendState {
    IDLE,
    LOADING,
    ERROR,
}

/** The distinct presentations required by the discovery spec: initial, loading, content
 * (optionally stale, optionally mid-append), empty, and a first-page recoverable error. */
sealed interface SearchContent {
    data object Initial : SearchContent

    data object Loading : SearchContent

    /** The backend answered, and answered that there are no matches: terminal. */
    data class Empty(val queryText: String) : SearchContent

    /**
     * No visible papers yet, but the backend is still offering a cursor and the automatic
     * empty-page bound was reached. Saying "no results" here would be untrue — the papers may be
     * on a later page (AND-004 / API-005).
     */
    data class MorePagesPending(val queryText: String) : SearchContent

    /**
     * The classified cause itself, not a flattened message. The UI maps it to a headline, an
     * explanation, whether a retry is even offered, and a route that still works under it — see
     * [com.example.paperReader.ui.components.toPresentation].
     */
    data class Error(val error: RepositoryError) : SearchContent

    data class Loaded(
        val papers: List<Paper>,
        val isStale: Boolean = false,
        val appendState: AppendState = AppendState.IDLE,
        val hasMore: Boolean = false,
        /** The matched total the data source supplied, or null when it supplied none. Never
         * derived from [papers] — "showing N" and "showing N of M" are different claims. */
        val totalItems: Int? = null,
    ) : SearchContent
}

data class SearchUiState(
    val queryText: String = "",
    val filters: PaperFilters = PaperFilters(),
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val recentQueries: List<String> = emptyList(),
    val content: SearchContent = SearchContent.Initial,
    /** Why the last download attempt could not start. Persistent until dismissed so the outcome
     * of a user action is never silently dropped (AND-006). */
    val downloadFeedback: DownloadFeedback? = null,
    /** Starting points offered before any query has been submitted. A fixed local list presented
     * as plain queries — see [SUGGESTED_TOPICS]. */
    val suggestedTopics: List<String> = SUGGESTED_TOPICS,
    val demoDisclosure: DemoDisclosureState = DemoDisclosureState.Hidden,
)
