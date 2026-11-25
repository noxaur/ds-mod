package io.github.noxaur.dragen.core.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val END_STONE = "minecraft:end_stone"
private const val BEDROCK = "minecraft:bedrock"

class BreakSorterTest {
    private val sorter = BreakSorter()

    private fun breaks(x: Int, y: Int = 0, z: Int = 0, was: String = END_STONE) = sorter.changed(x, y, z, was, BEDROCK)

    private fun idle(ticks: Int) = repeat(ticks) { sorter.tick() }

    private fun assertBrokenBy(vararg by: Breaker) = assertEquals(by.toList(), sorter.tick().map { it.by })

    @Test
    fun `a block the player dug is theirs when it breaks within a second`() {
        sorter.dig(1, 0, 0)
        sorter.tick()
        idle(18)
        breaks(1)
        assertEquals(listOf(BlockBroken(END_STONE, 1, 0, 0, 19, Breaker.PLAYER)), sorter.tick())
    }

    @Test
    fun `a block the player dug long before breaks by another`() {
        sorter.dig(1, 0, 0)
        idle(21)
        breaks(1)
        assertBrokenBy(Breaker.OTHER)
    }

    @Test
    fun `spread beside the player's break is with the player, and a far break in that tick is not`() {
        sorter.dig(0, 0, 0)
        breaks(0)
        breaks(1, 1, 1)
        breaks(80, 0, 0)
        assertBrokenBy(Breaker.PLAYER, Breaker.WITH_PLAYER, Breaker.OTHER)
    }

    @Test
    fun `pickobulus breaks with the player within 3 blocks of the block they right-clicked`() {
        sorter.use(0, 0, 0)
        idle(4)
        breaks(0)
        breaks(3, 0, 0)
        breaks(0, 2, 2)
        breaks(9, 0, 0)
        assertBrokenBy(Breaker.WITH_PLAYER, Breaker.WITH_PLAYER, Breaker.WITH_PLAYER, Breaker.OTHER)
    }

    @Test
    fun `only a block that became bedrock broke`() {
        sorter.changed(0, 0, 0, BEDROCK, END_STONE)
        sorter.changed(1, 0, 0, "minecraft:air", BEDROCK)
        sorter.changed(2, 0, 0, BEDROCK, BEDROCK)
        sorter.changed(3, 0, 0, END_STONE, "minecraft:air")
        assertTrue(sorter.tick().isEmpty())
    }

    @Test
    fun `an ender node breaks as the sandstone it is`() {
        breaks(0, was = "minecraft:sandstone")
        assertEquals("minecraft:sandstone", sorter.tick().single().block)
    }
}
