package com.example.paperReader.build

import com.example.paperReader.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-001/AND-003: the integration variant is the only one that may reach the network, and the
 * cleartext exception exists only in its debug build.
 */
class IntegrationManifestTest {

    private val merged = MergedManifest.forVariantUnderTest()

    @Test
    fun integrationMergedManifestRequestsInternet() {
        assertTrue(
            "Retrofit and external PDF transfers cannot run without it",
            merged.contains("android.permission.INTERNET"),
        )
    }

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
