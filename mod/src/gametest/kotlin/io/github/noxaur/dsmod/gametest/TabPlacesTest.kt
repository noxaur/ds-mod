package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.menus.betterui.menutabs.MenuTabs
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class TabPlacesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT }
        // @keep A tab shows an item, and items need a world's registries.
        context.worldBuilder().create().use { TabsDriver(context).places() }
        context.runOnClient<Nothing> {
            DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT
            DsModClient.saveSettings()
        }
        restore(context, window)
    }

    private fun TabsDriver.places() {
        val tabs = { context.computeOnClient<List<MenuTab>, Nothing> { DsModClient.feature<MenuTabs>().tabs.value } }

        context.setScreen { DsModClient.settingsScreen(null, SettingsTabs(at("Menus")), reveal = "tabs") }
        context.waitTicks(2)
        spot("tabs")
        context.shotInBothThemes("tab-places")

        val from = place(TabEdge.BOTTOM, 0)
        val to = place(TabEdge.TOP, 0)
        hold(from.first, from.second)
        for (step in 1..4) move(from.first + (to.first - from.first) * step / 4, from.second + (to.second - from.second) * step / 4)
        context.shotInBothThemes("tab-places-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        val storage = tabs().first { it.command == "storage" }
        check(storage.edge == TabEdge.TOP && storage.slot == 0) { "Storage was dropped at ${storage.edge} ${storage.slot}" }

        val ec = place(TabEdge.BOTTOM, 1)
        hold(ec.first, ec.second)
        place(TabEdge.BOTTOM, 4).let { move(it.first, it.second) }
        escapeHeld()
        keptOpen()
        check(tabs().first { it.command == "ec" }.slot == 1) { "Escape did not put the dragged tab back" }

        place(TabEdge.BOTTOM, 5).let { click(it.first, it.second) }
        context.input.typeChars("/pets")
        context.waitTicks(2)
        key(GLFW.GLFW_KEY_TAB)
        repeat("paper".length) { key(GLFW.GLFW_KEY_BACKSPACE) }
        context.input.typeChars("bone")
        key(GLFW.GLFW_KEY_ENTER)
        val pets = tabs().last()
        check(pets.edge == TabEdge.BOTTOM && pets.slot == 5 && pets.command == "pets" && pets.item == "minecraft:bone") { "The new tab is $pets" }
        context.takeScreenshot("tab-places-added")

        key(GLFW.GLFW_KEY_RIGHT)
        check(tabs().last().slot == 6) { "Right did not move the tab: ${tabs().last()}" }
        key(GLFW.GLFW_KEY_DELETE)
        check(tabs().any { it.command == "pets" }) { "One press on Delete removed the tab" }
        context.takeScreenshot("tab-places-delete")
        key(GLFW.GLFW_KEY_DELETE)
        check(tabs().none { it.command == "pets" }) { "Two presses on Delete left ${tabs()}" }

        val row = spot("tabs")
        click(row.markerX + 1, row.markerY + 1)
        check(tabs() == TabLayout.DEFAULT) { "The marker left ${tabs()}" }

        place(TabEdge.BOTTOM, 2).let { click(it.first, it.second) }
        key(GLFW.GLFW_KEY_ESCAPE)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "Escape on the menu closed the settings" }
        key(GLFW.GLFW_KEY_ESCAPE)
        key(GLFW.GLFW_KEY_ESCAPE)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() == null }) { "Escape did not close the settings after leaving the menu" }
    }
}
