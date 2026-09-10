package com.example.paperReader.core

import com.example.paperReader.BuildConfig
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationProblem
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.core.config.BackendMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The integration flavor is HTTP-only by construction. The base URL is the one external input, so
 * these assertions cover the mode and the misconfiguration outcomes rather than a fixed address.
 */
class IntegrationFlavorConfigurationTest {

    @Test
    fun integrationFlavorHardcodesHttpMode() {
        assertEquals("http", BuildConfig.PAPER_READER_REMOTE_MODE)
        assertEquals(BackendMode.HTTP, BackendMode.parse(BuildConfig.PAPER_READER_REMOTE_MODE))
    }

    @Test
    fun anIntegrationBuildWithoutAUrlFailsInsteadOfServingDemoData() {
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
    fun whateverUrlThisBuildCarriesMustBeUsableForItsBuildType() {
        val result = BackendConfiguration.resolve(
            rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
            rawBaseUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL.ifBlank { "https://api.paperreader.example" },
            isDebugBuild = BuildConfig.DEBUG,
        )

        assertEquals(
            "The configured integration base URL is not usable for this build type: $result",
            BackendMode.HTTP,
            (result as BackendConfigurationResult.Valid).configuration.mode,
        )
    }
}
