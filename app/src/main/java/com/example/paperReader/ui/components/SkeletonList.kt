package com.example.paperReader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.paperReader.R
import com.example.paperReader.ui.theme.spacing

/** Number of placeholder cards. Fixed and small on purpose: it holds the list's geometry without
 * implying how many results are on the way (design.md decision 3). */
const val SKELETON_CARD_COUNT = 3

const val SKELETON_LIST_TEST_TAG = "search-skeleton-list"
const val SKELETON_CARD_TEST_TAG = "search-skeleton-card"

/**
 * Card-shaped placeholders in the proportions of a real [PaperCard], emitted with the same spacing
 * the result list uses, so results arrive *into* the list instead of replacing a centred spinner
 * and reflowing everything around it.
 *
 * Deliberately static. A shimmer is a visual nicety; holding the geometry is the requirement, and
 * an endlessly running animation would also mean this surface never reaches an idle state for
 * tests or for accessibility services.
 *
 * Accessibility: the whole block is one polite live region carrying a single announcement, and
 * each placeholder clears its own semantics — a screen reader hears "Loading results" once, never
 * once per placeholder (task 2.4).
 */
@Composable
fun SkeletonPaperCardList(
    modifier: Modifier = Modifier,
    cardCount: Int = SKELETON_CARD_COUNT,
    announcement: String = stringResource(R.string.search_skeleton_announcement),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SKELETON_LIST_TEST_TAG)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        repeat(cardCount) {
            SkeletonPaperCard()
        }
    }
}

@Composable
private fun SkeletonPaperCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The tag is set *inside* the cleared block: clearing the subtree's semantics would
            // otherwise take the tag with it, and a placeholder must stay findable without being
            // announced.
            .clearAndSetSemantics { testTag = SKELETON_CARD_TEST_TAG },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.card),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.control),
        ) {
            // The bars a real card's content occupies: a two-line title, the author line, and the
            // metadata row beneath it.
            SkeletonBar(widthFraction = 1f, heightDp = TITLE_LINE_HEIGHT_DP)
            SkeletonBar(widthFraction = 0.7f, heightDp = TITLE_LINE_HEIGHT_DP)
            SkeletonBar(widthFraction = 0.5f, heightDp = BODY_LINE_HEIGHT_DP)
            SkeletonBar(widthFraction = 0.35f, heightDp = BODY_LINE_HEIGHT_DP)
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, heightDp: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(heightDp.dp)
            .clip(MaterialTheme.shapes.small)
            // A neutral wash over the card rather than a distinct colour: a placeholder must read
            // as absent content, not as a filled field.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = PLACEHOLDER_ALPHA)),
    )
}

private const val PLACEHOLDER_ALPHA = 0.16f
private const val TITLE_LINE_HEIGHT_DP = 20
private const val BODY_LINE_HEIGHT_DP = 14
