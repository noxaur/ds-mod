package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.KitGalleryScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.guiScale
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class KitTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = inKit(context) {
        context.setScreen { KitGalleryScreen() }
        context.takeScreenshot("kit-charcoal")
        context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-vanilla") }
        guiScale(context, 1)
        context.takeScreenshot("kit-charcoal-gui1")
    }

    companion object {
        fun inKit(context: ClientGameTestContext, test: () -> Unit) {
            val window = SettingsSteps.windowSize(context)
            context.input.resizeWindow(1280, 800)
            // @keep An item needs a world's registries, and the gallery shows items in its slots.
            context.worldBuilder().create().use {
                guiScale(context, 2)
                test()
                context.setScreen { null }
            }
            SettingsSteps.restore(context, window)
        }
    }
}
