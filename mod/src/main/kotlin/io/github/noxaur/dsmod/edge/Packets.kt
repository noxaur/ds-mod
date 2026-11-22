package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.PingMeasured
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.SystemMessage
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.util.Util
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

object Packets {
    private lateinit var game: Game

    fun bind(game: Game) {
        this.game = game
    }

    fun actionBar(text: Component) {
        if (game.wants(ActionBar::class.java)) game.post(System.currentTimeMillis(), ActionBar(text.toText()))
    }

    private var shown = true

    fun systemChat(text: Component, overlay: Boolean) {
        if (overlay) return actionBar(text)
        shown = !game.wants(SystemMessage::class.java) || game.post(System.currentTimeMillis(), SystemMessage(text.toText()))
    }

    fun shows() = shown

    fun systemChatHandled() {
        shown = true
    }

    fun time(gameTime: Long) {
        if (game.wants(ServerTime::class.java)) game.post(System.currentTimeMillis(), ServerTime(gameTime))
    }

    /** @keep The F3 network chart's requests are answered the same way, and count as well. The game handles the answer on the
     * network thread, so the round trip is read there, before it waits for a frame. */
    fun pong(sent: Long) {
        val ping = PingMeasured(Util.getMillis() - sent)
        Minecraft.getInstance().execute {
            if (game.wants(PingMeasured::class.java)) game.post(System.currentTimeMillis(), ping)
        }
    }

    /* @keep A head arrives with its chunk or as a block update in the first moments of a world, when the tab list has not
     * yet said which area this is. */
    private val waiting = LinkedHashMap<BlockPos, Int>()

    /** @keep The profile packet follows the block update by more than one tick, and a second is enough. */
    private const val BARE_SKULL_TICKS = 20

    fun worldChanged() = waiting.clear()

    fun block(pos: BlockPos, state: BlockState) {
        if (state.block === Blocks.PLAYER_HEAD) hold(pos.immutable())
    }

    private fun hold(pos: BlockPos) {
        val entity = Minecraft.getInstance().level?.getBlockEntity(pos) as? SkullBlockEntity
        if (entity?.ownerProfile != null) {
            waiting.remove(pos)
            head(pos)
            return
        }
        if (game.seesHeads() || game.wants(HeadPlaced::class.java)) waiting.putIfAbsent(pos, 0)
    }

    /** @keep Only a head with a skin counts: a server builds its markers from those, and the plain heads that decorate a map
     * would be posted as markers otherwise. */
    fun chunk(chunk: LevelChunk) {
        if (chunk.blockEntities.isEmpty()) return
        for ((pos, entity) in chunk.blockEntities) {
            if (entity is SkullBlockEntity && entity.ownerProfile != null && entity.blockState.block === Blocks.PLAYER_HEAD) head(pos.immutable())
        }
    }

    private fun head(pos: BlockPos) {
        if (game.seesHeads()) postHead(game, pos)
        if (!game.reaches(HeadPlaced::class.java) && game.wants(HeadPlaced::class.java)) waiting[pos] = 0
    }

    fun tick(client: Minecraft) {
        if (waiting.isEmpty()) return
        val level = client.level
        val each = waiting.entries.iterator()
        while (each.hasNext()) {
            val held = each.next()
            val pos = held.key
            val keep = if (level?.getBlockState(pos)?.block !== Blocks.PLAYER_HEAD) false else {
                val entity = level.getBlockEntity(pos) as? SkullBlockEntity
                when {
                    entity == null -> true
                    entity.ownerProfile == null -> (held.value + 1 < BARE_SKULL_TICKS).also { held.setValue(held.value + 1) }
                    game.seesHeads() -> {
                        postHead(game, pos)
                        false
                    }
                    else -> game.wants(HeadPlaced::class.java)
                }
            }
            if (!keep) each.remove()
        }
    }

    private fun Game.seesHeads() = reaches(HeadPlaced::class.java) || tap != null

    private fun postHead(game: Game, pos: BlockPos) = game.post(System.currentTimeMillis(), HeadPlaced(pos.x, pos.y, pos.z))

    fun entityEvent(packet: ClientboundEntityEventPacket) {
        if (packet.eventId != EntityEvent.DEATH || !game.wants(EntityDied::class.java)) return
        val entity = Minecraft.getInstance().level?.let(packet::getEntity) ?: return
        if (entity is Player) return
        game.post(
            System.currentTimeMillis(),
            EntityDied(entity.typeId, entity.name.toText(), entity.x, entity.y, entity.z),
        )
    }
}
