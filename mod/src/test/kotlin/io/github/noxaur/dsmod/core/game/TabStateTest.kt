package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** The rows are the ones a capture of 2026-11-14 shows in each place, with the padding the server sends. */
class TabStateTest {
    private fun areas(vararg rows: String): List<Area> {
        val tab = TabState().also { state -> state.update(rows.map { ServerText(ServerSpan(it)) }) }
        return Area.entries.filter { it != Area.ANYWHERE && tab.isIn(it) }
    }

    @Test
    fun `each place puts the player in its own area and no other`() {
        assertEquals(listOf(Area.VILLAGE), areas("             Info             ", "Area: Village", " Instance: main"))
        assertEquals(listOf(Area.CRYPTS), areas("Area: The Crypts", " Instance: main"))
        assertEquals(listOf(Area.END), areas("Area: The End", " Lobby: #1", " Protector: Rising (Tier 3/5, 83)"))
        assertEquals(listOf(Area.CATACOMBS), areas("Dungeon Stats", " Dungeon: Catacombs", " Opened Rooms: 2"))
    }

    @Test
    fun `the dungeon hub and an empty tab list are no area`() {
        assertEquals(emptyList(), areas("Dungeons (7)", "[MVP+] Player1"))
        assertEquals(emptyList(), areas())
    }
}
