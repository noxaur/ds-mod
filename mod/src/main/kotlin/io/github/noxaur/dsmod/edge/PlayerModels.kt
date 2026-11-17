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

/**
 * Player Model: the size, tilt, spin and pose of a player's model in the world and the inventory. It changes only
 * what is drawn, so the hitbox, the camera and what other players see stay the game's own. First person draws no
 * model, and its hand is drawn apart from it, so nothing here reaches first person.
 */
object PlayerModels {
    private val settings: PlayerModel get() = DsModClient.feature(PlayerModel::class.java)

    /**
     * Whether the feature changes [state]'s model: the player's own, or every player's in the world. A model a screen
     * draws for itself, such as the profile viewer's, has an id below 1, which the world never gives, and is left alone.
     */
    private fun applies(state: AvatarRenderState): Boolean = DsModClient.runs<PlayerModel>() &&
        (state.id == Minecraft.getInstance().player?.id || settings.target.value == ModelTarget.EVERYONE && state.id > 0)

    /**
     * The way the model faces: while it spins, where the spin has got to and nowhere else, so walking, looking around
     * and the inventory's following of the mouse neither speed it up nor hold it back. Otherwise the game's [bodyRot].
     */
    fun facing(state: AvatarRenderState, bodyRot: Float): Float =
        if (spinning && applies(state)) ModelShape.spin(System.nanoTime(), settings.spin.value) else bodyRot

    /** Whether the player's own model spins, so its facing is the spin's. */
    val spinning: Boolean get() = DsModClient.runs<PlayerModel>() && settings.spin.value != 0f

    /** How many times its own height the player's own model is drawn across its longest side, or 1 while the feature is off. */
    val reach: Float get() {
        if (!DsModClient.runs<PlayerModel>()) return 1f
        val s = settings
        return s.size.value * maxOf(s.width.value, s.height.value, s.depth.value)
    }

    /** How many times its own height the player's own model is drawn tall, or 1 while the feature is off. */
    val tall: Float get() = if (DsModClient.runs<PlayerModel>()) settings.let { it.size.value * it.height.value } else 1f

    /**
     * Runs after the game has turned the model to face where the player faces, with the feet at the origin and the
     * face toward -z, turned by [facing] while it spins. Every turn is about the point the model stands on, so a tilted
     * model leans with its feet on the ground and spins like a top, and a flipped one stands on its head and leans
     * from there.
     */
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
            // The pose is already scaled by the entity's own scale, which the bounding box includes.
            pose.translate(0f, state.boundingBoxHeight / state.scale * height, 0f)
            pose.mulPose(Axis.ZP.rotationDegrees(180f))
        }
        val width = size * s.width.value
        val depth = size * s.depth.value
        if (width != 1f || height != 1f || depth != 1f) pose.scale(width, height, depth)
    }

    /**
     * Holds the limbs in the chosen pose, after the game has posed them for this frame, and turns the head with the
     * body while it spins, though it still looks up and down. The armor's models are posed from the same state, so the
     * armor and the held items follow.
     */
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
