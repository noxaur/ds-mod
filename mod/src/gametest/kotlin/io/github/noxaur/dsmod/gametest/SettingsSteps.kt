package io.github.noxaur.dsmod.gametest

import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.TabPlacesEditor
import io.github.noxaur.dsmod.edge.ui.ItemsEditor
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText

internal object SettingsSteps {
    val names = SettingsScreen.sections(DsModConfig()).map { it.name }

    fun at(name: String, query: String = "") = Place(names.indexOf(name).also { check(it >= 0) { "No section $name" } }, query)

    fun setUp(context: ClientGameTestContext): Pair<Int, Int> {
        context.waitForScreen(TitleScreen::class.java)
        return windowSize(context).also { fit(context, 2) }
    }

    fun windowSize(context: ClientGameTestContext) = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }

    fun fit(context: ClientGameTestContext, scale: Int, width: Int = 1280, height: Int = 720) {
        context.input.resizeWindow(width, height)
        guiScale(context, scale)
    }

    fun restore(context: ClientGameTestContext, window: Pair<Int, Int>) {
        context.restoreDefaultGameOptions()
        context.runOnClient<Nothing> { it.resizeGui() }
        context.input.resizeWindow(window.first, window.second)
    }

    fun hear(context: ClientGameTestContext, want: String, step: String) {
        val said = context.computeOnClient<String, Nothing> { (it.gui.screen() as SettingsScreen).narration() }
        check(want in said) { "$step, the narrator says \"$said\", not \"$want\"" }
    }

    fun open(context: ClientGameTestContext, tabs: SettingsTabs) {
        context.setScreen { SettingsScreen(TitleScreen(), tabs) }
        context.waitTicks(2)
    }

    fun expect(context: ClientGameTestContext, tabs: SettingsTabs, titles: String, step: String) {
        val shown = context.computeOnClient<String, Nothing> {
            tabs.tabs.withIndex().joinToString(" ") { (i, tab) -> tab.title(names).let { if (i == tabs.selected) "[$it]" else it } }
        }
        check(shown == titles) { "$step: the tabs are $shown, not $titles" }
    }

    fun savedConfig() = JsonParser.parseString(FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()).asJsonObject

    fun clearBinds(context: ClientGameTestContext) = context.runOnClient<Nothing> {
        DsModClient.feature<CommandBinds>().fill()
        DsModClient.saveSettings()
    }

    fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}

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

    fun relayout(also: () -> Unit = {}) {
        context.runOnClient<Nothing> {
            val screen = it.gui.screen() as SettingsScreen
            screen.resize(screen.width, screen.height)
            also()
        }
        context.waitTicks(2)
    }

    fun hold(x: Int, y: Int) {
        move(x, y)
        context.input.holdMouse(0)
        context.waitTick()
    }

    fun escapeHeld() {
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.input.releaseMouse(0)
        context.waitTicks(2)
    }

    fun keptOpen() = context.checkClient("Escape during a drag closed the settings") { it.gui.screen() is SettingsScreen }

    fun shows(id: String, entry: Int = -1) = context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot(id, entry) != null }

    fun toggle(id: String) {
        val row = spot(id)
        click(row.extraX + 7, row.controlY + 6)
    }

    fun unfold(id: String, opener: String, entry: Int = -1, press: (SettingRows.Spot) -> Pair<Int, Int>) {
        if (shows(id, entry)) return
        val (x, y) = press(spot(opener))
        move(x, y)
        context.input.pressMouse(0)
        context.waitTicks(2)
    }

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

internal fun CommandBinds.fill(binds: List<CommandBind> = emptyList(), groups: List<String> = emptyList(), collapsed: List<String> = emptyList()) {
    this.binds.value = binds
    this.groups.value = groups
    collapsedGroups.value = collapsed
    offGroups.value = emptyList()
}

internal class TabsDriver(context: ClientGameTestContext) : SettingsDriver(context) {
    private fun editor() = context.computeOnClient<TabPlacesEditor, Nothing> { (it.gui.screen() as SettingsScreen).editor("tabs") as TabPlacesEditor }

    fun place(edge: TabEdge, slot: Int) = context.computeOnClient<Pair<Int, Int>, Nothing> { editor().placeAt(edge, slot) }
}
