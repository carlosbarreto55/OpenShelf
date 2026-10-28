package com.example.paperReader.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.ui.components.DemoExplanation
import com.example.paperReader.ui.components.DemoIndicator
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.ErrorRoute
import com.example.paperReader.ui.components.PaperCard
import com.example.paperReader.ui.components.RecoverableErrorState
import com.example.paperReader.ui.components.SkeletonPaperCardList
import com.example.paperReader.ui.components.StaleDataNotice
import com.example.paperReader.ui.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SearchRoute(
    onOpenPaper: (PaperId) -> Unit,
    onNavigateTo: (ErrorRoute) -> Unit,
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
        onOpenDemoExplanation = viewModel::openDemoExplanation,
        onDismissDemoExplanation = viewModel::dismissDemoExplanation,
        onReadOnline = uriHandler::openUri,
        onNavigateTo = onNavigateTo,
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
    onOpenDemoExplanation: () -> Unit = {},
    onDismissDemoExplanation: () -> Unit = {},
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Null on surfaces with no navigation to offer (previews, screenshots): the error state then
     * omits its secondary route rather than rendering one that goes nowhere. */
    onNavigateTo: ((ErrorRoute) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val content = state.content

    val canLoadMore = content is SearchContent.Loaded && content.hasMore && content.appendState == AppendState.IDLE
    // Both sides of the comparison below are indices *within the list*, so moving the controls out
    // of it shifted them together: [PREFETCH_THRESHOLD] still means "this many items from the end"
    // and needs no adjustment for the items that left.
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

    // Changing what is being searched makes the current scroll position meaningless — it belongs
    // to results that no longer exist — so the list returns to its first item (paper-discovery:
    // "Editing from the scrolled position"). Already at the top on first composition, where this
    // is a no-op.
    LaunchedEffect(state.queryText, state.filters, state.sortOrder) {
        listState.scrollToItem(0)
    }

    val expanded = rememberControlsExpanded(listState)
    // Not derived from the filters themselves: a user who opens the panel, clears everything and
    // wants to pick something else should not have the panel shut under them.
    var showMoreFilters by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Outside the LazyColumn, so the query, the filters and the sort order stay operable at
        // every scroll position instead of scrolling away with the first page of results.
        SearchControls(
            state = state,
            expanded = expanded,
            showMoreFilters = showMoreFilters,
            onShowMoreFiltersChanged = { showMoreFilters = it },
            onQueryTextChanged = onQueryTextChanged,
            onFiltersChanged = onFiltersChanged,
            onClearFilters = onClearFilters,
            onSortOrderChanged = onSortOrderChanged,
            onOpenDemoExplanation = onOpenDemoExplanation,
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.page,
                top = MaterialTheme.spacing.compact,
                end = MaterialTheme.spacing.page,
            ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(SEARCH_RESULT_LIST_TEST_TAG),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.page,
                vertical = MaterialTheme.spacing.compact,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact),
        ) {
            // In the list rather than the pinned region: a disclosure the user can finish with
            // must not reserve layout above the results for the life of the installation.
            if (state.demoDisclosure == DemoDisclosureState.FirstRun) {
                item(key = "demo-explanation") {
                    DemoExplanation(onDismiss = onDismissDemoExplanation)
                }
            }

            state.downloadFeedback?.let { feedback ->
                item(key = "download-feedback") {
                    DownloadFeedbackNotice(feedback = feedback, onDismiss = onDismissDownloadFeedback)
                }
            }

            when (content) {
                SearchContent.Initial -> item(key = "initial") {
                    InitialContent(
                        suggestedTopics = state.suggestedTopics,
                        recentQueries = state.recentQueries,
                        onQuerySelected = onQueryTextChanged,
                    )
                }

                // Placeholders in the shape of result cards rather than a centred spinner, so
                // results arrive into the list instead of replacing it (spec: "Search is loading").
                SearchContent.Loading -> item(key = "loading") {
                    SkeletonPaperCardList(
                        announcement = stringResource(R.string.search_loading_message),
                    )
                }

                is SearchContent.Empty -> item(key = "empty") {
                    SearchEmptyState(
                        queryText = content.queryText,
                        filters = state.filters,
                        onFiltersChanged = onFiltersChanged,
                        onClearFilters = onClearFilters,
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
                    RecoverableErrorState(
                        error = content.error,
                        onRetry = onRetry,
                        onNavigateTo = onNavigateTo,
                    )
                }

                is SearchContent.Loaded -> {
                    item(key = "result-count") {
                        // `papers` already holds one entry per stable paper ID (the cache enforces
                        // that), so its size is the count of unique visible papers. A stored backend
                        // total is only useful while the result set is fresh: after an offline/failed
                        // refresh, only the locally saved rows are available and "of total" would
                        // imply that the remaining matches can still be reached.
                        val shown = content.papers.size
                        val total = content.totalItems
                        Text(
                            text = when {
                                content.isStale -> pluralStringResource(
                                    R.plurals.search_result_count_cached,
                                    shown,
                                    shown,
                                    state.queryText,
                                )

                                total != null -> pluralStringResource(
                                    R.plurals.search_result_count_of_total,
                                    total,
                                    shown,
                                    total,
                                    state.queryText,
                                )

                                else -> pluralStringResource(
                                    R.plurals.search_result_count,
                                    shown,
                                    shown,
                                    state.queryText,
                                )
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
                            modifier = Modifier.testTag(SEARCH_RESULT_CARD_TEST_TAG),
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
}

/**
 * Whether the control region is showing its introductory header, derived from how far the *list*
 * has scrolled.
 *
 * Deliberately local Compose state rather than [SearchUiState]: collapse is a property of the
 * scroll position, not of the domain, and hoisting it into the ViewModel would turn every scroll
 * frame into a state emission (design.md decision 6).
 *
 * The two thresholds differ on purpose. Collapsing shortens the control region, which is exactly
 * the kind of height change that can feed back into the list; a single threshold would let the
 * header flicker on and off around it, so re-expanding requires scrolling materially further back
 * than collapsing did.
 */
@Composable
private fun rememberControlsExpanded(listState: LazyListState): Boolean {
    var expanded by remember(listState) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            // Past the first item the exact offset stops mattering: the user is in the results.
            if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE else listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { offset ->
                expanded = when {
                    offset >= COLLAPSE_THRESHOLD_PX -> false
                    offset <= EXPAND_THRESHOLD_PX -> true
                    else -> expanded
                }
            }
    }
    return expanded
}

/**
 * The query, the filters and the sort order: everything that decides *which* papers the list below
 * is showing, held outside that list so it is reachable at every scroll position.
 *
 * [expanded] changes only the introductory header and the height of the query field. Every control
 * — the query, each filter chip, the extra-filters disclosure and the sort menu — is present, in
 * the same order, with the same labels and the same selected states in both states, so a screen
 * reader user is never handed a different control set depending on how far the list has scrolled.
 */
@Composable
private fun SearchControls(
    state: SearchUiState,
    expanded: Boolean,
    showMoreFilters: Boolean,
    onShowMoreFiltersChanged: (Boolean) -> Unit,
    onQueryTextChanged: (String) -> Unit,
    onFiltersChanged: (PaperFilters) -> Unit,
    onClearFilters: () -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    onOpenDemoExplanation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        AnimatedVisibility(visible = expanded) {
            SearchHeader(
                demoDisclosure = state.demoDisclosure,
                onOpenDemoExplanation = onOpenDemoExplanation,
            )
        }

        SearchField(
            query = state.queryText,
            onQueryChanged = onQueryTextChanged,
            compact = !expanded,
        )

        FiltersAndSortRow(
            filters = state.filters,
            sortOrder = state.sortOrder,
            showMoreFilters = showMoreFilters,
            onShowMoreFiltersChanged = onShowMoreFiltersChanged,
            onFiltersChanged = onFiltersChanged,
            onClearFilters = onClearFilters,
            onSortOrderChanged = onSortOrderChanged,
        )

        // Year and work type sit behind a disclosure rather than permanently above the results:
        // pinned, they cost more of the first viewport than the first result card needs
        // (paper-discovery: "First-result visibility"). The disclosure itself is in the row above,
        // so they are reachable at any scroll position.
        AnimatedVisibility(visible = showMoreFilters) {
            SearchFilterControls(filters = state.filters, onFiltersChanged = onFiltersChanged)
        }
    }
}

@Composable
private fun SearchHeader(
    demoDisclosure: DemoDisclosureState,
    onOpenDemoExplanation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // Present for the whole life of a demo build, acknowledged or not.
            if (demoDisclosure.isDemoBuild) {
                DemoIndicator(onClick = onOpenDemoExplanation)
            }
        }
        Text(
            text = stringResource(R.string.search_hero_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The query field.
 *
 * Built on [BasicTextField] rather than `OutlinedTextField` for one reason: the outlined field's
 * own minimum height cannot be reduced, and the collapsed control region needs a shorter one. Both
 * heights are floored at the touch-target token, so "shorter" never means "harder to hit".
 *
 * The accessible name is a content description in both states rather than a floating label, so the
 * field is announced identically however tall it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val fieldLabel = stringResource(R.string.search_field_label)
    val interactionSource = remember { MutableInteractionSource() }
    val spacing = MaterialTheme.spacing
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) spacing.fieldCompact else spacing.field)
            .semantics { contentDescription = fieldLabel },
        textStyle = LocalTextStyle.current.merge(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    top = if (compact) spacing.control else spacing.card,
                    bottom = if (compact) spacing.control else spacing.card,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                    )
                },
            )
        },
    )
}

@Composable
private fun FiltersAndSortRow(
    filters: PaperFilters,
    sortOrder: SortOrder,
    showMoreFilters: Boolean,
    onShowMoreFiltersChanged: (Boolean) -> Unit,
    onFiltersChanged: (PaperFilters) -> Unit,
    onClearFilters: () -> Unit,
    onSortOrderChanged: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wraps rather than scrolls sideways, for the same reason the year and work-type controls do:
    // a control the user has to discover a horizontal scroll to reach is not "reachable at every
    // scroll position" in any useful sense. On a 412dp phone the sort control is the one that
    // falls off the end.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Which filters are on is answered in exactly one place, shared with the empty state,
        // so a filter added to `activeFiltersOf` is reflected here without another comparison
        // against `PaperFilters()` (design.md decision 4).
        val activeIds = activeFiltersOf(filters).map { it.id }.toSet()
        FilterChip(
            selected = activeIds.isEmpty(),
            onClick = onClearFilters,
            label = { Text(stringResource(R.string.filter_all)) },
        )
        FilterChip(
            selected = ActiveFilterId.OPEN_ACCESS in activeIds,
            onClick = { onFiltersChanged(filters.copy(openAccessOnly = !filters.openAccessOnly)) },
            label = { Text(stringResource(R.string.filter_open_access)) },
        )
        FilterChip(
            selected = ActiveFilterId.PDF_AVAILABLE in activeIds,
            onClick = { onFiltersChanged(filters.copy(pdfAvailableOnly = !filters.pdfAvailableOnly)) },
            label = { Text(stringResource(R.string.filter_pdf_available)) },
        )
        // Selected when the panel is open *or* when a filter inside it is narrowing the search, so
        // a year or work type is never applied invisibly.
        val moreFiltersActive = ActiveFilterId.PUBLICATION_YEAR in activeIds ||
            ActiveFilterId.PAPER_TYPE in activeIds
        val moreFiltersDescription = stringResource(
            if (showMoreFilters) R.string.search_filters_collapse else R.string.search_filters_expand,
        )
        FilterChip(
            selected = showMoreFilters || moreFiltersActive,
            onClick = { onShowMoreFiltersChanged(!showMoreFilters) },
            label = { Text(stringResource(R.string.search_filters_label)) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
            modifier = Modifier.semantics { contentDescription = moreFiltersDescription },
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
            modifier = Modifier
                .heightIn(min = MaterialTheme.spacing.touchTarget)
                .semantics { contentDescription = sortContentDescription },
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

/**
 * What the screen offers before any query has been submitted: things that start a search.
 *
 * Both rows submit through the same handler typed input uses, so a suggestion is normalised,
 * debounced, cached and recorded into recent searches identically — there is no second search path
 * for it to drift from.
 */
@Composable
private fun InitialContent(
    suggestedTopics: List<String>,
    recentQueries: List<String>,
    onQuerySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact),
    ) {
        if (recentQueries.isNotEmpty()) {
            QueryChipRow(
                label = stringResource(R.string.search_recent_label),
                queries = recentQueries,
                onQuerySelected = onQuerySelected,
            )
        }

        if (suggestedTopics.isNotEmpty()) {
            QueryChipRow(
                label = stringResource(R.string.search_suggestions_label),
                queries = suggestedTopics,
                onQuerySelected = onQuerySelected,
            )
        }
    }
}

@Composable
private fun QueryChipRow(
    label: String,
    queries: List<String>,
    onQuerySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            queries.forEach { query ->
                SuggestionChip(
                    onClick = { onQuerySelected(query) },
                    label = { Text(query) },
                    modifier = Modifier.heightIn(min = MaterialTheme.spacing.touchTarget),
                )
            }
        }
    }
}

/** The scrolling result region, so a test can scroll it without depending on which item happens
 * to be at which index. */
const val SEARCH_RESULT_LIST_TEST_TAG = "search-result-list"

const val SEARCH_RESULT_CARD_TEST_TAG = "search-result-card"

private const val PREFETCH_THRESHOLD = 3

/** Scrolled far enough into the results that the introductory header has stopped earning its
 * space. In pixels, because that is the unit [LazyListState] reports offsets in. */
private const val COLLAPSE_THRESHOLD_PX = 48

/** Scrolled back near enough to the top to bring the header back. Materially below
 * [COLLAPSE_THRESHOLD_PX]: the gap between them is what stops the header flickering at the
 * boundary. */
private const val EXPAND_THRESHOLD_PX = 8
