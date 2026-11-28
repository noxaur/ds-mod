package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.api.Box
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.world.BlockArea
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Part
import io.github.noxaur.dragen.api.boss.dragon.SeenBossBar
import io.github.noxaur.dragen.api.boss.dragon.SeenEntity
import io.github.noxaur.dragen.api.boss.dragon.Watch
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

object DragonFeeds {
    lateinit var system: DragonSystem
        private set

    private var watched = Watch.NOTHING
    private val names = HashMap<BlockState, String>()
    private val types = HashMap<EntityType<*>, String>()

    fun bind(system: DragonSystem) {
        this.system = system
        watched = Watch.NOTHING
    }

    private fun name(state: BlockState) = names.getOrPut(state) { BlockStateParser.serialize(state) }

    private fun id(state: BlockState) = BuiltInRegistries.BLOCK.getKey(state.block).toString()

    fun chat(text: Component) = system.feed.chat(text.string)

    fun tabList(rows: List<Component>) = system.feed.tabList(rows.map { it.string })

    fun sidebar(lines: List<Component>) = system.feed.sidebar(lines.map { it.string })

    fun worldChanged() {
        system.feed.worldChanged()
        watched = Watch.NOTHING
    }

    fun block(pos: BlockPos, state: BlockState) {
        val watch = system.feed.watch
        if (watch === Watch.NOTHING) return
        if (id(state) in watch.blocks || watch.areas.any { it.contains(pos.x, pos.y, pos.z) }) system.feed.block(pos.x, pos.y, pos.z, name(state))
    }

    fun chunk(chunk: LevelChunk) {
        val watch = system.feed.watch
        if (watch.blocks.isNotEmpty()) scan(chunk, watch.blocks)
        for (area in watch.areas) clip(area, chunk)?.let { scan(chunk, it) }
    }

    fun unloaded(chunk: LevelChunk) {
        if (system.feed.watch !== Watch.NOTHING) system.feed.forget(bounds(chunk))
    }

    private fun bounds(chunk: LevelChunk) = BlockArea(chunk.pos.minBlockX, chunk.minY, chunk.pos.minBlockZ, chunk.pos.maxBlockX, chunk.maxY, chunk.pos.maxBlockZ)

    private fun clip(area: BlockArea, chunk: LevelChunk): BlockArea? {
        val bounds = bounds(chunk)
        val clipped = BlockArea(
            maxOf(area.minX, bounds.minX), maxOf(area.minY, bounds.minY), maxOf(area.minZ, bounds.minZ),
            minOf(area.maxX, bounds.maxX), minOf(area.maxY, bounds.maxY), minOf(area.maxZ, bounds.maxZ),
        )
        return clipped.takeIf { it.minX <= it.maxX && it.minY <= it.maxY && it.minZ <= it.maxZ }
    }

    private fun scan(chunk: LevelChunk, blocks: Set<String>) = chunk.eachBlock({ id(it) in blocks }) { x, y, z, state -> system.feed.block(x, y, z, name(state)) }

    private fun scan(blocks: BlockGetter, area: BlockArea) {
        val pos = BlockPos.MutableBlockPos()
        for (x in area.minX..area.maxX) for (z in area.minZ..area.maxZ) for (y in area.minY..area.maxY) {
            val state = blocks.getBlockState(pos.set(x, y, z))
            if (!state.isAir) system.feed.block(x, y, z, name(state))
        }
    }

    private fun changed(level: ClientLevel, watch: Watch) {
        if (watch.blocks.isNotEmpty() && watch.blocks != watched.blocks) {
            val player = Minecraft.getInstance().player ?: return
            val radius = Minecraft.getInstance().options.renderDistance().get()
            val center = player.chunkPosition()
            for (cx in center.x - radius..center.x + radius) for (cz in center.z - radius..center.z + radius) {
                val chunk = level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false) as? LevelChunk ?: continue
                scan(chunk, watch.blocks)
            }
        }
        for (area in watch.areas - watched.areas.toSet()) scan(level, area)
        watched = watch
    }

    fun tick(client: Minecraft) {
        val level = client.level ?: return
        val feed = system.feed
        var watch = feed.watch
        repeat(SETTLE_PASSES) {
            if (watch == watched) return@repeat
            changed(level, watch)
            watch = feed.watch
        }
        if (watch.entities.isNotEmpty()) {
            val seen = ArrayList<SeenEntity>()
            for (entity in level.entitiesForRendering()) {
                val type = types.getOrPut(entity.type) { entity.typeId }
                if (type !in watch.entities) continue
                val parts = (entity as? EnderDragon)?.subEntities?.map { Part(it.name, Box(Vec(it.x, it.y + it.bbHeight / 2.0, it.z), it.bbWidth.toDouble(), it.bbHeight.toDouble())) }.orEmpty()
                seen += SeenEntity(entity.id, type, Vec(entity.x, entity.y, entity.z), entity.yRot.toDouble(), entity.xRot.toDouble(), entity.displayName.string, parts)
            }
            feed.entities(seen)
            feed.bossBars(client.gui.hud.bossOverlay.events.values.map { SeenBossBar(it.name.string, it.progress.toDouble()) })
        }
        feed.tick(level.levelData.gameTime)
    }

    /** @keep Scanning can find the altar, which adds its area to the watch, so a start settles in at most this many passes. */
    private const val SETTLE_PASSES = 3
}
