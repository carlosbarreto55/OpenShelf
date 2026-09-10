package com.example.paperReader.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.PaperCard
import com.example.paperReader.ui.components.RecoverableErrorState
import com.example.paperReader.ui.components.StaleDataNotice
import com.example.paperReader.ui.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SearchRoute(
    onOpenPaper: (PaperId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SearchScreen(
        state = uiState,
        onQueryTextChanged = viewModel::onQueryTextChanged,
        onFiltersChanged = viewModel::onFiltersChanged,
        onClearFilters = viewModel::clearFilters,
        onSortOrderChanged = viewModel::onSortOrderChanged,
        onRetry = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        onToggleSave = viewModel::toggleSaved,
        onOpenPaper = { onOpenPaper(it.id) },
        onStartDownload = viewModel::startDownload,
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
        onReadOnline = uriHandler::openUri,
        modifier = modifier,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryTextChanged: (String) -> Unit,
    onFiltersChanged: (PaperFilters) -> Unit,
    onClearFilters: () -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    onToggleSave: (Paper) -> Unit,
    onOpenPaper: (Paper) -> Unit,
    onStartDownload: (Paper) -> Unit,
    onDismissDownloadFeedback: () -> Unit = {},
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val content = state.content

    val canLoadMore = content is SearchContent.Loaded && content.hasMore && content.appendState == AppendState.IDLE
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - PREFETCH_THRESHOLD
            }
            .distinctUntilChanged()
            .collect { shouldLoad -> if (shouldLoad) onLoadNextPage() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.page,
            vertical = MaterialTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item(key = "header") {
            SearchHeader()
        }

        item(key = "search-field") {
            SearchField(
                query = state.queryText,
                onQueryChanged = onQueryTextChanged,
            )
        }

        state.downloadFeedback?.let { feedback ->
            item(key = "download-feedback") {
                DownloadFeedbackNotice(feedback = feedback, onDismiss = onDismissDownloadFeedback)
            }
        }

        item(key = "filter-controls") {
            SearchFilterControls(filters = state.filters, onFiltersChanged = onFiltersChanged)
        }

        item(key = "filters") {
            FiltersAndSortRow(
                filters = state.filters,
                sortOrder = state.sortOrder,
                onFiltersChanged = onFiltersChanged,
                onClearFilters = onClearFilters,
                onSortOrderChanged = onSortOrderChanged,
            )
        }

        when (content) {
            SearchContent.Initial -> item(key = "initial") {
                InitialContent(
                    recentQueries = state.recentQueries,
                    onQuerySelected = onQueryTextChanged,
                )
            }

            SearchContent.Loading -> item(key = "loading") {
                LoadingState(message = stringResource(R.string.search_loading_message))
            }

            is SearchContent.Empty -> item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.search_empty_title, content.queryText),
                    description = stringResource(R.string.search_empty_description),
                    actionLabel = stringResource(R.string.search_clear_filters),
                    onAction = onClearFilters,
                )
            }

            // Non-terminal: a cursor is still available, so this offers continuation rather than
            // claiming the query has no matches.
            is SearchContent.MorePagesPending -> item(key = "more-pages-pending") {
                EmptyState(
                    title = stringResource(R.string.search_more_pages_title),
                    description = stringResource(R.string.search_more_pages_description),
                    actionLabel = stringResource(R.string.search_more_pages_action),
                    onAction = onLoadNextPage,
                )
            }

            is SearchContent.Error -> item(key = "error") {
                RecoverableErrorState(message = content.message, onRetry = onRetry)
            }

            is SearchContent.Loaded -> {
                item(key = "result-count") {
                    // `papers` already holds one entry per stable paper ID (the cache enforces
                    // that), so its size is the count of unique visible papers. The matched total
                    // is only mentioned when the data source supplied one — it is never inferred.
                    val shown = content.papers.size
                    val total = content.totalItems
                    Text(
                        text = if (total != null) {
                            pluralStringResource(
                                R.plurals.search_result_count_of_total,
                                total,
                                shown,
                                total,
                                state.queryText,
                            )
                        } else {
                            pluralStringResource(R.plurals.search_result_count, shown, shown, state.queryText)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (content.isStale) {
                    item(key = "stale") {
                        StaleDataNotice(onRefresh = onRetry)
                    }
                }

                items(content.papers, key = { it.id.value }) { paper ->
                    PaperCard(
                        paper = paper,
                        onClick = { onOpenPaper(paper) },
                        onToggleSave = { onToggleSave(paper) },
                        onDownloadPdf = { onStartDownload(paper) },
                        onReadOnline = onReadOnline,
                    )
                }

                when (content.appendState) {
                    AppendState.LOADING -> item(key = "append-loading") {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(MaterialTheme.spacing.section))
                        }
                    }

                    AppendState.ERROR -> item(key = "append-error") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.search_append_error),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onLoadNextPage) {
                                Text(stringResource(R.string.search_append_retry))
                            }
                        }
                    }

                    AppendState.IDLE -> Unit
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Text(
            text = stringResource(R.string.brand_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.search_hero_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.search_hero_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_field_label)) },
        placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
        },
        singleLine = true,
    )
}

@Composable
private fun FiltersAndSortRow(
    filters: PaperFilters,
    sortOrder: SortOrder,
    onFiltersChanged: (PaperFilters) -> Unit,
    onClearFilters: () -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val noFiltersActive = filters == PaperFilters()
        FilterChip(
            selected = noFiltersActive,
            onClick = onClearFilters,
            label = { Text(stringResource(R.string.filter_all)) },
        )
        FilterChip(
            selected = filters.openAccessOnly,
            onClick = { onFiltersChanged(filters.copy(openAccessOnly = !filters.openAccessOnly)) },
            label = { Text(stringResource(R.string.filter_open_access)) },
        )
        FilterChip(
            selected = filters.pdfAvailableOnly,
            onClick = { onFiltersChanged(filters.copy(pdfAvailableOnly = !filters.pdfAvailableOnly)) },
            label = { Text(stringResource(R.string.filter_pdf_available)) },
        )

        SortMenuButton(sortOrder = sortOrder, onSortOrderChanged = onSortOrderChanged)
    }
}

@Composable
private fun SortMenuButton(
    sortOrder: SortOrder,
    onSortOrderChanged: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = sortOrder.displayLabel()
    val sortContentDescription = stringResource(R.string.search_sort_label, currentLabel)
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = sortContentDescription },
        ) {
            Text(currentLabel)
            Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayLabel()) },
                    onClick = {
                        onSortOrderChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortOrder.displayLabel(): String = when (this) {
    SortOrder.RELEVANCE -> stringResource(R.string.search_sort_relevance)
    SortOrder.NEWEST -> stringResource(R.string.search_sort_newest)
    SortOrder.CITATIONS -> stringResource(R.string.search_sort_citations)
}

@Composable
private fun InitialContent(
    recentQueries: List<String>,
    onQuerySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.card),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
            ) {
                Text(
                    text = stringResource(R.string.search_shell_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.search_shell_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (recentQueries.isNotEmpty()) {
            Text(
                text = stringResource(R.string.search_recent_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
            ) {
                recentQueries.forEach { recent ->
                    SuggestionChip(
                        onClick = { onQuerySelected(recent) },
                        label = { Text(recent) },
                    )
                }
            }
        }
    }
}

private const val PREFETCH_THRESHOLD = 3
