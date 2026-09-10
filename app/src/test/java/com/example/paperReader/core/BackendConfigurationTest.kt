package com.example.paperReader.core

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationProblem
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.core.config.BackendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flavor-independent resolution rules. The per-flavor expectations that read `BuildConfig` live in
 * `src/testDemo` and `src/testIntegration`, because the mode is fixed by the flavor.
 */
class BackendConfigurationTest {

    @Test
    fun demoModeNeedsNoBackendUrl() {
        val configuration = valid(rawMode = "fake", rawBaseUrl = "")

        assertEquals(BackendMode.FAKE, configuration.mode)
        assertFalse(configuration.usesHttp)
        assertNull(configuration.baseUrl)
    }

    @Test
    fun validIntegrationUrlSelectsHttpAndKeepsATrailingSlash() {
        val configuration = valid(rawMode = "http", rawBaseUrl = "https://api.paperreader.example")

        assertEquals(BackendMode.HTTP, configuration.mode)
        assertTrue(configuration.usesHttp)
        assertEquals("https://api.paperreader.example/", configuration.requireBaseUrl())
    }

    @Test
    fun unknownModeIsAConfigurationFailureRatherThanASilentFallbackToFake() {
        assertNull(BackendMode.parse("unsupported"))
        assertNull(BackendMode.parse(null))

        assertEquals(
            BackendConfigurationProblem.UNKNOWN_MODE,
            invalid(rawMode = "unsupported", rawBaseUrl = "https://api.paperreader.example").problem,
        )
    }

    @Test
    fun missingIntegrationUrlNeverSubstitutesTheFakeSource() {
        val failure = invalid(rawMode = "http", rawBaseUrl = "   ")

        assertEquals(BackendConfigurationProblem.MISSING_BASE_URL, failure.problem)
        assertTrue(
            "The message must say how to fix it",
            failure.message.contains("-PpaperReaderBackendBaseUrl"),
        )
    }

    @Test
    fun malformedIntegrationUrlIsRejected() {
        listOf("not a url", "ftp://api.paperreader.example", "https:///nohost").forEach { raw ->
            assertEquals(
                "\"$raw\" must be rejected",
                BackendConfigurationProblem.MALFORMED_BASE_URL,
                invalid(rawMode = "http", rawBaseUrl = raw).problem,
            )
        }
    }

    @Test
    fun debugBuildAcceptsCleartextOnlyForTheLoopbackDevelopmentHosts() {
        BackendConfiguration.LOOPBACK_DEVELOPMENT_HOSTS.forEach { host ->
            val configuration = valid(rawMode = "http", rawBaseUrl = "http://$host:8080", isDebugBuild = true)
            assertEquals("http://$host:8080/", configuration.requireBaseUrl())
        }

        assertEquals(
            BackendConfigurationProblem.CLEARTEXT_BASE_URL_FOR_REMOTE_HOST,
            invalid(rawMode = "http", rawBaseUrl = "http://api.paperreader.example", isDebugBuild = true).problem,
        )
    }

    @Test
    fun releaseBuildRejectsAnyCleartextUrl() {
        val failure = invalid(rawMode = "http", rawBaseUrl = "http://10.0.2.2:8080", isDebugBuild = false)

        assertEquals(BackendConfigurationProblem.CLEARTEXT_BASE_URL_IN_RELEASE, failure.problem)
    }

    @Test
    fun releaseBuildAcceptsAnHttpsUrl() {
        val configuration = valid(rawMode = "http", rawBaseUrl = "https://api.paperreader.example/", isDebugBuild = false)

        assertEquals("https://api.paperreader.example/", configuration.requireBaseUrl())
    }

    private fun valid(rawMode: String?, rawBaseUrl: String?, isDebugBuild: Boolean = true): BackendConfiguration {
        val result = BackendConfiguration.resolve(rawMode, rawBaseUrl, isDebugBuild)
        return (result as BackendConfigurationResult.Valid).configuration
    }

    private fun invalid(
        rawMode: String?,
        rawBaseUrl: String?,
        isDebugBuild: Boolean = true,
    ): BackendConfigurationResult.Invalid {
        return BackendConfiguration.resolve(rawMode, rawBaseUrl, isDebugBuild) as BackendConfigurationResult.Invalid
    }
}
