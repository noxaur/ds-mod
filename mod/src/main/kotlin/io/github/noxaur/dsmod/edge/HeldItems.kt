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
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI = Math.PI.toFloat()

/** How much of the game's swing arc the 1.7 block-hit keeps. */
private const val BLOCK_ARC = 0.3f

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

    /**
     * Moves the item before the arm puts it in the hand, so Side moves it across the screen rather than along the
     * blade, and outward for either hand. The empty hand and a map are not an item to move.
     */
    fun place(pose: PoseStack, arm: HumanoidArm, stack: ItemStack) {
        if (!on || stack.isEmpty || stack.has(DataComponents.MAP_ID)) return
        val s = settings
        if (s.itemSide != 0f || s.itemHeight != 0f || s.itemDepth != 0f) pose.translate(side(arm) * s.itemSide, s.itemHeight, -s.itemDepth)
    }

    /** Runs just before the item is drawn, so it grows about the grip and stays in the hand. */
    fun size(pose: PoseStack) {
        if (!on) return
        val size = settings.itemSize
        if (size != 1f) pose.scale(size, size, size)
    }

    /**
     * Draws this frame's swing in place of the game's, and says whether it did. Every style is the resting pose at
     * [attack] 0, as the game's own swing is.
     */
    fun swing(pose: PoseStack, arm: HumanoidArm, attack: Float): Boolean {
        if (!on) return false
        val side = side(arm)
        when (settings.swingStyle) {
            SwingStyle.VANILLA -> return false
            SwingStyle.STILL -> {}
            SwingStyle.SPIN -> pose.mulPose(Axis.YP.rotationDegrees(side * -360f * attack))
            SwingStyle.STAB -> SpearAnimations.firstPersonAttack(attack, pose, side, arm)
            SwingStyle.BLOCK_HIT -> {
                val player = Minecraft.getInstance().player ?: return false
                if (arm != player.mainArm) return false
                val k = block.at(if (player.swinging) 1f else 0f, System.nanoTime())
                if (k == 0f) return false
                // The game's swing fades into a short punch as the pose comes in. At full strength its arc would
                // tip the sword below the screen.
                val push = 1 - k
                val root = sqrt(attack)
                pose.translate(push * side * -0.4f * sin(root * PI), push * 0.2f * sin(root * 2 * PI), push * -0.2f * sin(attack * PI))
                val arc = 1 - k * (1 - BLOCK_ARC)
                val turn = sin(root * PI) * arc
                pose.mulPose(Axis.YP.rotationDegrees(side * (45f - 20f * sin(attack * attack * PI) * arc)))
                pose.mulPose(Axis.ZP.rotationDegrees(side * -20f * turn))
                pose.mulPose(Axis.XP.rotationDegrees(-80f * turn))
                pose.mulPose(Axis.YP.rotationDegrees(side * -45f))
                // The sword held across the screen, as the game holds a blocking one.
                pose.translate(side * -0.14142136f * k, 0.08f * k, 0.14142136f * k)
                pose.mulPose(Axis.XP.rotationDegrees(-102.25f * k))
                pose.mulPose(Axis.YP.rotationDegrees(side * 13.365f * k))
                pose.mulPose(Axis.ZP.rotationDegrees(side * 78.05f * k))
            }
        }
        return true
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
