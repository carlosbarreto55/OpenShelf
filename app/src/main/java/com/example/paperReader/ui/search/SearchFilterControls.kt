package com.example.paperReader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.paperReader.R
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.PublicationYear
import com.example.paperReader.ui.components.AccessibleIconButton
import com.example.paperReader.ui.theme.spacing

/**
 * The publication-year and work-type controls the discovery spec requires (AND-010).
 *
 * Both route their result through [PaperFilters] rather than holding query state of their own, so
 * changing or clearing either one starts a new first-page cursor session through exactly the same
 * path as the existing chips, and both survive sorting and pagination.
 */
@Composable
fun SearchFilterControls(
    filters: PaperFilters,
    onFiltersChanged: (PaperFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        PublicationYearField(
            selectedYear = filters.publicationYear,
            onYearChanged = { onFiltersChanged(filters.copy(publicationYear = it)) },
        )
        WorkTypeChips(
            selectedType = filters.paperType,
            onTypeChanged = { onFiltersChanged(filters.copy(paperType = it)) },
        )
    }
}

/**
 * An exact year. Invalid input is explained and simply not applied — a request is never issued for
 * a year outside the range mapping would accept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicationYearField(
    selectedYear: Int?,
    onYearChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local text so a partially typed year ("20") is neither applied nor erased mid-entry; the
    // applied value always comes from `selectedYear`.
    var text by remember(selectedYear) { mutableStateOf(selectedYear?.toString().orEmpty()) }
    val validation = PublicationYear.validate(text)
    val isInvalid = validation is PublicationYear.Validation.NotANumber ||
        validation is PublicationYear.Validation.OutOfRange
    val selectedDescription = selectedYear?.let { stringResource(R.string.filter_year_selected, it) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText.filter(Char::isDigit).take(YEAR_DIGITS)
            when (val result = PublicationYear.validate(text)) {
                is PublicationYear.Validation.Valid -> onYearChanged(result.year)
                PublicationYear.Validation.Empty -> onYearChanged(null)
                else -> Unit // Explained below the field; the active filter is left as it was.
            }
        },
        modifier = modifier
            .widthIn(min = YEAR_FIELD_MIN_WIDTH_DP.dp)
            .semantics { selectedDescription?.let { contentDescription = it } },
        label = { Text(stringResource(R.string.filter_year_label)) },
        placeholder = { Text(stringResource(R.string.filter_year_placeholder)) },
        singleLine = true,
        isError = isInvalid,
        supportingText = if (isInvalid) {
            {
                Text(
                    stringResource(
                        R.string.filter_year_invalid,
                        PublicationYear.SUPPORTED_RANGE.first,
                        PublicationYear.SUPPORTED_RANGE.last,
                    ),
                )
            }
        } else {
            null
        },
        trailingIcon = if (text.isNotEmpty()) {
            {
                AccessibleIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.filter_year_clear),
                    onClick = {
                        text = ""
                        onYearChanged(null)
                    },
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * One of the five canonical public work types, or none. Free text is deliberately not offered: the
 * contract publishes a finite set, so an unsupported value would only ever be a wasted request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTypeChips(
    selectedType: PaperType?,
    onTypeChanged: (PaperType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
    ) {
        Text(
            text = stringResource(R.string.filter_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Wraps rather than scrolls horizontally so every option stays reachable on a narrow
        // screen and at large font scales.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control)) {
            PaperType.SUPPORTED_FILTER_TYPES.forEach { type ->
                val isSelected = selectedType == type
                FilterChip(
                    selected = isSelected,
                    // Tapping the selected chip clears just this filter.
                    onClick = { onTypeChanged(if (isSelected) null else type) },
                    label = { Text(type.displayLabel()) },
                )
            }
            if (selectedType != null) {
                AccessibleIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.filter_type_clear),
                    onClick = { onTypeChanged(null) },
                )
            }
        }
    }
}

@Composable
private fun PaperType.displayLabel(): String = when (this) {
    PaperType.ARTICLE -> stringResource(R.string.filter_type_article)
    PaperType.REVIEW -> stringResource(R.string.filter_type_review)
    PaperType.PREPRINT -> stringResource(R.string.filter_type_preprint)
    PaperType.BOOK_CHAPTER -> stringResource(R.string.filter_type_book_chapter)
    PaperType.DATASET -> stringResource(R.string.filter_type_dataset)
    // Not offered as filters; the contract publishes only the five above.
    PaperType.OTHER, PaperType.UNKNOWN -> name
}

private const val YEAR_DIGITS = 4
private const val YEAR_FIELD_MIN_WIDTH_DP = 160
