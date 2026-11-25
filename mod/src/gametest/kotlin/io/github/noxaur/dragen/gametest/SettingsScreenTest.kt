package io.github.noxaur.dragen.gametest

import com.terraformersmc.modmenu.ModMenu
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.SettingsScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class SettingsScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        fun screen() = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        fun place() = context.computeOnClient<Int, Nothing> { SettingsScreen.session(DragenClient.settings).current.place.section }

        context.waitForScreen(TitleScreen::class.java)
        check(DragenClient.settings.favorites.isEmpty() && place() != SettingsScreen.FAVORITES) { "The first open would show an empty Favorites" }

        val title = screen()
        val fromModMenu = context.computeOnClient<Screen?, Nothing> { ModMenu.getConfigScreen("dragen", title) }
        check(fromModMenu is SettingsScreen) { "Mod Menu's config button opens $fromModMenu" }
        context.setScreen { fromModMenu }
        context.waitTicks(2)
        context.takeScreenshot("settings")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(screen() === title) { "Escape from the settings went to ${screen()}, not back to Mod Menu's parent $title" }

        context.worldBuilder().create().use {
            context.waitTicks(5)
            context.runOnClient<Nothing> { it.connection!!.sendCommand("dragen") }
            context.waitTicks(3)
            check(screen() is SettingsScreen) { "/dragen opened ${screen()}" }
            context.takeScreenshot("settings-dragen")
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(screen() == null) { "Escape from /dragen's settings went to ${screen()}" }

            val general = DragenClient.settings.general
            try {
                general.settingsKey.value = GLFW.GLFW_KEY_KP_ADD
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(3)
                check(screen() is SettingsScreen) { "The Settings Key opened ${screen()}" }
            } finally {
                general.settingsKey.value = -1
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                context.waitTicks(2)
            }
        }
    }
}
