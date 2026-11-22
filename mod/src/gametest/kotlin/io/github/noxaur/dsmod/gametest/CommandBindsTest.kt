package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.ui.ItemsEditor
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Drives the list of command binds where it sits in the settings: an empty list says what to do, Add Bind opens its
 * command to type, keys are recorded held together, where it works cycles, its details open under it, a copy clashes
 * and is ringed, and Delete asks twice. `CommandBindGroupsTest` drives the groups, and [CommandKeysTest] sends the
 * binds in a world. Read the screenshots.
 */
class CommandBindsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.runOnClient<Nothing> { clear() }
        BindsDriver(context).binds()
        context.runOnClient<Nothing> {
            clear()
            DsModClient.saveSettings()
        }
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun BindsDriver.binds() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        open(context, SettingsTabs(at("Binds")))
        list()
        context.takeScreenshot("command-binds-empty")
        check(lines() == 2) { "An empty list shows ${lines()} lines, not its message and its buttons" }

        // Add Bind opens the new bind's command to type into.
        press(line(foot()), "Add Bind")
        check(commandBinds.binds.value == listOf(CommandBind())) { "Add Bind left ${commandBinds.binds.value}" }
        context.input.typeChars("/me bound")
        context.waitTicks(2)
        check(commandBinds.binds.value.single().command == "/me bound") { "Typing into the command left ${commandBinds.binds.value}" }
        // Tab goes back to the name, Shift with it, and Enter leaves the field.
        key(GLFW.GLFW_KEY_TAB, shift = true)
        context.input.typeChars("Warp End")
        key(GLFW.GLFW_KEY_ENTER)
        check(commandBinds.binds.value.single().name == "Warp End") { "Typing into the name left ${commandBinds.binds.value}" }

        // Shift + F6, recorded by holding Shift and pressing F6.
        press(lineOf(0), "Keys")
        context.input.holdShift()
        context.waitTick()
        context.input.pressKey(GLFW.GLFW_KEY_F6)
        context.waitTick()
        context.takeScreenshot("command-binds-recording")
        context.input.releaseShift()
        context.waitTicks(2)
        check(commandBinds.binds.value.single().keys == listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)) { "Recording left ${commandBinds.binds.value}" }

        // Where cycles forward on a press and back on a right press.
        press(lineOf(0), "Where")
        press(lineOf(0), "Where")
        check(commandBinds.binds.value.single().where == Where.BOTH) { "Two presses on Where left ${commandBinds.binds.value.single().where}" }
        press(lineOf(0), "Where", button = 1)
        check(commandBinds.binds.value.single().where == Where.MENUS) { "A right press on Where left ${commandBinds.binds.value.single().where}" }
        context.takeScreenshot("command-binds-one")

        // Its details open under it, with the area, the priority and how keys are read.
        press(lineOf(0), "Settings")
        context.shotInBothThemes("command-binds-details")
        val details = lineOf(0, details = true)
        press(details, "Area")
        check(commandBinds.binds.value.single().area != null) { "Area did not step" }
        press(details, "Priority", dx = at(details, "Priority")[2] - 4)
        check(commandBinds.binds.value.single().priority == 1) { "The priority's plus left ${commandBinds.binds.value.single().priority}" }
        press(details, "Keys in Order")
        check(commandBinds.binds.value.single().inOrder) { "Keys in Order did not turn on" }
        press(details, "Keys in Order")

        // Duplicate adds a copy that starts off, after the original. Once on, it clashes, and both keys are ringed.
        press(details, "Duplicate")
        check(commandBinds.binds.value.size == 2 && !commandBinds.binds.value[1].enabled) { "The copy is ${commandBinds.binds.value.getOrNull(1)}" }
        press(lineOf(1), "On")
        check(commandBinds.binds.value[1].enabled) { "The copy's switch did not turn it on" }
        context.shotInBothThemes("command-binds-clash")

        // Delete asks twice, and a press elsewhere calls it off.
        press(lineOf(1), "Settings")
        press(lineOf(1, details = true), "Delete")
        check(commandBinds.binds.value.size == 2) { "One press on Delete deleted the bind" }
        context.takeScreenshot("command-binds-delete")
        press(lineOf(0), "Name", dx = 2)
        key(GLFW.GLFW_KEY_ESCAPE)
        press(lineOf(1, details = true), "Delete")
        check(commandBinds.binds.value.size == 2) { "A press elsewhere did not call the delete off" }
        press(lineOf(1, details = true), "Delete")
        check(commandBinds.binds.value.size == 1) { "Two presses on Delete left ${commandBinds.binds.value}" }

        // While keys are recorded, the mouse's back button is a key, not a step back through the settings.
        press(lineOf(0), "Keys")
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_4)
        context.waitTicks(2)
        check(commandBinds.binds.value.single().keys == listOf(GLFW.GLFW_MOUSE_BUTTON_4)) { "Recording the back button left ${commandBinds.binds.value.single().keys}" }
    }

    private fun clear() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        commandBinds.binds.value = emptyList()
        commandBinds.groups.value = emptyList()
        commandBinds.collapsedGroups.value = emptyList()
        commandBinds.offGroups.value = emptyList()
    }
}

/** Drives the list of binds in the settings, by its lines and the names of their controls, at GUI scale 2. */
internal class BindsDriver(context: ClientGameTestContext) : SettingsDriver(context) {
    /** Scrolls the list into view. */
    fun list() = spot("binds")

    fun lines() = context.computeOnClient<Int, Nothing> { ((it.gui.screen() as SettingsScreen).editor("binds") as ItemsEditor<*>).size }

    fun foot() = lines() - 1

    fun line(i: Int) = i

    /** The line of bind [index], or of its details. */
    fun lineOf(index: Int, details: Boolean = false) = context.computeOnClient<Int, Nothing> { editorNow(it).lineOf(index, details) }

    /** The line of [group]'s heading, or of its details. */
    fun headingOf(group: String, details: Boolean = false) = context.computeOnClient<Int, Nothing> { editorNow(it).headingOf(group, details) }

    private fun editorNow(client: net.minecraft.client.Minecraft) = (client.gui.screen() as SettingsScreen).editor("binds") as ItemsEditor<*>

    /** Where line [line]'s control named [control] is, or the line itself. */
    fun at(line: Int, control: String? = null): IntArray {
        val spot = context.computeOnClient<IntArray?, Nothing> { (((it.gui.screen() as SettingsScreen).editor("binds")) as ItemsEditor<*>).spot(line, control) }
        return spot ?: error("Line $line of the binds has no ${control ?: "line"}; it has ${lines()} lines")
    }

    fun press(line: Int, control: String? = null, button: Int = 0, dx: Int = -1) {
        val (x, y, w, h) = at(line, control).let { listOf(it[0], it[1], it[2], it[3]) }
        click(x + if (dx >= 0) dx else w / 2, y + h / 2, button)
    }
}
