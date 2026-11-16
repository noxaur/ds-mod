package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.mixin.HandAccessor
import io.github.noxaur.dsmod.gametest.mixin.HudAccessor
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * A sword held over still ground, for the Animations tests to compare frames of. Time and weather stand still and the
 * player looks straight down, so a frame changes only where the held item does.
 */
class HeldItemScene private constructor(private val context: ClientGameTestContext, private val world: TestSingleplayerContext) {
    fun shot(name: String): BufferedImage = ImageIO.read(context.takeScreenshot(name).toFile())

    fun command(command: String) = world.server.runCommand(command)

    fun swing() = context.runOnClient<Nothing> { it.player!!.swing(InteractionHand.MAIN_HAND) }

    fun swinging() = context.computeOnClient<Boolean, Nothing> { it.player!!.swinging }

    /** What the game does on a swing at the air: the swing, and the cooldown that lowers the item after it. */
    fun swingAtAir() = context.runOnClient<Nothing> {
        it.player!!.resetAttackStrengthTicker()
        it.player!!.swing(InteractionHand.MAIN_HAND)
    }

    /**
     * Waits until each hand draws the item the player holds, the swing has ended, the cooldown has run out, the items
     * are fully raised again and the arm has caught up with the camera's last turn, then clears the item's name over
     * the hotbar and the recipe toast a new item brings, so only the items can differ between two frames.
     */
    fun settle() {
        context.waitFor { client ->
            val player = client.player!!
            val hand = client.gameRenderer.itemInHandRenderer as HandAccessor
            ItemStack.matches(hand.mainHandItem, player.mainHandItem) && !player.swinging &&
                player.getAttackStrengthScale(0f) >= 1f && hand.mainHandHeight == 1f && hand.oMainHandHeight == 1f &&
                ItemStack.matches(hand.offHandItem, player.offhandItem) && hand.offHandHeight == 1f && hand.oOffHandHeight == 1f &&
                player.xBob == player.xRot && player.xBobO == player.xRot && player.yBob == player.yRot && player.yBobO == player.yRot
        }
        context.runOnClient<Nothing> {
            (it.gui.hud as HudAccessor).setToolHighlightTimer(0)
            it.gui.toastManager().clear()
        }
    }

    /** Puts [item] in the main hand, or the [off] hand, and waits for the client to have it. */
    fun hold(item: Item, off: Boolean = false) {
        command("item replace entity @a weapon.${if (off) "offhand" else "mainhand"} with ${BuiltInRegistries.ITEM.getKey(item).path}")
        context.waitFor { (if (off) it.player!!.offhandItem else it.player!!.mainHandItem).`is`(item) }
        settle()
    }

    /** The frame two ticks into a swing, which is mid-swing at the game's own speed. */
    fun midSwing(name: String, swingAt: () -> Unit = ::swing): BufferedImage {
        swingAt()
        context.waitTicks(2)
        return shot(name).also { settle() }
    }

    /** Ends a slowed swing at once, since the rest of it shows nothing new. */
    fun endSwing() {
        context.runOnClient<Nothing> {
            it.player!!.swinging = false
            it.player!!.swingTime = 0
        }
        settle()
    }

    companion object {
        /** How many pixels differ between two frames, in the whole frame or only its left half. */
        fun changed(a: BufferedImage, b: BufferedImage, right: Boolean = true): Int {
            check(a.width == b.width && a.height == b.height) { "The frames are different sizes" }
            val width = if (right) a.width else a.width / 2
            var count = 0
            for (y in 0 until a.height) for (x in 0 until width) if (a.getRGB(x, y) != b.getRGB(x, y)) count++
            return count
        }

        /** Opens a world with a sword in the hand, runs [test], and puts every Animations setting back after it. */
        fun run(context: ClientGameTestContext, test: HeldItemScene.() -> Unit) {
            val settings = DsModClient.settings.general
            context.worldBuilder().create().use { world ->
                val scene = HeldItemScene(context, world)
                listOf("gamerule advance_time false", "gamerule advance_weather false", "clear @a").forEach(scene::command)
                context.input.lookAt(0f, 90f)
                // The vignette eases toward the light where the player stands for half a minute and shades the whole
                // frame, and the attack indicator shows the cooldown a swing at the air starts. Neither is the item.
                val vignette = context.computeOnClient<Boolean, Nothing> { it.options.vignette().get() }
                val indicator = context.computeOnClient<AttackIndicatorStatus, Nothing> { it.options.attackIndicator().get() }
                context.runOnClient<Nothing> {
                    it.options.vignette().set(false)
                    it.options.attackIndicator().set(AttackIndicatorStatus.OFF)
                }
                scene.hold(Items.DIAMOND_SWORD)
                try {
                    scene.test()
                } finally {
                    context.runOnClient<Nothing> {
                        it.options.vignette().set(vignette)
                        it.options.attackIndicator().set(indicator)
                    }
                    settings.animations = true
                    settings.resetAnimations()
                }
            }
        }
    }
}
