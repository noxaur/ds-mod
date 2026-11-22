package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.KitGalleryScreen
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.gametest.SettingsSteps.guiScale
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KClass

/** @keep Covers [io.github.noxaur.dsmod.edge.Browser], [io.github.noxaur.dsmod.edge.mixin.MouseTurnMixin]. */
class KitTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = SettingsSteps.windowSize(context)
        context.input.resizeWindow(1280, 800)
        // @keep An item needs a world's registries, and the gallery shows items in its slots.
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
        SettingsSteps.restore(context, window)
    }

    private fun rows(context: ClientGameTestContext) {
        val kinds = HashSet<Kind>()
        val looks = HashSet<KClass<out Look>>()
        fun collect() = context.runOnClient<Nothing> { client -> (client.gui.screen() as SettingsGalleryScreen).let { kinds += it.kinds; looks += it.looks } }
        for (page in listOf(SettingsGalleryScreen.Page.LISTS, SettingsGalleryScreen.Page.PANELS)) {
            context.setScreen { SettingsGalleryScreen(page) }
            context.waitTicks(2)
            context.point(0, 0, settle = 2)
            val name = page.name.lowercase()
            context.takeScreenshot("kit-$name-charcoal")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("kit-$name-vanilla") }
            collect()
        }
        context.setScreen { SettingsGalleryScreen() }
        context.waitTicks(2)
        collect()
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
}
