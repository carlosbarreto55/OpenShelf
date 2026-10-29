package com.example.paperReader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paperReader.R
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.ui.components.AccessibleButton
import com.example.paperReader.ui.components.AccessibleIconButton
import com.example.paperReader.ui.components.DownloadFailureSummary
import com.example.paperReader.ui.components.DownloadFeedbackNotice
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.NoticeState
import com.example.paperReader.ui.components.openInAnotherApp
import com.example.paperReader.ui.theme.spacing
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pageImages by viewModel.pageImages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.openFileRequestsFlow.collect { request ->
            if (!context.openInAnotherApp(request)) viewModel.onNoViewerAvailable()
        }
    }
    // Leaving writes the last position at once; the debounced writes cover a process that dies.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onLeave() }
    }

    ReaderScreen(
        state = uiState,
        pageImages = pageImages,
        onBack = onBack,
        onCancelTransfer = viewModel::cancelTransfer,
        onRetryTransfer = viewModel::retryTransfer,
        onReadAgain = viewModel::readAgain,
        onOpenInAnotherApp = viewModel::openInAnotherApp,
        onDismissDownloadFeedback = viewModel::dismissDownloadFeedback,
        onDismissNoViewerNotice = viewModel::dismissNoViewerNotice,
        onViewport = viewModel::onViewport,
        onPositionChanged = viewModel::onPositionChanged,
        onZoomChanged = viewModel::onZoomChanged,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    pageImages: Map<Int, PageImage>,
    onBack: () -> Unit,
    onCancelTransfer: () -> Unit,
    onRetryTransfer: () -> Unit,
    onReadAgain: () -> Unit,
    onOpenInAnotherApp: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissDownloadFeedback: () -> Unit = {},
    onDismissNoViewerNotice: () -> Unit = {},
    onViewport: (ReaderViewport) -> Unit = {},
    onPositionChanged: (ReadingPosition) -> Unit = {},
    onZoomChanged: (Float) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    AccessibleIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
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
            if (state.noViewerAvailable) {
                NoticeState(
                    headline = stringResource(R.string.downloads_no_viewer_title),
                    message = stringResource(R.string.downloads_no_viewer_message),
                    actionLabel = stringResource(R.string.error_notice_dismiss),
                    onAction = onDismissNoViewerNotice,
                    modifier = Modifier.padding(MaterialTheme.spacing.page),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (val content = state.content) {
                    ReaderContent.Opening -> LoadingState(
                        message = stringResource(R.string.reader_opening),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is ReaderContent.Transferring -> TransferProgress(content, onCancelTransfer)

                    is ReaderContent.TransferFailed -> ReaderMessage {
                        DownloadFailureSummary(errorMessage = content.errorMessage)
                        AccessibleButton(label = stringResource(R.string.reader_action_retry), onClick = onRetryTransfer)
                    }

                    ReaderContent.Canceled -> ReaderMessage {
                        MessageText(
                            headline = stringResource(R.string.reader_canceled_title),
                            message = stringResource(R.string.reader_canceled_message),
                        )
                        AccessibleButton(label = stringResource(R.string.reader_action_back), onClick = onBack)
                    }

                    is ReaderContent.Refused -> ReaderMessage {
                        MessageText(
                            headline = stringResource(content.refusal.headlineRes),
                            message = stringResource(content.refusal.messageRes),
                        )
                        if (content.canReadAgain) {
                            AccessibleButton(label = stringResource(R.string.reader_action_read_again), onClick = onReadAgain)
                        }
                        if (content.canOpenExternally) {
                            TextButton(onClick = onOpenInAnotherApp) {
                                Text(stringResource(R.string.reader_action_open_elsewhere))
                            }
                        }
                    }

                    is ReaderContent.Reading -> DocumentPages(
                        content = content,
                        pageImages = pageImages,
                        onViewport = onViewport,
                        onPositionChanged = onPositionChanged,
                        onZoomChanged = onZoomChanged,
                    )
                }
            }
        }
    }
}

/** No partial pages: a transfer shows only its progress, and the document once it is complete. */
@Composable
private fun TransferProgress(content: ReaderContent.Transferring, onCancel: () -> Unit) {
    val status = when {
        content.queued -> stringResource(R.string.reader_transfer_queued)
        content.progress != null -> stringResource(R.string.reader_transfer_progress, (content.progress * 100).roundToInt())
        else -> stringResource(R.string.reader_transfer_running)
    }
    ReaderMessage {
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val label = stringResource(R.string.reader_transfer_running)
        if (content.progress != null) {
            LinearProgressIndicator(
                progress = { content.progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        }
        Text(
            text = stringResource(R.string.reader_transfer_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.reader_action_cancel)) }
    }
}

@Composable
private fun ReaderMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.card),
    ) {
        content()
    }
}

@Composable
private fun MessageText(headline: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            // Announced when it appears, since it usually replaces progress the user was waiting on.
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
