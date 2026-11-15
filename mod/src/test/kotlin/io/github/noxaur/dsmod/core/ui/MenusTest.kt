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
}
