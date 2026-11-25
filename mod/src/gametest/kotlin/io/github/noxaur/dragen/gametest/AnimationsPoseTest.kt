package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.hand.HandStyle
import io.github.noxaur.dragen.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.item.Items

class AnimationsPoseTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.runVanilla(context) { animations, vanilla, vanillaSwing ->
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
    }
}
