package com.example.paperReader.domain

import com.example.paperReader.domain.repository.RepositoryError
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryErrorTest {
    @Test
    fun errorMessagesAreSafeAndUserFacing() {
        assertEquals(
            "You're offline. Check your connection or try cached results.",
            RepositoryError.Offline.userMessage,
        )
        assertEquals(
            "The service returned data we could not read.",
            RepositoryError.Contract(RepositoryError.ContractFailure.INVALID_RESPONSE).userMessage,
        )
        assertEquals(
            "Paper access could not be verified.",
            RepositoryError.AccessValidation(RepositoryError.AccessFailure.INVALID_DESCRIPTOR).userMessage,
        )
    }
}
