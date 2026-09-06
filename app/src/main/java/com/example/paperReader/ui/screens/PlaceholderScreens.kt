package com.example.paperReader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.paperReader.R
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.theme.spacing

@Composable
fun SearchPlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.page, vertical = MaterialTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.search_hero_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.search_hero_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_field_label)) },
            placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            listOf(
                R.string.filter_all,
                R.string.filter_open_access,
                R.string.filter_pdf_available,
            ).forEach { labelResId ->
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(labelResId)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.card),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
            ) {
                Text(
                    text = stringResource(R.string.search_shell_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.search_shell_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LibraryPlaceholderScreen(modifier: Modifier = Modifier) {
    PlaceholderScreenHeader(
        title = stringResource(R.string.library_title),
        description = stringResource(R.string.library_description),
        modifier = modifier,
    ) {
        EmptyState(
            title = stringResource(R.string.library_empty_title),
            description = stringResource(R.string.library_empty_description),
            icon = Icons.Outlined.BookmarkBorder,
        )
    }
}

@Composable
fun DownloadsPlaceholderScreen(modifier: Modifier = Modifier) {
    PlaceholderScreenHeader(
        title = stringResource(R.string.downloads_title),
        description = stringResource(R.string.downloads_description),
        modifier = modifier,
    ) {
        EmptyState(
            title = stringResource(R.string.downloads_empty_title),
            description = stringResource(R.string.downloads_empty_description),
            icon = Icons.Outlined.Download,
        )
    }
}

@Composable
private fun PlaceholderScreenHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}
