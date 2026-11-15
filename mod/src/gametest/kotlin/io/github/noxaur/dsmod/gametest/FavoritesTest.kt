package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Favorites
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Settings
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Field
import kotlin.io.path.readText

/**
 * Stars a setting with the real mouse and finds it in Favorites, in its own place among the defaults, then unstars it
 * there. A search hides Favorites, the star is saved when the screen closes, and the screen opens again where the
 * player left it. The Settings Key opens it in the world and not from chat, and `/dsm` and `/dsmod <search>` open it too.
 */
class FavoritesTest : FabricClientGameTest {
    private val fullbright = DsModConfig.General::class.java.getField("fullbright")

    override fun runTest(context: ClientGameTestContext) {
        val settings = DsModClient.settings
        fun screen() = context.computeOnClient<String, Nothing> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
        fun open() = screen().contains("MoulConfig")
        fun shown(): List<String> = context.computeOnClient<List<String>, Nothing> { Settings.favorites }
        fun listed(): Set<String> = context.computeOnClient<Set<String>, Nothing> { Settings.listed }
        fun category(field: Field) = context.computeOnClient<String, Nothing> { Settings.category(field) }

        /** Moves the mouse onto the star of [field] where it was last drawn. */
        fun point(field: Field) {
            val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { Settings.star(field) }
            val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
            context.input.setCursorPos((x + 3) * scale, (y + 3) * scale)
            context.waitTicks(2)
        }

        fun press(field: Field) {
            point(field)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
        }

        context.worldBuilder().create().use {
            try {
                context.waitTicks(20)
                settings.favorites = Favorites.DEFAULT

                // Favorites is the first category, and lists the defaults in the order the settings declare them.
                context.runOnClient<Nothing> {
                    Settings.open()
                    Settings.showFavorites()
                }
                context.waitTicks(10)
                check(open()) { "The settings did not open, got ${screen()}" }
                check(listed().first() == Settings.FAVORITES) { "Favorites is not first in ${listed()}" }
                check(shown() == Favorites.DEFAULT) { "Favorites shows ${shown()}" }
                context.takeScreenshot("settings-favorites")

                // A press on Fullbright's star stars it, and Favorites lists it first, since General comes first.
                context.runOnClient<Nothing> { DsModClient.openSettings(fullbright) }
                context.waitTicks(10)
                check("general.fullbright" !in settings.favorites) { "Fullbright starts starred" }
                point(fullbright)
                context.takeScreenshot("settings-star-hover")
                press(fullbright)
                check(settings.favorites.last() == "general.fullbright") { "The press left ${settings.favorites}" }
                check(settings.general.fullbright == false) { "The press on the star also switched Fullbright on" }
                check(shown() == listOf("general.fullbright") + Favorites.DEFAULT) { "Favorites shows ${shown()}" }
                context.input.setCursorPos(0.0, 0.0)
                context.waitTicks(2)
                context.takeScreenshot("settings-starred")

                // A search finds each setting once, in its own category, so Favorites is hidden while it runs.
                context.runOnClient<Nothing> { Settings.search("fullbright") }
                context.waitTicks(2)
                check((Settings.FAVORITES in listed()) == Favorites.listed("fullbright") && category(fullbright) in listed()) { "Searching showed ${listed()}" }
                context.runOnClient<Nothing> { Settings.search("") }

                // The star in Favorites unstars it there.
                context.runOnClient<Nothing> { Settings.showFavorites() }
                context.waitTicks(10)
                press(fullbright)
                check("general.fullbright" !in settings.favorites && shown() == Favorites.DEFAULT) { "Unstarring in Favorites left ${shown()}" }

                // Closing saves the stars, and the screen opens again on Favorites.
                context.waitTicks(5)
                val loot = DsModConfig.Loot::class.java.getField("lootAlerts")
                press(loot)
                check("loot.lootAlerts" !in settings.favorites) { "Loot Alerts is still starred" }
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTicks(5)
                check(!open()) { "Escape left ${screen()}" }
                val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
                check("\"slayer.slayerTracker\"" in saved && "\"loot.lootAlerts\"" !in saved) { "The config file holds $saved" }
                context.runOnClient<Nothing> { DsModClient.openSettings() }
                context.waitTicks(5)
                check(context.computeOnClient<String, Nothing> { Settings.selected } == Settings.FAVORITES) { "The settings did not open on Favorites" }
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTicks(5)

                // The Settings Key has no key until the player picks one, and then opens the settings from the world.
                check(settings.general.settingsKey == -1) { "The Settings Key starts on ${settings.general.settingsKey}" }
                settings.general.settingsKey = GLFW.GLFW_KEY_KP_ADD
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(5)
                check(open()) { "The Settings Key opened ${screen()}" }
                context.takeScreenshot("settings-key")
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTicks(5)

                // A key typed into chat is not a press.
                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitTicks(2)
                context.input.pressKey(GLFW.GLFW_KEY_KP_ADD)
                context.waitTicks(5)
                check(screen() == "ChatScreen") { "The key typed into chat opened ${screen()}" }
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                settings.general.settingsKey = -1

                // `/dsm` is the short name, and words after it search.
                context.runOnClient<Nothing> { it.connection!!.sendCommand("dsm") }
                context.waitTicks(5)
                check(open()) { "/dsm opened ${screen()}" }
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                context.waitTicks(2)
                context.runOnClient<Nothing> { it.connection!!.sendCommand("dsmod zealot") }
                context.waitTicks(5)
                val found = listed()
                check(open() && found == setOf(category(DsModConfig.End::class.java.getField("zealotTracker")))) { "/dsmod zealot opened ${screen()} on $found" }
                context.takeScreenshot("settings-search")
                context.runOnClient<Nothing> { Settings.search("") }
            } finally {
                settings.favorites = Favorites.DEFAULT
                settings.general.settingsKey = -1
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
            }
        }
    }
}
