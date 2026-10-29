package com.example.paperReader.ui.search

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Author
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.SortOrder
import com.example.paperReader.domain.model.Topic
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.theme.PaperReaderTheme

private val previewPapers = listOf(
    Paper(
        id = PaperId("W2741809807"),
        title = "Harnessing Generative AI for Automated Feedback in Higher Education",
        authors = listOf(Author("R. M. Mahoney"), Author("A. Chen")),
        venue = "Online Learning",
        publicationYear = 2024,
        type = PaperType.REVIEW,
        topics = listOf(Topic("Education")),
        citationCount = 151,
        access = AccessResolution.DirectPdf(
            ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W2741809807.pdf"),
        ),
    ),
    Paper(
        id = PaperId("W3123456789"),
        title = "Towards Sustainable Agriculture: Harnessing AI for Global Food Security",
        authors = listOf(Author("A. Javaid")),
        venue = "Artificial Intelligence in Agriculture",
        publicationYear = 2024,
        type = PaperType.ARTICLE,
        topics = listOf(Topic("Agriculture")),
        citationCount = 171,
        access = AccessResolution.OpenFullText(sourceUrl = "https://demo.paperreader.invalid/papers/W3123456789"),
    ),
    Paper(
        id = PaperId("W3987654321"),
        title = "Empowering SMEs with Generative AI for Resilience",
        authors = listOf(Author("P. Chatterjee")),
        venue = "Management Research Review",
        publicationYear = 2024,
        type = PaperType.ARTICLE,
        citationCount = 82,
        access = AccessResolution.NoFreeFullText,
    ),
    Paper(
        id = PaperId("W4031122556"),
        title = "A Field Note on Generative AI in Community Journalism",
        authors = listOf(Author("M. Ibrahim")),
        access = AccessResolution.Unknown,
    ),
)

private fun previewState(content: SearchContent) = SearchUiState(
    queryText = "generative AI",
    filters = PaperFilters(),
    sortOrder = SortOrder.RELEVANCE,
    content = content,
    demoDisclosure = DemoDisclosureState.IndicatorOnly,
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.8f)
// 360dp is the narrowest width still common on supported (minSdk 26) phones; combined with a
// large font scale this is the worst case for the filter-chip/sort row overflowing horizontally.
@Preview(name = "Narrow width, large font", showBackground = true, widthDp = 360, fontScale = 1.8f)
@Composable
private fun SearchScreenLoadedPreview() {
    PaperReaderTheme {
        SearchScreen(
            state = previewState(SearchContent.Loaded(papers = previewPapers, hasMore = true)),
            onQueryTextChanged = {},
            onFiltersChanged = {},
            onClearFilters = {},
            onSortOrderChanged = {},
            onRetry = {},
            onLoadNextPage = {},
            onToggleSave = {},
            onOpenPaper = {},
            onReadPdf = {},
            onReadOnline = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun SearchScreenEmptyPreview() {
    PaperReaderTheme {
        SearchScreen(
            state = previewState(SearchContent.Empty(queryText = "generative AI")),
            onQueryTextChanged = {},
            onFiltersChanged = {},
            onClearFilters = {},
            onSortOrderChanged = {},
            onRetry = {},
            onLoadNextPage = {},
            onToggleSave = {},
            onOpenPaper = {},
            onReadPdf = {},
            onReadOnline = {},
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun SearchScreenErrorPreview() {
    PaperReaderTheme {
        SearchScreen(
            state = previewState(SearchContent.Error(RepositoryError.Offline)),
            onQueryTextChanged = {},
            onFiltersChanged = {},
            onClearFilters = {},
            onSortOrderChanged = {},
            onRetry = {},
            onLoadNextPage = {},
            onToggleSave = {},
            onOpenPaper = {},
            onReadPdf = {},
            onReadOnline = {},
        )
    }
}

@Preview(name = "Initial", showBackground = true)
@Preview(name = "Initial, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenInitialPreview() {
    PaperReaderTheme {
        SearchScreen(
            state = SearchUiState(recentQueries = listOf("robotics")),
            onQueryTextChanged = {},
            onFiltersChanged = {},
            onClearFilters = {},
            onSortOrderChanged = {},
            onRetry = {},
            onLoadNextPage = {},
            onToggleSave = {},
            onOpenPaper = {},
            onReadPdf = {},
            onReadOnline = {},
        )
    }
}
