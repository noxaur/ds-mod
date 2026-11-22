package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ContainerStateTest {
    private val stone = Item("minecraft:stone", 1)
    private val dirt = Item("minecraft:dirt", 2)

    private fun ContainerState.post(vararg events: GameEvent) = events.forEach {
        when (it) {
            is ContainerOpened -> opened(it)
            is SlotChanged -> changed(it)
            else -> error("Not a container event: $it")
        }
    }

    @Test
    fun `a menu starts empty, and so does the inventory read again from nothing`() {
        val state = ContainerState()
        state.post(ContainerOpened(0, null, 46), SlotChanged(9, stone), ContainerOpened(5, null, 63))
        assertEquals(List(63) { null }, state.slots)
        state.post(ContainerOpened(0, null, 46))
        assertEquals(List(46) { null }, state.slots)
    }

    @Test
    fun `back in the inventory, kept slots are as the last menu found them, with the changes since`() {
        val state = ContainerState()
        state.post(ContainerOpened(0, null, 46), SlotChanged(9, stone), SlotChanged(10, dirt))
        state.post(ContainerOpened(5, null, 63), SlotChanged(54, stone), ContainerOpened(0, null, 46, kept = true), SlotChanged(10, null))
        assertEquals(stone, state.slots[9])
        assertEquals(null, state.slots[10])
        state.post(ContainerOpened(6, null, 90), ContainerOpened(0, null, 46, kept = true))
        assertEquals(listOf(stone, null), state.slots.subList(9, 11))
    }

    @Test
    fun `a menu's own slots come first and the player's 36 last, and in the inventory every slot is the player's`() {
        val state = ContainerState()
        state.post(ContainerOpened(5, null, 63))
        assertEquals(0 until 27, state.menuSlots)
        assertEquals(27 until 63, state.playerSlots)
        state.post(ContainerOpened(0, null, 46))
        assertEquals(0 until 0, state.menuSlots)
        assertEquals(0 until 46, state.playerSlots)
    }
}
