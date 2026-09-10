package com.example.paperReader.contract

import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun sharedContractDebtStaysOpenUntilTheProviderVerifiesTheSameBundle() {
        // SHR-001/SHR-002 cannot be closed from this repository alone. This assertion is the
        // reminder: flipping the flag requires provider CI to consume the same contractVersion.
        assertFalse(
            "verifiedByProviderCi may only become true once PaperReaderApi's CI verifies contract " +
                "version ${ContractBundle.manifest.contractVersion}.",
            ContractBundle.manifest.upstream.verifiedByProviderCi,
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
