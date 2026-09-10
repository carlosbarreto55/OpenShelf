package com.example.paperReader.ui.search

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.paperReader.domain.model.PaperFilters
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * AND-010's accessibility half: both controls must expose their selected state, be individually
 * clearable, refuse an out-of-range year with an explanation, and stay reachable on a narrow phone
 * at a large font scale.
 */
class SearchFilterControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aValidYearIsAppliedAndAnnouncedAsSelected() {
        val filters = mutableStateOf(PaperFilters())
        show(filters)

        composeRule.onNodeWithText("Publication year").performTextInput("2024")

        assertEquals(2024, filters.value.publicationYear)
        composeRule.onNodeWithContentDescription("Publication year 2024, selected").assertIsDisplayed()
    }

    @Test
    fun anOutOfRangeYearIsExplainedAndNotApplied() {
        val filters = mutableStateOf(PaperFilters())
        show(filters)

        composeRule.onNodeWithText("Publication year").performTextInput("1200")

        assertNull("An unsupported year must never become an active filter", filters.value.publicationYear)
        composeRule.onNodeWithText("Enter a year between 1500 and 2100.").assertIsDisplayed()
    }

    @Test
    fun theYearCanBeClearedOnItsOwn() {
        val filters = mutableStateOf(PaperFilters(publicationYear = 2024, paperType = PaperType.REVIEW))
        show(filters)

        composeRule.onNodeWithContentDescription("Clear publication year").assertHasClickAction().performClick()

        assertNull(filters.value.publicationYear)
        assertEquals("Only the year is cleared", PaperType.REVIEW, filters.value.paperType)
    }

    @Test
    fun onlyTheFiveCanonicalWorkTypesAreOffered() {
        show(mutableStateOf(PaperFilters()))

        listOf("Article", "Review", "Preprint", "Book chapter", "Dataset").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun selectingAWorkTypeExposesItsSelectedState() {
        val filters = mutableStateOf(PaperFilters())
        show(filters)

        composeRule.onNodeWithText("Book chapter").assertIsNotSelected().performClick()

        assertEquals(PaperType.BOOK_CHAPTER, filters.value.paperType)
        composeRule.onNodeWithText("Book chapter").assertIsSelected()
    }

    @Test
    fun theWorkTypeCanBeClearedOnItsOwn() {
        val filters = mutableStateOf(PaperFilters(publicationYear = 2024, paperType = PaperType.DATASET))
        show(filters)

        composeRule.onNodeWithContentDescription("Clear work type").assertHasClickAction().performClick()

        assertNull(filters.value.paperType)
        assertEquals("Only the type is cleared", 2024, filters.value.publicationYear)
    }

    @Test
    fun bothControlsStayReachableOnANarrowScreenAtALargeFontScale() {
        val filters = mutableStateOf(PaperFilters())
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE),
            ) {
                PaperReaderTheme {
                    SearchFilterControls(
                        filters = filters.value,
                        onFiltersChanged = { filters.value = it },
                        modifier = Modifier.width(NARROW_PHONE_WIDTH_DP.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Publication year").assertIsDisplayed()
        // The chips wrap rather than scroll, so the last one is still on screen and operable.
        composeRule.onNodeWithText("Dataset").assertIsDisplayed().performClick()
        assertEquals(PaperType.DATASET, filters.value.paperType)
    }

    @Test
    fun clearingTheYearFieldRemovesTheFilterWithoutAnError() {
        val filters = mutableStateOf(PaperFilters(publicationYear = 2024))
        show(filters)

        composeRule.onNodeWithText("Publication year").performTextClearance()

        assertNull(filters.value.publicationYear)
        composeRule.onNodeWithText("Enter a year between 1500 and 2100.").assertDoesNotExist()
    }

    /** Hoists the filter state so an assertion can read what the control actually applied. */
    private fun show(filters: MutableState<PaperFilters>) {
        composeRule.setContent {
            PaperReaderTheme {
                SearchFilterControls(filters = filters.value, onFiltersChanged = { filters.value = it })
            }
        }
    }

    private companion object {
        const val LARGE_FONT_SCALE = 2.0f
        const val NARROW_PHONE_WIDTH_DP = 320
    }
}
