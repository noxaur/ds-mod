package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.TabPlacesEditor
import io.github.noxaur.dsmod.edge.ui.ItemsEditor
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen

/** Steps the game tests of ds-mod's own settings screen share. */
internal object SettingsSteps {
    val names = SettingsScreen.sections(DsModConfig()).map { it.name }

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

    fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}

/** Drives the settings screen as the player's mouse and keys would, in GUI pixels at GUI scale 2. */
internal open class SettingsDriver(val context: ClientGameTestContext) {
    fun move(x: Int, y: Int) = context.point(x, y)

    fun click(x: Int, y: Int, button: Int = 0) = context.click(x, y, button, settle = 2)

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

internal class BindsDriver(context: ClientGameTestContext) : SettingsDriver(context) {
    fun list() = spot("binds")

    fun lines() = context.computeOnClient<Int, Nothing> { ((it.gui.screen() as SettingsScreen).editor("binds") as ItemsEditor<*>).size }

    fun foot() = lines() - 1

    fun line(i: Int) = i

    fun lineOf(index: Int, details: Boolean = false) = context.computeOnClient<Int, Nothing> { editorNow(it).lineOf(index, details) }

    fun headingOf(group: String, details: Boolean = false) = context.computeOnClient<Int, Nothing> { editorNow(it).headingOf(group, details) }

    private fun editorNow(client: Minecraft) = (client.gui.screen() as SettingsScreen).editor("binds") as ItemsEditor<*>

    fun at(line: Int, control: String? = null): IntArray {
        val spot = context.computeOnClient<IntArray?, Nothing> { (((it.gui.screen() as SettingsScreen).editor("binds")) as ItemsEditor<*>).spot(line, control) }
        return spot ?: error("Line $line of the binds has no ${control ?: "line"}; it has ${lines()} lines")
    }

    fun press(line: Int, control: String? = null, button: Int = 0, dx: Int = -1) {
        val (x, y, w, h) = at(line, control).let { listOf(it[0], it[1], it[2], it[3]) }
        click(x + if (dx >= 0) dx else w / 2, y + h / 2, button)
    }
}

internal class TabsDriver(context: ClientGameTestContext) : SettingsDriver(context) {
    private fun editor() = context.computeOnClient<TabPlacesEditor, Nothing> { (it.gui.screen() as SettingsScreen).editor("tabs") as TabPlacesEditor }

    fun place(edge: TabEdge, slot: Int) = context.computeOnClient<Pair<Int, Int>, Nothing> { editor().placeAt(edge, slot) }

    fun field(which: Int) = context.computeOnClient<IntArray, Nothing> { editor().fieldSpot(which) }
}
