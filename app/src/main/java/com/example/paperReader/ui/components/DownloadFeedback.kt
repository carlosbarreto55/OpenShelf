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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.paperReader.R
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.theme.spacing

/**
 * Why a download could not start or resume, carried as the classified cause so this surface names
 * the failure the same way every other one does (design.md decision 1).
 *
 * Everything shown comes from [toPresentation], whose explanation is only ever
 * [RepositoryError.userMessage] — never a descriptor URL, an endpoint address, or a server response
 * body. The transfer-time confirmation failures reported here would otherwise be the easiest place
 * for one to leak into presentation state.
 */
data class DownloadFeedback(val error: RepositoryError) {
    companion object {
        fun from(error: RepositoryError): DownloadFeedback = DownloadFeedback(error)
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
        val presentation = feedback.error.toPresentation()
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            Text(
                text = stringResource(presentation.headlineRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = presentation.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            AccessibleButton(
                label = stringResource(R.string.error_notice_dismiss),
                onClick = onDismiss,
            )
        }
    }
}
