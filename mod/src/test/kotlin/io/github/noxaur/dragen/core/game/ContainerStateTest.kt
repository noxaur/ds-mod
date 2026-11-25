package io.github.noxaur.dragen.core.game

import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.ownInventory
import kotlin.test.Test
import kotlin.test.assertEquals

class ContainerStateTest : Harness() {
    private val stone = Item("minecraft:stone", 1)
    private val dirt = Item("minecraft:dirt", 2)

    private val state = game.container

    @Test
    fun `a menu starts empty, and so does the inventory read again from nothing`() {
        post(*ownInventory(9 to stone), ContainerOpened(5, null, 63))
        assertEquals(List(63) { null }, state.slots)
        post(*ownInventory())
        assertEquals(List(46) { null }, state.slots)
    }

    @Test
    fun `back in the inventory, kept slots are as the last menu found them, with the changes since`() {
        post(*ownInventory(9 to stone, 10 to dirt))
        post(ContainerOpened(5, null, 63), SlotChanged(54, stone), ContainerOpened(0, null, 46, kept = true), SlotChanged(10, null))
        assertEquals(stone, state.slots[9])
        assertEquals(null, state.slots[10])
        post(ContainerOpened(6, null, 90), ContainerOpened(0, null, 46, kept = true))
        assertEquals(listOf(stone, null), state.slots.subList(9, 11))
    }

    @Test
    fun `a menu's own slots come first and the player's 36 last, and in the inventory every slot is the player's`() {
        post(ContainerOpened(5, null, 63))
        assertEquals(0 until 27, state.menuSlots)
        assertEquals(27 until 63, state.playerSlots)
        post(ContainerOpened(0, null, 46))
        assertEquals(0 until 0, state.menuSlots)
        assertEquals(0 until 46, state.playerSlots)
    }
}
