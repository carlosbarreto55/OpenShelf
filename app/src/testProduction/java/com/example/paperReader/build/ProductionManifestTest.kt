package com.example.paperReader.build

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Production carries no cleartext exception in *either* build type.
 *
 * This is the one place the production flavor is strictly stricter than integration.
 * `integrationDebug` is entitled to a loopback allowlist because reaching a local server is what it
 * is for; production has no local server, so a debuggable production build gets the same platform
 * default that rejects cleartext as the release build does. The Gradle-side counterpart is
 * `verifyProductionDebugNetworkPolicy`, which asserts the same thing against the packaged resources.
 */
class ProductionManifestTest {

    private val merged = MergedManifest.forVariantUnderTest()

    @Test
    fun noBuildTypeCarriesANetworkSecurityConfig() {
        assertFalse(
            "Production must inherit the platform default that rejects cleartext",
            merged.contains("networkSecurityConfig"),
        )
    }

    @Test
    fun noBuildTypeOptsIntoCleartextTraffic() {
        assertFalse(merged.contains("usesCleartextTraffic"))
    }
}
