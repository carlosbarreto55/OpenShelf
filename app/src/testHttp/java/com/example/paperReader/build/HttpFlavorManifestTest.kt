package com.example.paperReader.build

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AND-001: only the HTTP flavors may reach the network, and each of them must actually be able to.
 * The per-flavor cleartext expectations differ and live next to each flavor's own tests.
 */
class HttpFlavorManifestTest {

    @Test
    fun mergedManifestRequestsInternet() {
        assertTrue(
            "Retrofit and external PDF transfers cannot run without it",
            MergedManifest.forVariantUnderTest().contains("android.permission.INTERNET"),
        )
    }
}
