package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Settings
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen

class SettingsScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        context.runOnClient<Nothing> { DsModClient.openSettings() }
        context.waitTicks(20)
        val screen = context.computeOnClient<Any?, Nothing> { it.gui.screen() }
        check(screen != null && screen !is TitleScreen) { "Settings screen did not open, got $screen" }
        // With nothing starred, the first open shows the first of the player's categories and not an empty Favorites.
        check(DsModClient.settings.favorites.isEmpty() && context.computeOnClient<String, Nothing> { Settings.selected } != Settings.FAVORITES) {
            "The first open showed an empty Favorites"
        }
        context.takeScreenshot("settings")

        // A dropdown is the one editor besides the switch and the button, so look at it once too.
        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.Loot::class.java.getField("lootAlertTier")) }
        context.waitTicks(20)
        context.takeScreenshot("settings-dropdown")

        // The draggable list picks and orders what Equipment Display shows.
        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.General::class.java.getField("equipmentPieces")) }
        context.waitTicks(20)
        context.takeScreenshot("settings-equipment-pieces")

        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.General::class.java.getField("onlyOnDragSim")) }
        context.waitTicks(20)
        context.takeScreenshot("settings-general")

        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.Hud::class.java.getField("theme")) }
        context.waitTicks(20)
        context.takeScreenshot("settings-interface")
        context.setScreen { TitleScreen() }
    }
}
