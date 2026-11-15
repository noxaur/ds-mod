package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.expect
import io.github.noxaur.dsmod.gametest.SettingsSteps.guiScale
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import io.github.noxaur.dsmod.gametest.SettingsSteps.theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Screenshots ds-mod's own settings screen in both themes: a section with its subsections, a row's tooltip, every
 * section expanded, a search with hits and one with none, the narrow sidebar at GUI scale 3, and the tab bar with
 * several tabs, a blank one, a drag, an overflow and the 427x240 size. Each tab press, drag and key is checked
 * against the tabs, and the tabs outlive the screen. Escape goes back to the screen it was opened over.
 * `SettingRowsTest` drives the rows.
 */
class OwnSettingsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = SettingsDriver(context).screen()

    private fun SettingsDriver.screen() {
        val window = setUp(context)
        open(context, SettingsTabs(Place(0)))
        context.takeScreenshot("own-settings-charcoal")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-vanilla")
        theme(context, Theme.CHARCOAL)

        // The sidebar's list starts 24 down, with sections 16 tall and subsections 13.

        // Inventory is under General's four subsections, Camera, Binds, Interface and Chat. Its first row, under the mouse, lifts.
        click(30, 24 + 16 + 4 * 13 + 4 * 16 + 8)
        move(160, 18 + 8 + 2 + 12 + 4 + 10)
        context.waitTicks(10)
        context.takeScreenshot("own-settings-hover")

        // The button beside Done collapses every section while one is open, and then expands them all.
        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-collapsed")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-expanded")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)

        // From the top: Down past General's four subsections and seven sections to Combat, Right opens it, Down onto Loot, and Enter jumps there.
        open(context, SettingsTabs(Place(0)))
        for (k in List(12) { GLFW.GLFW_KEY_DOWN } + listOf(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER)) {
            context.input.pressKey(k)
            context.waitTick()
        }
        context.waitTicks(10)
        context.takeScreenshot("own-settings-keys")
        // The wheel over the pane scrolls it.
        move(400, 200)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-wheel")
        // Ctrl+F takes the keyboard to the search field.
        key(GLFW.GLFW_KEY_F, control = true)
        context.input.typeChars("eyes")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-search")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-search-vanilla")
        theme(context, Theme.CHARCOAL)
        context.input.typeChars("zz")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-empty")

        // Tabs. With The End open, the sidebar's rows are seven sections from 24, The End 136, Dragon 152, Protector 165, Drops 178, then Combat 191.
        val tabs = SettingsTabs(at("The End"))
        open(context, tabs)
        context.input.holdControl()
        click(30, 191 + 8)
        context.input.releaseControl()
        expect(context, tabs, "[The End] Combat", "A Ctrl press on a section opens it after the selected tab, in the background")
        click(40, 165 + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[The End] The End Combat", "A middle press on a subsection opens its section")
        // The bar's tabs start 4 in from the sidebar's 116, each 92 wide and 1 apart, and the plus comes after them.
        click(120 + 3 * 93 + 7, 9)
        expect(context, tabs, "The End [New Tab] The End Combat", "The plus opens a blank tab after the selected one")
        context.input.typeChars("eyes")
        context.waitTicks(2)
        expect(context, tabs, "The End [eyes] The End Combat", "Typing turns the new tab into a search")
        key(GLFW.GLFW_KEY_T, control = true)
        expect(context, tabs, "The End eyes [New Tab] The End Combat", "Ctrl+T opens a blank tab")
        context.takeScreenshot("own-settings-tab-blank")
        // Five tabs share the room, so each is 85 wide.
        click(120 + 3 * 86 + 30, 9)
        expect(context, tabs, "The End eyes New Tab [The End] Combat", "A press selects a tab")
        val marked = context.computeOnClient<Int, Nothing> { tabs.current.current }
        check(marked == 1) { "The tab opened from Protector marks subsection $marked, not Protector" }
        move(120 + 1 * 86 + 30, 9)
        context.takeScreenshot("own-settings-tabs")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-tabs-vanilla")
        theme(context, Theme.CHARCOAL)

        // A drag past a neighbor's middle moves the tab there, and the tab follows the mouse.
        move(120 + 4 * 86 + 20, 9)
        context.input.holdMouse(0)
        context.waitTick()
        for (x in listOf(440, 400, 360, 330, 324)) move(x, 9)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-drag")
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "The dragged tab is selected and passed two neighbors")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Releasing keeps the tab where it was dropped")

        key(GLFW.GLFW_KEY_W, control = true)
        expect(context, tabs, "The End eyes [New Tab] The End", "Ctrl+W closes the tab and selects the one that took its place")
        key(GLFW.GLFW_KEY_T, control = true, shift = true)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Ctrl+Shift+T reopens it where it was")
        key(GLFW.GLFW_KEY_TAB, control = true)
        expect(context, tabs, "The End eyes Combat [New Tab] The End", "Ctrl+Tab selects the next tab")
        key(GLFW.GLFW_KEY_TAB, control = true, shift = true)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Ctrl+Shift+Tab selects the one before")
        key(GLFW.GLFW_KEY_9, control = true)
        expect(context, tabs, "The End eyes Combat New Tab [The End]", "Ctrl+9 selects the last tab")
        key(GLFW.GLFW_KEY_1, control = true)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "Ctrl+1 selects the first tab")

        // A press in the sidebar takes the tab there, and the history walks back and forward.
        click(30, 191 + 8)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "A press on a section goes there in the same tab")
        key(GLFW.GLFW_KEY_LEFT, alt = true)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "Alt+Left goes back")
        key(GLFW.GLFW_KEY_RIGHT, alt = true)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "Alt+Right goes forward")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_4)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "The mouse's back button goes back")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_5)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "The mouse's forward button goes forward")

        // The × shows on the tab under the mouse, 5 in from its right, and a middle press closes a tab too.
        click(120 + 86 + 85 - 5 - 4, 9)
        expect(context, tabs, "[Combat] Combat New Tab The End", "A press on the × closes that tab")
        click(120 + 86 + 30, 9, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[Combat] New Tab The End", "A middle press closes a tab")

        // Too many tabs: they shrink to 44 and the strip scrolls, keeping the selected one in sight.
        repeat(8) { key(GLFW.GLFW_KEY_T, control = true) }
        expect(context, tabs, "Combat New Tab New Tab New Tab New Tab New Tab New Tab New Tab [New Tab] New Tab The End", "Ctrl+T opens each after the last one opened")
        context.input.typeChars("loot")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow")
        // The wheel down scrolls the strip on to the tabs past its right end.
        move(300, 9)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow-wheel")

        // The tabs outlive the screen, as a browser's session does.
        val before = context.computeOnClient<Int, Nothing> { SettingsScreen.session.size }
        context.setScreen { SettingsScreen(TitleScreen()) }
        context.waitTicks(2)
        key(GLFW.GLFW_KEY_T, control = true)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.setScreen { SettingsScreen(TitleScreen()) }
        context.waitTicks(2)
        val after = context.computeOnClient<Int, Nothing> { SettingsScreen.session.size }
        check(after == before + 1) { "The settings opened again with $after tabs, not the ${before + 1} they closed with" }

        // The smallest window the game allows at GUI scale 2.
        context.input.resizeWindow(854, 480)
        open(context, SettingsTabs(at("Camera")))
        context.takeScreenshot("own-settings-small-camera")
        val small = SettingsTabs(at("The End"))
        context.runOnClient<Nothing> {
            small.open(at("Chat"))
            small.open(at("Inventory", "slot"))
            small.open(Place.BLANK)
            small.select(2)
        }
        open(context, small)
        context.takeScreenshot("own-settings-small")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-small-vanilla")
        theme(context, Theme.CHARCOAL)
        context.input.resizeWindow(1280, 720)

        guiScale(context, 3)
        open(context, SettingsTabs(at("The End")))
        context.input.pressKey(GLFW.GLFW_KEY_END)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-gui3-end")

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        val back = context.computeOnClient<Any?, Nothing> { it.gui.screen() }
        check(back is TitleScreen) { "Escape went to $back, not the screen the settings were opened over" }

        restore(context, window)
    }
}
