package io.github.noxaur.dragen.edge

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.noxaur.dragen.core.feature.visuals.animations.Animations
import io.github.noxaur.dragen.core.hand.HandStyle
import io.github.noxaur.dragen.core.hand.HeldItem
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.model.effects.SpearAnimations
import net.minecraft.client.resources.model.cuboid.ItemTransform
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ProjectileWeaponItem
import net.minecraft.world.item.SwingAnimationType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

private const val PI = Math.PI.toFloat()
private const val DEGREES = PI / 180

object HeldItems {
    private lateinit var settings: Animations

    fun bind(animations: Animations) {
        settings = animations
    }

    private val on: Boolean get() = settings.feature.runs
    private val style: HandStyle get() = settings.style.value

    private val noDipAfterHit: Boolean get() = on && (settings.noEquipReset.value || style == HandStyle.V1_7)

    private fun side(arm: HumanoidArm) = if (arm == HumanoidArm.RIGHT) 1 else -1

    private val drawn = arrayOf(ItemStack.EMPTY, ItemStack.EMPTY)

    fun canBlock(stack: ItemStack) = stack.`is`(ItemTags.SWORDS)

    private fun blocking(stack: ItemStack): Boolean {
        if (!on || !settings.blocking.value || style == HandStyle.STAB) return false
        return Minecraft.getInstance().options.keyUse.isDown && canBlock(stack)
    }

    fun blocks(entity: Any, arm: HumanoidArm): Boolean {
        val player = Minecraft.getInstance().player
        return entity === player && arm == player.mainArm && blocking(player.mainHandItem)
    }

    fun place(pose: PoseStack, arm: HumanoidArm, stack: ItemStack) {
        drawn[arm.ordinal] = stack
        if (!on || stack.isEmpty || stack.has(DataComponents.MAP_ID)) return
        val s = settings
        val x = s.itemX.value
        val y = s.itemY.value
        val z = s.itemZ.value
        if (x != 0f || y != 0f || z != 0f) pose.translate(side(arm) * x, y, -z)
    }

    fun turnAndSize(pose: PoseStack, arm: HumanoidArm) {
        if (!on) return
        val s = settings
        val side = side(arm)
        val yaw = s.itemYaw.value
        val pitch = s.itemPitch.value
        val roll = s.itemRoll.value
        val size = s.itemSize.value
        if (yaw != 0f) pose.mulPose(Axis.YP.rotationDegrees(side * yaw))
        if (pitch != 0f) pose.mulPose(Axis.XP.rotationDegrees(pitch))
        if (roll != 0f) pose.mulPose(Axis.ZP.rotationDegrees(side * roll))
        if (size != 1f) pose.scale(size, size, size)
    }

    fun swing(pose: PoseStack, arm: HumanoidArm, attack: Float, spear: Boolean): Boolean {
        val player = Minecraft.getInstance().player
        val stack = drawn[arm.ordinal]
        return swing(pose, arm, attack, stack, spear, player != null && arm == player.mainArm && blocking(stack))
    }

    private fun swing(pose: PoseStack, arm: HumanoidArm, attack: Float, stack: ItemStack, spear: Boolean, blocks: Boolean): Boolean {
        if (!on) return false
        val s = settings
        val style = style
        val still = s.noSwing.value || s.noBowSwing.value && stack.item is ProjectileWeaponItem
        val reach = (if (s.scaleSwing.value) s.itemSize.value else 1f) * s.reach.value / 100
        val turn = s.turn.value / 100
        if (style == HandStyle.MODERN && !still && !blocks && reach == 1f && turn == 1f) return false
        val side = side(arm)
        val swung = if (still || blocks && style != HandStyle.V1_7) 0f else attack
        if (style == HandStyle.STAB || spear && style == HandStyle.MODERN) {
            stab(pose, side, arm, swung, reach, turn)
        } else {
            push(pose, side, swung, reach)
            arc(pose, side, swung, turn)
        }
        if (blocks) {
            pose.translate(side * -0.14142136f, 0.08f, 0.14142136f)
            pose.mulPose(Axis.XP.rotationDegrees(-102.25f))
            pose.mulPose(Axis.YP.rotationDegrees(side * 13.365f))
            pose.mulPose(Axis.ZP.rotationDegrees(side * 78.05f))
        }
        return true
    }

    private val stabbing = PoseStack()
    private val stabTurn = Quaternionf()
    private val noTurn = Quaternionf()

    private fun stab(pose: PoseStack, side: Int, arm: HumanoidArm, attack: Float, reach: Float, turn: Float) {
        if (reach == 1f && turn == 1f) return SpearAnimations.firstPersonAttack(attack, pose, side, arm)
        stabbing.setIdentity()
        SpearAnimations.firstPersonAttack(attack, stabbing, side, arm)
        val stab = stabbing.last().pose()
        pose.translate(stab.m30() * reach, stab.m31() * reach, stab.m32() * reach)
        pose.mulPose(noTurn.identity().slerp(stab.getNormalizedRotation(stabTurn), turn))
    }

    private fun push(pose: PoseStack, side: Int, attack: Float, reach: Float) {
        if (reach == 0f) return
        val root = sqrt(attack)
        pose.translate(reach * side * -0.4f * sin(root * PI), reach * 0.2f * sin(root * 2 * PI), reach * -0.2f * sin(attack * PI))
    }

    private fun arc(pose: PoseStack, side: Int, attack: Float, amount: Float) {
        if (amount == 0f) return
        val turn = sin(sqrt(attack) * PI) * amount
        pose.mulPose(Axis.YP.rotationDegrees(side * (45f - 20f * sin(attack * attack * PI) * amount)))
        pose.mulPose(Axis.ZP.rotationDegrees(side * -20f * turn))
        pose.mulPose(Axis.XP.rotationDegrees(-80f * turn))
        pose.mulPose(Axis.YP.rotationDegrees(side * -45f))
    }

    @JvmStatic
    var drawing = false

    fun hold(transform: ItemTransform, left: Boolean, pose: PoseStack.Pose): Boolean {
        if (!drawing || !on || style == HandStyle.MODERN || !isFlatSpriteInHand(transform)) return false
        pose.mulPose(if (style == HandStyle.V1_7) (if (left) SPRITE_HOLD_1_7_LEFT else SPRITE_HOLD_1_7_RIGHT) else if (left) SPRITE_HOLD_SPEAR_LEFT else SPRITE_HOLD_SPEAR_RIGHT)
        return true
    }

    val flipping: Boolean get() = drawing && on && settings.flip.value

    private val beforeHold = Matrix4f()
    private val afterHold = Matrix4f()
    private val grip = Vector3f()

    fun beforeHold(pose: PoseStack.Pose) {
        beforeHold.set(pose.pose())
    }

    fun flip(pose: PoseStack.Pose) {
        afterHold.set(pose.pose()).invert().transformPosition(beforeHold.getTranslation(grip))
        pose.translate(2 * grip.x, 0f, 0f)
        pose.scale(-1f, 1f, 1f)
    }

    private val mirrored = IdentityHashMap<BakedQuad, BakedQuad>()

    fun mirrored(quads: List<BakedQuad>): List<BakedQuad> {
        if (mirrored.size > MIRRORED_KEPT) mirrored.clear()
        return quads.map { quad ->
            mirrored.getOrPut(quad) {
                BakedQuad(
                    quad.position0(), quad.position3(), quad.position2(), quad.position1(),
                    quad.packedUV0(), quad.packedUV3(), quad.packedUV2(), quad.packedUV1(), quad.direction(), quad.materialInfo(),
                )
            }
        }
    }

    private const val MIRRORED_KEPT = 4096

    private fun isFlatSpriteInHand(t: ItemTransform): Boolean {
        val r = t.rotation()
        val at = t.translation()
        val s = t.scale()
        return near(r.x(), 0f) && near(abs(r.y()), 90f) && near(abs(r.z()), 25f) && r.y() * r.z() < 0 &&
            near(at.x(), 1.13f / 16) && near(at.y(), 3.2f / 16) && near(at.z(), 1.13f / 16) && near(s.x(), 0.68f) && near(s.y(), 0.68f) && near(s.z(), 0.68f)
    }

    private fun near(a: Float, b: Float) = abs(a - b) < 0.001f

    private val SPRITE_HOLD_1_7_RIGHT = Matrix4f()
        .rotateY(45 * DEGREES).translate(0f, -0.12f, 0f).scale(0.6f)
        .rotateY(50 * DEGREES).rotateZ(-25 * DEGREES).translate(-0.9375f, -0.0625f, 0f)
        .translate(1f, 0f, 7.5f / 16).rotateY(PI)

    private val SPRITE_HOLD_SPEAR_RIGHT = Matrix4f()
        .translate(3.13f / 16, 2f / 16, 0.13f / 16).rotateXYZ(-20 * DEGREES, 90 * DEGREES, -35 * DEGREES).scale(1.36f, 1.36f, 0.68f)
        .translate(-0.5f, -0.5f, -0.5f).translate(1f, 0f, 1f).rotateY(PI)

    private val SPRITE_HOLD_1_7_LEFT = mirroredForLeftHand(SPRITE_HOLD_1_7_RIGHT)
    private val SPRITE_HOLD_SPEAR_LEFT = mirroredForLeftHand(SPRITE_HOLD_SPEAR_RIGHT)

    private fun mirroredForLeftHand(right: Matrix4f) = Matrix4f().scale(-1f, 1f, 1f).mul(right).translate(0f, 0f, 1f).scale(1f, 1f, -1f)

    private val SPEAR_TO_COPY by lazy { ItemStack(Items.DIAMOND_SPEAR) }

    fun previewTicks(stack: ItemStack): Int {
        val swung = if (on && style == HandStyle.STAB) SPEAR_TO_COPY else stack
        return if (on) HeldItem.swingTicks(swung.swingAnimation.duration(), settings.swingSpeed.value) else swung.swingAnimation.duration()
    }

    fun swingTicks(entity: LivingEntity, ticks: Int): Int {
        val client = Minecraft.getInstance()
        if (entity !== client.player || !client.options.cameraType.isFirstPerson || !on) return ticks
        val s = settings
        val own = entity.getItemInHand(entity.swingingArm ?: InteractionHand.MAIN_HAND).swingAnimation.duration()
        val swung = if (style == HandStyle.STAB) SPEAR_TO_COPY.swingAnimation.duration() else own
        val base = if (s.ignoreHaste.value) swung else ticks - own + swung
        return HeldItem.swingTicks(base, s.swingSpeed.value)
    }

    fun swapDepth(stack: ItemStack, scale: Float): Float =
        if (on && style == HandStyle.STAB && !stack.isEmpty && !stack.has(DataComponents.MAP_ID)) {
            Minecraft.getInstance().itemModelResolver.swapAnimationScale(SPEAR_TO_COPY)
        } else {
            scale
        }

    fun swapScale(scale: Float): Float = if (noDipAfterHit) 1f else scale

    fun replaceAtOnce(vanilla: Boolean, shown: ItemStack, next: ItemStack): Boolean = vanilla || on && settings.noEquipReset.value && ItemStack.isSameItem(shown, next)

    fun dipOnUse(): Boolean = !(on && settings.noEquipReset.value)

    fun firstPerson(pose: PoseStack, arm: HumanoidArm, stack: ItemStack, attack: Float) {
        val side = side(arm)
        place(pose, arm, stack)
        pose.translate(side * 0.56f, -0.52f, -0.72f)
        val spear = stack.swingAnimation.type() == SwingAnimationType.STAB
        if (!swing(pose, arm, attack, stack, spear, blocks = false)) {
            if (spear) SpearAnimations.firstPersonAttack(attack, pose, side, arm) else {
                push(pose, side, attack, 1f)
                arc(pose, side, attack, 1f)
            }
        }
        if (attack < CHARGED_CROSSBOW_AT_REST && stack.`is`(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            pose.translate(side * -0.641864f, 0f, 0f)
            pose.mulPose(Axis.YP.rotationDegrees(side * 10f))
        }
        turnAndSize(pose, arm)
    }

    private const val CHARGED_CROSSBOW_AT_REST = 0.001f
}
