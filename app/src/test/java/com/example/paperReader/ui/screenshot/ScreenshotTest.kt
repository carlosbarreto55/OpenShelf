package com.example.paperReader.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Author
import com.example.paperReader.domain.model.ConfirmedPdfDescriptor
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.model.PaperId
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.domain.model.Topic
import com.example.paperReader.domain.repository.RepositoryError
import com.example.paperReader.ui.components.EmptyState
import com.example.paperReader.ui.components.LoadingState
import com.example.paperReader.ui.components.PaperCard
import com.example.paperReader.ui.components.RecoverableErrorState
import com.example.paperReader.ui.components.SkeletonPaperCardList
import com.example.paperReader.ui.search.SearchContent
import com.example.paperReader.ui.search.SearchScreen
import com.example.paperReader.ui.search.SearchUiState
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Representative screenshot coverage for design.md decision 7 (the Lattice reference look) and the
 * verified-paper-access fail-closed rule (design.md decision 4): one golden per state named in
 * task 8.5 — the full reference search screen, a confirmed PDF card, a confirmed open-HTML card, a
 * metadata-only (no actionable access) card, and the shared loading/empty/error states. Goldens
 * live at `app/src/test/screenshots/` (see the `roborazzi { outputDir }` config in
 * `app/build.gradle.kts`). Run `./gradlew recordRoborazziDebug` to (re)record goldens after an
 * intentional UI change, or `./gradlew verifyRoborazziDebug` to fail on an unintended regression;
 * `-Proborazzi.test.verify=true` is set by default in `gradle.properties` so a plain
 * `:app:testDebugUnitTest` run also verifies against the committed goldens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uiReferenceState_searchScreenLoaded() {
        composeRule.setContent {
            PaperReaderTheme(darkTheme = true) {
                FixedSize(width = 412, height = 900) {
                    SearchScreen(
                        state = SearchUiState(
                            queryText = "generative AI",
                            content = SearchContent.Loaded(papers = referencePapers, hasMore = true),
                        ),
                        onQueryTextChanged = {},
                        onFiltersChanged = {},
                        onClearFilters = {},
                        onSortOrderChanged = {},
                        onRetry = {},
                        onLoadNextPage = {},
                        onToggleSave = {},
                        onOpenPaper = {},
                        onStartDownload = {},
                        onReadOnline = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("search_reference_state.png")
    }

    @Test
    fun confirmedPdfCard() {
        renderCard(
            paperWith(
                access = AccessResolution.DirectPdf(
                    ConfirmedPdfDescriptor(url = "https://demo.paperreader.invalid/papers/W1.pdf"),
                ),
            ),
        )

        composeRule.onRoot().captureRoboImage("paper_card_confirmed_pdf.png")
    }

    @Test
    fun confirmedOpenFullTextCard() {
        renderCard(
            paperWith(access = AccessResolution.OpenFullText(sourceUrl = "https://demo.paperreader.invalid/papers/W1")),
        )

        composeRule.onRoot().captureRoboImage("paper_card_confirmed_open_full_text.png")
    }

    @Test
    fun metadataOnlyCard() {
        renderCard(paperWith(access = AccessResolution.NoFreeFullText, minimalMetadata = true))

        composeRule.onRoot().captureRoboImage("paper_card_metadata_only.png")
    }

    @Test
    fun loadingState() {
        // CircularProgressIndicator's sweep animation runs forever, so idling never settles it at
        // a fixed phase; freeze the clock before capture instead of getting a flaky arc length.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PaperReaderTheme {
                FixedSize(width = 360, height = 160) { LoadingState() }
            }
        }
        composeRule.mainClock.advanceTimeBy(300L)

        composeRule.onRoot().captureRoboImage("loading_state.png")
    }

    @Test
    fun skeletonLoadingState() {
        // The search list's loading state, which holds the geometry of result cards rather than
        // replacing the list with a spinner.
        composeRule.setContent {
            PaperReaderTheme {
                FixedSize(width = 360, height = 480) { SkeletonPaperCardList() }
            }
        }

        composeRule.onRoot().captureRoboImage("skeleton_loading_state.png")
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            PaperReaderTheme {
                FixedSize(width = 360, height = 220) {
                    EmptyState(
                        title = "No results for \"quantum foam\"",
                        description = "Try a different topic or adjust your filters.",
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("empty_state.png")
    }

    @Test
    fun errorState() {
        composeRule.setContent {
            PaperReaderTheme {
                FixedSize(width = 360, height = 300) {
                    RecoverableErrorState(
                        error = RepositoryError.Offline,
                        onRetry = {},
                        onNavigateTo = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("error_state.png")
    }

    private fun renderCard(paper: Paper) {
        composeRule.setContent {
            PaperReaderTheme {
                FixedSize(width = 360, height = 220) {
                    PaperCard(paper = paper, onClick = {}, onToggleSave = {}, onDownloadPdf = {}, onReadOnline = {})
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun FixedSize(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }

    private fun paperWith(access: AccessResolution, minimalMetadata: Boolean = false): Paper {
        return if (minimalMetadata) {
            Paper(id = PaperId("W1000000001"), title = "A Field Note on Generative AI in Community Journalism", access = access)
        } else {
            Paper(
                id = PaperId("W2741809807"),
                title = "Harnessing Generative AI for Automated Feedback in Higher Education",
                authors = listOf(Author("R. M. Mahoney"), Author("A. Chen")),
                venue = "Online Learning",
                publicationYear = 2024,
                type = PaperType.REVIEW,
                topics = listOf(Topic("Education")),
                citationCount = 151,
                access = access,
            )
        }
    }

    private val referencePapers = listOf(
        paperWith(
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
        paperWith(access = AccessResolution.NoFreeFullText, minimalMetadata = true),
    )
}
