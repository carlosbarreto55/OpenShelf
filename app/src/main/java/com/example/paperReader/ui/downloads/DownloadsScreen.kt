package com.example.paperReader.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.DownloadFailureReason
import com.example.paperReader.domain.model.DownloadRecord
import com.example.paperReader.domain.model.DownloadState
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.NoticeState
import com.example.paperReader.ui.theme.spacing

@Composable
fun DownloadsRoute(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openFileRequestsFlow.collect { request ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(request.contentUri.toUri(), request.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                viewModel.onNoViewerAvailable(request.downloadId)
            }
        }
    }

    DownloadsScreen(
        state = uiState,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onOpen = viewModel::open,
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
                DownloadFailureText(errorMessage = record.errorMessage)
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

                    DownloadState.COMPLETED -> TextButton(onClick = onOpen) {
                        Text(stringResource(R.string.downloads_action_open))
                    }

                    DownloadState.CANCELED -> TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.downloads_action_retry))
                    }
                }
            }
        }
    }
}

/**
 * A failure named by its cause, followed by the record's own safe message, followed by the fact the
 * user actually needs: nothing half-written was left behind. That last line is unconditional
 * because it is unconditionally true — bytes are only ever written after validation succeeds, and a
 * canceled transfer deletes whatever it had.
 */
@Composable
private fun DownloadFailureText(errorMessage: String?, modifier: Modifier = Modifier) {
    val headingRes = when (DownloadFailureReason.fromMessage(errorMessage)) {
        DownloadFailureReason.INVALID_PDF -> R.string.downloads_failure_heading_invalid_pdf
        DownloadFailureReason.INTERRUPTED -> R.string.downloads_failure_heading_interrupted
        // A transport failure carries the underlying I/O message; there is no cause to name beyond
        // "it did not finish", and inventing one would be a claim we cannot support.
        null -> R.string.downloads_failure_heading_generic
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Text(
            text = stringResource(headingRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.downloads_failure_no_partial_file),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DownloadState.displayLabelRes(): Int = when (this) {
    DownloadState.QUEUED -> R.string.downloads_state_queued
    DownloadState.RUNNING -> R.string.downloads_state_running
    DownloadState.COMPLETED -> R.string.downloads_state_completed
    DownloadState.FAILED -> R.string.downloads_state_failed
    DownloadState.CANCELED -> R.string.downloads_state_canceled
}
