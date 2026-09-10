package com.example.paperReader.build

import org.junit.Assert.assertFalse
import org.junit.Test

/** AND-001: the demo APK must stay usable without ever asking for network access. */
class DemoManifestTest {

    private val merged = MergedManifest.forVariantUnderTest()

    @Test
    fun demoMergedManifestDoesNotRequestInternet() {
        assertFalse(
            "The demo variant must remain functional without backend network calls",
            merged.contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun demoMergedManifestCarriesNoCleartextException() {
        assertFalse(merged.contains("networkSecurityConfig"))
        assertFalse(merged.contains("usesCleartextTraffic"))
    }
}
