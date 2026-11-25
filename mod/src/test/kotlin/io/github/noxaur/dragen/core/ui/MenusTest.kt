package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MenusTest {
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

    private fun storage() = MenuTrail().apply {
        sent(1_000, "storage")
        opened(1_100, "Storage")
    }

    private fun MenuTrail.next(at: Long, title: String) {
        closed(at)
        opened(at, title)
    }

    @Test
    fun `the command's own menu knows its command, through the run, but not the menus deeper in it`() {
        val trail = storage()
        assertEquals("storage", trail.opener)
        trail.next(2_000, "Backpack")
        assertNull(trail.opener, "a menu deeper in the run was opened by a click")
        trail.next(3_000, "Storage")
        assertEquals("storage", trail.opener, "back at the command's menu")
    }

    @Test
    fun `a run ends when the player leaves the menus`() {
        val trail = storage()
        trail.closed(2_000)
        trail.opened(2_001 + Menus.SWAP_MILLIS, "Maddox the Slayer")
        trail.next(3_000, "Storage")
        assertNull(trail.opener, "a run from an NPC has no command")
    }

    @Test
    fun `only a menu soon after a command is the command's`() {
        val trail = MenuTrail()
        trail.opened(1_000, "Maddox the Slayer")
        assertNull(trail.opener, "no command sent")
        trail.closed(1_500)
        trail.sent(2_000, "warp end")
        trail.opened(2_001 + Menus.COMMAND_MILLIS, "Spirit Leap")
        assertNull(trail.opener, "opened long after the command")
    }

    @Test
    fun `a command sent from a menu starts the run again`() {
        val trail = storage()
        trail.closed(2_000)
        trail.sent(2_000, "pets")
        trail.opened(2_050, "Pets")
        assertEquals("pets", trail.opener)
        trail.next(3_000, "Storage")
        assertNull(trail.opener, "the run is the pets command's now")
    }
}
