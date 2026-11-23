package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.BlockBroken
import io.github.noxaur.dsmod.core.game.BreakSorter
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.IdentityHashMap

object BlockBreaks {
    private lateinit var game: Game
    private val sorter = BreakSorter()
    private val ids = IdentityHashMap<Block, String>()

    fun bind(game: Game) {
        this.game = game
    }

    // @keep Called before the level holds the new state, so the block it replaces is still there.
    fun block(pos: BlockPos, state: BlockState) {
        if (!game.wants(BlockBroken::class.java)) return
        val was = Minecraft.getInstance().level?.getBlockState(pos) ?: return
        sorter.changed(pos.x, pos.y, pos.z, was.block.id, state.block.id)
    }

    // @keep A right click can start and end within one tick, so the use is heard as the game makes it, not read at the tick's end.
    @InputApproved(by = "noxaur", reaches = ["Refusals.useBlock"])
    fun register() {
        Refusals.useBlock().register { player, level, _, hit ->
            if (level.isClientSide && player === Minecraft.getInstance().player) sorter.use(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z)
            InteractionResult.PASS
        }
        Ticks.end { client ->
            val hit = client.hitResult
            if (client.gameMode?.isDestroying == true && hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                sorter.dig(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z)
            }
            val broken = sorter.tick()
            if (broken.isNotEmpty()) game.post(System.currentTimeMillis(), broken)
        }
    }

    private val Block.id: String get() = ids.getOrPut(this) { BuiltInRegistries.BLOCK.getKey(this).toString() }
}
