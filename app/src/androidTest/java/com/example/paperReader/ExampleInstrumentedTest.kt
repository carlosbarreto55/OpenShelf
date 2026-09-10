package com.example.paperReader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that the instrumentation is attached to the variant we actually built.
 *
 * The assertion is against [BuildConfig.APPLICATION_ID] rather than a hardcoded string because the
 * flavors deliberately differ: `demo` carries a `.demo` suffix so a demo and an integration build
 * can be installed side by side on one device.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun instrumentationTargetsTheVariantUnderTest() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
        assertTrue(
            "Every variant stays under the same application ID root",
            appContext.packageName.startsWith("com.example.paperReader"),
        )
    }
}
