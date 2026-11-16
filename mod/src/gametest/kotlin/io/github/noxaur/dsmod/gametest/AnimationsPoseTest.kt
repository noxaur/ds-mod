package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.item.Items

/**
 * Where the held item sits and how far it swings: Item Size, X, Y and Z, Yaw, Pitch and Roll, Scale Swing, No Swing
 * and No Bow Swing, on a sword, a spear, a bow and the off hand, and Reset. Each setting changes the frame, and each
 * that should leave it alone leaves it the same to the pixel. Read the screenshots.
 */
class AnimationsPoseTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = HeldItemScene.run(context) {
        val settings = DsModClient.settings.general
        settings.animations = false
        val vanilla = shot("animations-vanilla")
        val vanillaSwing = midSwing("animations-vanilla-swing")
        settings.animations = true

        settings.itemSize = 1.5f
        check(changed(vanilla, shot("animations-size")) > 1000) { "Item Size did not change the sword" }
        settings.itemSize = 1f
        settings.itemX = -0.3f
        settings.itemY = 0.2f
        settings.itemZ = 0.3f
        check(changed(vanilla, shot("animations-place")) > 1000) { "Item X, Y and Z did not move the sword" }
        settings.itemX = 0f
        settings.itemY = 0f
        settings.itemZ = 0f
        for ((name, turn) in listOf<Pair<String, (Float) -> Unit>>(
            "yaw" to { settings.itemYaw = it },
            "pitch" to { settings.itemPitch = it },
            "roll" to { settings.itemRoll = it },
        )) {
            turn(40f)
            check(changed(vanilla, shot("animations-$name")) > 1000) { "Item ${name.replaceFirstChar(Char::uppercase)} did not turn the sword" }
            turn(0f)
        }

        // A smaller sword swings a shorter way with Scale Swing, and the game's full way without it, and so does a stab.
        settings.itemSize = 0.5f
        val scaled = midSwing("animations-scale-swing")
        settings.scaleSwing = false
        check(changed(scaled, midSwing("animations-full-swing")) > 1000) { "Scale Swing did not change the swing" }
        settings.swingStyle = SwingStyle.STAB
        val fullStab = midSwing("animations-full-stab")
        settings.scaleSwing = true
        check(changed(fullStab, midSwing("animations-scaled-stab")) > 1000) { "Scale Swing did not change the stab" }
        settings.swingStyle = SwingStyle.VANILLA
        settings.itemSize = 1f

        settings.noSwing = true
        check(changed(vanilla, midSwing("animations-no-swing")) == 0) { "No Swing moved the sword" }

        // A spear swings by a stab of its own, which the settings reach too.
        hold(Items.DIAMOND_SPEAR)
        val spear = shot("animations-spear")
        check(changed(spear, midSwing("animations-spear-no-swing")) == 0) { "No Swing let the spear stab" }
        settings.noSwing = false
        check(changed(spear, midSwing("animations-spear-vanilla")) > 1000) { "The spear did not stab" }
        settings.itemSize = 0.5f
        val smallSpear = shot("animations-spear-small")
        val scaledSpear = midSwing("animations-spear-stab")
        check(changed(smallSpear, scaledSpear) > 1000) { "Scale Swing stopped the spear's own stab" }
        settings.scaleSwing = false
        check(changed(scaledSpear, midSwing("animations-spear-full-stab")) > 1000) { "Scale Swing did not change the spear's stab" }
        settings.scaleSwing = true
        settings.itemSize = 1f

        // No Bow Swing keeps a bow still and leaves every other item's swing alone.
        hold(Items.BOW)
        val bow = shot("animations-bow")
        check(changed(bow, midSwing("animations-bow-swing")) > 1000) { "The bow did not swing" }
        settings.noBowSwing = true
        check(changed(bow, midSwing("animations-no-bow-swing")) == 0) { "No Bow Swing let the bow swing" }
        hold(Items.DIAMOND_SWORD)
        check(changed(vanillaSwing, midSwing("animations-no-bow-swing-sword")) == 0) { "No Bow Swing changed the sword's swing" }
        settings.noBowSwing = false

        // The off hand moves outward, to the left, where the main hand moves right.
        hold(Items.DIAMOND_SWORD, off = true)
        val both = shot("animations-off-hand")
        settings.itemX = 0.3f
        val moved = shot("animations-off-hand-x")
        settings.itemX = 0f
        check(changed(both, moved, right = false) > 1000) { "Item X did not move the off hand's sword" }
        hold(Items.AIR, off = true)

        // Reset puts every setting back, so the frame is the game's own again.
        settings.swingStyle = SwingStyle.SPIN
        settings.swingSpeed = 2f
        settings.itemSize = 1.5f
        settings.itemX = 0.5f
        settings.itemYaw = 30f
        settings.noSwing = true
        settings.noBowSwing = true
        settings.noEquipReset = true
        settings.resetAnimations.run()
        check(changed(vanilla, shot("animations-reset")) == 0) { "Reset left the sword moved" }
        check(changed(vanillaSwing, midSwing("animations-reset-swing")) == 0) { "Reset left the swing changed" }
    }
}
