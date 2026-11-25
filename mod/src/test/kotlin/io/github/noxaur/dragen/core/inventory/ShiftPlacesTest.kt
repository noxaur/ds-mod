package io.github.noxaur.dragen.core.inventory

import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftPlacesTest {
    private fun slot(
        index: Int,
        count: Int = 0,
        limit: Int = 64,
        same: Boolean = false,
        acceptsEmpty: Boolean = count == 0,
        locked: Boolean = false,
    ) = ShiftSlot(index, count, limit, same, acceptsEmpty, locked)

    private fun matching(index: Int, count: Int, locked: Boolean = false) = slot(index, count = count, same = true, acceptsEmpty = false, locked = locked)

    private fun other(index: Int, locked: Boolean = false) = slot(index, count = 64, same = false, acceptsEmpty = false, locked = locked)

    @Test
    fun `a partial stack takes what fits and a locked empty slot takes none`() {
        val slots = listOf(slot(0, locked = true), matching(1, 40))
        assertEquals(listOf(1 to 24), shiftPlaces(32, slots))
    }

    @Test
    fun `what a locked slot would have taken goes on to the next unlocked slot`() {
        val slots = listOf(matching(0, 60, locked = true), slot(1), slot(2))
        assertEquals(listOf(1 to 10), shiftPlaces(10, slots))
        assertEquals(listOf(0 to 4, 1 to 6), shiftPlaces(10, slots, skipLocked = false))
    }

    @Test
    fun `a stack fills matching stacks before empty slots, and splits across them`() {
        val slots = listOf(slot(3), matching(1, 50), matching(2, 60))
        assertEquals(listOf(1 to 14, 2 to 4, 3 to 12), shiftPlaces(30, slots))
    }

    @Test
    fun `nothing is placed when every slot that could take any is locked`() {
        val slots = listOf(slot(0, locked = true), matching(1, 10, locked = true))
        assertEquals(emptyList(), shiftPlaces(5, slots))
        assertEquals(listOf(1 to 5), shiftPlaces(5, slots, skipLocked = false))
    }

    @Test
    fun `a full stack of a different item is not a place, locked or not`() {
        val slots = listOf(other(0, locked = true), other(1))
        assertEquals(emptyList(), shiftPlaces(8, slots))
        assertEquals(emptyList(), shiftPlaces(8, slots, skipLocked = false))
    }

    @Test
    fun `an item that does not stack skips a matching slot and takes an empty one`() {
        val slots = listOf(slot(0, count = 1, limit = 1, same = true, acceptsEmpty = false), slot(1))
        assertEquals(listOf(1 to 1), shiftPlaces(1, slots, stackable = false))
    }
}
