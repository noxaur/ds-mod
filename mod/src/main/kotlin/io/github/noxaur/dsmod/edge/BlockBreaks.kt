package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.BlockBroken
import io.github.noxaur.dsmod.core.game.BreakSorter
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
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

    fun register() = Ticks.end { client ->
        val hit = client.hitResult
        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
            val pos = hit.blockPos
            if (client.gameMode?.isDestroying == true) sorter.dig(pos.x, pos.y, pos.z)
            if (client.options.keyUse.isDown) sorter.use(pos.x, pos.y, pos.z)
        }
        val broken = sorter.tick()
        if (broken.isNotEmpty()) game.post(System.currentTimeMillis(), broken)
    }

    private val Block.id: String get() = ids.getOrPut(this) { BuiltInRegistries.BLOCK.getKey(this).toString() }
}
