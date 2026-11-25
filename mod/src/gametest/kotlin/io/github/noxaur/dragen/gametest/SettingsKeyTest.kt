package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.SettingsScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

class SettingsKeyTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val settings = DragenClient.settings.general
        val unclaimedKey = GLFW.GLFW_KEY_KP_ADD
        fun screen() = context.computeOnClient<String, Nothing> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
        fun open() = screen() == "SettingsScreen"
        fun close() {
            context.runOnClient<Nothing> { it.gui.setScreen(null) }
            context.waitTicks(2)
        }

        context.worldBuilder().create().use {
            try {
                context.waitTicks(5)
                check(settings.settingsKey.value == -1) { "The Settings Key starts on ${settings.settingsKey.value}" }
                settings.settingsKey.value = unclaimedKey
                context.input.pressKey(unclaimedKey)
                context.waitTicks(3)
                check(open()) { "The Settings Key opened ${screen()}" }
                context.takeScreenshot("settings-key")
                close()

                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitTicks(2)
                context.input.pressKey(unclaimedKey)
                context.waitTicks(3)
                check(screen() == "ChatScreen") { "The key typed into chat opened ${screen()}" }
                close()

                for (alias in listOf("dg", "dragon")) {
                    context.runOnClient<Nothing> { it.connection!!.sendCommand(alias) }
                    context.waitTicks(3)
                    check(open()) { "/$alias opened ${screen()}" }
                    close()
                }

                context.runOnClient<Nothing> { it.connection!!.sendCommand("dragen zealot") }
                context.waitTicks(3)
                check(open()) { "/dragen zealot opened ${screen()}" }
                val found = context.computeOnClient<List<String>, Nothing> { (it.gui.screen() as SettingsScreen).shown() }
                val query = context.computeOnClient<String, Nothing> { SettingsScreen.session(DragenClient.settings).current.place.query }
                check(query == "zealot" && "zealot-tracker" in found) { "/dragen zealot searched \"$query\" and lists $found" }
                context.takeScreenshot("settings-search")
                context.runOnClient<Nothing> { SettingsScreen.session(DragenClient.settings).current.search("") }
            } finally {
                settings.settingsKey.value = -1
                close()
            }
        }
    }
}
