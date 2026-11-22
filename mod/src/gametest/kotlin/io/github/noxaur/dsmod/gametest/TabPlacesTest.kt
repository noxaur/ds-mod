package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
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

/**
 * Drives Menu Tabs' places in the settings: drags a tab to the top, cancels a drag with Escape, adds a tab on an
 * empty place and types its command and item, moves it with an arrow key, deletes it with a second press, and puts
 * every tab back with the changed marker. Escape leaves the menu before it closes the screen. Read the screenshots
 * in both themes, mid-drag among them.
 */
class TabPlacesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.runOnClient<Nothing> { DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT }
        // A tab shows an item, and items need a world's registries.
        context.worldBuilder().create().use { TabsDriver(context).places() }
        context.runOnClient<Nothing> {
            DsModClient.feature<MenuTabs>().tabs.value = TabLayout.DEFAULT
            DsModClient.saveSettings()
        }
        restore(context, window)
    }

    private fun TabsDriver.places() {
        val tabs = { context.computeOnClient<List<MenuTab>, Nothing> { DsModClient.feature<MenuTabs>().tabs.value } }

        context.setScreen { SettingsScreen(null, SettingsTabs(at("Menus")), reveal = "tabs") }
        context.waitTicks(2)
        spot("tabs")
        context.shotInBothThemes("tab-places")

        // Storage dragged from the bottom-left to the top-left follows the mouse, and lands there.
        val from = place(TabEdge.BOTTOM, 0)
        val to = place(TabEdge.TOP, 0)
        hold(from.first, from.second)
        for (step in 1..4) move(from.first + (to.first - from.first) * step / 4, from.second + (to.second - from.second) * step / 4)
        context.shotInBothThemes("tab-places-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        val storage = tabs().first { it.command == "storage" }
        check(storage.edge == TabEdge.TOP && storage.slot == 0) { "Storage was dropped at ${storage.edge} ${storage.slot}" }

        // Escape during a drag puts the tab back, and leaves the screen open.
        val ec = place(TabEdge.BOTTOM, 1)
        hold(ec.first, ec.second)
        place(TabEdge.BOTTOM, 4).let { move(it.first, it.second) }
        escapeHeld()
        keptOpen()
        check(tabs().first { it.command == "ec" }.slot == 1) { "Escape did not put the dragged tab back" }

        // A press on an empty place adds a tab there with its command to type, and the item, reached with Tab, is typed by its name.
        place(TabEdge.BOTTOM, 5).let { click(it.first, it.second) }
        context.input.typeChars("/pets")
        context.waitTicks(2)
        // Tab goes on from the command to the item.
        key(GLFW.GLFW_KEY_TAB)
        // The test's keyboard sends no modifiers with a key, so the field is emptied with Backspace.
        repeat("paper".length) { key(GLFW.GLFW_KEY_BACKSPACE) }
        context.input.typeChars("bone")
        key(GLFW.GLFW_KEY_ENTER)
        val pets = tabs().last()
        check(pets.edge == TabEdge.BOTTOM && pets.slot == 5 && pets.command == "pets" && pets.item == "minecraft:bone") { "The new tab is $pets" }
        context.takeScreenshot("tab-places-added")

        // With no field taking the keys, Right moves the tab, and Delete asks for a second press.
        key(GLFW.GLFW_KEY_RIGHT)
        check(tabs().last().slot == 6) { "Right did not move the tab: ${tabs().last()}" }
        key(GLFW.GLFW_KEY_DELETE)
        check(tabs().any { it.command == "pets" }) { "One press on Delete removed the tab" }
        context.takeScreenshot("tab-places-delete")
        key(GLFW.GLFW_KEY_DELETE)
        check(tabs().none { it.command == "pets" }) { "Two presses on Delete left ${tabs()}" }

        // The changed marker puts every tab back where it was.
        val row = spot("tabs")
        click(row.markerX + 1, row.markerY + 1)
        check(tabs() == TabLayout.DEFAULT) { "The marker left ${tabs()}" }

        // Escape leaves the menu, then gives the keys back, then closes the screen.
        place(TabEdge.BOTTOM, 2).let { click(it.first, it.second) }
        key(GLFW.GLFW_KEY_ESCAPE)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "Escape on the menu closed the settings" }
        key(GLFW.GLFW_KEY_ESCAPE)
        key(GLFW.GLFW_KEY_ESCAPE)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() == null }) { "Escape did not close the settings after leaving the menu" }
    }
}
