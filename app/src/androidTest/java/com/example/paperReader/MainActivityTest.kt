package com.example.paperReader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

    @Test
    fun rapidTabSwitchesSettleOnOneDestinationWithinTwoFrames() {
        // Frame by frame, so a transition still running between taps is caught mid-flight rather
        // than settled by the test's idling first.
        composeRule.mainClock.autoAdvance = false
        val taps = listOf("Library", "Downloads", "Search", "Library", "Search", "Downloads")

        taps.forEach { tab ->
            composeRule.onNodeWithContentDescription(tab).performClick()
            advanceUntil("$tab composed", MAX_FRAMES_TO_SWITCH) { tab in composedDestinations() }
            // Navigation keeps the outgoing screen composed until even a zero-length transition has
            // ended — one frame for a push, two when the tap pops back towards Search — and draws
            // it transparent meanwhile. The default 700 ms crossfade kept both for 44 frames.
            advanceUntil("only $tab composed", MAX_OVERLAP_FRAMES) { composedDestinations() == setOf(tab) }
            // And the next tap follows at once, well inside where that crossfade would still run.
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertComposedDestinations(setOf(taps.last()))
        composeRule.onNodeWithText(destinationMarkers.getValue(taps.last())).assertIsDisplayed()
    }

    private fun advanceUntil(condition: String, maxFrames: Int, satisfied: () -> Boolean) {
        repeat(maxFrames) {
            composeRule.mainClock.advanceTimeByFrame()
            if (satisfied()) return
        }
        throw AssertionError("not $condition within $maxFrames frames; composed: ${composedDestinations()}")
    }

    /** Composed rather than displayed: a destination fading out is still composed, and the
     * half-transparent text of one was exactly what overlapped the next. */
    private fun assertComposedDestinations(expected: Set<String>) {
        assertEquals("destinations composed", expected, composedDestinations())
    }

    private fun composedDestinations(): Set<String> = destinationMarkers.filterValues { marker ->
        composeRule.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty()
    }.keys

    private companion object {
        /** A tap reaches the screen two frames later; this leaves room without hiding a stall. */
        const val MAX_FRAMES_TO_SWITCH = 5

        /** Frames both screens may stay composed once the new one is: see the test. */
        const val MAX_OVERLAP_FRAMES = 2

        /** Text only each tab's empty, first-run screen shows. */
        val destinationMarkers = mapOf(
            "Search" to "Find the work behind the idea.",
            "Library" to "Your library is empty",
            "Downloads" to "No downloads yet",
        )
    }
}
