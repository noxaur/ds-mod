package io.github.noxaur.dsmod.gametest

import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.hear
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import io.github.noxaur.dsmod.gametest.SettingsSteps.theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText

/**
 * Stars settings with the real mouse and keyboard: a press on the star, the Favorite Key over a row or on the row
 * the keys are on, and a mouse button bound as that key. The key types into a focused search instead, and Ctrl+F
 * still focuses it. Favorites starts empty with a line that says how to fill it, lists starred rows in the tree's
 * order, and keeps a row unstarred there until the player leaves, so a quick second press stars it again. A search
 * finds nothing in Favorites, and the stars are saved when the screen closes.
 */
class FavoritesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        val settings = DsModClient.settings
        val driver = SettingsDriver(context)
        fun starred() = context.computeOnClient<List<String>, Nothing> { DsModClient.settings.favorites.toList() }
        fun shown() = context.computeOnClient<List<String>, Nothing> { (it.gui.screen() as SettingsScreen).shown() }

        /** Presses the star of the row [id], or puts the mouse on it with [press] false. */
        fun star(id: String, press: Boolean = true) {
            val spot = driver.spot(id)
            check(spot.starX >= 0) { "$id has no star" }
            if (press) driver.click(spot.starX + 3, spot.starY + 3) else driver.move(spot.starX + 3, spot.starY + 3)
        }

        /** Puts the mouse on the middle of the row [id], away from its control. */
        fun over(id: String) = driver.spot(id).let { driver.move(it.x + 20, it.y + it.height / 2) }

        try {
            context.runOnClient<Nothing> { settings.favorites = emptyList() }
            val tabs = SettingsTabs(Place(SettingsScreen.FAVORITES))
            open(context, tabs)
            check(shown().isEmpty()) { "An empty Favorites lists ${shown()}" }
            context.takeScreenshot("settings-favorites-empty")

            // A press on the star stars the row. Its star shows while the mouse is on it.
            context.runOnClient<Nothing> { tabs.current.go(at("General").section) }
            open(context, tabs)
            star("fullbright", press = false)
            context.takeScreenshot("settings-favorites-star-hover")
            star("fullbright")
            check(starred() == listOf("fullbright")) { "The press left ${starred()}" }
            hear(context, "Fullbright, switch, off, starred", "Over a starred row")

            // F over a row stars it, and types nothing into the search.
            over("hideExplosions")
            driver.key(GLFW.GLFW_KEY_F)
            check("hideExplosions" in starred()) { "F over Hide Explosions left ${starred()}" }
            check(!tabs.current.place.searching) { "F over a row started a search for \"${tabs.current.place.query}\"" }

            // Ctrl+F focuses the search, and F then types there and stars nothing.
            driver.key(GLFW.GLFW_KEY_F, control = true)
            check("hideExplosions" in starred()) { "Ctrl+F unstarred the row" }
            context.input.typeChars("ful")
            context.waitTicks(2)
            check(starred() == listOf("fullbright", "hideExplosions")) { "Typing in the search changed the stars: ${starred()}" }
            // A search finds Fullbright once, in its own section, and nothing in Favorites.
            check(shown() == listOf("fullbright")) { "Searching \"ful\" lists ${shown()}" }
            context.takeScreenshot("settings-favorites-search")
            driver.key(GLFW.GLFW_KEY_ESCAPE)
            context.runOnClient<Nothing> { tabs.current.search("") }
            open(context, tabs)

            // The keys' row takes the Favorite Key once they show, wherever the mouse is.
            over("fullbright")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            driver.key(GLFW.GLFW_KEY_DOWN)
            driver.move(2, 2)
            driver.key(GLFW.GLFW_KEY_F)
            check("hideExplosions" !in starred()) { "F on the keys' row left ${starred()}" }
            driver.key(GLFW.GLFW_KEY_F)
            check("hideExplosions" in starred()) { "F on the keys' row again left ${starred()}" }
            driver.key(GLFW.GLFW_KEY_ESCAPE)
            // That press on the row toggled Fullbright itself, so it goes back.
            context.runOnClient<Nothing> { settings.general.fullbright = false }

            // A mouse button bound as the Favorite Key stars the row under the mouse, and not with Ctrl held.
            context.runOnClient<Nothing> {
                settings.general.favoriteKey = GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                tabs.current.go(at("Combat").section)
            }
            open(context, tabs)
            over("rareDropAlert")
            context.input.holdControl()
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.input.releaseControl()
            context.waitTicks(2)
            check("rareDropAlert" !in starred()) { "Ctrl and the middle button starred Rare Drop Alert" }
            // A middle press with Ctrl is the row's own, which toggles the switch, so it goes back.
            context.runOnClient<Nothing> { settings.loot.rareDropAlert = true }
            over("rareDropAlert")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.waitTicks(2)
            check("rareDropAlert" in starred()) { "The middle button left ${starred()}" }
            context.runOnClient<Nothing> { settings.general.favoriteKey = GLFW.GLFW_KEY_F }

            // Favorites lists them in the tree's order, under their sections and groups.
            context.runOnClient<Nothing> { tabs.current.go(SettingsScreen.FAVORITES) }
            open(context, tabs)
            check(shown() == listOf("fullbright", "hideExplosions", "rareDropAlert")) { "Favorites lists ${shown()}" }
            context.takeScreenshot("settings-favorites")
            theme(context, Theme.VANILLA)
            context.takeScreenshot("settings-favorites-vanilla")
            theme(context, Theme.CHARCOAL)

            // A row unstarred here stays, so a second press lands on it and stars it again.
            star("fullbright")
            check("fullbright" !in starred() && shown().first() == "fullbright") { "The unstarred row moved: ${shown()}" }
            context.takeScreenshot("settings-favorites-kept")
            star("fullbright")
            check("fullbright" in starred()) { "The second press left ${starred()}" }

            // Once the player leaves Favorites, a row unstarred there goes.
            star("fullbright")
            context.runOnClient<Nothing> { tabs.current.go(at("General").section) }
            open(context, tabs)
            context.runOnClient<Nothing> { tabs.current.go(SettingsScreen.FAVORITES) }
            open(context, tabs)
            check(shown() == listOf("hideExplosions", "rareDropAlert")) { "After leaving, Favorites lists ${shown()}" }

            // Closing saves the stars.
            context.setScreen { TitleScreen() }
            val saved = JsonParser.parseString(FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()).asJsonObject
            check(saved["favorites"].asJsonArray.map { it.asString } == listOf("hideExplosions", "rareDropAlert")) { "The file holds ${saved["favorites"]}" }
        } finally {
            context.runOnClient<Nothing> {
                settings.favorites = emptyList()
                settings.general.favoriteKey = GLFW.GLFW_KEY_F
                DsModClient.saveSettings()
            }
            context.setScreen { TitleScreen() }
            restore(context, window)
        }
    }
}
