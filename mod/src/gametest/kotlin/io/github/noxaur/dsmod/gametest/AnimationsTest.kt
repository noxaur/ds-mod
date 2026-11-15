package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.mixin.HandAccessor
import io.github.noxaur.dsmod.gametest.mixin.HudAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Holds a sword over still ground and compares screenshots. With every setting at its default the frame is the
 * game's own to the pixel, at rest, mid-swing and after the dip that follows a swing. Each setting then changes the
 * frame, and the swing's length is measured in ticks. Read the screenshots for each pose.
 */
class AnimationsTest : FabricClientGameTest {
    private fun read(shot: Path): BufferedImage = ImageIO.read(shot.toFile())

    /** How many pixels differ between two frames. */
    private fun changed(a: BufferedImage, b: BufferedImage): Int {
        check(a.width == b.width && a.height == b.height) { "The frames are different sizes" }
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) count++
        return count
    }

    override fun runTest(context: ClientGameTestContext) {
        val settings = DsModClient.settings.general

        fun shot(name: String) = read(context.takeScreenshot(name))
        fun swing() = context.runOnClient<Nothing> { it.player!!.swing(InteractionHand.MAIN_HAND) }
        fun swinging() = context.computeOnClient<Boolean, Nothing> { it.player!!.swinging }

        /** What the game does on a swing at the air: the swing, and the cooldown that lowers the item after it. */
        fun swingAtAir() = context.runOnClient<Nothing> {
            it.player!!.resetAttackStrengthTicker()
            it.player!!.swing(InteractionHand.MAIN_HAND)
        }

        /**
         * Waits until the hand draws the item the player holds, the swing has ended, the cooldown has run out, the item
         * is fully raised again and the arm has caught up with the camera's last turn, then clears the item's name over
         * the hotbar, so only the item can differ between two frames.
         */
        fun settle() {
            context.waitFor { client ->
                val player = client.player!!
                val hand = client.gameRenderer.itemInHandRenderer as HandAccessor
                ItemStack.matches(hand.mainHandItem, player.mainHandItem) && !player.swinging &&
                    player.getAttackStrengthScale(0f) >= 1f && hand.mainHandHeight == 1f && hand.oMainHandHeight == 1f &&
                    player.xBob == player.xRot && player.xBobO == player.xRot && player.yBob == player.yRot && player.yBobO == player.yRot
            }
            context.runOnClient<Nothing> { (it.gui.hud as HudAccessor).setToolHighlightTimer(0) }
        }

        /** Puts [item] in the hand and waits for the client to have it. */
        fun hold(world: TestSingleplayerContext, item: Item) {
            world.server.runCommand("item replace entity @a weapon.mainhand with ${BuiltInRegistries.ITEM.getKey(item).path}")
            context.waitFor { it.player!!.mainHandItem.`is`(item) }
            settle()
        }

        /** The frame two ticks into a swing, which is mid-swing at the game's own speed. */
        fun midSwing(name: String, swingAt: () -> Unit = ::swing): BufferedImage {
            swingAt()
            context.waitTicks(2)
            return shot(name).also { settle() }
        }

        context.worldBuilder().create().use { world ->
            listOf("gamerule advance_time false", "gamerule advance_weather false", "clear @a")
                .forEach(world.server::runCommand)
            context.input.lookAt(0f, 90f)
            // The vignette eases toward the light where the player stands for half a minute and shades the whole frame,
            // and the attack indicator shows the cooldown a swing at the air starts. Neither is the sword.
            val vignette = context.computeOnClient<Boolean, Nothing> { it.options.vignette().get() }
            val indicator = context.computeOnClient<AttackIndicatorStatus, Nothing> { it.options.attackIndicator().get() }
            context.runOnClient<Nothing> {
                it.options.vignette().set(false)
                it.options.attackIndicator().set(AttackIndicatorStatus.OFF)
            }
            hold(world, Items.DIAMOND_SWORD)
            try {
                settings.animations = false
                val vanilla = shot("animations-vanilla")
                val vanillaSwing = midSwing("animations-vanilla-swing")
                val vanillaDip = midSwing("animations-vanilla-dip", ::swingAtAir)
                check(changed(vanilla, vanillaSwing) > 1000) { "The swing did not move the sword" }

                settings.animations = true
                context.waitTicks(2)
                check(changed(vanilla, shot("animations-default")) == 0) { "At its defaults the sword at rest is not the game's own" }
                check(changed(vanillaSwing, midSwing("animations-default-swing")) == 0) { "At its defaults the swing is not the game's own" }
                check(changed(vanillaDip, midSwing("animations-default-dip", ::swingAtAir)) == 0) { "At its defaults the dip is not the game's own" }

                settings.itemSize = 1.5f
                check(changed(vanilla, shot("animations-size")) > 1000) { "Item Size did not change the sword" }
                settings.itemSize = 1f
                settings.itemSide = -0.3f
                settings.itemHeight = 0.2f
                settings.itemDepth = 0.3f
                check(changed(vanilla, shot("animations-place")) > 1000) { "Item Side, Height and Depth did not move the sword" }
                settings.itemSide = 0f
                settings.itemHeight = 0f
                settings.itemDepth = 0f

                settings.noEquipReset = true
                check(changed(vanillaSwing, midSwing("animations-no-equip-reset", ::swingAtAir)) == 0) {
                    "No Equip Reset did not keep the sword up through a swing at the air"
                }
                settings.noEquipReset = false
                settings.swingStyle = SwingStyle.STILL
                check(changed(vanilla, midSwing("animations-still")) == 0) { "Still moved the sword" }
                // A spear swings by a stab of its own, which the style replaces too.
                hold(world, Items.DIAMOND_SPEAR)
                val spear = shot("animations-spear")
                check(changed(spear, midSwing("animations-spear-still")) == 0) { "Still let the spear stab" }
                settings.swingStyle = SwingStyle.VANILLA
                check(changed(spear, midSwing("animations-spear-vanilla")) > 1000) { "The spear did not stab" }
                hold(world, Items.DIAMOND_SWORD)

                // Slowed to a fifth, so the swing lasts 30 ticks and the frame 10 ticks in is a third of the way.
                settings.swingSpeed = 0.2f
                val poses = (SwingStyle.entries - SwingStyle.STILL).map { style ->
                    settings.swingStyle = style
                    swing()
                    context.waitTicks(10)
                    val pose = shot("animations-${style.name.lowercase()}")
                    // The rest of the slowed swing shows nothing new.
                    context.runOnClient<Nothing> {
                        it.player!!.swinging = false
                        it.player!!.swingTime = 0
                    }
                    settle()
                    check(changed(vanilla, pose) > 1000) { "$style did not move the sword" }
                    style to pose
                }
                poses.forEach { (a, first) ->
                    poses.filter { it.first.ordinal > a.ordinal }.forEach { (b, second) ->
                        check(changed(first, second) > 1000) { "$a and $b draw the same swing" }
                    }
                }
                settings.swingStyle = SwingStyle.VANILLA

                fun lasts(ticks: Int): Boolean {
                    swing()
                    context.waitTicks(ticks)
                    return swinging().also { settle() }
                }
                settings.swingSpeed = 1f
                check(!lasts(9)) { "The game's own swing lasted past 9 ticks" }
                settings.swingSpeed = 0.5f
                check(lasts(9)) { "At half speed the swing ended within 9 ticks" }
                settings.swingSpeed = 2f
                check(!lasts(4)) { "At twice the speed the swing lasted past 4 ticks" }
                settings.swingSpeed = 1f

                world.server.runCommand("effect give @a haste infinite 2")
                context.waitTicks(5)
                check(!lasts(4)) { "Haste III did not shorten the swing" }
                settings.ignoreHaste = true
                check(lasts(4)) { "Ignore Haste still let Haste shorten the swing" }
            } finally {
                context.runOnClient<Nothing> {
                    it.options.vignette().set(vignette)
                    it.options.attackIndicator().set(indicator)
                }
                settings.animations = true
                settings.swingStyle = SwingStyle.VANILLA
                settings.swingSpeed = 1f
                settings.ignoreHaste = false
                settings.noEquipReset = false
                settings.itemSize = 1f
                settings.itemSide = 0f
                settings.itemHeight = 0f
                settings.itemDepth = 0f
            }
        }
    }
}
