package com.example.paperReader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.PaperCard
import com.example.paperReader.ui.theme.spacing

@Composable
fun LibraryRoute(
    onOpenPaper: (PaperId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LibraryScreen(
        state = uiState,
        onOpenPaper = onOpenPaper,
        onToggleSave = viewModel::toggleSaved,
        onStartDownload = viewModel::startDownload,
        onReadOnline = uriHandler::openUri,
        modifier = modifier,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenPaper: (PaperId) -> Unit,
    onToggleSave: (Paper) -> Unit,
    onStartDownload: (Paper) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.page,
            vertical = MaterialTheme.spacing.section,
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.library_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val content = state.content) {
            LibraryContent.Loading -> item(key = "loading") { LoadingState() }

            LibraryContent.Empty -> item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.library_empty_title),
                    description = stringResource(R.string.library_empty_description),
                    icon = Icons.Outlined.BookmarkBorder,
                )
            }

            is LibraryContent.Loaded -> items(content.papers, key = { it.id.value }) { paper ->
                PaperCard(
                    paper = paper,
                    onClick = { onOpenPaper(paper.id) },
                    onToggleSave = { onToggleSave(paper) },
                    onDownloadPdf = { onStartDownload(paper) },
                    onReadOnline = onReadOnline,
                )
            }
        }
    }
}
