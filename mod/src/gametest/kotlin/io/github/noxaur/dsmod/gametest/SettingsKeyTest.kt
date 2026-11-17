package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

/**
 * The ways into the settings besides `/dsmod`: the Settings Key opens them in the world and not from chat, and has no
 * key until the player picks one. `/dsm` is the short name, and words after either command search.
 */
class SettingsKeyTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val settings = DsModClient.settings.general
        fun screen() = context.computeOnClient<String, Nothing> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
        fun open() = screen() == "SettingsScreen"
        fun close() {
            context.runOnClient<Nothing> { it.gui.setScreen(null) }
            context.waitTicks(2)
        }

        context.worldBuilder().create().use {
            try {
                context.waitTicks(5)
                check(settings.settingsKey == -1) { "The Settings Key starts on ${settings.settingsKey}" }
                // A key the game leaves free, since O and the like are the game's own now.
                settings.settingsKey = GLFW.GLFW_KEY_KP_ADD
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(3)
                check(open()) { "The Settings Key opened ${screen()}" }
                context.takeScreenshot("settings-key")
                close()

                // A key typed into chat is not a press.
                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitTicks(2)
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(3)
                check(screen() == "ChatScreen") { "The key typed into chat opened ${screen()}" }
                close()

                context.runOnClient<Nothing> { it.connection!!.sendCommand("dsm") }
                context.waitTicks(3)
                check(open()) { "/dsm opened ${screen()}" }
                close()

                context.runOnClient<Nothing> { it.connection!!.sendCommand("dsmod zealot") }
                context.waitTicks(3)
                check(open()) { "/dsmod zealot opened ${screen()}" }
                val found = context.computeOnClient<List<String>, Nothing> { (it.gui.screen() as SettingsScreen).shown() }
                val query = context.computeOnClient<String, Nothing> { SettingsScreen.session().current.place.query }
                check(query == "zealot" && "zealot-tracker" in found) { "/dsmod zealot searched \"$query\" and lists $found" }
                context.takeScreenshot("settings-search")
                context.runOnClient<Nothing> { SettingsScreen.session().current.search("") }
            } finally {
                settings.settingsKey = -1
                close()
            }
        }
    }
}
