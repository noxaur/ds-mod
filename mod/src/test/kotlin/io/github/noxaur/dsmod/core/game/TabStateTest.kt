package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import kotlin.test.assertEquals

class TabStateTest {
    private fun rows(vararg lines: String) = lines.map { ServerText(listOf(ServerSpan(it))) }

    @Test
    fun `the players are the rows above the info panel, and they follow the tab list`() {
        val tab = TabState()
        tab.update(rows("The End", "[MVP+] Alice [IRON]", "Bob", "Info", "Area: The End", " Admin Booster 6m 47s by [ADMIN] Carol"))
        assertEquals(setOf("Alice", "Bob", "Carol"), tab.players)

        tab.update(rows("The End", "Dave", "Info"))
        assertEquals(setOf("Dave"), tab.players)
    }
}
