package com.example.paperReader.ui.downloads

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.ui.components.DownloadFailureSummary
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.NoticeState
import com.example.paperReader.ui.components.openInAnotherApp
import com.example.paperReader.ui.theme.spacing

@Composable
fun DownloadsRoute(
    onOpenReader: (downloadId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openFileRequestsFlow.collect { request ->
            if (!context.openInAnotherApp(request)) viewModel.onNoViewerAvailable(request.downloadId)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.openReaderRequestsFlow.collect(onOpenReader)
    }

    DownloadsScreen(
        state = uiState,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onOpen = viewModel::open,
        onOpenInAnotherApp = viewModel::openInAnotherApp,
        onDismissNoViewerNotice = viewModel::dismissNoViewerNotice,
        onClearCompleted = viewModel::clearCompleted,
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
        modifier = modifier,
    )
}

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onOpen: (DownloadRecord) -> Unit,
    onDismissNoViewerNotice: () -> Unit,
    modifier: Modifier = Modifier,
    onClearCompleted: () -> Unit = {},
    onDismissDownloadFeedback: () -> Unit = {},
    onOpenInAnotherApp: (DownloadRecord) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.page, vertical = MaterialTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
                Text(
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.downloads_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Offered only when there is something completed to clear, so the control never
                // appears as an action with nothing to act on.
                if (state.hasCompletedDownloads) {
                    TextButton(onClick = onClearCompleted, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.downloads_action_clear_completed))
                    }
                }
            }
        }

        state.downloadFeedback?.let { feedback ->
            item(key = "download-feedback") {
                DownloadFeedbackNotice(feedback = feedback, onDismiss = onDismissDownloadFeedback)
            }
        }

        if (state.noViewerAvailableForDownloadId != null) {
            item(key = "no-viewer") {
                // A fact about the device, not a repository failure, so it does not borrow a
                // cause from the error taxonomy.
                NoticeState(
                    headline = stringResource(R.string.downloads_no_viewer_title),
                    message = stringResource(R.string.downloads_no_viewer_message),
                    actionLabel = stringResource(R.string.error_notice_dismiss),
                    onAction = onDismissNoViewerNotice,
                )
            }
        }

        when (val content = state.content) {
            DownloadsContent.Loading -> item(key = "loading") { LoadingState() }

            DownloadsContent.Empty -> item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.downloads_empty_title),
                    description = stringResource(R.string.downloads_empty_description),
                    icon = Icons.Outlined.Download,
                )
            }

            // Only groups that actually have records are emitted, so no heading ever stands over
            // an empty section.
            is DownloadsContent.Loaded -> content.sections.forEach { section ->
                item(key = "heading-" + section.group.name) {
                    Text(
                        text = stringResource(section.group.headingRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                items(section.records, key = { it.id }) { record ->
                    DownloadRow(
                        record = record,
                        onRetry = { onRetry(record.id) },
                        onCancel = { onCancel(record.id) },
                        onOpen = { onOpen(record) },
                        onOpenInAnotherApp = { onOpenInAnotherApp(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onOpenInAnotherApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.card),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(record.state.displayLabelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (record.state == DownloadState.RUNNING) {
                val progress = record.progress
                if (progress?.totalBytes != null && progress.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.bytesDownloaded.toFloat() / progress.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (record.state == DownloadState.COMPLETED && record.mimeType != "application/pdf") {
                Text(
                    text = stringResource(R.string.downloads_demo_placeholder_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (record.state == DownloadState.FAILED) {
                DownloadFailureSummary(errorMessage = record.errorMessage)
            } else if (record.errorMessage != null) {
                Text(
                    text = record.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control), modifier = Modifier.align(Alignment.End)) {
                when (record.state) {
                    DownloadState.QUEUED, DownloadState.RUNNING -> TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.downloads_action_cancel))
                    }

                    DownloadState.FAILED -> TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.downloads_action_retry))
                    }

                    // Open reads it here; the handoff stays a real, explicit choice beside it.
                    DownloadState.COMPLETED -> {
                        TextButton(onClick = onOpenInAnotherApp) {
                            Text(stringResource(R.string.downloads_action_open_elsewhere))
                        }
                        TextButton(onClick = onOpen) {
                            Text(stringResource(R.string.downloads_action_open))
                        }
                    }

                    DownloadState.CANCELED -> TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.downloads_action_retry))
                    }
                }
            }
        }
    }
}

private fun DownloadState.displayLabelRes(): Int = when (this) {
    DownloadState.QUEUED -> R.string.downloads_state_queued
    DownloadState.RUNNING -> R.string.downloads_state_running
    DownloadState.COMPLETED -> R.string.downloads_state_completed
    DownloadState.FAILED -> R.string.downloads_state_failed
    DownloadState.CANCELED -> R.string.downloads_state_canceled
}
