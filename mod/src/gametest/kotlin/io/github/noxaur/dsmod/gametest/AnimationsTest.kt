package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items

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

        // Slowed to a fifth, so the swing lasts 30 ticks and the frame 10 ticks in is a third of the way.
        settings.swingSpeed = 0.2f
        val poses = SwingStyle.entries.map { style ->
            settings.swingStyle = style
            swing()
            context.waitTicks(10)
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

        command("effect give @a haste infinite 2")
        context.waitFor { it.player!!.hasEffect(MobEffects.HASTE) }
        check(!lasts(4)) { "Haste III did not shorten the swing" }
        settings.ignoreHaste = true
        check(lasts(4)) { "Ignore Haste still let Haste shorten the swing" }

        // A spear swings by a stab of its own, which the game still draws under Vanilla.
        hold(Items.DIAMOND_SPEAR)
        check(changed(shot("animations-spear"), midSwing("animations-spear-vanilla")) > 1000) { "The spear did not stab" }
    }
}
