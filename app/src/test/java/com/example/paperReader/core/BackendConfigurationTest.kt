package com.example.paperReader.core

import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendConfigurationTest {
    @Test
    fun unknownCompileTimeModeFallsBackToFake() {
        assertEquals(BackendMode.FAKE, BackendMode.from("unsupported"))
        assertEquals(BackendMode.FAKE, BackendMode.from(null))
    }

    @Test
    fun defaultBuildUsesFakeModeWithoutBackendUrl() {
        val configuration = BackendConfiguration.fromBuildConfig()

        assertEquals(BackendMode.FAKE, configuration.mode)
        assertFalse(configuration.shouldUseHttp)
    }
}
