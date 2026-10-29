package com.example.paperReader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.paperReader.R
import com.example.paperReader.domain.model.DownloadFailureReason
import com.example.paperReader.ui.theme.spacing

/**
 * A failed transfer named by its cause, followed by the record's own safe message, followed by the
 * fact the user actually needs: nothing half-written was left behind. That last line is
 * unconditional because it is unconditionally true — bytes are only ever written after validation
 * succeeds, and a canceled transfer deletes whatever it had.
 *
 * Shared by Downloads and the reader, so a failure reads the same wherever it is met. A transfer
 * failure is a [DownloadFailureReason], not a repository error: a retry that cannot be confirmed is
 * reported separately, through [DownloadFeedbackNotice].
 */
@Composable
fun DownloadFailureSummary(errorMessage: String?, modifier: Modifier = Modifier) {
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
            modifier = Modifier.semantics { heading() },
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
