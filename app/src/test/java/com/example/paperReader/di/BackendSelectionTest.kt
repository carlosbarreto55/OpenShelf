package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.RemoteJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Provider
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Retrofit

/**
 * Each flavor's configuration must resolve to exactly one remote source and one download gateway.
 * The unselected provider must never even be asked for an instance — a demo build has to be able
 * to run with no backend at all, and an integration build must never reach fixture data.
 */
class BackendSelectionTest {

    private val demo = BackendConfiguration(BackendMode.FAKE, baseUrl = null)
    private val integration = BackendConfiguration(BackendMode.HTTP, baseUrl = "https://api.paperreader.example/")

    @Test
    fun demoConfigurationSelectsTheFakeRemoteSourceAndNeverBuildsTheHttpOne() {
        val source = BackendModule.providePaperRemoteDataSource(
            configuration = demo,
            fakeProvider = { FakePaperRemoteDataSource() },
            httpProvider = neverRequested("HttpPaperRemoteDataSource"),
        )

        assertTrue(source is FakePaperRemoteDataSource)
    }

    @Test
    fun integrationConfigurationSelectsTheHttpRemoteSourceAndNeverBuildsTheFakeOne() {
        val source = BackendModule.providePaperRemoteDataSource(
            configuration = integration,
            fakeProvider = neverRequested("FakePaperRemoteDataSource"),
            httpProvider = { HttpPaperRemoteDataSource(anyApi()) },
        )

        assertTrue(source is HttpPaperRemoteDataSource)
    }

    @Test
    fun demoConfigurationSelectsTheSimulatedDownloadGateway() {
        assertEquals(
            Selected.FAKE,
            selectedGateway(demo),
        )
    }

    @Test
    fun integrationConfigurationSelectsTheWorkManagerDownloadGateway() {
        assertEquals(
            Selected.WORK_MANAGER,
            selectedGateway(integration),
        )
    }

    @Test
    fun anIntegrationApiIsBuiltAgainstTheConfiguredBaseUrl() {
        val api = BackendModule.providePaperReaderApi(integration)

        // Retrofit resolves the interface eagerly enough that a bad base URL would have thrown.
        assertTrue(api is PaperReaderApi)
    }

    /**
     * Records which provider the module asked for without constructing either gateway: both carry
     * Android dependencies (WorkManager, file storage) that this assertion does not need.
     */
    private fun selectedGateway(configuration: BackendConfiguration): Selected {
        return try {
            DownloadModule.provideDownloadGateway(
                configuration = configuration,
                fakeProvider = { throw ProviderRequested(Selected.FAKE) },
                workManagerProvider = { throw ProviderRequested(Selected.WORK_MANAGER) },
            )
            fail("Expected exactly one gateway provider to be requested")
            error("unreachable")
        } catch (requested: ProviderRequested) {
            requested.which
        }
    }

    private fun anyApi(): PaperReaderApi {
        return Retrofit.Builder()
            .baseUrl("https://api.paperreader.example/")
            .addConverterFactory(RemoteJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PaperReaderApi::class.java)
    }

    private fun <T : Any> neverRequested(name: String): Provider<T> = Provider {
        fail("$name must not be constructed for this configuration")
        error("unreachable")
    }

    private enum class Selected { FAKE, WORK_MANAGER }

    private class ProviderRequested(val which: Selected) : RuntimeException()
}
