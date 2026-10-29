package com.example.paperReader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.ResumablePaper
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.PaperCard
import com.example.paperReader.ui.theme.spacing

@Composable
fun LibraryRoute(
    onOpenPaper: (PaperId) -> Unit,
    onOpenReader: (downloadId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.openReaderRequestsFlow.collect(onOpenReader)
    }
    LibraryScreen(
        state = uiState,
        onOpenPaper = onOpenPaper,
        onToggleSave = viewModel::toggleSaved,
        onReadPdf = viewModel::readPdf,
        onReadOnline = uriHandler::openUri,
        onContinueReading = viewModel::continueReading,
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
        modifier = modifier,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenPaper: (PaperId) -> Unit,
    onToggleSave: (Paper) -> Unit,
    onReadPdf: (Paper) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
    onContinueReading: (ResumablePaper) -> Unit = {},
    onDismissDownloadFeedback: () -> Unit = {},
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

        state.downloadFeedback?.let { feedback ->
            item(key = "download-feedback") {
                DownloadFeedbackNotice(feedback = feedback, onDismiss = onDismissDownloadFeedback)
            }
        }

        if (state.continueReading.isNotEmpty()) {
            item(key = "continue-reading-heading") {
                SectionHeading(stringResource(R.string.library_continue_reading_title))
            }
            items(state.continueReading, key = { "continue-${it.paperId.value}" }) { paper ->
                ContinueReadingRow(paper = paper, onClick = { onContinueReading(paper) })
            }
            item(key = "saved-heading") {
                SectionHeading(stringResource(R.string.library_saved_title))
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
                    onReadPdf = { onReadPdf(paper) },
                    onReadOnline = onReadOnline,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/** One paper to pick up again: its title and the page it was left on, opening the reader there. */
@Composable
private fun ContinueReadingRow(paper: ResumablePaper, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.library_continue_reading_action), onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.card),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline),
            ) {
                Text(
                    text = paper.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_continue_reading_page, paper.position.pageIndex + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
