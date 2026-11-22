package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
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
import io.github.noxaur.dsmod.gametest.mixin.MouseButtonInvoker
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.ui.KitTabBar], [io.github.noxaur.dsmod.edge.Browser], [io.github.noxaur.dsmod.edge.mixin.PerspectiveKeyMixin]. */
class OwnSettingsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = SettingsDriver(context).screen()

    private fun SettingsDriver.screen() {
        val window = setUp(context)
        open(context, SettingsTabs(at("General")))
        context.takeScreenshot("own-settings-charcoal")
        context.inTheme(Theme.VANILLA) { context.takeScreenshot("own-settings-vanilla") }

        click(30, 24 + 16 + 16 + 4 * 13 + 4 * 16 + 8)
        move(160, 18 + 8 + 2 + 12 + 4 + 10)
        context.waitTicks(10)
        context.takeScreenshot("own-settings-hover")

        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-collapsed")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-expanded")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)

        open(context, SettingsTabs(at("General")))
        for (k in List(12) { GLFW.GLFW_KEY_DOWN } + listOf(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER)) {
            context.input.pressKey(k)
            context.waitTick()
        }
        context.waitTicks(10)
        context.takeScreenshot("own-settings-keys")
        move(400, 200)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-wheel")
        key(GLFW.GLFW_KEY_F, control = true)
        context.input.typeChars("eyes")
        context.waitTicks(2)
        context.shotInBothThemes("own-settings-search")
        context.input.typeChars("zz")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-empty")

        // @keep On macOS the game makes a Ctrl left press a right press, and Ctrl Click keeps it.
        val ctrlTabs = SettingsTabs(at("The End"))
        open(context, ctrlTabs)
        move(30, 207 + 8)
        context.input.holdKey(GLFW.GLFW_KEY_LEFT_CONTROL)
        for (action in listOf(GLFW.GLFW_PRESS, GLFW.GLFW_RELEASE)) context.runOnClient<Nothing> {
            (it.mouseHandler as MouseButtonInvoker).invokeOnButton(it.window.handle(), MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_CONTROL), action)
        }
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL)
        context.waitTicks(2)
        expect(context, ctrlTabs, "[The End] Combat", "A press with the Ctrl key opens a tab in the background, on macOS too")
        val tabs = SettingsTabs(at("The End"))
        open(context, tabs)
        context.input.holdControl()
        click(30, 207 + 8)
        context.input.releaseControl()
        expect(context, tabs, "[The End] Combat", "A Ctrl press on a section opens it after the selected tab, in the background")
        click(40, 181 + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[The End] The End Combat", "A middle press on a subsection opens its section")
        click(120 + 3 * 93 + 7, 9)
        expect(context, tabs, "The End [New Tab] The End Combat", "The plus opens a blank tab after the selected one")
        context.input.typeChars("eyes")
        context.waitTicks(2)
        expect(context, tabs, "The End [eyes] The End Combat", "Typing turns the new tab into a search")
        key(GLFW.GLFW_KEY_T, control = true)
        expect(context, tabs, "The End eyes [New Tab] The End Combat", "Ctrl+T opens a blank tab")
        context.takeScreenshot("own-settings-tab-blank")
        click(120 + 3 * 86 + 30, 9)
        expect(context, tabs, "The End eyes New Tab [The End] Combat", "A press selects a tab")
        val marked = context.computeOnClient<Int, Nothing> { tabs.current.current }
        check(marked == 1) { "The tab opened from Protector marks subsection $marked, not Protector" }
        move(120 + 1 * 86 + 30, 9)
        context.shotInBothThemes("own-settings-tabs")

        move(120 + 4 * 86 + 20, 9)
        context.input.holdMouse(0)
        context.waitTick()
        for (x in listOf(440, 400, 360, 330, 324)) move(x, 9)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-drag")
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "The dragged tab is selected and passed two neighbors")
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        context.waitTicks(2)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "A middle click on the held tab closes nothing")
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

        click(30, 207 + 8)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "A press on a section goes there in the same tab")
        key(GLFW.GLFW_KEY_LEFT, alt = true)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "Alt+Left goes back")
        key(GLFW.GLFW_KEY_RIGHT, alt = true)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "Alt+Right goes forward")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_4)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "The mouse's back button goes back")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_5)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "The mouse's forward button goes forward")

        click(120 + 86 + 85 - 5 - 4, 9)
        expect(context, tabs, "[Combat] Combat New Tab The End", "A press on the × closes that tab")
        click(120 + 86 + 30, 9, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[Combat] New Tab The End", "A middle press closes a tab")

        repeat(8) { key(GLFW.GLFW_KEY_T, control = true) }
        expect(context, tabs, "Combat New Tab New Tab New Tab New Tab New Tab New Tab New Tab [New Tab] New Tab The End", "Ctrl+T opens each after the last one opened")
        context.input.typeChars("loot")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow")
        move(300, 9)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow-wheel")

        val before = context.computeOnClient<Int, Nothing> { SettingsScreen.session(DsModClient.settings).size }
        context.setScreen { DsModClient.settingsScreen(TitleScreen()) }
        context.waitTicks(2)
        key(GLFW.GLFW_KEY_T, control = true)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.setScreen { DsModClient.settingsScreen(TitleScreen()) }
        context.waitTicks(2)
        val after = context.computeOnClient<Int, Nothing> { SettingsScreen.session(DsModClient.settings).size }
        check(after == before + 1) { "The settings opened again with $after tabs, not the ${before + 1} they closed with" }

        // @keep 854x480 is the smallest window the game allows.
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
        context.shotInBothThemes("own-settings-small")
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
