package com.example.paperReader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.paperReader.domain.model.Author
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SavedState
import com.example.paperReader.ui.theme.spacing

/** Shared by every screen that lists papers (search results, saved library) so metadata
 * formatting and the fail-closed access row stay in exactly one place. */
@Composable
fun PaperCard(
    paper: Paper,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    onDownloadPdf: (ConfirmedPdfDescriptor) -> Unit,
    onReadOnline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = paper.title, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.card),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = paper.title,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                val saved = paper.savedState == SavedState.SAVED
                AccessibleIconButton(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Remove from library" else "Save paper",
                    onClick = onToggleSave,
                )
            }

            val metadataLine = formatMetadataLine(paper)
            if (metadataLine != null) {
                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val topicName = paper.topics.firstOrNull()?.name
            if (topicName != null) {
                MetadataPill(text = topicName)
            }

            AccessActionRow(
                access = paper.access,
                onDownloadPdf = onDownloadPdf,
                onReadOnline = onReadOnline,
            )
        }
    }
}

@Composable
internal fun MetadataPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.compact,
                vertical = MaterialTheme.spacing.control,
            ),
        )
    }
}

private fun formatMetadataLine(paper: Paper): String? {
    val parts = buildList {
        formatCompactAuthors(paper.authors)?.let(::add)
        paper.venue?.let(::add)
        paper.publicationYear?.let { add(it.toString()) }
        paper.type.displayLabel()?.let(::add)
        paper.citationCount?.let { add(formatCitationCount(it)) }
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(separator = " · ")
}

private fun formatCompactAuthors(authors: List<Author>): String? {
    return when {
        authors.isEmpty() -> null
        authors.size <= 2 -> authors.joinToString(separator = ", ") { it.name }
        else -> "${authors.first().name} et al."
    }
}

internal fun formatCitationCount(count: Int): String {
    return if (count == 1) "1 citation" else "$count citations"
}

internal fun PaperType.displayLabel(): String? = when (this) {
    PaperType.ARTICLE -> "Article"
    PaperType.REVIEW -> "Review"
    PaperType.PREPRINT -> "Preprint"
    PaperType.BOOK_CHAPTER -> "Book chapter"
    PaperType.DATASET -> "Dataset"
    PaperType.OTHER -> "Other"
    PaperType.UNKNOWN -> null
}
