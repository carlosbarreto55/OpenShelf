package com.example.paperReader.core

import com.example.paperReader.BuildConfig
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.core.config.BackendMode
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The production flavor points at the deployed PaperReader service and takes no external input.
 *
 * Unlike `integration`, this flavor pins a literal address, so these assertions are deliberately
 * exact. The reason they matter: `paperReaderBackendBaseUrl` is conventionally set machine-globally
 * to `http://10.0.2.2:8080` for local development, and if this flavor ever started reading that
 * property — or inherited it through a shared default — a build labelled "production" would quietly
 * target a developer's laptop. Nothing else in the build would complain. These tests would.
 */
class ProductionFlavorConfigurationTest {

    private val configuredUrl = BuildConfig.PAPER_READER_BACKEND_BASE_URL

    @Test
    fun productionFlavorHardcodesHttpMode() {
        assertEquals("http", BuildConfig.PAPER_READER_REMOTE_MODE)
        assertEquals(BackendMode.HTTP, BackendMode.parse(BuildConfig.PAPER_READER_REMOTE_MODE))
    }

    @Test
    fun productionTargetsTheDeployedBackendAndNothingElse() {
        assertEquals("https://barretolab.tech/", configuredUrl)
    }

    @Test
    fun productionTransportIsAlwaysEncrypted() {
        assertEquals("https", URI(configuredUrl).scheme)
    }

    @Test
    fun productionIsNotPointedAtADevelopmentLoopbackHost() {
        assertFalse(
            "A local development address has leaked into the production flavor: $configuredUrl",
            URI(configuredUrl).host in BackendConfiguration.LOOPBACK_DEVELOPMENT_HOSTS,
        )
    }

    @Test
    fun theConfiguredUrlResolvesInBothBuildTypes() {
        // Production has no loopback allowance, so unlike integration it must be valid even when
        // the build is debuggable — resolution must not depend on which build type happens to run.
        listOf(true, false).forEach { isDebugBuild ->
            val result = BackendConfiguration.resolve(
                rawMode = BuildConfig.PAPER_READER_REMOTE_MODE,
                rawBaseUrl = configuredUrl,
                isDebugBuild = isDebugBuild,
            )

            assertEquals(
                "The production base URL is not usable with isDebugBuild=$isDebugBuild: $result",
                BackendMode.HTTP,
                (result as BackendConfigurationResult.Valid).configuration.mode,
            )
        }
    }
}
