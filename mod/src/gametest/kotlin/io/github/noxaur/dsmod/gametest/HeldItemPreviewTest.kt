package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.hand.HandPreview
import io.github.noxaur.dsmod.core.hand.HandStyle
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.HeldItemPreview
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class HeldItemPreviewTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val animations = DsModClient.feature<Animations>()
        val window = SettingsSteps.setUp(context)
        val driver = SettingsDriver(context)
        fun shown() = context.computeOnClient<HandPreview, Nothing> { HeldItemPreview.shown }
        fun width(label: HandPreview) = context.computeOnClient<Int, Nothing> { it.font.width(label.toString()) } + 12
        try {
            HeldItemPreview.shown = HandPreview.HELD
            context.setScreen { DsModClient.settingsScreen(TitleScreen(), SettingsTabs(at("General")), reveal = "heldItemPreview") }
            context.waitTicks(2)
            val spot = driver.spot("heldItemPreview")
            context.shotInBothThemes("held-preview")

            val swinging = spot.controlX + width(HandPreview.HELD) + 2 + 3
            driver.click(swinging, spot.controlY + 4)
            check(shown() == HandPreview.SWINGING) { "A press on Swinging showed ${shown()}" }
            context.takeScreenshot("held-preview-swinging")
            context.waitTicks(3)
            context.takeScreenshot("held-preview-swinging-later")

            driver.key(GLFW.GLFW_KEY_RIGHT)
            check(shown() == HandPreview.SCREEN) { "Right showed ${shown()}" }
            driver.key(GLFW.GLFW_KEY_RIGHT)
            check(shown() == HandPreview.SCREEN) { "Right past the last view showed ${shown()}" }
            context.shotInBothThemes("held-preview-screen")

            animations.itemX.value = -1.5f
            animations.itemY.value = 1f
            context.waitTicks(2)
            context.takeScreenshot("held-preview-screen-top-left")
            animations.itemX.value = 2f
            animations.itemY.value = 0f
            context.waitTicks(2)
            context.takeScreenshot("held-preview-screen-off-screen")
            animations.itemX.value = 0f
            animations.style.value = HandStyle.V1_7
            animations.flip.value = true
            context.waitTicks(2)
            context.takeScreenshot("held-preview-screen-1.7-flipped")

            driver.key(GLFW.GLFW_KEY_LEFT)
            driver.key(GLFW.GLFW_KEY_LEFT)
            check(shown() == HandPreview.HELD) { "Left, Left showed ${shown()}" }
            context.takeScreenshot("held-preview-1.7-flipped")
            context.setScreen { null }
        } finally {
            animations.reset()
            HeldItemPreview.shown = HandPreview.HELD
            SettingsSteps.restore(context, window)
        }
    }
}
