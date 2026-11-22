package io.github.noxaur.dsmod.edge

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.Entity

object InventoryModel {
    const val Y_OFFSET = 0.0625f

    fun state(entity: Entity, yaw: Float): EntityRenderState {
        val state = Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity).createRenderState(entity, 1f)
        state.shadowPieces.clear()
        state.outlineColor = 0
        state.nameTag = null
        state.scoreText = null
        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f + yaw
            state.yRot = 0f
            state.xRot = 0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1f
        }
        return state
    }
}
