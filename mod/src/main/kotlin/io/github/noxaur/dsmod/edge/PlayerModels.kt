package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.noxaur.dsmod.core.feature.general.PlayerModel
import io.github.noxaur.dsmod.core.player.ModelShape
import io.github.noxaur.dsmod.core.player.ModelTarget
import io.github.noxaur.dsmod.core.player.Turn
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.entity.state.AvatarRenderState

private const val RADIANS = (Math.PI / 180).toFloat()

object PlayerModels {
    private lateinit var settings: PlayerModel

    fun bind(playerModel: PlayerModel) {
        settings = playerModel
    }

    /** @keep A model a screen draws for itself, such as the profile viewer's, has an id below 1, which the world never gives, and is left alone. */
    private fun applies(state: AvatarRenderState): Boolean = settings.feature.runs &&
        (state.id == Minecraft.getInstance().player?.id || settings.target.value == ModelTarget.EVERYONE && state.id > 0)

    fun facing(state: AvatarRenderState, bodyRot: Float): Float =
        if (spinning && applies(state)) ModelShape.spin(System.nanoTime(), settings.spin.value) else bodyRot

    val spinning: Boolean get() = settings.feature.runs && settings.spin.value != 0f

    val reach: Float get() {
        if (!settings.feature.runs) return 1f
        val s = settings
        return s.size.value * maxOf(s.width.value, s.height.value, s.depth.value)
    }

    val tall: Float get() = if (settings.feature.runs) settings.let { it.size.value * it.height.value } else 1f

    fun shape(state: AvatarRenderState, pose: PoseStack) {
        if (!applies(state)) return
        val s = settings
        val forward = s.tiltForward.value
        val sideways = s.tiltSideways.value
        if (forward != 0f) pose.mulPose(Axis.XP.rotationDegrees(-forward))
        if (sideways != 0f) pose.mulPose(Axis.ZP.rotationDegrees(-sideways))
        val size = s.size.value
        val height = size * s.height.value
        if (s.upsideDown.value) {
            // @keep The pose is already scaled by the entity's own scale, which the bounding box includes.
            pose.translate(0f, state.boundingBoxHeight / state.scale * height, 0f)
            pose.mulPose(Axis.ZP.rotationDegrees(180f))
        }
        val width = size * s.width.value
        val depth = size * s.depth.value
        if (width != 1f || height != 1f || depth != 1f) pose.scale(width, height, depth)
    }

    fun pose(model: HumanoidModel<*>, state: AvatarRenderState) {
        val limbs = settings.pose.value.limbs
        if (limbs == null && !spinning || !applies(state)) return
        if (spinning) model.head.yRot = 0f
        if (limbs == null) return
        turn(model.rightArm, limbs.rightArm)
        turn(model.leftArm, limbs.leftArm)
        turn(model.rightLeg, limbs.rightLeg)
        turn(model.leftLeg, limbs.leftLeg)
    }

    private fun turn(part: ModelPart, turn: Turn) {
        part.xRot = turn.x * RADIANS
        part.yRot = turn.y * RADIANS
        part.zRot = turn.z * RADIANS
    }
}
