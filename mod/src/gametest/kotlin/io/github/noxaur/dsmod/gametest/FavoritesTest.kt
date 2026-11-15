package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Favorites
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Settings
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Field
import kotlin.io.path.readText

/**
 * Stars settings with the real mouse and keyboard: a press on the star, the Favorite Key over a setting, and a mouse
 * button bound as that key. The key types into a focused search instead, and Ctrl+F still focuses it. Favorites starts
 * empty with a line that says how to fill it, lists starred settings in their own order, and keeps a row unstarred there
 * until the player leaves, so a quick second press stars it again. A search hides Favorites, and the stars are saved
 * when the screen closes, which opens again where the player left it.
 */
class FavoritesTest : FabricClientGameTest {
    private val fullbright = DsModConfig.General::class.java.getField("fullbright")
    private val particles = DsModConfig.General::class.java.getField("explosionParticleHider")
    private val loot = DsModConfig.Loot::class.java.getField("lootAlerts")

    override fun runTest(context: ClientGameTestContext) {
        val settings = DsModClient.settings
        fun screen() = context.computeOnClient<String, Nothing> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
        fun open() = screen().contains("MoulConfig")
        fun shown(): List<String> = context.computeOnClient<List<String>, Nothing> { Settings.favorites }
        fun listed(): Set<String> = context.computeOnClient<Set<String>, Nothing> { Settings.listed }
        fun category(field: Field) = context.computeOnClient<String, Nothing> { Settings.category(field) }
        fun show(category: String) {
            context.runOnClient<Nothing> { Settings.show(category) }
            context.waitTicks(3)
        }

        /** Moves the mouse to [dx], [dy] from the star of [field], where it was last drawn. */
        fun point(field: Field, dx: Int = 3, dy: Int = 3) {
            val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { Settings.star(field) }
            val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
            context.input.setCursorPos((x + dx) * scale, (y + dy) * scale)
            context.waitTicks(2)
        }

        fun press(field: Field) {
            point(field)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
        }

        fun ctrlF() {
            context.input.holdKey(GLFW.GLFW_KEY_LEFT_CONTROL)
            context.input.pressKey(GLFW.GLFW_KEY_F)
            context.input.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL)
            context.waitTicks(2)
        }

        context.waitForScreen(TitleScreen::class.java)
        try {
            settings.favorites = emptyList()

            // Favorites is first and starts empty, with a line that says how to fill it.
            context.runOnClient<Nothing> { Settings.open() }
            show(Settings.FAVORITES)
            check(open()) { "The settings did not open, got ${screen()}" }
            check(listed().first() == Settings.FAVORITES && shown().isEmpty()) { "Favorites is not first and empty: ${listed()}, ${shown()}" }
            context.takeScreenshot("settings-favorites-empty")

            // A press on Fullbright's star stars it and nothing else.
            context.runOnClient<Nothing> { DsModClient.openSettings(fullbright) }
            context.waitTicks(5)
            point(fullbright)
            context.takeScreenshot("settings-star-hover")
            press(fullbright)
            check(settings.favorites == listOf("general.fullbright")) { "The press left ${settings.favorites}" }
            check(!settings.general.fullbright) { "The press on the star also switched Fullbright on" }

            // F over a setting, away from its star, stars it, and its letter does not land in the search.
            point(particles, dx = 30, dy = 12)
            context.input.pressKey(GLFW.GLFW_KEY_F)
            context.waitTicks(2)
            check("general.explosionParticleHider" in settings.favorites) { "F over Explosion Particle Hider left ${settings.favorites}" }
            check(Settings.FAVORITES in listed()) { "F typed into the search, which shows ${listed()}" }
            context.input.setCursorPos(0.0, 0.0)
            context.waitTicks(2)
            context.takeScreenshot("settings-starred")

            // Ctrl+F is MoulConfig's, and focuses the search. F then types there and stars nothing.
            point(particles, dx = 30, dy = 12)
            ctrlF()
            check("general.explosionParticleHider" in settings.favorites) { "Ctrl+F unstarred the setting" }
            context.input.typeChars("ful")
            context.waitTicks(2)
            check("general.explosionParticleHider" in settings.favorites) { "Typing in the search changed the stars: ${settings.favorites}" }
            // A search hides Favorites, as the rule in core says, and finds Fullbright in its own category.
            check((Settings.FAVORITES in listed()) == Favorites.listed("ful") && category(fullbright) in listed()) { "Searching showed ${listed()}" }
            ctrlF()
            context.runOnClient<Nothing> { Settings.search("") }
            context.waitTicks(2)

            // A setting scrolled partly under the header takes neither the key nor a press there, only inside the pane.
            context.runOnClient<Nothing> { DsModClient.openSettings(fullbright) }
            // MoulConfig slides its scroll over 150 ms, so the stars are read once it has stopped.
            context.waitTicks(10)
            val top = context.computeOnClient<Pair<Int, Int>, Nothing> { Settings.star(fullbright) }.second
            point(particles, dx = 30, dy = 12)
            context.input.scroll(-1.0)
            context.waitTicks(10)
            val scrolled = context.computeOnClient<Pair<Int, Int>, Nothing> { Settings.star(fullbright) }.second
            // The first box starts 6 above its star and the pane a little above that, so 16 above the star is the header.
            // That point must fall on Fullbright's box, which now starts 6 above its star and runs over 30 below it.
            check(top - 16 in scrolled - 6..scrolled + 30) { "One notch moved Fullbright's star from $top to $scrolled, which leaves the header off its box" }
            point(fullbright, dx = 30, dy = top - 16 - scrolled)
            context.input.pressKey(GLFW.GLFW_KEY_F)
            context.waitTicks(2)
            point(fullbright, dx = 3, dy = top - 16 - scrolled)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            check(settings.favorites == listOf("general.fullbright", "general.explosionParticleHider")) { "F or a press over the header left ${settings.favorites}" }

            // A mouse button bound as the Favorite Key works the same way.
            settings.general.favoriteKey = GLFW.GLFW_MOUSE_BUTTON_MIDDLE
            context.runOnClient<Nothing> { DsModClient.openSettings(loot) }
            context.waitTicks(5)
            point(loot, dx = 30, dy = 12)
            // With Ctrl held it is left alone, as the keyboard's key is.
            context.input.holdKey(GLFW.GLFW_KEY_LEFT_CONTROL)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.input.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL)
            context.waitTicks(2)
            check("loot.lootAlerts" !in settings.favorites) { "Ctrl and the middle button starred Loot Alerts" }
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
            context.waitTicks(2)
            check("loot.lootAlerts" in settings.favorites) { "The middle button left ${settings.favorites}" }
            settings.general.favoriteKey = GLFW.GLFW_KEY_F

            // Favorites lists them in the settings' own order.
            show(Settings.FAVORITES)
            check(shown() == listOf("general.fullbright", "general.explosionParticleHider", "loot.lootAlerts")) { "Favorites shows ${shown()}" }
            context.takeScreenshot("settings-favorites")

            // A quick second press on a star unstarred here lands on the same row, which stays, and stars it again.
            point(fullbright)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(1)
            check("general.fullbright" !in settings.favorites && shown().first() == "general.fullbright") { "The unstarred row moved: ${shown()}" }
            context.input.setCursorPos(0.0, 0.0)
            context.waitTicks(2)
            context.takeScreenshot("settings-favorites-kept")
            point(fullbright)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(2)
            check("general.fullbright" in settings.favorites && "general.explosionParticleHider" in settings.favorites) {
                "The second press left ${settings.favorites}"
            }

            // A row unstarred here goes once the player leaves Favorites.
            press(fullbright)
            show(category(fullbright))
            show(Settings.FAVORITES)
            check(shown() == listOf("general.explosionParticleHider", "loot.lootAlerts")) { "After leaving, Favorites shows ${shown()}" }

            // Closing saves the stars, and the screen opens again on Favorites.
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(3)
            check(!open()) { "Escape left ${screen()}" }
            val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
            check("\"loot.lootAlerts\"" in saved && "\"general.fullbright\"" !in saved) { "The config file holds $saved" }
            context.runOnClient<Nothing> { DsModClient.openSettings() }
            context.waitTicks(3)
            check(context.computeOnClient<String, Nothing> { Settings.selected } == Settings.FAVORITES) { "The settings did not open on Favorites" }
        } finally {
            settings.favorites = emptyList()
            settings.general.favoriteKey = GLFW.GLFW_KEY_F
            context.setScreen { TitleScreen() }
        }
    }
}
