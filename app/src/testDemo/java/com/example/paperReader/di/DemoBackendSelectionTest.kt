package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.data.download.FakeDownloadManager
import com.example.paperReader.data.remote.FakePaperRemoteDataSource
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.domain.repository.DownloadGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The demo flavor serves fixture data and simulates downloads. Its counterpart is
 * `IntegrationBackendSelectionTest`; if one of the two is updated without the other, the flavors
 * have drifted.
 */
class DemoBackendSelectionTest {

    @Test
    fun theFakeSourceAndSimulatedGatewayAreWhatIsBound() {
        val bindings = DemoBackendModule::class.java.declaredMethods
            .associate { it.returnType to it.parameterTypes.single() }

        assertEquals(FakePaperRemoteDataSource::class.java, bindings[PaperRemoteDataSource::class.java])
        assertEquals(FakeDownloadManager::class.java, bindings[DownloadGateway::class.java])
    }

    @Test
    fun aDemoBuildResolvesWithNoBackendAddressAtAll() {
        val configuration = BackendConfiguration(BackendMode.FAKE, baseUrl = null)

        // Nothing in this flavor constructs Retrofit, so there is no placeholder URL to fall back to.
        assertNull(configuration.baseUrl)
    }
}
