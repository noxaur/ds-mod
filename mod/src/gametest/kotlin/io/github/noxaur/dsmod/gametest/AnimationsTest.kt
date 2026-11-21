package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.hand.HandStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class AnimationsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.run(context) {
        val animations = DsModClient.feature<Animations>()
        animations.feature.enabled = false
        val vanilla = shot("animations-vanilla")
        val vanillaSwing = midSwing("animations-vanilla-swing")
        val vanillaDip = midSwing("animations-vanilla-dip", ::swingAtAir)
        check(changed(vanilla, vanillaSwing) > 1000) { "The swing did not move the sword" }

        animations.feature.enabled = true
        context.waitTicks(2)
        check(changed(vanilla, shot("animations-default")) == 0) { "At its defaults the sword at rest is not the game's own" }
        check(changed(vanillaSwing, midSwing("animations-default-swing")) == 0) { "At its defaults the swing is not the game's own" }
        check(changed(vanillaDip, midSwing("animations-default-dip", ::swingAtAir)) == 0) { "At its defaults the dip is not the game's own" }

        animations.noEquipReset.value = true
        check(changed(vanillaSwing, midSwing("animations-no-equip-reset", ::swingAtAir)) == 0) {
            "No Equip Reset did not keep the sword up through a swing at the air"
        }
        animations.noEquipReset.value = false

        animations.style.value = HandStyle.V1_7
        val old = shot("animations-1.7")
        check(changed(vanilla, old) > 1000) { "1.7 held the sword where the game does" }
        val oldSwing = midSwing("animations-1.7-swing")
        check(changed(oldSwing, midSwing("animations-1.7-dip", ::swingAtAir)) == 0) { "1.7 lowered the sword after a swing at the air" }
        animations.style.value = HandStyle.MODERN

        // Slowed to half, so the swing lasts 12 ticks and the frame 4 ticks in is a third of the way.
        animations.swingSpeed.value = 0.5f
        val poses = HandStyle.entries.map { style ->
            animations.style.value = style
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
        animations.style.value = HandStyle.MODERN

        fun lasts(ticks: Int): Boolean {
            swing()
            context.waitTicks(ticks)
            return swinging().also { settle() }
        }
        animations.swingSpeed.value = 1f
        check(!lasts(9)) { "The game's own swing lasted past 9 ticks" }
        animations.swingSpeed.value = 0.5f
        check(lasts(9)) { "At half speed the swing ended within 9 ticks" }
        animations.swingSpeed.value = 2f
        check(!lasts(4)) { "At twice the speed the swing lasted past 4 ticks" }
        animations.swingSpeed.value = 1f

        val spear = context.computeOnClient<Int, Nothing> { ItemStack(Items.DIAMOND_SPEAR).swingAnimation.duration() }
        val sword = context.computeOnClient<Int, Nothing> { ItemStack(Items.DIAMOND_SWORD).swingAnimation.duration() }
        check(spear > sword + 2) { "A spear swings $spear ticks and a sword $sword, too near to tell Stab's pace apart" }
        check(!lasts(spear - 2)) { "The sword's own swing lasted a spear's" }
        animations.style.value = HandStyle.STAB
        check(lasts(spear - 2) && !lasts(spear + 2)) { "Stab did not swing the sword for a spear's $spear ticks" }
        animations.style.value = HandStyle.MODERN

        fun view(type: CameraType) = context.runOnClient<Nothing> { it.options.cameraType = type }
        view(CameraType.THIRD_PERSON_BACK)
        animations.swingSpeed.value = 0.5f
        check(!lasts(9)) { "Swing Speed slowed the player's own arm in third person" }
        animations.swingSpeed.value = 2f
        check(lasts(4)) { "Swing Speed sped up the player's own arm in third person" }
        animations.swingSpeed.value = 1f
        view(CameraType.FIRST_PERSON)

        fun rightClick(down: Boolean) {
            context.runOnClient<Nothing> { it.options.keyUse.isDown = down }
            context.waitTicks(2)
        }
        rightClick(true)
        check(changed(vanilla, shot("animations-right-click-off")) == 0) { "Right click moved the sword with 1.7 Blocking off" }
        animations.blocking.value = true
        val blocking = shot("animations-blocking")
        check(changed(vanilla, blocking) > 1000) { "1.7 Blocking did not raise the sword" }
        check(changed(blocking, midSwing("animations-blocking-swing")) == 0) { "Modern swung the sword while it blocked" }
        animations.style.value = HandStyle.V1_7
        val oldBlocking = shot("animations-1.7-blocking")
        check(changed(oldBlocking, midSwing("animations-1.7-block-hit")) > 1000) { "1.7 did not swing the sword while it blocked" }
        animations.style.value = HandStyle.MODERN
        hold(Items.STICK)
        val stick = shot("animations-blocking-stick")
        rightClick(false)
        check(changed(stick, shot("animations-stick")) == 0) { "1.7 Blocking raised a stick" }
        hold(Items.DIAMOND_SWORD)
        check(changed(vanilla, shot("animations-blocking-released")) == 0) { "The sword stayed up after right click let go" }

        context.input.lookAt(0f, 0f)
        view(CameraType.THIRD_PERSON_BACK)
        context.waitTicks(2)
        val back = shot("animations-third-person")
        rightClick(true)
        check(changed(back, shot("animations-third-person-blocking")) > 200) { "1.7 Blocking did not raise the arm in third person" }
        rightClick(false)
        view(CameraType.FIRST_PERSON)
        context.input.lookAt(0f, 90f)
        settle()

        command("effect give @a haste infinite 2")
        context.waitFor { it.player!!.hasEffect(MobEffects.HASTE) }
        check(!lasts(4)) { "Haste III did not shorten the swing" }
        animations.ignoreHaste.value = true
        check(lasts(4)) { "Ignore Haste still let Haste shorten the swing" }
    }
}
