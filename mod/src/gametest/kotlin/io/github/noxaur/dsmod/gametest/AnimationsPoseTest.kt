package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.hand.HandStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.item.Items

class AnimationsPoseTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.run(context) {
        val animations = DsModClient.feature<Animations>()
        animations.feature.enabled = false
        val vanilla = shot("animations-vanilla")
        val vanillaSwing = midSwing("animations-vanilla-swing")
        animations.feature.enabled = true

        animations.itemSize.value = 1.5f
        check(changed(vanilla, shot("animations-size")) > 1000) { "Item Size did not change the sword" }
        animations.itemSize.value = 1f
        animations.itemX.value = -0.3f
        animations.itemY.value = 0.2f
        animations.itemZ.value = 0.3f
        check(changed(vanilla, shot("animations-place")) > 1000) { "Item X, Y and Z did not move the sword" }
        animations.itemX.value = -1.5f
        animations.itemY.value = 1f
        check(changed(vanilla, shot("animations-top-left"), right = false) > 1000) { "Item X and Y did not reach the top left" }
        animations.itemX.value = 0f
        animations.itemY.value = 0f
        animations.itemZ.value = 0f
        for ((name, turn) in listOf<Pair<String, (Float) -> Unit>>(
            "yaw" to { animations.itemYaw.value = it },
            "pitch" to { animations.itemPitch.value = it },
            "roll" to { animations.itemRoll.value = it },
        )) {
            turn(40f)
            check(changed(vanilla, shot("animations-$name")) > 1000) { "Item ${name.replaceFirstChar(Char::uppercase)} did not turn the sword" }
            turn(0f)
        }

        animations.flip.value = true
        check(changed(vanilla, shot("animations-flip")) > 1000) { "Flip did not mirror the sword" }
        hold(Items.CRAFTING_TABLE)
        val flippedBlock = shot("animations-flip-block")
        animations.flip.value = false
        check(changed(flippedBlock, shot("animations-block")) > 200) { "Flip did not mirror the block" }
        hold(Items.DIAMOND_SWORD)

        animations.reach.value = 0f
        val pivot = midSwing("animations-pivot")
        check(changed(vanillaSwing, pivot) > 1000 && changed(vanilla, pivot) > 1000) { "Reach 0% did not anchor the grip and pivot the sword" }
        animations.turn.value = 0f
        check(changed(vanilla, midSwing("animations-no-turn-no-reach")) == 0) { "Turn and Reach at 0% still moved the sword" }
        animations.reach.value = 100f
        val slide = midSwing("animations-slide")
        check(changed(vanillaSwing, slide) > 1000 && changed(vanilla, slide) > 1000) { "Turn 0% did not slide the sword without turning it" }
        animations.turn.value = 100f

        // A smaller sword swings a shorter way with Scale Swing, and the game's full way without it, and so does a stab.
        animations.itemSize.value = 0.5f
        val scaled = midSwing("animations-scale-swing")
        animations.scaleSwing.value = false
        check(changed(scaled, midSwing("animations-full-swing")) > 1000) { "Scale Swing did not change the swing" }
        animations.style.value = HandStyle.STAB
        val fullStab = midSwing("animations-full-stab")
        animations.scaleSwing.value = true
        check(changed(fullStab, midSwing("animations-scaled-stab")) > 1000) { "Scale Swing did not change the stab" }
        animations.style.value = HandStyle.MODERN
        animations.itemSize.value = 1f

        animations.noSwing.value = true
        check(changed(vanilla, midSwing("animations-no-swing")) == 0) { "No Swing moved the sword" }

        // A spear swings by a stab of its own, which the settings reach too.
        hold(Items.DIAMOND_SPEAR)
        val spear = shot("animations-spear")
        check(changed(spear, midSwing("animations-spear-no-swing")) == 0) { "No Swing let the spear stab" }
        animations.noSwing.value = false
        check(changed(spear, midSwing("animations-spear-vanilla")) > 1000) { "The spear did not stab" }
        animations.itemSize.value = 0.5f
        val smallSpear = shot("animations-spear-small")
        val scaledSpear = midSwing("animations-spear-stab")
        check(changed(smallSpear, scaledSpear) > 1000) { "Scale Swing stopped the spear's own stab" }
        animations.scaleSwing.value = false
        check(changed(scaledSpear, midSwing("animations-spear-full-stab")) > 1000) { "Scale Swing did not change the spear's stab" }
        animations.scaleSwing.value = true
        animations.itemSize.value = 1f

        // No Bow Swing keeps a bow still and leaves every other item's swing alone.
        hold(Items.BOW)
        val bow = shot("animations-bow")
        check(changed(bow, midSwing("animations-bow-swing")) > 1000) { "The bow did not swing" }
        animations.noBowSwing.value = true
        check(changed(bow, midSwing("animations-no-bow-swing")) == 0) { "No Bow Swing let the bow swing" }
        hold(Items.DIAMOND_SWORD)
        check(changed(vanillaSwing, midSwing("animations-no-bow-swing-sword")) == 0) { "No Bow Swing changed the sword's swing" }
        animations.noBowSwing.value = false

        // The off hand moves outward, to the left, where the main hand moves right.
        hold(Items.DIAMOND_SWORD, off = true)
        val both = shot("animations-off-hand")
        animations.itemX.value = 0.3f
        val moved = shot("animations-off-hand-x")
        animations.itemX.value = 0f
        check(changed(both, moved, right = false) > 1000) { "Item X did not move the off hand's sword" }
        hold(Items.AIR, off = true)

        // Reset puts every setting back, so the frame is the game's own again.
        animations.style.value = HandStyle.V1_7
        animations.swingSpeed.value = 2f
        animations.turn.value = 50f
        animations.flip.value = true
        animations.blocking.value = true
        animations.itemSize.value = 1.5f
        animations.itemX.value = 0.5f
        animations.itemYaw.value = 30f
        animations.noSwing.value = true
        animations.noBowSwing.value = true
        animations.noEquipReset.value = true
        animations.reset()
        check(changed(vanilla, shot("animations-reset")) == 0) { "Reset left the sword moved" }
        check(changed(vanillaSwing, midSwing("animations-reset-swing")) == 0) { "Reset left the swing changed" }
    }
}
