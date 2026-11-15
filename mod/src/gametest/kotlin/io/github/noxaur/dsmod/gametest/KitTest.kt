package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.KitGalleryScreen
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KClass

/**
 * Screenshots the kit's gallery in each theme at GUI scale 2, and in Charcoal at GUI scale 1, then its page of
 * settings rows, which must show every kind of row and every look.
 */
class KitTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        // The gallery is 575x335 GUI pixels, which needs more room than the test window has at GUI scale 2.
        context.input.resizeWindow(1280, 720)
        // An item needs a world's registries, and the gallery shows items in its slots.
        context.worldBuilder().create().use {
            context.setScreen { KitGalleryScreen() }
            guiScale(context, 2)
            context.takeScreenshot("kit-charcoal")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("kit-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
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
        context.setScreen { SettingsGalleryScreen() }
        context.waitTicks(2)
        val (kinds, looks) = context.computeOnClient<Pair<Set<Kind>, Set<KClass<out Look>>>, Nothing> { client ->
            (client.gui.screen() as SettingsGalleryScreen).let { it.kinds to it.looks }
        }
        val missing = Kind.entries.filter { it !in kinds } + Look::class.sealedSubclasses.filter { it !in looks }.map { it.simpleName }
        check(missing.isEmpty()) { "The gallery of settings rows does not show $missing" }
        fun press(id: String) {
            val spot = context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).spot(id) } ?: error("No row $id")
            context.input.setCursorPos((spot.controlX + 8) * 2.0, (spot.controlY + 8) * 2.0)
            context.waitTick()
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        val distance = context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).spot("distance") }!!
        context.input.setCursorPos((distance.x + 20) * 2.0, (distance.y + 6) * 2.0)
        context.waitTicks(2)
        context.takeScreenshot("kit-rows-charcoal")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("kit-rows-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
        press("tier")
        context.takeScreenshot("kit-rows-choice-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        press("preset")
        context.takeScreenshot("kit-rows-pick-menu")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("kit-rows-pick-menu-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
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
