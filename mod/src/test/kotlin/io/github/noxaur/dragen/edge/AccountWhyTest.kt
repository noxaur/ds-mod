package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.backend.Backend
import io.github.noxaur.dragen.core.backend.BackendException
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountWhyTest {
    @Test
    fun `a 503 is Mojang only when sign-in was refused`() {
        assertEquals(
            "Mojang did not answer. Try again in a moment.",
            Account.why(BackendException(503, Backend.MOJANG_DOWN)),
        )
        assertEquals(
            "The backend did not answer. Try again in a moment.",
            Account.why(BackendException(503, "The backend answered 503.")),
        )
    }
}
