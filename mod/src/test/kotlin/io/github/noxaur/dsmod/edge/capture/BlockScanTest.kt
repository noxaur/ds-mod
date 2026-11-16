package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.capture.ChangeLog
import io.github.noxaur.dsmod.core.capture.RawRecord
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
        val scan = BlockScan(ChangeLog { type, _ -> offered += type })
        val player = unsafe.allocateInstance(LocalPlayer::class.java) as LocalPlayer
        val position = net.minecraft.world.entity.Entity::class.java.getDeclaredField("chunkPosition").apply { isAccessible = true }
        position.set(player, ChunkPos(0, 0))
        val first = level(slow = true)
        repeat(20) { scan.tick(first, player) }
        scan.tick(level(slow = false), player)
        assertTrue(offered.isEmpty(), "A warp published $offered")
        scan.detach()
        assertTrue(offered.isEmpty(), "Detach published a partial pass: $offered")
    }
}

private class StubChunkCache(level: ClientLevel) : ClientChunkCache(level, 1) {
    var slow: Boolean = false

    override fun getChunk(x: Int, z: Int, status: ChunkStatus, creating: Boolean): LevelChunk? {
        if (slow) Thread.sleep(2)
        return null
    }
}

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

private fun level(slow: Boolean): ClientLevel {
    val level = unsafe.allocateInstance(ClientLevel::class.java) as ClientLevel
    val cache = unsafe.allocateInstance(StubChunkCache::class.java) as StubChunkCache
    cache.slow = slow
    val field = ClientLevel::class.java.getDeclaredField("chunkSource")
    field.isAccessible = true
    field.set(level, cache)
    return level
}
