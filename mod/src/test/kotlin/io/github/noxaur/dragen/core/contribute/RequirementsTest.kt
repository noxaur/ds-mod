package io.github.noxaur.dragen.core.contribute

import io.github.noxaur.dragen.core.backend.Request
import io.github.noxaur.dragen.harness.text
import io.github.noxaur.dragen.core.backend.Requirement
import io.github.noxaur.dragen.core.brief.Brief
import io.github.noxaur.dragen.core.game.Sidebar
import io.github.noxaur.dragen.core.game.SidebarState
import io.github.noxaur.dragen.core.game.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequirementsTest {
    private fun request(vararg requirements: Requirement) =
        Request("r", Brief("A", listOf(Brief.Step("B"))), requirements.toList(), 60)


    @Test
    fun `an area is met when the player has been there`() {
        assertTrue(Requirements.qualifies(request(Requirement("area", "The End")), Places(setOf("Hub", "The End"))))
        assertFalse(Requirements.qualifies(request(Requirement("area", "The End")), Places(setOf("Hub"))))
    }

    @Test
    fun `every requirement must be met, and none is met by anyone`() {
        val both = request(Requirement("area", "The End"), Requirement("area", "Hub"))
        assertFalse(Requirements.qualifies(both, Places(setOf("Hub"))))
        assertTrue(Requirements.qualifies(both, Places(setOf("Hub", "The End"))))
        assertTrue(Requirements.qualifies(request(), Places()))
    }

    @Test
    fun `a kind the mod does not know is never offered`() {
        assertFalse(Requirements.qualifies(request(Requirement("rank", "VIP")), Places(setOf("VIP"))))
    }

    @Test
    fun `a place is the tab list's area or the sidebar's, without a dungeon's floor`() {
        val tab = TabState().apply { update(listOf(text("Area: The End"))) }
        val sidebar = SidebarState().apply { update(Sidebar(text("DRAGSIM"), listOf(text("⏣ The Catacombs (F1)")))) }

        val places = Places().with(tab, sidebar)

        assertEquals(setOf("The End", "The Catacombs"), places.names)
        assertSame(places, places.with(tab, sidebar), "nothing new is no new value, so nothing is written")
    }
}
