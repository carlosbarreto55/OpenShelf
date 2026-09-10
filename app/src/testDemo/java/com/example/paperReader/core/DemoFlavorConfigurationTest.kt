package com.example.paperReader.core

import com.example.paperReader.BuildConfig
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo flavor is fake-only by construction: no Gradle property can turn it into a live build. */
class DemoFlavorConfigurationTest {

    @Test
    fun demoFlavorHardcodesFakeModeAndNoBackendUrl() {
        assertEquals("fake", BuildConfig.PAPER_READER_REMOTE_MODE)
        assertTrue(BuildConfig.PAPER_READER_BACKEND_BASE_URL.isEmpty())
    }

    @Test
    fun demoBuildResolvesWithoutABackend() {
        val configuration = BackendConfiguration.fromBuildConfig()

        assertEquals(BackendMode.FAKE, configuration.mode)
        assertFalse(configuration.usesHttp)
        assertNull(configuration.baseUrl)
    }
}
