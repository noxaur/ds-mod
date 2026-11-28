package io.github.noxaur.dragen.api

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.world.BlockArea
import io.github.noxaur.dragen.api.boss.dragon.Cycle
import io.github.noxaur.dragen.api.boss.dragon.DragonFeed
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AltarChunksTest {
    private val ring = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)

    private fun DragonFeed.altar() {
        ring.forEachIndexed { i, (x, z) -> block(x, 10, z, "minecraft:end_portal_frame[eye=${i < 3},facing=north]") }
        for (y in 10..46) block(0, y, 0, "minecraft:purple_stained_glass")
    }

    @Test
    fun `an unloaded altar is forgotten and read again when its chunk returns`() {
        val system = DragonSystem(ServerClock())
        val feed = system.feed
        system.api.watch()
        feed.tabList(listOf("Area: The End"))
        feed.altar()
        feed.tick(1)
        val seen = system.api.cycle
        assertIs<Cycle.Respawning>(seen, "an altar with its tower and no egg is rebuilding")

        feed.forget(BlockArea(-16, -64, -16, -1, 319, -1))
        feed.tick(2)
        assertEquals(Cycle.Unknown, system.api.cycle, "the altar's chunk unloaded, so the API no longer claims to see it")

        feed.altar()
        feed.tick(3)
        assertEquals(seen, system.api.cycle, "the chunk came back, so the API reads the same altar again")
    }
}
