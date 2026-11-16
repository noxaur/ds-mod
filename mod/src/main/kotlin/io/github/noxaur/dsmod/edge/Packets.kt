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

    // Heads seen before any feature could be handed them. A head arrives with its chunk or as a block update in the
    // first moments of a world, when the tab list has not yet said which area this is.
    private val waiting = LinkedHashSet<BlockPos>()

    fun worldChanged() = waiting.clear()

    fun block(pos: BlockPos, state: BlockState) {
        if (state.block === Blocks.PLAYER_HEAD) hold(pos.immutable())
    }

    /**
     * The block update is handled before the skull's profile packet, so a head with no profile yet is held for [tick].
     * [tick] posts it once the profile is there and drops it if the skull is still bare, the same rule [chunk] keeps.
     */
    private fun hold(pos: BlockPos) {
        val entity = Minecraft.getInstance().level?.getBlockEntity(pos) as? SkullBlockEntity
        if (entity?.ownerProfile != null) {
            head(pos)
            return
        }
        val game = DsModClient.game
        if (game.reaches(HeadPlaced::class.java) || game.tap != null || game.wants(HeadPlaced::class.java)) waiting += pos
    }

    /**
     * A head that was placed before its chunk reached the client came with no block update, so a loaded chunk is
     * read for the heads it holds. Only a head with a skin counts: a server builds its markers from those, and the
     * plain heads that decorate a map would be posted as markers otherwise.
     */
    fun chunk(chunk: LevelChunk) {
        if (chunk.blockEntities.isEmpty()) return
        for ((pos, entity) in chunk.blockEntities) {
            if (entity is SkullBlockEntity && entity.ownerProfile != null && entity.blockState.block === Blocks.PLAYER_HEAD) head(pos.immutable())
        }
    }

    private fun head(pos: BlockPos) {
        val game = DsModClient.game
        val reaches = game.reaches(HeadPlaced::class.java)
        // A capture records the head when it is seen, whether or not a feature is there to take it.
        if (reaches || game.tap != null) game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))
        if (!reaches && game.wants(HeadPlaced::class.java)) waiting += pos
    }

    /** Posts the heads that were held back, once a feature can be handed them and the skull has a skin. */
    fun tick(client: Minecraft) {
        if (waiting.isEmpty()) return
        val game = DsModClient.game
        val level = client.level
        val still = LinkedHashSet<BlockPos>()
        for (pos in waiting) {
            if (level?.getBlockState(pos)?.block !== Blocks.PLAYER_HEAD) continue
            val entity = level.getBlockEntity(pos) as? SkullBlockEntity
            when {
                entity == null -> still += pos
                entity.ownerProfile == null -> {}
                game.reaches(HeadPlaced::class.java) || game.tap != null ->
                    game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))
                game.wants(HeadPlaced::class.java) -> still += pos
            }
        }
        waiting.clear()
        waiting += still
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
