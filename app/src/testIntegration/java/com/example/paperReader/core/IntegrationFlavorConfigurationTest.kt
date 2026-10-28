package com.example.paperReader.core

import com.example.paperReader.BuildConfig
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationProblem
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.core.config.BackendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The integration flavor is HTTP-only by construction. The base URL is the one external input, and
 * it is not a fixed value — it is whatever `-PpaperReaderBackendBaseUrl` supplied, or the deployed
 * backend when nothing did. These assertions therefore cover the mode, the fact that a usable
 * address is always present, and the misconfiguration outcomes, rather than a literal address. The
 * flavor that does pin a literal address is `production`; see `ProductionFlavorConfigurationTest`.
 */
class IntegrationFlavorConfigurationTest {

    @Test
    fun integrationFlavorHardcodesHttpMode() {
        assertEquals("http", BuildConfig.PAPER_READER_REMOTE_MODE)
        assertEquals(BackendMode.HTTP, BackendMode.parse(BuildConfig.PAPER_READER_REMOTE_MODE))
    }

    @Test
    fun anHttpBuildWithoutAUrlFailsInsteadOfServingDemoData() {
        // Unreachable from the flavor itself now that it defaults to the deployed backend, but the
        // rule still has to hold: absent an address, an HTTP build fails rather than inventing one.
        val result = BackendConfiguration.resolve(
            rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
            rawBaseUrl = "",
            isDebugBuild = BuildConfig.DEBUG,
        )

        assertEquals(
            BackendConfigurationProblem.MISSING_BASE_URL,
            (result as BackendConfigurationResult.Invalid).problem,
        )
    }

    @Test
    fun theFlavorAlwaysCarriesAnAddress() {
        assertTrue(
            "integration falls back to the deployed backend, so this is never blank",
            BuildConfig.PAPER_READER_BACKEND_BASE_URL.isNotBlank(),
        )
    }

    @Test
    fun whateverUrlThisBuildCarriesMustBeUsableForItsBuildType() {
        val result = BackendConfiguration.resolve(
            rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
            rawBaseUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL,
            isDebugBuild = BuildConfig.DEBUG,
        )

        assertEquals(
            "The configured integration base URL is not usable for this build type: $result",
            BackendMode.HTTP,
            (result as BackendConfigurationResult.Valid).configuration.mode,
        )
    }
}
