package io.github.noxaur.dsmod.core.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlotLocksTest {
    @Test
    fun `a slot locks and unlocks`() {
        val locked = SlotLocks().toggle(4)
        assertEquals(setOf(4), locked.locked)
        assertEquals(SlotLocks(), locked.toggle(4))
    }

    @Test
    fun `a link takes a hotbar slot and a slot above, in either order, and locks both`() {
        val linked = SlotLocks().link(20, 3)!!
        assertEquals(setOf(3, 20), linked.locked)
        assertEquals(20, linked.partner(3))
        assertEquals(3, linked.partner(20))
        assertNull(linked.partner(4))
        assertNull(SlotLocks().link(3, 4), "Two hotbar slots")
        assertNull(SlotLocks().link(20, 21), "Two slots above")
        assertNull(SlotLocks().link(3, 38), "Armor")
        assertNull(SlotLocks().link(3, SlotLocks.OFF_HAND), "The off hand")
    }

    @Test
    fun `the same link again ends it and keeps the locks`() {
        val ended = SlotLocks().link(3, 20)!!.link(20, 3)!!
        assertEquals(emptyMap(), ended.links)
        assertEquals(setOf(3, 20), ended.locked)
    }

    @Test
    fun `a slot has one partner, so a new link replaces the old one at both ends`() {
        val moved = SlotLocks().link(3, 20)!!.link(3, 21)!!
        assertEquals(mapOf(3 to 21), moved.links)
        assertEquals(mapOf(4 to 21), moved.link(4, 21)!!.links)
    }

    @Test
    fun `unlocking either end ends the link and leaves the other locked`() {
        val linked = SlotLocks().link(3, 20)!!
        assertEquals(SlotLocks(setOf(20)), linked.toggle(3))
        assertEquals(SlotLocks(setOf(3)), linked.toggle(20))
    }
}
