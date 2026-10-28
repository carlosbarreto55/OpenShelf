package com.example.paperReader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.paperReader.R
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.theme.spacing

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { this.contentDescription = message },
        )
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Search,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            AccessibleButton(label = actionLabel, onClick = onAction)
        }
    }
}

/**
 * A failure presented by its classified cause (design.md decision 1): its own headline, its own
 * safe explanation, a retry *only* when the taxonomy says retrying can succeed, and a second route
 * that still works under this particular failure — offline, for instance, offers the Room-backed
 * library rather than a retry that cannot work.
 *
 * [onNavigateTo] is null on surfaces that have no navigation to offer (previews, screenshots);
 * the secondary route is then simply not rendered rather than rendered inert.
 */
@Composable
fun RecoverableErrorState(
    error: RepositoryError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateTo: ((ErrorRoute) -> Unit)? = null,
) {
    val presentation = error.toPresentation()
    ErrorSurface(
        headline = stringResource(presentation.headlineRes),
        message = presentation.explanation,
        modifier = modifier,
    ) {
        if (presentation.canRetry) {
            AccessibleButton(label = stringResource(R.string.error_action_retry), onClick = onRetry)
        }
        if (onNavigateTo != null) {
            val route = presentation.secondaryRoute
            TextButton(onClick = { onNavigateTo(route) }) {
                Text(stringResource(route.labelRes))
            }
        }
    }
}

/**
 * The same error surface for a condition that is not a [RepositoryError] — a device fact such as
 * "no app here can open this file". Kept separate so nothing is tempted to invent a taxonomy
 * variant for something the repository never produced.
 */
@Composable
fun NoticeState(
    headline: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorSurface(headline = headline, message = message, modifier = modifier) {
        AccessibleButton(label = actionLabel, onClick = onAction)
    }
}

@Composable
private fun ErrorSurface(
    headline: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.section)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions()
    }
}

@Composable
fun StaleDataNotice(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "Showing saved results",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.compact, vertical = MaterialTheme.spacing.control),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                )
                Text("Refresh")
            }
        }
    }
}

@Composable
fun AccessibleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Text(label)
    }
}

@Composable
fun AccessibleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Button
        },
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}
