package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus

/** Block kinds the dungeon puzzles and the End read. Add a block here, or a tag to [watchedTags], to record it too. */
private val watchedBlocks: Set<Block> = setOf(
    Blocks.OBSIDIAN, Blocks.SEA_LANTERN, Blocks.PRISMARINE, Blocks.ICE, Blocks.PACKED_ICE,
    Blocks.POLISHED_ANDESITE, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.END_PORTAL_FRAME,
)
private val watchedTags = listOf(BlockTags.BUTTONS)

private fun watched(state: BlockState) = state.block in watchedBlocks || watchedTags.any { state.`is`(it) }

private const val RADIUS_CHUNKS = 4
private const val PERIOD_TICKS = 20
private const val BUDGET_NANOS = 500_000L

/**
 * Records `blockstates` (the watched blocks) and `skulls` (skull blocks with their profile) in the chunks
 * around the player, including what was there before the capture started. A pass over the chunks nearest
 * first takes at most [BUDGET_NANOS] a tick, and a new pass starts [PERIOD_TICKS] after the last one ended.
 */
class BlockScan(private val changes: ChangeLog) {
    private val queue = ArrayDeque<ChunkPos>()
    private val states = HashMap<String, JsonElement>()
    private val skulls = HashMap<String, JsonElement>()
    private val serialized = HashMap<BlockState, JsonPrimitive>()
    private var idle = 0

    fun tick(level: ClientLevel, player: Player) {
        if (queue.isEmpty()) {
            if (++idle < PERIOD_TICKS) return
            idle = 0
            val center = player.chunkPosition()
            queue += (-RADIUS_CHUNKS..RADIUS_CHUNKS)
                .flatMap { dx -> (-RADIUS_CHUNKS..RADIUS_CHUNKS).map { dz -> dx to dz } }
                .sortedBy { (dx, dz) -> dx * dx + dz * dz }
                .map { (dx, dz) -> ChunkPos(center.x() + dx, center.z() + dz) }
            states.clear()
            skulls.clear()
        }
        val deadline = System.nanoTime() + BUDGET_NANOS
        do scan(level, queue.removeFirst()) while (queue.isNotEmpty() && System.nanoTime() < deadline)
        if (queue.isEmpty()) {
            changes.offerEntries("blockstates", JsonObject(emptyMap()), states)
            changes.offerEntries("skulls", JsonObject(emptyMap()), skulls)
        }
    }

    private fun scan(level: ClientLevel, pos: ChunkPos) {
        val chunk = level.chunkSource.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) ?: return
        chunk.blockEntities.values.filterIsInstance<SkullBlockEntity>().forEach { skull ->
            skulls["${skull.blockPos.x},${skull.blockPos.y},${skull.blockPos.z}"] = buildJsonObject {
                put("state", BlockStateParser.serialize(skull.blockState))
                skull.ownerProfile?.let { put("profile", it.toCaptureJson()) }
                skull.noteBlockSound?.let { put("noteBlockSound", it.toString()) }
            }
        }
        chunk.sections.forEachIndexed { index, section ->
            if (section.hasOnlyAir() || !section.maybeHas(::watched)) return@forEachIndexed
            val minY = chunk.minY + index * 16
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val state = section.getBlockState(x, y, z)
                if (watched(state)) {
                    states["${pos.minBlockX + x},${minY + y},${pos.minBlockZ + z}"] =
                        serialized.getOrPut(state) { JsonPrimitive(BlockStateParser.serialize(state)) }
                }
            }
        }
    }
}
