package io.github.noxaur.dsmod.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTabsTest {
    private val names = listOf("The End", "Combat", "Inventory", "Menus")

    private fun tabsOf(vararg opened: Int) = SettingsTabs(Place(0)).also { tabs -> opened.forEach { tabs.open(Place(it)) } }

    private fun SettingsTabs.titles() = tabs.mapIndexed { i, tab -> tab.title(names).let { if (i == selected) "[$it]" else it } }.joinToString(" ")

    @Test
    fun `a tab is titled by its section, its search or as a new tab`() {
        assertEquals("Combat", SettingsTab(Place(1)).title(names))
        assertEquals("eyes", SettingsTab(Place(1, " eyes ")).title(names))
        assertEquals("New Tab", SettingsTab(Place.BLANK).title(names))
        assertEquals("Combat", SettingsTab(Place(1, "  ")).title(names), "a blank query is no search")
    }

    @Test
    fun `going to a section keeps the place before it, and back and forward walk the history`() {
        val tab = SettingsTab(Place(0))
        tab.scroll = 120
        tab.current = 2
        tab.go(1)
        assertEquals(Place(1), tab.place)
        assertEquals(0, tab.scroll)
        tab.go(2)
        assertTrue(tab.back())
        assertEquals(Place(1), tab.place)
        assertTrue(tab.back())
        assertEquals(Place(0), tab.place)
        assertEquals(120, tab.scroll, "back puts the pane where it was")
        assertEquals(2, tab.current)
        assertFalse(tab.back(), "there is nothing before the first place")
        assertTrue(tab.forward())
        assertTrue(tab.forward())
        assertEquals(Place(2), tab.place)
        assertFalse(tab.forward())
    }

    @Test
    fun `going somewhere new drops the forward history`() {
        val tab = SettingsTab(Place(0))
        tab.go(1)
        tab.back()
        tab.go(3)
        assertFalse(tab.forward())
        tab.back()
        assertEquals(Place(0), tab.place)
    }

    @Test
    fun `going to the section a tab is on scrolls it to the top and adds no history`() {
        val tab = SettingsTab(Place(0))
        tab.scroll = 80
        tab.go(0)
        assertEquals(0, tab.scroll)
        assertFalse(tab.back())
    }

    @Test
    fun `typing a search is one step in the history, however many keys it takes`() {
        val tab = SettingsTab(Place(2))
        tab.search("e")
        tab.search("ey")
        tab.search("eyes")
        assertEquals(Place(2, "eyes"), tab.place)
        assertTrue(tab.back())
        assertEquals(Place(2), tab.place)
        assertTrue(tab.forward())
        assertEquals(Place(2, "eyes"), tab.place, "forward comes back to the whole search")
    }

    @Test
    fun `clearing a search returns the tab to its section, and a section ends a search`() {
        val tab = SettingsTab(Place(2))
        tab.search("eyes")
        tab.search("")
        assertEquals(Place(2), tab.place)
        tab.search("loot")
        tab.go(1)
        assertEquals(Place(1), tab.place)
        assertTrue(tab.back())
        assertEquals(Place(2, "loot"), tab.place)
    }

    @Test
    fun `the first open has one tab, and a tab from the sidebar opens after the selected one in the background`() {
        val tabs = SettingsTabs(Place(0))
        assertEquals("[The End]", tabs.titles())
        tabs.open(Place(1))
        tabs.open(Place(2), jump = 1)
        assertEquals("[The End] Inventory Combat", tabs.titles())
        assertEquals(1, tabs.tabs[1].jump, "a subsection opens scrolled to its heading")
        tabs.select(2)
        tabs.open(Place(3))
        assertEquals("The End Inventory [Combat] Menus", tabs.titles())
    }

    @Test
    fun `a new tab is blank, after the selected one, and selected`() {
        val tabs = tabsOf(1)
        tabs.new()
        assertEquals("The End [New Tab] Combat", tabs.titles())
        assertEquals(Place.BLANK, tabs.current.place)
    }

    @Test
    fun `closing the selected tab selects the one that takes its place, or the one before it at the end`() {
        val tabs = tabsOf(3, 2, 1)
        tabs.select(1)
        assertEquals("The End [Combat] Inventory Menus", tabs.titles())
        tabs.close(1)
        assertEquals("The End [Inventory] Menus", tabs.titles())
        tabs.select(2)
        tabs.close(2)
        assertEquals("The End [Inventory]", tabs.titles())
    }

    @Test
    fun `closing another tab keeps the selected one`() {
        val tabs = tabsOf(2, 1)
        tabs.select(2)
        tabs.close(0)
        assertEquals("Combat [Inventory]", tabs.titles())
        tabs.close(0)
        assertEquals("[Inventory]", tabs.titles())
    }

    @Test
    fun `the last tab cannot be closed`() {
        val tabs = SettingsTabs(Place(0))
        assertFalse(tabs.canClose)
        assertFalse(tabs.close(0))
        assertEquals("[The End]", tabs.titles())
    }

    @Test
    fun `a closed tab reopens where it was, selected, with its history`() {
        val tabs = tabsOf(2, 1)
        tabs.select(1)
        tabs.current.go(3)
        tabs.close(1)
        tabs.close(1)
        assertEquals("[The End]", tabs.titles())
        assertTrue(tabs.reopen())
        assertEquals("The End [Inventory]", tabs.titles(), "the last closed comes back first")
        assertTrue(tabs.reopen())
        assertEquals("The End [Menus] Inventory", tabs.titles())
        assertTrue(tabs.current.back())
        assertEquals(Place(1), tabs.current.place)
        assertFalse(tabs.reopen(), "nothing is left to reopen")
    }

    @Test
    fun `a tab moved by dragging keeps the same tab selected`() {
        val tabs = tabsOf(3, 2, 1)
        tabs.select(1)
        tabs.move(1, 3)
        assertEquals("The End Inventory Menus [Combat]", tabs.titles())
        tabs.move(0, 2)
        assertEquals("Inventory Menus The End [Combat]", tabs.titles())
        tabs.move(3, 0)
        assertEquals("[Combat] Inventory Menus The End", tabs.titles())
    }

    @Test
    fun `ctrl tab cycles past either end, and ctrl and a digit picks a tab, with 9 the last`() {
        val tabs = tabsOf(3, 2, 1)
        tabs.cycle(-1)
        assertEquals(3, tabs.selected)
        tabs.cycle(1)
        assertEquals(0, tabs.selected)
        tabs.number(3)
        assertEquals(2, tabs.selected)
        tabs.number(8)
        assertEquals(2, tabs.selected, "a digit past the last tab does nothing")
        tabs.number(9)
        assertEquals(3, tabs.selected)
        tabs.number(1)
        assertEquals(0, tabs.selected)
    }

    @Test
    fun `the narrator hears a tab's title, whether it is a search, and which is selected`() {
        val tabs = SettingsTabs(Place(1))
        tabs.open(Place(0, "eyes"))
        assertEquals("Combat, tab, selected, 1 of 2", tabs.narration(0, names))
        assertEquals("eyes, search tab, 2 of 2", tabs.narration(1, names))
    }
}
