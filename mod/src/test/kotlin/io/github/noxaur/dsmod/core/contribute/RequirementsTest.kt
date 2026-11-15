package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.backend.Requirement
import io.github.noxaur.dsmod.core.brief.Brief
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequirementsTest {
    private fun request(vararg requirements: Requirement) =
        Request("r", Brief("A", listOf(Brief.Step("B"))), requirements.toList(), 60)

    @Test
    fun `an area is met when the player has been there`() {
        assertTrue(Requirements.qualifies(request(Requirement("area", "The End")), setOf("Hub", "The End")))
        assertFalse(Requirements.qualifies(request(Requirement("area", "The End")), setOf("Hub")))
    }

    @Test
    fun `every requirement must be met, and none is met by anyone`() {
        val both = request(Requirement("area", "The End"), Requirement("area", "Hub"))
        assertFalse(Requirements.qualifies(both, setOf("Hub")))
        assertTrue(Requirements.qualifies(both, setOf("Hub", "The End")))
        assertTrue(Requirements.qualifies(request(), emptySet()))
    }

    @Test
    fun `a kind the mod does not know is never offered`() {
        assertFalse(Requirements.qualifies(request(Requirement("rank", "VIP")), setOf("VIP")))
    }
}
