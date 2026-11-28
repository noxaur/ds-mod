package io.github.noxaur.dragen.edge

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess

fun ChunkAccess.eachBlock(match: (BlockState) -> Boolean, found: (x: Int, y: Int, z: Int, state: BlockState) -> Unit) {
    sections.forEachIndexed { index, section ->
        if (section.hasOnlyAir() || !section.maybeHas(match)) return@forEachIndexed
        val minY = minY + index * 16
        for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
            val state = section.getBlockState(x, y, z)
            if (match(state)) found(pos.minBlockX + x, minY + y, pos.minBlockZ + z, state)
        }
    }
}
