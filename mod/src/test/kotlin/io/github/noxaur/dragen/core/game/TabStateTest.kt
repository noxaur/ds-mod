package io.github.noxaur.dragen.core.game

import kotlin.test.Test
import io.github.noxaur.dragen.harness.lines
import kotlin.test.assertEquals

class TabStateTest {
    @Test
    fun `the players are the rows above the info panel, and they follow the tab list`() {
        val tab = TabState()
        tab.update(lines("The End", "[MVP+] Alice [IRON]", "Bob", "Info", "Area: The End", " Admin Booster 6m 47s by [ADMIN] Carol"))
        assertEquals(setOf("Alice", "Bob", "Carol"), tab.players)

        tab.update(lines("The End", "Dave", "Info"))
        assertEquals(setOf("Dave"), tab.players)
    }
}
