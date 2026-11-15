package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen

/** Steps the game tests of ds-mod's own settings screen share. */
internal object SettingsSteps {
    val names = SettingsTree.sections(DsModConfig()).map { it.name }

    /** The place of the section [name], so a step names the section it means and survives a new order of sections. */
    fun at(name: String, query: String = "") = Place(names.indexOf(name).also { check(it >= 0) { "No section $name" } }, query)

    /** Makes the window 1280x720 at GUI scale 2 over the title screen, and returns its size before, for [restore]. */
    fun setUp(context: ClientGameTestContext): Pair<Int, Int> {
        context.waitForScreen(TitleScreen::class.java)
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        context.input.resizeWindow(1280, 720)
        guiScale(context, 2)
        return window
    }

    fun restore(context: ClientGameTestContext, window: Pair<Int, Int>) {
        context.restoreDefaultGameOptions()
        context.runOnClient<Nothing> { it.resizeGui() }
        context.input.resizeWindow(window.first, window.second)
    }

    /** Checks that the narrator would say [want] now, as vanilla collects what the screen narrates. */
    fun hear(context: ClientGameTestContext, want: String, step: String) {
        val said = context.computeOnClient<String, Nothing> { (it.gui.screen() as SettingsScreen).narration() }
        check(want in said) { "$step, the narrator says \"$said\", not \"$want\"" }
    }

    fun open(context: ClientGameTestContext, tabs: SettingsTabs) {
        context.setScreen { SettingsScreen(TitleScreen(), tabs) }
        context.waitTicks(2)
    }

    /** Checks the tabs' titles from the left, with the selected one in brackets. */
    fun expect(context: ClientGameTestContext, tabs: SettingsTabs, titles: String, step: String) {
        val shown = context.computeOnClient<String, Nothing> {
            tabs.tabs.withIndex().joinToString(" ") { (i, tab) -> tab.title(names).let { if (i == tabs.selected) "[$it]" else it } }
        }
        check(shown == titles) { "$step: the tabs are $shown, not $titles" }
    }

    fun theme(context: ClientGameTestContext, theme: Theme) {
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
        context.waitTicks(2)
    }

    fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}

/** Drives the settings screen as the player's mouse and keys would, in GUI pixels at GUI scale 2. */
internal class SettingsDriver(val context: ClientGameTestContext) {
    fun move(x: Int, y: Int) {
        context.input.setCursorPos(x * 2.0, y * 2.0)
        context.waitTick()
    }

    fun click(x: Int, y: Int, button: Int = 0) {
        move(x, y)
        context.input.pressMouse(button)
        context.waitTicks(2)
    }

    fun key(key: Int, control: Boolean = false, shift: Boolean = false, alt: Boolean = false) {
        if (control) context.input.holdControl()
        if (shift) context.input.holdShift()
        if (alt) context.input.holdAlt()
        context.input.pressKey(key)
        if (alt) context.input.releaseAlt()
        if (shift) context.input.releaseShift()
        if (control) context.input.releaseControl()
        context.waitTicks(2)
    }

    /** Scrolls the row [id], or its ordered list's [entry], into view, and says where it is. */
    fun spot(id: String, entry: Int = -1): SettingRows.Spot {
        context.runOnClient<Nothing> { (it.gui.screen() as SettingsScreen).reveal(id) }
        context.waitTicks(2)
        return context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsScreen).spot(id, entry) }
            ?: error("No row $id${if (entry >= 0) ", entry $entry," else ""} on the screen")
    }
}
