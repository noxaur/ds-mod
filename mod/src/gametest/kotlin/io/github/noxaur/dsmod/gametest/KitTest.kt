package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.KitGalleryScreen
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/** Screenshots the kit's gallery in each theme at GUI scale 2, and in Charcoal at GUI scale 1. */
class KitTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        // The gallery is 476x324 GUI pixels, which needs more room than the test window has at GUI scale 2.
        context.input.resizeWindow(1280, 720)
        // An item needs a world's registries, and the gallery shows items in its slots.
        context.worldBuilder().create().use {
            context.setScreen { KitGalleryScreen() }
            guiScale(context, 2)
            context.takeScreenshot("kit-charcoal")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("kit-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
            guiScale(context, 1)
            context.takeScreenshot("kit-charcoal-gui1")
            context.setScreen { null }
        }
        context.restoreDefaultGameOptions()
        context.runOnClient<Nothing> { it.resizeGui() }
        context.input.resizeWindow(window.first, window.second)
    }

    private fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}
