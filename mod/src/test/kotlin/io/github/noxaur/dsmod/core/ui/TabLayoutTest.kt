package io.github.noxaur.dsmod.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabLayoutTest {
    private val tabs = TabLayout.DEFAULT

    @Test
    fun `each place has a tab's width under it`() {
        for (slot in 0 until TabLayout.PER_EDGE) {
            assertEquals(slot, TabLayout.slotAt(TabLayout.x(slot)))
            assertEquals(slot, TabLayout.slotAt(TabLayout.x(slot) + TabLayout.TAB_WIDTH - 1))
        }
        assertEquals(-1, TabLayout.slotAt(-20))
        assertEquals(-1, TabLayout.slotAt(TabLayout.x(TabLayout.PER_EDGE) + 4))
    }

    @Test
    fun `a tab moves to a free place`() {
        val moved = TabLayout.move(tabs, 0, TabEdge.TOP, 2)
        assertEquals(TabEdge.TOP to 2, moved[0].edge to moved[0].slot)
        assertEquals(-1, TabLayout.at(moved, TabEdge.BOTTOM, 0))
    }

    @Test
    fun `a tab dropped on another trades places with it`() {
        val moved = TabLayout.move(tabs, 0, TabEdge.BOTTOM, 2)
        assertEquals(2, moved[0].slot)
        assertEquals(0, moved[2].slot)
        assertEquals(tabs.size, moved.size)
    }

    @Test
    fun `a new tab takes the first free place, bottom first`() {
        val added = TabLayout.add(tabs, MenuTab(command = "pets"))!!
        assertEquals(TabEdge.BOTTOM to 4, added.last().edge to added.last().slot)
        var full = emptyList<MenuTab>()
        repeat(TabLayout.PER_EDGE * 2) { full = TabLayout.add(full, MenuTab(command = "c$it"))!! }
        assertEquals(TabEdge.TOP to TabLayout.PER_EDGE - 1, full.last().edge to full.last().slot)
        assertNull(TabLayout.add(full, MenuTab(command = "one more")))
    }

    @Test
    fun `the search bar opens toward the middle and stays within the menu`() {
        assertEquals(TabLayout.x(0) to TabLayout.SEARCH_WIDTH, TabLayout.searchBar(0, 176))
        val right = TabLayout.searchBar(TabLayout.PER_EDGE - 1, 176)
        assertEquals(TabLayout.x(TabLayout.PER_EDGE - 1) + TabLayout.TAB_WIDTH, right.first + right.second)
        val middle = TabLayout.searchBar(3, 176)
        assertEquals(176, middle.first + middle.second, "a tab just left of the middle opens right and stops at the menu's edge")
        assert(middle.first <= TabLayout.x(3)) { "the bar still covers its tab" }
    }

    @Test
    fun `the tab for the command that opened the menu is selected`() {
        assertEquals(1, TabLayout.selected(tabs, "ec"))
        assertEquals(-1, TabLayout.selected(tabs, "warp end"))
        assertEquals(-1, TabLayout.selected(tabs, null))
    }

    @Test
    fun `typed items and commands are tidied`() {
        assertEquals("minecraft:ender_chest", TabLayout.itemId(" Ender Chest "))
        assertEquals("dsmod:thing", TabLayout.itemId("dsmod:thing"))
        assertEquals("warp end", TabLayout.command(" /warp end "))
    }
}
