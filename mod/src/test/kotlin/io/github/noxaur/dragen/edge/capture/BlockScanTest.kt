package io.github.noxaur.dragen.edge.capture

import io.github.noxaur.dragen.core.capture.ChangeLog
import io.github.noxaur.dragen.core.capture.RawRecord
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertTrue

class BlockScanTest {
    @Test
    fun `a queued pass is dropped when the world changes and nothing is published`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val offered = mutableListOf<RawRecord>()
        var now = 0L
        var step = 1_000_000L
        val scan = BlockScan(ChangeLog { type, _ -> offered += type }) { now += step; now }
        val player = unsafe.allocateInstance(LocalPlayer::class.java) as LocalPlayer
        val position = net.minecraft.world.entity.Entity::class.java.getDeclaredField("chunkPosition").apply { isAccessible = true }
        position.set(player, ChunkPos(0, 0))
        val first = level()
        repeat(20) { scan.tick(first, player) }
        step = 0
        scan.tick(level(), player)
        assertTrue(offered.isEmpty(), "A warp published $offered")
        scan.detach()
        assertTrue(offered.isEmpty(), "Detach published a partial pass: $offered")
    }
}

private class StubChunkCache(level: ClientLevel) : ClientChunkCache(level, 1) {
    override fun getChunk(x: Int, z: Int, status: ChunkStatus, creating: Boolean): LevelChunk? = null
}

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

private fun level(): ClientLevel {
    val level = unsafe.allocateInstance(ClientLevel::class.java) as ClientLevel
    val cache = unsafe.allocateInstance(StubChunkCache::class.java) as StubChunkCache
    val field = ClientLevel::class.java.getDeclaredField("chunkSource")
    field.isAccessible = true
    field.set(level, cache)
    return level
}
