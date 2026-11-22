package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Animations
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

class HeldItemScene private constructor(private val context: ClientGameTestContext, private val world: TestSingleplayerContext) {
    fun shot(name: String): BufferedImage = image(context.takeScreenshot(name))

    fun command(command: String) = world.server.runCommand(command)

    fun swing() = context.runOnClient<Nothing> { it.player!!.swing(InteractionHand.MAIN_HAND) }

    fun swinging() = context.computeOnClient<Boolean, Nothing> { it.player!!.swinging }

    fun swingAtAir() = context.runOnClient<Nothing> {
        it.player!!.resetAttackStrengthTicker()
        it.player!!.swing(InteractionHand.MAIN_HAND)
    }

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

    fun hold(item: Item, off: Boolean = false) {
        command("item replace entity @a weapon.${if (off) "offhand" else "mainhand"} with ${BuiltInRegistries.ITEM.getKey(item).path}")
        context.waitFor { (if (off) it.player!!.offhandItem else it.player!!.mainHandItem).`is`(item) }
        settle()
    }

    fun midSwing(name: String, swingAt: () -> Unit = ::swing): BufferedImage {
        swingAt()
        context.waitTicks(2)
        return shot(name).also { settle() }
    }

    fun endSwing() {
        context.runOnClient<Nothing> {
            it.player!!.swinging = false
            it.player!!.swingTime = 0
        }
        settle()
    }

    companion object {
        fun changed(a: BufferedImage, b: BufferedImage, right: Boolean = true) = diff(a, b) { x, _ -> right || x < a.width / 2 }

        fun runVanilla(context: ClientGameTestContext, test: HeldItemScene.(Animations, BufferedImage, BufferedImage) -> Unit) = run(context) {
            val animations = DsModClient.feature<Animations>()
            animations.feature.enabled = false
            val vanilla = shot("animations-vanilla")
            val vanillaSwing = midSwing("animations-vanilla-swing")
            test(animations, vanilla, vanillaSwing)
        }

        fun run(context: ClientGameTestContext, test: HeldItemScene.() -> Unit) {
            val animations = DsModClient.feature<Animations>()
            context.worldBuilder().create().use { world ->
                val scene = HeldItemScene(context, world)
                world.freezeTime(at = null)
                scene.command("clear @a")
                context.input.lookAt(0f, 90f)
                // @keep The vignette eases toward the light where the player stands for half a minute and shades the whole frame, and the attack indicator shows the cooldown a swing at the air starts.
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
                    animations.feature.enabled = true
                    animations.reset()
                }
            }
        }
    }
}
