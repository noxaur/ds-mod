package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.KitGalleryScreen
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KClass

/**
 * Screenshots the kit's gallery in each theme at GUI scale 2, and in Charcoal at GUI scale 1, then its pages of
 * settings rows, lists and panels, which together must show every kind of row and every look.
 */
class KitTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        // The gallery is 575x335 GUI pixels with a glass strip 47 below it, which needs more room than the test window has at GUI scale 2.
        context.input.resizeWindow(1280, 800)
        // An item needs a world's registries, and the gallery shows items in its slots.
        context.worldBuilder().create().use {
            context.setScreen { KitGalleryScreen() }
            guiScale(context, 2)
            context.takeScreenshot("kit-charcoal")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-vanilla") }
            guiScale(context, 1)
            context.takeScreenshot("kit-charcoal-gui1")
            guiScale(context, 2)
            rows(context)
            context.setScreen { null }
        }
        context.restoreDefaultGameOptions()
        context.runOnClient<Nothing> { it.resizeGui() }
        context.input.resizeWindow(window.first, window.second)
    }

    /** The settings rows' page: every kind and every look, in both themes, with a choice's and a pick's menu open. */
    private fun rows(context: ClientGameTestContext) {
        val kinds = HashSet<Kind>()
        val looks = HashSet<KClass<out Look>>()
        for (page in listOf(SettingsGalleryScreen.Page.LISTS, SettingsGalleryScreen.Page.PANELS)) {
            context.setScreen { SettingsGalleryScreen(page) }
            context.waitTicks(2)
            context.point(0, 0, settle = 2)
            val name = page.name.lowercase()
            context.takeScreenshot("kit-$name-charcoal")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-$name-vanilla") }
            context.runOnClient<Nothing> { client -> (client.gui.screen() as SettingsGalleryScreen).let { kinds += it.kinds; looks += it.looks } }
        }
        context.setScreen { SettingsGalleryScreen() }
        context.waitTicks(2)
        context.runOnClient<Nothing> { client -> (client.gui.screen() as SettingsGalleryScreen).let { kinds += it.kinds; looks += it.looks } }
        val missing = Kind.entries.filter { it !in kinds } + Look::class.sealedSubclasses.filter { it !in looks }.map { it.simpleName }
        check(missing.isEmpty()) { "No page of the gallery of settings rows shows $missing" }
        fun press(id: String) {
            val spot = context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).spot(id) } ?: error("No row $id")
            context.click(spot.controlX + 8, spot.controlY + 8, settle = 2)
        }
        val distance = context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).spot("distance") }!!
        context.point(distance.x + 20, distance.y + 6, settle = 2)
        context.takeScreenshot("kit-rows-charcoal")
        context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-rows-vanilla") }
        press("tier")
        context.takeScreenshot("kit-rows-choice-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        press("preset")
        context.shotInBothThemes("kit-rows-pick-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
    }

    private fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}
