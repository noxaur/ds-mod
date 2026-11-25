package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.hand.HandStyle
import io.github.noxaur.dragen.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.item.Items

class AnimationsItemsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.runVanilla(context) { animations, vanilla, vanillaSwing ->
        animations.feature.enabled = true
        animations.noSwing.value = true

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

        hold(Items.BOW)
        val bow = shot("animations-bow")
        check(changed(bow, midSwing("animations-bow-swing")) > 1000) { "The bow did not swing" }
        animations.noBowSwing.value = true
        check(changed(bow, midSwing("animations-no-bow-swing")) == 0) { "No Bow Swing let the bow swing" }
        hold(Items.DIAMOND_SWORD)
        check(changed(vanillaSwing, midSwing("animations-no-bow-swing-sword")) == 0) { "No Bow Swing changed the sword's swing" }
        animations.noBowSwing.value = false

        hold(Items.DIAMOND_SWORD, off = true)
        val both = shot("animations-off-hand")
        animations.itemX.value = 0.3f
        val moved = shot("animations-off-hand-x")
        animations.itemX.value = 0f
        check(changed(both, moved, right = false) > 1000) { "Item X did not move the off hand's sword" }
        hold(Items.AIR, off = true)

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
