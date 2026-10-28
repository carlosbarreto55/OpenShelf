package com.example.paperReader.build

import com.example.paperReader.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-003: integration is the one flavor with a loopback development story, and the cleartext
 * exception that story needs exists only in its debug build. The Internet-permission half of this
 * is shared with production and lives in `HttpFlavorManifestTest`.
 */
class IntegrationManifestTest {

    private val merged = MergedManifest.forVariantUnderTest()

    @Test
    fun onlyTheDebugBuildCarriesTheCleartextNetworkSecurityConfig() {
        if (BuildConfig.DEBUG) {
            assertTrue(
                "integrationDebug needs the loopback cleartext allowlist for local development",
                merged.contains("networkSecurityConfig"),
            )
        } else {
            assertFalse(
                "integrationRelease must inherit the platform default that rejects cleartext",
                merged.contains("networkSecurityConfig"),
            )
            assertFalse(merged.contains("usesCleartextTraffic"))
        }
    }
}
