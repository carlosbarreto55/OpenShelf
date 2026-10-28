package com.example.paperReader.di

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import com.example.paperReader.data.download.WorkManagerDownloadGateway
import com.example.paperReader.data.remote.HttpPaperRemoteDataSource
import com.example.paperReader.data.remote.PaperReaderApi
import com.example.paperReader.data.remote.PaperRemoteDataSource
import com.example.paperReader.domain.repository.DownloadGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every flavor that reaches a real server cannot reach anything else.
 *
 * Selection used to be a runtime `when (configuration.mode)` over two providers that both shipped
 * in every APK. It is now decided by which source set is compiled, so the guarantee is stronger
 * than "the right branch is taken": the wrong implementation is not present to take.
 *
 * This suite lives in `src/testHttp/` and therefore runs unchanged under both `integration` and
 * `production`. That is the point — the two flavors differ only in where their address comes from,
 * and the assertions that say so must not be maintained in two copies that can drift apart.
 */
class HttpBackendSelectionTest {

    @Test
    fun noFakeImplementationExistsOnThisFlavorsClasspath() {
        // The point of the whole arrangement. If these ever resolve, the demo sources have leaked
        // back into `main` and an HTTP build is shipping a fake data source again.
        listOf(
            "com.example.paperReader.data.remote.FakePaperRemoteDataSource",
            "com.example.paperReader.data.download.FakeDownloadManager",
            "com.example.paperReader.data.remote.fixtures.PaperFixtures",
            "com.example.paperReader.di.DemoBackendModule",
        ).forEach { className ->
            assertThrows(
                "$className must not exist in an HTTP build",
                ClassNotFoundException::class.java,
            ) { Class.forName(className) }
        }
    }

    @Test
    fun theHttpSourceAndWorkManagerGatewayAreWhatIsBound() {
        val bindings = HttpBackendBindsModule::class.java.declaredMethods
            .associate { it.returnType to it.parameterTypes.single() }

        assertEquals(HttpPaperRemoteDataSource::class.java, bindings[PaperRemoteDataSource::class.java])
        assertEquals(WorkManagerDownloadGateway::class.java, bindings[DownloadGateway::class.java])
    }

    @Test
    fun anApiIsBuiltAgainstTheConfiguredBaseUrl() {
        val configuration = BackendConfiguration(BackendMode.HTTP, baseUrl = "https://api.paperreader.example/")

        // Retrofit validates the base URL eagerly, so an unusable one would have thrown here.
        assertTrue(HttpBackendModule.providePaperReaderApi(configuration) is PaperReaderApi)
    }
}
