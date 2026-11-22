package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KClass

class KitRowsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = KitTest.inKit(context) {
        val kinds = HashSet<Kind>()
        val looks = HashSet<KClass<out Look>>()
        for (page in listOf(SettingsGalleryScreen.Page.LISTS, SettingsGalleryScreen.Page.PANELS, SettingsGalleryScreen.Page.ROWS)) {
            context.setScreen { SettingsGalleryScreen(page) }
            context.waitTicks(2)
            context.runOnClient<Nothing> { client -> (client.gui.screen() as SettingsGalleryScreen).let { kinds += it.kinds; looks += it.looks } }
        }
        val missing = Kind.entries.filter { it !in kinds } + Look::class.sealedSubclasses.filter { it !in looks }.map { it.simpleName }
        check(missing.isEmpty()) { "No page of the gallery of settings rows shows $missing" }
        fun press(id: String) {
            val spot = context.computeOnClient<Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).settingRows.spot(id) } ?: error("No row $id")
            context.click(spot.controlX + 8, spot.controlY + 8, settle = 2)
        }
        val distance = context.computeOnClient<Spot?, Nothing> { (it.gui.screen() as SettingsGalleryScreen).settingRows.spot("distance") }!!
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
