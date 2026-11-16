package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items
import java.awt.image.BufferedImage

/**
 * With every setting at its default, the held sword is the game's own to the pixel, at rest, mid-swing and in the dip
 * after a swing at the air. Then each swing style and the swing's speed: each style moves the sword its own way, and
 * the swing's length is measured in ticks. `AnimationsPoseTest` covers where the item sits. Read the screenshots.
 */
class AnimationsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.run(context) {
        val settings = DsModClient.settings.general
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

        settings.noEquipReset = true
        check(changed(vanillaSwing, midSwing("animations-no-equip-reset", ::swingAtAir)) == 0) {
            "No Equip Reset did not keep the sword up through a swing at the air"
        }
        settings.noEquipReset = false

        // Slowed to half, so the swing lasts 12 ticks and the frame 4 ticks in is a third of the way.
        settings.swingSpeed = 0.5f
        val poses = SwingStyle.entries.map { style ->
            settings.swingStyle = style
            swing()
            context.waitTicks(4)
            val pose = shot("animations-${style.name.lowercase()}")
            endSwing()
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

        // No Bow Swing goes by the item the hand draws, which stays the bow while it lowers for a sword just put in
        // the hand. A swing already under way then must leave the frame as it would be with no swing.
        settings.noBowSwing = true
        fun swap(name: String, swinging: Boolean): BufferedImage {
            hold(Items.BOW)
            if (swinging) swing()
            command("item replace entity @a weapon.mainhand with diamond_sword")
            context.waitFor { it.player!!.mainHandItem.`is`(Items.DIAMOND_SWORD) }
            context.waitTicks(1)
            return shot(name).also { settle() }
        }
        check(changed(swap("animations-swap", false), swap("animations-swap-swing", true)) == 0) {
            "No Bow Swing let the lowering bow swing once a sword was in the hand"
        }
        settings.noBowSwing = false

        command("effect give @a haste infinite 2")
        context.waitFor { it.player!!.hasEffect(MobEffects.HASTE) }
        check(!lasts(4)) { "Haste III did not shorten the swing" }
        settings.ignoreHaste = true
        check(lasts(4)) { "Ignore Haste still let Haste shorten the swing" }
    }
}
