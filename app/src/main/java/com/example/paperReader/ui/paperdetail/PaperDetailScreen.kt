package com.example.paperReader.ui.paperdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.ui.components.AccessActionRow
import com.example.paperReader.ui.components.AccessibleIconButton
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.ErrorRoute
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.MetadataPill
import com.example.paperReader.ui.components.RecoverableErrorState
import com.example.paperReader.ui.components.StaleDataNotice
import com.example.paperReader.ui.components.displayLabel
import com.example.paperReader.ui.components.formatCitationCount
import com.example.paperReader.ui.theme.spacing

@Composable
fun PaperDetailRoute(
    onBack: () -> Unit,
    onNavigateTo: (ErrorRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaperDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    PaperDetailScreen(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggleSave = viewModel::toggleSaved,
        onDownloadPdf = { viewModel.startDownload() },
        onReadOnline = uriHandler::openUri,
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
        onNavigateTo = onNavigateTo,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailScreen(
    state: PaperDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleSave: () -> Unit,
    onDownloadPdf: (ConfirmedPdfDescriptor) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismissDownloadFeedback: () -> Unit = {},
    /** Null where there is no navigation to offer; the secondary route is then not rendered. */
    onNavigateTo: ((ErrorRoute) -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    AccessibleIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            state.downloadFeedback?.let { feedback ->
                DownloadFeedbackNotice(
                    feedback = feedback,
                    onDismiss = onDismissDownloadFeedback,
                    modifier = Modifier.padding(MaterialTheme.spacing.page),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (val content = state.content) {
                    PaperDetailContent.Loading -> LoadingState(
                        message = "Loading paper",
                        modifier = Modifier.align(Alignment.Center),
                    )

                    // Terminal, and therefore offered without a retry control: the backend has
                    // confirmed the paper is gone, so "try again" would be misleading.
                    PaperDetailContent.NotFound -> EmptyState(
                        title = "Paper not found",
                        description = "This paper is no longer available from the PaperReader service.",
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is PaperDetailContent.Error -> RecoverableErrorState(
                        error = content.error,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                        onNavigateTo = onNavigateTo,
                    )

                    is PaperDetailContent.Loaded -> PaperDetailBody(
                        paper = content.paper,
                        isStale = content.isStale,
                        onRefresh = onRetry,
                        onToggleSave = onToggleSave,
                        onDownloadPdf = onDownloadPdf,
                        onReadOnline = onReadOnline,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperDetailBody(
    paper: Paper,
    isStale: Boolean,
    onRefresh: () -> Unit,
    onToggleSave: () -> Unit,
    onDownloadPdf: (ConfirmedPdfDescriptor) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.page, vertical = MaterialTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.card),
    ) {
        if (isStale) {
            StaleDataNotice(onRefresh = onRefresh)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = paper.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            val saved = paper.savedState == SavedState.SAVED
            AccessibleIconButton(
                icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (saved) "Remove from library" else "Save paper",
                onClick = onToggleSave,
            )
        }

        val authorLine = paper.authors.takeIf(List<*>::isNotEmpty)?.joinToString(", ") { it.name }
        if (authorLine != null) {
            Text(
                text = authorLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val metadataLine = formatDetailMetadataLine(paper)
        if (metadataLine != null) {
            Text(
                text = metadataLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (paper.topics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
                paper.topics.forEach { topic -> MetadataPill(text = topic.name) }
            }
        }

        AccessActionRow(
            access = paper.access,
            onDownloadPdf = onDownloadPdf,
            onReadOnline = onReadOnline,
        )

        val abstractText = paper.abstractText
        if (abstractText != null) {
            Text(
                text = "Abstract",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = abstractText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun formatDetailMetadataLine(paper: Paper): String? {
    val parts = buildList {
        paper.venue?.let(::add)
        paper.publicationYear?.let { add(it.toString()) }
        paper.type.displayLabel()?.let(::add)
        paper.citationCount?.let { add(formatCitationCount(it)) }
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(separator = " · ")
}
