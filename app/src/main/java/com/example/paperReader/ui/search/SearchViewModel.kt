package com.example.paperReader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperReader.domain.model.CursorPage
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperQuery
import com.example.paperReader.domain.model.PaperSearchRequest
import com.example.paperReader.domain.model.SearchQuery
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.repository.DownloadsRepository
import com.example.paperReader.domain.repository.PaperRepository
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.domain.usecase.PageLoadOutcome
import com.example.paperReader.domain.usecase.SearchCursorSession
import com.example.paperReader.domain.usecase.SearchPapersUseCase
import com.example.paperReader.domain.usecase.ToggleSavedPaperUseCase
import com.example.paperReader.ui.components.DownloadFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PaperRepository,
    private val searchPapers: SearchPapersUseCase,
    private val toggleSavedPaper: ToggleSavedPaperUseCase,
    private val downloadsRepository: DownloadsRepository,
) : ViewModel() {

    private val rawQueryText = MutableStateFlow("")
    private val filters = MutableStateFlow(PaperFilters())
    private val sortOrder = MutableStateFlow(SortOrder.RELEVANCE)
    private val refreshSignal = MutableStateFlow(0)
    private val appendState = MutableStateFlow(AppendState.IDLE)
    private val downloadFeedback = MutableStateFlow<DownloadFeedback?>(null)
    private val paginationOutcome = MutableStateFlow<PageLoadOutcome?>(null)

    /** The traversal for the query currently on screen. Replaced wholesale when the query,
     * filters, or sort change, which is what cancels the old one (7.7). */
    @Volatile private var activeSession: SearchCursorSession? = null

    private val settledQuery = combine(
        rawQueryText.debounce(DEBOUNCE_MILLIS).distinctUntilChanged(),
        filters,
        sortOrder,
        refreshSignal,
    ) { text, currentFilters, currentSort, _ ->
        SearchQuery.normalize(text)?.let { PaperQuery(it, currentFilters, currentSort) }
    }

    private val contentFlow = settledQuery.flatMapLatest { query ->
        activeSession = query?.let { SearchCursorSession(it, searchPapers) }
        appendState.value = AppendState.IDLE
        if (query == null) flowOf(SearchContent.Initial) else resultsFlow(query)
    }

    val uiState: StateFlow<SearchUiState> = combine(
        rawQueryText,
        filters,
        sortOrder,
        repository.observeRecentQueries(),
        contentFlow,
        downloadFeedback,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SearchUiState(
            queryText = values[0] as String,
            filters = values[1] as PaperFilters,
            sortOrder = values[2] as SortOrder,
            recentQueries = values[3] as List<String>,
            content = values[4] as SearchContent,
            downloadFeedback = values[5] as DownloadFeedback?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    fun onQueryTextChanged(text: String) {
        rawQueryText.value = text
    }

    fun onFiltersChanged(newFilters: PaperFilters) {
        filters.value = newFilters
    }

    fun clearFilters() {
        filters.value = PaperFilters()
    }

    fun onSortOrderChanged(newSortOrder: SortOrder) {
        sortOrder.value = newSortOrder
    }

    fun retry() {
        refreshSignal.value++
    }

    fun loadNextPage() {
        val session = activeSession ?: return
        if (!session.hasMore) return
        if (appendState.value == AppendState.LOADING) return

        viewModelScope.launch {
            appendState.value = AppendState.LOADING
            // A late response from a session that has since been replaced must not touch state.
            val outcome = session.loadNextPage()
            if (activeSession !== session) return@launch
            appendState.value = when (outcome) {
                is PageLoadOutcome.Failed -> AppendState.ERROR
                else -> AppendState.IDLE
            }
            paginationOutcome.value = outcome
        }
    }

    fun toggleSaved(paper: Paper) {
        viewModelScope.launch { toggleSavedPaper(paper) }
    }

    fun startDownload(paper: Paper) {
        viewModelScope.launch {
            downloadFeedback.value = null
            // The transfer is authorized by a fresh descriptor confirmation, so this can fail for
            // reasons the user needs to hear about: no PDF, not found, offline, timeout.
            when (val result = downloadsRepository.startDownload(paper)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> downloadFeedback.value = DownloadFeedback.from(result.error)
            }
        }
    }

    fun dismissDownloadFeedback() {
        downloadFeedback.value = null
    }

    /** One query's observable results: the cached/Room-backed page decorated with the status of a
     * concurrent network refresh. Cancelling the collector (a new query settling via
     * [flatMapLatest]) cancels the in-flight [SearchPapersUseCase] call too. */
    private fun resultsFlow(query: PaperQuery) = channelFlow {
        val session = requireNotNull(activeSession)
        val networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Loading)

        launch {
            combine(
                repository.observeSearch(query),
                networkStatus,
                appendState,
                paginationOutcome,
            ) { page, status, append, outcome ->
                toContent(query, page, status, append, outcome)
            }.collect { send(it) }
        }

        launch {
            // One first-page action, however many empty pages the session has to traverse inside
            // it: the status stays Loading until the whole traversal concludes, so a later
            // non-empty page never appears after a terminal empty state has flashed (7.4).
            val outcome = session.loadFirstPage()
            paginationOutcome.value = outcome
            networkStatus.value = when (outcome) {
                is PageLoadOutcome.Failed -> NetworkStatus.Error(outcome.error)
                else -> NetworkStatus.Idle
            }
        }
    }

    private fun toContent(
        query: PaperQuery,
        page: CursorPage<Paper>,
        status: NetworkStatus,
        append: AppendState,
        outcome: PageLoadOutcome?,
    ): SearchContent {
        return when {
            page.items.isEmpty() && status is NetworkStatus.Loading -> SearchContent.Loading
            page.items.isEmpty() && status is NetworkStatus.Error ->
                SearchContent.Error(status.error.userMessage, status.error.isRecoverable)

            // "No matches" is only true when the sequence actually ended. A retained cursor means
            // there may be papers on a later page, and saying otherwise would be a false claim.
            page.items.isEmpty() && outcome is PageLoadOutcome.EmptyPageBoundReached ->
                SearchContent.MorePagesPending(query.text.value)

            page.items.isEmpty() -> SearchContent.Empty(query.text.value)
            else -> SearchContent.Loaded(
                papers = page.items,
                isStale = status is NetworkStatus.Error,
                appendState = append,
                hasMore = page.nextCursor != null,
                totalItems = page.totalItems,
            )
        }
    }

    private sealed interface NetworkStatus {
        data object Loading : NetworkStatus

        data object Idle : NetworkStatus

        data class Error(val error: RepositoryError) : NetworkStatus
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
