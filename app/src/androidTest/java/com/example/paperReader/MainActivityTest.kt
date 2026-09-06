package com.example.paperReader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsOnSearchWithAccessibleNavigation() {
        composeRule.onNodeWithText("Find the work behind the idea.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Library").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Downloads").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOpensPlaceholderDestinations() {
        composeRule.onNodeWithContentDescription("Library").performClick()
        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Downloads").performClick()
        composeRule.onNodeWithText("No downloads yet").assertIsDisplayed()
    }
}
