package io.github.noxaur.dsmod.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MenusTest {
    private fun rows(n: Int) = 114 + n * 18

    @Test
    fun `a six-row menu sits where the game centers it`() {
        for (screen in listOf(222, 240, 251, 480)) assertEquals((screen - 222) / 2, Menus.top(screen, rows(6)))
    }

    @Test
    fun `every menu's bottom row sits where a six-row menu's does`() {
        for (screen in listOf(200, 240, 251, 480)) {
            // The bottom row of a chest menu starts 18 above its inventory label, which is 94 above the menu's bottom.
            val bottomRows = (1..6).map { n -> Menus.top(screen, rows(n)) + 18 + (n - 1) * 18 }
            assertEquals(1, bottomRows.distinct().size, "on a screen $screen tall: $bottomRows")
            val bottoms = (1..6).map { n -> Menus.top(screen, rows(n)) + rows(n) }
            assertEquals(1, bottoms.distinct().size, "on a screen $screen tall: $bottoms")
        }
    }

    @Test
    fun `the cursor comes back once, only to a screen that opens soon after`() {
        val memory = CursorMemory()
        assertNull(memory.reopened(1_000), "nothing closed yet")
        memory.left(1_000, 120.0, 340.0)
        assertEquals(120.0 to 340.0, memory.reopened(1_000 + Menus.SWAP_MILLIS))
        assertNull(memory.reopened(1_001 + Menus.SWAP_MILLIS), "already handed out")
        memory.left(5_000, 1.0, 2.0)
        assertNull(memory.reopened(5_001 + Menus.SWAP_MILLIS), "opened too late")
    }

    @Test
    fun `a menu opened from a command's menu goes back to it`() {
        val trail = MenuTrail()
        trail.sent(1_000, "storage")
        assertNull(trail.opened(1_100, "Storage"), "the command's own menu")
        trail.closed(2_000)
        assertEquals("storage", trail.opened(2_000, "Backpack"))
        trail.closed(3_000)
        assertEquals("storage", trail.opened(3_000, "Backpack Slot 1"), "two menus deep")
        trail.closed(4_000)
        assertNull(trail.opened(4_000, "Storage"), "back at the command's menu")
    }

    @Test
    fun `a run ends when the player leaves the menus`() {
        val trail = MenuTrail()
        trail.sent(1_000, "storage")
        trail.opened(1_100, "Storage")
        trail.closed(2_000)
        assertNull(trail.opened(2_001 + Menus.SWAP_MILLIS, "Maddox the Slayer"), "opened from an NPC after a pause")
        trail.closed(3_000)
        assertNull(trail.opened(3_000, "Choose a Slayer"), "a run from an NPC has no command")
    }

    @Test
    fun `only a menu soon after a command is the command's`() {
        val trail = MenuTrail()
        assertNull(trail.opened(1_000, "Maddox the Slayer"), "no command sent")
        trail.closed(1_500)
        trail.sent(2_000, "warp end")
        assertNull(trail.opened(2_001 + Menus.COMMAND_MILLIS, "Spirit Leap"), "opened long after the command")
        trail.closed(10_000)
        assertNull(trail.opened(10_000, "Leaped"), "the late menu did not take the command")
    }

    @Test
    fun `a command sent from a menu starts the run again`() {
        val trail = MenuTrail()
        trail.sent(1_000, "storage")
        trail.opened(1_100, "Storage")
        trail.closed(2_000)
        trail.sent(2_000, "pets")
        assertNull(trail.opened(2_050, "Pets"))
        trail.closed(3_000)
        assertEquals("pets", trail.opened(3_000, "Pet Upgrades"))
    }
}
