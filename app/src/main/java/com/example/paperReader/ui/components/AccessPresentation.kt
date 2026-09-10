package com.example.paperReader.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.ui.theme.spacing

/** The two — and only two — access actions the product allows. See [toAccessAction]. */
sealed interface AccessAction {
    data class DownloadPdf(val descriptor: ConfirmedPdfDescriptor) : AccessAction

    data class ReadOnline(val sourceUrl: String, val provenance: String?) : AccessAction
}

/**
 * The single access-to-UI mapping point required by design.md decision 4: every screen that shows
 * a paper's access affordance (search cards, paper details, saved papers, downloads) MUST render
 * through this function rather than branching on [AccessResolution] itself. By the time a [Paper]
 * reaches the UI, [AccessResolution.DirectPdf]/[AccessResolution.OpenFullText] are already
 * validated (see `PaperMappers`/`LocalMappers`), so no further URL checking happens here — every
 * other state simply yields no action, badge, or source row.
 */
fun AccessResolution.toAccessAction(): AccessAction? = when (this) {
    is AccessResolution.DirectPdf -> AccessAction.DownloadPdf(descriptor)
    is AccessResolution.OpenFullText -> AccessAction.ReadOnline(sourceUrl, provenance)
    AccessResolution.NoFreeFullText,
    AccessResolution.Paywalled,
    AccessResolution.Unknown,
    AccessResolution.Checking,
    AccessResolution.Error,
    -> null
}

/** Renders nothing for any state without a confirmed action — no badge, lock, or label either. */
@Composable
fun AccessActionRow(
    access: AccessResolution,
    onDownloadPdf: (ConfirmedPdfDescriptor) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val action = access.toAccessAction()) {
        is AccessAction.DownloadPdf -> TextButton(
            onClick = { onDownloadPdf(action.descriptor) },
            modifier = modifier.semantics { role = Role.Button },
        ) {
            Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
            Text(
                text = "Download PDF",
                modifier = Modifier.padding(start = MaterialTheme.spacing.control),
            )
        }

        is AccessAction.ReadOnline -> TextButton(
            onClick = { onReadOnline(action.sourceUrl) },
            modifier = modifier.semantics { role = Role.Button },
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            Text(
                text = "Read online",
                modifier = Modifier.padding(start = MaterialTheme.spacing.control),
            )
        }

        null -> Unit
    }
}
