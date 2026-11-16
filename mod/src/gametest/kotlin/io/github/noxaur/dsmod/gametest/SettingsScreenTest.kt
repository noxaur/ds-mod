package io.github.noxaur.dsmod.gametest

import com.terraformersmc.modmenu.ModMenu
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * The jars a player installs open ds-mod's own settings every way the player can ask: `/dsmod`, the Settings Key in
 * the world, and Mod Menu's config button, each going back to where it came from. With nothing starred, the first
 * open shows the first section of the tree and not an empty Favorites.
 */
class SettingsScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        fun screen() = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        fun place() = context.computeOnClient<Int, Nothing> { SettingsScreen.session().current.place.section }

        context.waitForScreen(TitleScreen::class.java)
        check(DsModClient.settings.favorites.isEmpty() && place() != SettingsScreen.FAVORITES) { "The first open would show an empty Favorites" }

        // Mod Menu's config button for ds-mod, which goes back to the screen it was pressed on.
        val title = screen()
        val fromModMenu = context.computeOnClient<Screen?, Nothing> { ModMenu.getConfigScreen("dsmod", title) }
        check(fromModMenu is SettingsScreen) { "Mod Menu's config button opens $fromModMenu" }
        context.setScreen { fromModMenu }
        context.waitTicks(2)
        context.takeScreenshot("settings")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(screen() === title) { "Escape from the settings went to ${screen()}, not back to Mod Menu's parent $title" }

        context.worldBuilder().create().use {
            context.waitTicks(5)
            context.runOnClient<Nothing> { it.connection!!.sendCommand("dsmod") }
            context.waitTicks(3)
            check(screen() is SettingsScreen) { "/dsmod opened ${screen()}" }
            context.takeScreenshot("settings-dsmod")
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(screen() == null) { "Escape from /dsmod's settings went to ${screen()}" }

            val general = DsModClient.settings.general
            try {
                general.settingsKey = GLFW.GLFW_KEY_KP_ADD
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(3)
                check(screen() is SettingsScreen) { "The Settings Key opened ${screen()}" }
            } finally {
                general.settingsKey = -1
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                context.waitTicks(2)
            }
        }
    }
}
