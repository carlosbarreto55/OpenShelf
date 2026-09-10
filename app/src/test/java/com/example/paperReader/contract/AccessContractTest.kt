package com.example.paperReader.contract

import com.example.paperReader.data.remote.PaperMappers
import com.example.paperReader.data.remote.dto.PaperSearchRequestDto
import com.example.paperReader.domain.model.AccessResolution
import com.example.paperReader.domain.model.Paper
import com.example.paperReader.domain.repository.RepositoryResult
import com.example.paperReader.ui.components.toAccessAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The fail-closed rule, asserted end to end over the pinned access fixtures: an action may only
 * ever appear for a validated `DIRECT_PDF` or `OPEN_FULL_TEXT`. Every other state — including
 * states this client has never heard of, and descriptors that look confirmed but are not valid
 * HTTPS — must be non-actionable.
 */
class AccessContractTest {

    @get:Rule
    val server = ContractServer()

    @Test
    fun onlyValidatedConfirmedStatesResolveToAnActionableAccess() = runBlocking {
        val papers = accessFixturePapers()

        assertEquals(
            "Only these two states may ever carry an action",
            setOf("W2301", "W2302"),
            papers.filterValues { it.access.toAccessAction() != null }.keys,
        )
    }

    @Test
    fun aConfirmedPdfKeepsItsValidatedDescriptor() = runBlocking {
        val access = accessFixturePapers().getValue("W2301").access

        assertEquals(
            "https://cdn.example.org/W2301.pdf",
            (access as AccessResolution.DirectPdf).descriptor.url,
        )
    }

    @Test
    fun everyUncertainOrInactionableStateIsRenderedWithoutAnAffordance() = runBlocking {
        val papers = accessFixturePapers()

        val expectations = mapOf(
            "W2303" to AccessResolution.NoFreeFullText,
            "W2304" to AccessResolution.Paywalled,
            "W2305" to AccessResolution.Checking,
            "W2306" to AccessResolution.Error,
            // A state added to v1 after this client shipped: the fallback is non-actionable.
            "W2307" to AccessResolution.Unknown,
            "W2308" to AccessResolution.Unknown,
        )

        expectations.forEach { (id, expected) ->
            assertEquals(id, expected, papers.getValue(id).access)
            assertNull("$id must expose no action", papers.getValue(id).access.toAccessAction())
        }
    }

    @Test
    fun aMalformedDescriptorOrSourceUrlFailsClosedInsteadOfBecomingActionable() = runBlocking {
        val papers = accessFixturePapers()

        // Each of these claims a confirmed state but fails validation. The paper is kept — only
        // the claim is refused — and nothing about it is actionable.
        listOf("W2309", "W2310", "W2311", "W2312").forEach { id ->
            assertEquals(
                "$id claims a confirmed state it cannot support",
                AccessResolution.Unknown,
                papers.getValue(id).access,
            )
            assertNull("$id must expose no action", papers.getValue(id).access.toAccessAction())
        }
    }

    @Test
    fun aDescriptorThatFailsValidationExposesNoUrlAnywhereInTheDomainModel() = runBlocking {
        val paper = accessFixturePapers().getValue("W2309")

        // The cleartext URL from the fixture must not survive anywhere a screen could read it.
        assertEquals(AccessResolution.Unknown, paper.access)
        assertNull(paper.access.toAccessAction())
    }

    private suspend fun accessFixturePapers(): Map<String, Paper> {
        server.enqueueFixture("responses/search-access-states.json")
        val response = server.source.search(PaperSearchRequestDto(query = "access"))
        val page = PaperMappers.mapSearchResponse((response as RepositoryResult.Success).value)
        return (page as RepositoryResult.Success).value.items.associateBy { it.id.value }
    }
}
