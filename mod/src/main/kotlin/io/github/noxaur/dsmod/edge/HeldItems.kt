package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.hand.Ease
import io.github.noxaur.dsmod.core.hand.HeldItem
import io.github.noxaur.dsmod.core.hand.SwingStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.model.effects.SpearAnimations
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ProjectileWeaponItem
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI = Math.PI.toFloat()

/** How much of the game's swing arc the 1.7 block-hit keeps. */
private const val BLOCK_ARC = 0.3f

/** How far the block-hit styles raise the game's blocking pose, which sits behind the hotbar. */
private const val BLOCK_LIFT = 0.12f

/**
 * Animations: how the player's own held item swings and where it sits in first person. The mixins ask this object,
 * and it leaves the game's own pose whenever the feature is off or a setting is at its default. It changes only what
 * is drawn: the game sends the swing on every click whatever the swing looks like.
 */
object HeldItems {
    private val settings: DsModConfig.General get() = DsModClient.settings.general
    private val on: Boolean get() = DsModClient.active && settings.animations
    private val steady: Boolean get() = on && settings.noEquipReset

    /** How far into the block-hit pose the main hand is. */
    private val block = Ease()

    private fun side(arm: HumanoidArm) = if (arm == HumanoidArm.RIGHT) 1 else -1

    /** What the player holds in the hand on [arm]'s side. */
    private fun held(arm: HumanoidArm): ItemStack {
        val player = Minecraft.getInstance().player ?: return ItemStack.EMPTY
        return if (arm == player.mainArm) player.mainHandItem else player.offhandItem
    }

    /**
     * Moves the item before the arm puts it in the hand, so X moves it across the screen rather than along the
     * blade, and outward for either hand. The empty hand and a map are not an item to move.
     */
    fun place(pose: PoseStack, arm: HumanoidArm, stack: ItemStack) {
        if (!on || stack.isEmpty || stack.has(DataComponents.MAP_ID)) return
        val s = settings
        if (s.itemX != 0f || s.itemY != 0f || s.itemZ != 0f) pose.translate(side(arm) * s.itemX, s.itemY, -s.itemZ)
    }

    /**
     * Runs just before the item is drawn, after every swing and use pose, so the item turns and grows about the grip
     * and stays in the hand. Yaw and roll are mirrored for the left hand, as the hands are.
     */
    fun turnAndSize(pose: PoseStack, arm: HumanoidArm) {
        if (!on) return
        val s = settings
        val side = side(arm)
        if (s.itemYaw != 0f) pose.mulPose(Axis.YP.rotationDegrees(side * s.itemYaw))
        if (s.itemPitch != 0f) pose.mulPose(Axis.XP.rotationDegrees(s.itemPitch))
        if (s.itemRoll != 0f) pose.mulPose(Axis.ZP.rotationDegrees(side * s.itemRoll))
        if (s.itemSize != 1f) pose.scale(s.itemSize, s.itemSize, s.itemSize)
    }

    /**
     * Draws this frame's swing in place of the game's, and says whether it did. Every style is the resting pose at
     * [attack] 0, as the game's own swing is. A [spear] keeps its own stab under Vanilla.
     */
    fun swing(pose: PoseStack, arm: HumanoidArm, attack: Float, spear: Boolean): Boolean {
        if (!on) return false
        val s = settings
        if (s.noSwing || s.noBowSwing && held(arm).item is ProjectileWeaponItem) return true
        val side = side(arm)
        val reach = if (s.scaleSwing) s.itemSize else 1f
        when (s.swingStyle) {
            SwingStyle.VANILLA -> {
                if (reach == 1f) return false
                if (spear) {
                    stab(pose, side, arm, attack, reach)
                } else {
                    push(pose, side, attack, reach)
                    arc(pose, side, attack, 1f)
                }
            }
            SwingStyle.SPIN -> pose.mulPose(Axis.YP.rotationDegrees(side * -360f * attack))
            SwingStyle.STAB -> stab(pose, side, arm, attack, reach)
            SwingStyle.BLOCK_HIT, SwingStyle.EXHIBITION -> {
                val player = Minecraft.getInstance().player ?: return false
                // Only the main hand blocks. The off hand swings the game's way, scaled like any other swing.
                val k = if (arm == player.mainArm) block.at(if (player.swinging) 1f else 0f, System.nanoTime()) else 0f
                if (k == 0f && reach == 1f) return false
                // The game's swing fades out as the pose comes in.
                push(pose, side, attack, reach * (1 - k))
                if (s.swingStyle == SwingStyle.BLOCK_HIT) {
                    // A short punch: at full strength the game's arc would tip the sword below the screen.
                    arc(pose, side, attack, 1 - k * (1 - BLOCK_ARC))
                } else {
                    arc(pose, side, attack, 1 - k)
                    // The sword slides down and back toward the player at the swing's peak, and its tip dips.
                    val peak = sin(sqrt(attack) * PI) * k
                    pose.translate(0f, -0.08f * peak * reach, 0.1f * peak * reach)
                    pose.mulPose(Axis.XP.rotationDegrees(-25f * peak))
                }
                // The sword held across the screen, as the game holds a blocking one, lifted clear of the hotbar.
                pose.translate(side * -0.14142136f * k, (0.08f + BLOCK_LIFT) * k, 0.14142136f * k)
                pose.mulPose(Axis.XP.rotationDegrees(-102.25f * k))
                pose.mulPose(Axis.YP.rotationDegrees(side * 13.365f * k))
                pose.mulPose(Axis.ZP.rotationDegrees(side * 78.05f * k))
            }
        }
        return true
    }

    /** Where the spear's stab is worked out, so its push can be scaled apart from its turn. */
    private val stabbing = PoseStack()

    /** The game's spear stab, its push times [reach]. */
    private fun stab(pose: PoseStack, side: Int, arm: HumanoidArm, attack: Float, reach: Float) {
        if (reach == 1f) return SpearAnimations.firstPersonAttack(attack, pose, side, arm)
        stabbing.setIdentity()
        SpearAnimations.firstPersonAttack(attack, stabbing, side, arm)
        val stab = stabbing.last().pose()
        pose.mulPose(stab.setTranslation(stab.m30() * reach, stab.m31() * reach, stab.m32() * reach))
    }

    /** The game's own swing moves the hand by this much, times [reach]. */
    private fun push(pose: PoseStack, side: Int, attack: Float, reach: Float) {
        val root = sqrt(attack)
        pose.translate(reach * side * -0.4f * sin(root * PI), reach * 0.2f * sin(root * 2 * PI), reach * -0.2f * sin(attack * PI))
    }

    /** The game's own swing turns the item by this much, times [amount]. At 1 it is the game's turn exactly. */
    private fun arc(pose: PoseStack, side: Int, attack: Float, amount: Float) {
        val turn = sin(sqrt(attack) * PI) * amount
        pose.mulPose(Axis.YP.rotationDegrees(side * (45f - 20f * sin(attack * attack * PI) * amount)))
        pose.mulPose(Axis.ZP.rotationDegrees(side * -20f * turn))
        pose.mulPose(Axis.XP.rotationDegrees(-80f * turn))
        pose.mulPose(Axis.YP.rotationDegrees(side * -45f))
    }

    /** How many ticks the player's own swing lasts, from the game's [ticks]. Everyone else swings at their own pace. */
    fun swingTicks(entity: LivingEntity, ticks: Int): Int {
        if (entity !== Minecraft.getInstance().player || !on) return ticks
        val base = if (settings.ignoreHaste) entity.getItemInHand(entity.swingingArm ?: InteractionHand.MAIN_HAND).swingAnimation.duration() else ticks
        return HeldItem.swingTicks(base, settings.swingSpeed)
    }

    /** The game lowers the item after a swing by this cooldown scale, from 0 to 1. No Equip Reset keeps it up. */
    fun swapScale(scale: Float): Float = if (steady) 1f else scale

    /** Whether the hand shows [next] at once instead of lowering [shown] first. No Equip Reset does for the same item. */
    fun replaceAtOnce(vanilla: Boolean, shown: ItemStack, next: ItemStack): Boolean = vanilla || steady && ItemStack.isSameItem(shown, next)

    /** Whether the game may lower the item after the player uses it. */
    fun dipOnUse(): Boolean = !steady
}
