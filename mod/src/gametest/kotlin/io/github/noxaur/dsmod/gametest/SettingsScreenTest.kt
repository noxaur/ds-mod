package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen

class SettingsScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        context.runOnClient<Nothing> { DsModClient.openSettings() }
        context.waitTicks(5)
        val screen = context.computeOnClient<Any?, Nothing> { it.gui.screen() }
        check(screen != null && screen !is TitleScreen) { "Settings screen did not open, got $screen" }
        context.takeScreenshot("settings")
        // MoulConfig lays out its own screen, so this clicks where it draws the last category, Interface.
        context.input.setCursorPos(200.0, 279.0)
        context.input.pressMouse(0)
        context.waitTicks(2)
        context.takeScreenshot("settings-interface")
        context.setScreen { TitleScreen() }
    }
}
