package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.HeadPlaced
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * What the server sends that leaves nothing behind to poll: `ServerPacketsMixin` calls these on the client thread
 * as each packet is handled, so an event keeps the time and the order it arrived in.
 */
object Packets {
    fun actionBar(text: Component) {
        val game = DsModClient.game
        if (game.wants(ActionBar::class.java)) game.post(System.currentTimeMillis(), ActionBar(text.toText()))
    }

    // The heads already posted from a chunk, so a chunk that loads again posts nothing twice.
    private val headsSeen = HashSet<Long>()

    fun worldChanged() = headsSeen.clear()

    fun block(pos: BlockPos, state: BlockState) {
        val game = DsModClient.game
        if (state.block !== Blocks.PLAYER_HEAD || !game.wants(HeadPlaced::class.java)) return
        headsSeen += pos.asLong()
        game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))
    }

    /**
     * A head that was placed before its chunk reached the client came with no block update, so a loaded chunk is
     * read for the heads it holds. Only a head with a skin counts: a server builds its markers from those, and the
     * plain heads that decorate a map would be posted as markers otherwise.
     */
    fun chunk(chunk: LevelChunk) {
        val game = DsModClient.game
        if (chunk.blockEntities.isEmpty() || !game.wants(HeadPlaced::class.java)) return
        for ((pos, entity) in chunk.blockEntities) {
            if (entity !is SkullBlockEntity || entity.ownerProfile == null || entity.blockState.block !== Blocks.PLAYER_HEAD) continue
            if (headsSeen.add(pos.asLong())) game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))
        }
    }

    fun entityEvent(packet: ClientboundEntityEventPacket) {
        val game = DsModClient.game
        if (packet.eventId != EntityEvent.DEATH || !game.wants(EntityDied::class.java)) return
        val entity = Minecraft.getInstance().level?.let(packet::getEntity) ?: return
        if (entity is Player) return
        game.post(
            System.currentTimeMillis(),
            EntityDied(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(), entity.name.toText(), entity.x, entity.y, entity.z),
        )
    }
}
