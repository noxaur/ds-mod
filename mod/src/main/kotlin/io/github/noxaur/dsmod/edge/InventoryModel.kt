package io.github.noxaur.dsmod.edge

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.Entity

/** A model drawn in a box on a screen, as the inventory draws the player. */
object InventoryModel {
    /** The inventory's own offset, which keeps the feet off the bottom of the box. */
    const val Y_OFFSET = 0.0625f

    /**
     * [entity] as the inventory draws it: with no shadow, outline or name, at the game's own size, looking ahead and
     * turned [yaw] degrees from facing the viewer. A screen that wants the name writes it beside the box.
     */
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
