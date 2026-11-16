package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.backend.BackendException
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountWhyTest {
    @Test
    fun `a 503 is Mojang only when sign-in was refused`() {
        assertEquals(
            "Mojang did not answer. Try again in a moment.",
            Account.why(BackendException(503, "The backend did not accept the sign-in.")),
        )
        assertEquals(
            "The backend did not answer. Try again in a moment.",
            Account.why(BackendException(503, "The backend answered 503.")),
        )
    }
}
