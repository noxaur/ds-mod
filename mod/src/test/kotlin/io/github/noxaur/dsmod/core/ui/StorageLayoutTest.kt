package io.github.noxaur.dsmod.core.ui

import io.github.noxaur.dsmod.core.ui.StorageLayout.Companion.CELL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageLayoutTest {
    private val rows = listOf(3) + List(7) { 5 }

    @Test
    fun `pages form the most square grid, never wider than the screen`() {
        assertEquals(2, StorageLayout(2000, 2000, List(4) { 5 }).columns)
        assertEquals(3, StorageLayout(2000, 2000, List(5) { 5 }).columns)
        assertEquals(3, StorageLayout(2000, 2000, List(9) { 5 }).columns)
        assertEquals(4, StorageLayout(2000, 2000, List(12) { 5 }).columns)
        assertEquals(3, StorageLayout(960, 400, rows).columns, "3 by 3 scrolls rather than widening to 4 by 2")
        assertEquals(1, StorageLayout(200, 400, rows).columns)
        assertEquals(2, StorageLayout(400, 400, rows).columns)
        assertEquals(2, StorageLayout(960, 400, listOf(3, 5)).columns, "no column stands empty")
    }

    @Test
    fun `the panel ends where a six-row chest does, so the hotbar stays in sight`() {
        val layout = StorageLayout(853, 480, rows)
        assertEquals((480 + Menus.TALLEST) / 2, layout.top + layout.height)
        assertEquals(StorageLayout.MARGIN_Y, layout.top, "the pages fill the room above, a tab's height under the screen's top")
        assertTrue(layout.overflows)
        val few = StorageLayout(853, 480, listOf(3, 5))
        assertEquals(layout.top + layout.height, few.top + few.height, "the inventory stays put however many pages there are")
    }

    @Test
    fun `a row of pages is as tall as its tallest page`() {
        val layout = StorageLayout(400, 1000, listOf(3, 5, 5))
        assertEquals(layout.titleTop(0, 0), layout.titleTop(1, 0))
        assertEquals(layout.gridTop(1, 0) + 5 * CELL + StorageLayout.ROW_GAP, layout.titleTop(2, 0))
        assertEquals(layout.pageLeft(0) + StorageLayout.GRID + StorageLayout.COLUMN_GAP, layout.pageLeft(1))
        assertFalse(layout.overflows)
    }

    @Test
    fun `pages taller than the screen scroll above a still inventory`() {
        val layout = StorageLayout(400, 270, rows)
        assertTrue(layout.overflows)
        assertTrue(layout.top >= 0 && layout.top + layout.height <= 270)
        assertEquals(layout.top + layout.height, layout.inventoryCellY(27) + CELL + StorageLayout.INSET, "the stem ends an inset under the hotbar")
        assertEquals(layout.inventoryCellY(0) + 3 * CELL + StorageLayout.HOTBAR_GAP, layout.inventoryCellY(27), "the hotbar sits apart")
        assertEquals(layout.inventoryCellX(0), layout.inventoryCellX(27))
        assertEquals(layout.viewX + layout.pagesWidth + StorageLayout.SCROLL_GAP, layout.scrollbarX)
    }

    @Test
    fun `a point finds its page and cell, and only inside the view`() {
        val layout = StorageLayout(400, 270, rows)
        val x = layout.cellX(1, 10) + 3.0
        val y = layout.cellY(1, 10, 0) + 3.0
        assertEquals(1, layout.pageAt(x, y, 0))
        assertEquals(10, layout.cellAt(1, x, y, 0))
        assertEquals(1, layout.pageAt(layout.pageLeft(1) + 1.0, layout.titleTop(1, 0) + 1.0, 0), "the title is the page's")
        assertEquals(-1, layout.cellAt(1, layout.pageLeft(1) + 1.0, layout.titleTop(1, 0) + 1.0, 0))
        assertEquals(-1, layout.pageAt(layout.pageLeft(1) - 2.0, y, 0), "the gap between pages")
        val below = layout.titleTop(7, 0) + 1.0
        assertEquals(-1, layout.pageAt(layout.pageLeft(7) + 1.0, below, 0), "a page scrolled out of view")
        val offset = layout.titleTop(7, 0) - layout.viewY
        assertEquals(7, layout.pageAt(layout.pageLeft(7) + 1.0, layout.viewY + 1.0, offset))
    }

    @Test
    fun `a cell takes clicks only while all of it is in view`() {
        val layout = StorageLayout(400, 270, rows)
        val offset = layout.gridTop(0, 0) - layout.viewY + 1
        assertFalse(layout.cellShows(0, 0, offset), "the first row is cut by a pixel")
        assertTrue(layout.cellShows(0, 9, offset))
        assertTrue(layout.touches(0, offset))
        assertFalse(layout.shows(0, offset))
    }
}
