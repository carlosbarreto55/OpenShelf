package com.example.paperReader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.ui.components.AccessibleButton
import com.example.paperReader.ui.theme.spacing

/**
 * "No matches" told in terms of what is actually narrowing the search: every active filter is
 * named and individually removable, with a clear-all alongside. When nothing is filtering, the copy
 * says nothing about filters at all — implying the user's own filters caused an empty unfiltered
 * search would simply be untrue.
 *
 * The list of active filters comes from [activeFiltersOf], the same enumeration the chip row reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEmptyState(
    queryText: String,
    filters: PaperFilters,
    onFiltersChanged: (PaperFilters) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = activeFiltersOf(filters)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.search_empty_title, queryText),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = if (active.isEmpty()) {
                stringResource(R.string.search_empty_description_unfiltered)
            } else {
                stringResource(R.string.search_empty_description_filtered)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (active.isEmpty()) return@Column

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control, Alignment.CenterHorizontally),
        ) {
            active.forEach { filter ->
                val label = stringResource(filter.labelRes, *filter.labelArgs.toTypedArray())
                val removeDescription = stringResource(R.string.search_remove_filter, label)
                InputChip(
                    selected = true,
                    // The chip itself removes the filter, so the affordance works without having
                    // to hit the small trailing icon.
                    onClick = { onFiltersChanged(filter.clear()) },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(CHIP_ICON_SIZE_DP.dp),
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = removeDescription },
                )
            }
        }

        AccessibleButton(
            label = stringResource(R.string.search_clear_all_filters),
            onClick = onClearFilters,
        )
    }
}

private const val CHIP_ICON_SIZE_DP = 18
