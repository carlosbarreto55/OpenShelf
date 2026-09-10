package com.example.paperReader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.theme.spacing

/**
 * Why a download could not start or resume, in words safe to show.
 *
 * Built only from [RepositoryError.userMessage], which never contains a descriptor URL, an endpoint
 * address, or a server response body — the transfer-time confirmation failures this reports would
 * otherwise be the easiest place for one to leak into presentation state.
 */
data class DownloadFeedback(
    val message: String,
    val canRetry: Boolean,
) {
    companion object {
        fun from(error: RepositoryError): DownloadFeedback {
            return DownloadFeedback(message = error.userMessage, canRetry = error.isRecoverable)
        }
    }
}

/**
 * Announced assertively and dismissed explicitly, rather than shown as a transient snackbar: a
 * failed download confirmation is the answer to something the user just asked for, so it must not
 * disappear before a screen-reader user has heard it.
 */
@Composable
fun DownloadFeedbackNotice(
    feedback: DownloadFeedback,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            AccessibleButton(label = "Dismiss", onClick = onDismiss)
        }
    }
}
