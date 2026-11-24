package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.world.fullbright.Fullbright
import io.github.noxaur.dsmod.core.feature.combat.loot.raredropalert.RareDropAlert
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.hear
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.savedConfig
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class FavoritesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        val settings = DsModClient.settings
        val driver = SettingsDriver(context)
        fun starred() = context.computeOnClient<List<String>, Nothing> { DsModClient.settings.favorites.toList() }
        fun shown() = context.computeOnClient<List<String>, Nothing> { (it.gui.screen() as SettingsScreen).shown() }

        fun star(id: String, press: Boolean = true) {
            val spot = driver.spot(id)
            check(spot.starX >= 0) { "$id has no star" }
            if (press) driver.click(spot.starX + 3, spot.starY + 3) else driver.move(spot.starX + 3, spot.starY + 3)
        }

        fun over(id: String) = driver.spot(id).let { driver.move(it.x + 20, it.y + it.height / 2) }

        try {
            context.runOnClient<Nothing> { settings.favorites = emptyList() }
            val tabs = SettingsTabs(Place(SettingsScreen.FAVORITES))
            open(context, tabs)
            check(shown().isEmpty()) { "An empty Favorites lists ${shown()}" }
            context.takeScreenshot("settings-favorites-empty")

            context.runOnClient<Nothing> { tabs.current.go(at("General").section) }
            open(context, tabs)
            star("fullbright", press = false)
            context.takeScreenshot("settings-favorites-star-hover")
            star("fullbright")
            check(starred() == listOf("fullbright")) { "The press left ${starred()}" }
            hear(context, "Fullbright, switch, off, starred", "Over a starred row")

            over("hide-explosions")
            driver.key(GLFW.GLFW_KEY_F)
            check("hide-explosions" in starred()) { "F over Hide Explosions left ${starred()}" }
            check(!tabs.current.place.searching) { "F over a row started a search for \"${tabs.current.place.query}\"" }

            driver.key(GLFW.GLFW_KEY_F, control = true)
            check("hide-explosions" in starred()) { "Ctrl+F unstarred the row" }
            context.input.typeChars("ful")
            context.waitTicks(2)
            check(starred() == listOf("fullbright", "hide-explosions")) { "Typing in the search changed the stars: ${starred()}" }
            check(shown() == listOf("fullbright")) { "Searching \"ful\" lists ${shown()}" }
            context.takeScreenshot("settings-favorites-search")
            driver.key(GLFW.GLFW_KEY_ESCAPE)
            context.runOnClient<Nothing> { tabs.current.search("") }
            open(context, tabs)

            over("fullbright")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            driver.key(GLFW.GLFW_KEY_DOWN)
            driver.move(2, 2)
            driver.key(GLFW.GLFW_KEY_F)
            check("hide-explosions" !in starred()) { "F on the keys' row left ${starred()}" }
            driver.key(GLFW.GLFW_KEY_F)
            check("hide-explosions" in starred()) { "F on the keys' row again left ${starred()}" }
            driver.key(GLFW.GLFW_KEY_ESCAPE)
            context.runOnClient<Nothing> { DsModClient.feature<Fullbright>().feature.enabled = false }

            context.runOnClient<Nothing> {
                settings.general.favoriteKey.value = GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                tabs.current.go(at("Combat").section)
            }
            open(context, tabs)
            over("rare-drop-alert")
            context.input.holdControl()
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.input.releaseControl()
            context.waitTicks(2)
            check("rare-drop-alert" !in starred()) { "Ctrl and the middle button starred Rare Drop Alert" }
            context.runOnClient<Nothing> { DsModClient.feature<RareDropAlert>().feature.enabled = true }
            over("rare-drop-alert")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.waitTicks(2)
            check("rare-drop-alert" in starred()) { "The middle button left ${starred()}" }
            context.runOnClient<Nothing> { settings.general.favoriteKey.value = GLFW.GLFW_KEY_F }

            context.runOnClient<Nothing> { tabs.current.go(SettingsScreen.FAVORITES) }
            open(context, tabs)
            check(shown() == listOf("fullbright", "hide-explosions", "rare-drop-alert")) { "Favorites lists ${shown()}" }
            context.shotInBothThemes("settings-favorites")

            star("fullbright")
            check("fullbright" !in starred() && shown().first() == "fullbright") { "The unstarred row moved: ${shown()}" }
            context.takeScreenshot("settings-favorites-kept")
            star("fullbright")
            check("fullbright" in starred()) { "The second press left ${starred()}" }

            star("fullbright")
            context.runOnClient<Nothing> { tabs.current.go(at("General").section) }
            open(context, tabs)
            context.runOnClient<Nothing> { tabs.current.go(SettingsScreen.FAVORITES) }
            open(context, tabs)
            check(shown() == listOf("hide-explosions", "rare-drop-alert")) { "After leaving, Favorites lists ${shown()}" }

            context.setScreen { TitleScreen() }
            val saved = savedConfig()
            check(saved["favorites"].asJsonArray.map { it.asString } == listOf("hide-explosions", "rare-drop-alert")) { "The file holds ${saved["favorites"]}" }
        } finally {
            context.runOnClient<Nothing> {
                settings.favorites = emptyList()
                settings.general.favoriteKey.value = GLFW.GLFW_KEY_F
                DsModClient.saveSettings()
            }
            context.setScreen { TitleScreen() }
            restore(context, window)
        }
    }
}
