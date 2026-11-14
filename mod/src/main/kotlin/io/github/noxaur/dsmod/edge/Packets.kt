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
import net.minecraft.world.level.block.state.BlockState

/**
 * What the server sends that leaves nothing behind to poll: `ServerPacketsMixin` calls these on the client thread
 * as each packet is handled, so an event keeps the time and the order it arrived in.
 */
object Packets {
    fun actionBar(text: Component) {
        val game = DsModClient.game
        if (game.wants(ActionBar::class.java)) game.post(System.currentTimeMillis(), ActionBar(text.toText()))
    }

    fun block(pos: BlockPos, state: BlockState) {
        val game = DsModClient.game
        if (state.block !== Blocks.PLAYER_HEAD || !game.wants(HeadPlaced::class.java)) return
        game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))
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
