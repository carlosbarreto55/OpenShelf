package com.example.paperReader.ui.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paperReader.data.reader.PageSize
import com.example.paperReader.domain.model.ReadingPosition
import com.example.paperReader.ui.theme.PaperReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** On a device because a text field inside a dialog never lets Robolectric's Compose rule idle. */
@RunWith(AndroidJUnit4::class)
class ReaderGoToPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun goToPageJumpsStraightToIt() {
        composeRule.setContent {
            PaperReaderTheme {
                ReaderScreen(
                    state = ReaderUiState(
                        title = "Demo title",
                        content = ReaderContent.Reading(List(12) { PageSize(612, 792) }, ReadingPosition.START, initialZoom = 1f),
                    ),
                    pageImages = emptyMap(),
                    onBack = {},
                    onCancelTransfer = {},
                    onRetryTransfer = {},
                    onReadAgain = {},
                    onOpenInAnotherApp = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Go to page, Page 1 of 12").performClick()
        composeRule.onNodeWithText("Page number, 1 to 12").performTextInput("9")
        composeRule.onNodeWithText("Go").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Page 9 of 12").assertExists()
    }
}
