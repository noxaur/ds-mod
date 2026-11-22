package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.harness.lines
import kotlin.test.Test
import kotlin.test.assertEquals

/** @keep The rows and lines are the ones a capture of 2026-11-14 shows in each place, with the padding the server sends. */
class AreaTest {
    private fun areas(tab: List<String>, sidebar: List<String>): List<Area> {
        val tabState = TabState().also { it.update(lines(*tab.toTypedArray())) }
        val sidebarState = SidebarState().also { it.update(Sidebar(lines = lines(*sidebar.toTypedArray()))) }
        return Area.entries.filter { it != Area.ANYWHERE && it.holds(tabState, sidebarState) }
    }

    @Test
    fun `each place puts the player in its own area`() {
        assertEquals(listOf(Area.VILLAGE), areas(listOf("             Info             ", "Area: Village", " Instance: main"), listOf("Name: Player1", "⏣ Village")))
        assertEquals(listOf(Area.CRYPTS), areas(listOf("Area: The Crypts", " Instance: main"), listOf("⏣ Crypts")))
        assertEquals(listOf(Area.END), areas(listOf("Area: The End", " Lobby: #1"), listOf("Purse: 527,057,149", "⏣ Dragon's Den")))
        assertEquals(listOf(Area.DUNGEON_HUB), areas(listOf("         Dungeons (7)         ", "Active Boosters"), listOf(" ⏣ Dungeon Hub", "Catacombs: 0")))
        assertEquals(listOf(Area.CATACOMBS), areas(listOf("Dungeon Stats", " Dungeon: Catacombs"), listOf(" ⏣ The Catacombs (F1)", "Keys: ■ ✗ ■ 0x")))
    }

    @Test
    fun `a dungeon is told by the sidebar before its tab list arrives`() {
        assertEquals(listOf(Area.CATACOMBS), areas(listOf("         Dungeons (7)         "), listOf(" ⏣ The Catacombs (F1)")))
    }

    @Test
    fun `the Void Sepulture is a part of The End`() {
        assertEquals(listOf(Area.END, Area.VOID_SEPULTURE), areas(listOf("Area: The End"), listOf("⏣ Void Sepulture")))
    }

    @Test
    fun `before the server says anything the player is nowhere`() {
        assertEquals(emptyList(), areas(emptyList(), emptyList()))
    }

    @Test
    fun `the sidebar gives its place and its info lines`() {
        val state = SidebarState().also { it.update(Sidebar(lines = lines("", "Name: Player1", " ⏣ The Catacombs (F1)", "Purse: 527,057,149"))) }
        assertEquals("The Catacombs (F1)", state.place)
        assertEquals("527,057,149", state.info("Purse"))
    }
}
