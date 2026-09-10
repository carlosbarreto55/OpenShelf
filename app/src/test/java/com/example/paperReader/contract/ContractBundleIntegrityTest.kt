package com.example.paperReader.contract

import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drift gate for the pinned contract bundle (SHR-001/SHR-002): editing, adding, or deleting a
 * fixture without a reviewed manifest update fails here before any Android code is merged.
 */
class ContractBundleIntegrityTest {

    @Test
    fun manifestPinsAnApiVersionAndProvenance() {
        val manifest = ContractBundle.manifest

        assertEquals("v1", manifest.apiVersion)
        assertTrue("contractVersion must be recorded", manifest.contractVersion.isNotBlank())
        assertTrue("upstream provenance must be recorded", manifest.upstream.provenance.isNotBlank())
    }

    @Test
    fun everyPinnedResourceMatchesItsRecordedChecksum() {
        val manifest = ContractBundle.manifest
        assertTrue("The manifest must pin at least one resource", manifest.resources.isNotEmpty())

        manifest.resources.forEach { (relativePath, expectedChecksum) ->
            assertEquals(
                "Pinned contract resource $relativePath no longer matches its recorded checksum. " +
                    "If this is a reviewed contract update, rerun tools/update-contract-manifest.py.",
                expectedChecksum,
                ContractBundle.sha256(ContractBundle.readText(relativePath)),
            )
        }
    }

    @Test
    fun everyBundledResourceIsPinnedByTheManifest() {
        val root = bundleRoot()
        val onDisk = Files.walk(root.toPath()).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "json" }
                .map { root.toPath().relativize(it).joinToString("/") }
                .filter { it != MANIFEST_NAME }
                .sorted()
                .toList()
        }

        assertEquals(
            "Unpinned or removed contract fixtures. Rerun tools/update-contract-manifest.py as part " +
                "of a reviewed contract-version update.",
            onDisk,
            ContractBundle.manifest.resources.keys.sorted(),
        )
    }

    @Test
    fun providerVerificationIsClaimedOnlyWithTheProviderBundleBehindIt() {
        // This assertion used to run the other way: SHR-001/SHR-002 could not be closed from this
        // repository alone, so `verifiedByProviderCi` was pinned to false. The provider has since
        // published paperreader-v1.0.0, and it is vendored under contract/canonical with its own
        // SHA256SUMS. The flag may now be true — but only while that bundle is actually present and
        // pins the same version, so the claim can never outlive its evidence.
        assertTrue(
            "verifiedByProviderCi requires the provider bundle vendored at " +
                "${CanonicalBundle.DIR}. Do not flip this flag without re-vendoring it.",
            ContractBundle.manifest.upstream.verifiedByProviderCi,
        )
        assertEquals(
            "The consumer bundle must declare the same contract version the provider published.",
            CanonicalBundle.version,
            ContractBundle.manifest.contractVersion,
        )
    }

    private fun bundleRoot(): File {
        val url = checkNotNull(ContractBundleIntegrityTest::class.java.getResource("/${ContractBundle.VERSION_DIR}")) {
            "The pinned contract bundle is not on the test classpath"
        }
        return File(url.toURI())
    }

    private companion object {
        const val MANIFEST_NAME = "manifest.json"
    }
}
