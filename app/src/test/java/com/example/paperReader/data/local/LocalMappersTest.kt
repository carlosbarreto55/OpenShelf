package com.example.paperReader.data.local

import com.example.paperReader.data.local.LocalMappers.toDomain
import com.example.paperReader.data.local.entity.PaperRow
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.PaperType
import com.example.paperReader.ui.components.toAccessAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the fail-closed access rule survives a Room read-back: [LocalMappers.toDomain]
 * re-validates every persisted access state through [com.example.paperReader.data.remote.PaperMappers]
 * (see [com.example.paperReader.data.remote.PaperMapperTest] for the remote-response side of the same
 * rule) rather than trusting a stored row, and downgrades a stale actionable snapshot instead of
 * assuming it is still true. */
class LocalMappersTest {
    private val freshnessMillis = 24L * 60 * 60 * 1000
    private val now = 1_000_000_000L

    @Test
    fun freshDirectPdfWithValidDescriptorIsActionable() {
        val paper = rowWith(
            accessState = "DIRECT_PDF",
            accessDescriptorUrl = "https://demo.paperreader.invalid/papers/W1.pdf",
            accessCheckedAtEpochMillis = now,
        ).toDomain(freshnessMillis, now)

        assertTrue(paper.access is AccessResolution.DirectPdf)
        assertTrue(paper.access.toAccessAction() != null)
    }

    @Test
    fun freshOpenFullTextWithValidUrlIsActionable() {
        val paper = rowWith(
            accessState = "OPEN_FULL_TEXT",
            accessSourceUrl = "https://demo.paperreader.invalid/papers/W1",
            accessCheckedAtEpochMillis = now,
        ).toDomain(freshnessMillis, now)

        assertTrue(paper.access is AccessResolution.OpenFullText)
        assertTrue(paper.access.toAccessAction() != null)
    }

    @Test
    fun malformedPersistedDescriptorFailsClosedToUnknown() {
        val paper = rowWith(
            accessState = "DIRECT_PDF",
            accessDescriptorUrl = "http://not-https.example/paper.pdf",
            accessCheckedAtEpochMillis = now,
        ).toDomain(freshnessMillis, now)

        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun malformedPersistedSourceUrlFailsClosedToUnknown() {
        val paper = rowWith(
            accessState = "OPEN_FULL_TEXT",
            accessSourceUrl = "not-a-url",
            accessCheckedAtEpochMillis = now,
        ).toDomain(freshnessMillis, now)

        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun staleDirectPdfDowngradesToUnknown() {
        val paper = rowWith(
            accessState = "DIRECT_PDF",
            accessDescriptorUrl = "https://demo.paperreader.invalid/papers/W1.pdf",
            accessCheckedAtEpochMillis = now - freshnessMillis - 1,
        ).toDomain(freshnessMillis, now)

        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun actionableStateWithoutCheckedAtDowngradesToUnknown() {
        val paper = rowWith(
            accessState = "DIRECT_PDF",
            accessDescriptorUrl = "https://demo.paperreader.invalid/papers/W1.pdf",
            accessCheckedAtEpochMillis = null,
        ).toDomain(freshnessMillis, now)

        assertEquals(AccessResolution.Unknown, paper.access)
    }

    @Test
    fun everyNonActionableStateRoundTripsWithoutAnAction() {
        val silentStates = listOf("NO_FREE_FULL_TEXT", "PAYWALLED", "UNKNOWN", "CHECKING", "ERROR")
        silentStates.forEach { state ->
            val paper = rowWith(accessState = state, accessCheckedAtEpochMillis = now).toDomain(freshnessMillis, now)
            assertNull("expected no actionable access for $state", paper.access.toAccessAction())
        }
    }

    @Test
    fun missingAccessSnapshotMapsToUnknown() {
        val paper = rowWith(accessState = null).toDomain(freshnessMillis, now)

        assertEquals(AccessResolution.Unknown, paper.access)
    }

    private fun rowWith(
        accessState: String?,
        accessSourceUrl: String? = null,
        accessDescriptorUrl: String? = null,
        accessCheckedAtEpochMillis: Long? = null,
    ): PaperRow = PaperRow(
        id = "W1",
        title = "Test paper",
        authors = emptyList(),
        venue = null,
        publicationYear = null,
        type = PaperType.UNKNOWN,
        topics = emptyList(),
        citationCount = null,
        abstractText = null,
        accessState = accessState,
        accessSourceUrl = accessSourceUrl,
        accessDescriptorUrl = accessDescriptorUrl,
        accessDescriptorMimeType = null,
        accessDescriptorProvenance = null,
        accessDescriptorGenerated = false,
        accessDescriptorExpiresAtEpochMillis = null,
        accessProvenance = null,
        accessCheckedAtEpochMillis = accessCheckedAtEpochMillis,
        isSaved = false,
    )
}
