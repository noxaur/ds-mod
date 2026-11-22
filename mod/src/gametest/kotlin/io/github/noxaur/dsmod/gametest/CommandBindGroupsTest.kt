package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Drives the groups of the binds list in the settings: a group named by typing, a bind dragged under its heading
 * joining it, Escape during a drag, the right-press menu worked by the keys, Alt with Up or Down, a heading's arrow
 * and the keys folding it, its switch, export and import, a group moved and deleted with a second press. Read the
 * screenshots, mid-drag and the feature guide's among them.
 */
class CommandBindGroupsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        BindsDriver(context).groups()
        BindsDriver(context).guide()
        context.runOnClient<Nothing> {
            val commandBinds = DsModClient.feature<CommandBinds>()
            commandBinds.binds.value = emptyList()
            commandBinds.groups.value = emptyList()
            commandBinds.collapsedGroups.value = emptyList()
            commandBinds.offGroups.value = emptyList()
            DsModClient.saveSettings()
        }
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun BindsDriver.groups() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        val commands = { context.computeOnClient<List<String>, Nothing> { commandBinds.binds.value.map { it.command } } }
        context.runOnClient<Nothing> {
            commandBinds.binds.value = listOf("other", "/me bound", "/me seq", "/me end").map { CommandBind(command = it) }
            commandBinds.groups.value = emptyList()
            commandBinds.collapsedGroups.value = emptyList()
            commandBinds.offGroups.value = emptyList()
        }
        open(context, SettingsTabs(at("Binds")))
        list()

        // A new group opens its name, selected, to type over.
        press(foot(), "Add Group")
        context.input.typeChars("Dungeons")
        key(GLFW.GLFW_KEY_ENTER)
        check(commandBinds.groups.value == listOf("Dungeons")) { "Typing a group's name left ${commandBinds.groups.value}" }
        press(headingOf("Dungeons"), "Settings")

        // A bind dragged by its grip under the Dungeons heading joins it, and the lines make room as it passes.
        val from = at(lineOf(3))
        val empty = at(headingOf("Dungeons") + 1)
        move(from[0] + 13, from[1] + 9)
        context.input.holdMouse(0)
        context.waitTick()
        for (y in listOf(from[1] + 12, from[1] + 20, empty[1] + 4, empty[1] + 9)) move(from[0] + 13, y)
        context.shotInBothThemes("command-binds-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(commandBinds.binds.value.single { it.command == "/me end" }.group == "Dungeons") { "Dragging under Dungeons left ${commandBinds.binds.value}" }

        // Escape during a drag puts the list back.
        val first = at(lineOf(0))
        move(first[0] + 13, first[1] + 9)
        context.input.holdMouse(0)
        context.waitTick()
        for (dy in listOf(12, 24, 40)) move(first[0] + 13, first[1] + dy)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(commands() == listOf("other", "/me bound", "/me seq", "/me end")) { "Escape during a drag left ${commands()}" }

        // A right press opens the bind's menu, which the keys work. The first bind cannot go up, so the third item is Move Down.
        press(lineOf(0), button = 1)
        context.takeScreenshot("command-binds-menu")
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_ENTER)
        check(commands() == listOf("/me bound", "other", "/me seq", "/me end")) { "Move Down in the menu left ${commands()}" }
        // Alt with Down moves the picked bind on, and past the last of General it joins Dungeons.
        key(GLFW.GLFW_KEY_DOWN, alt = true)
        key(GLFW.GLFW_KEY_DOWN, alt = true)
        check(commandBinds.binds.value.single { it.command == "other" }.group == "Dungeons") { "Alt + Down left ${commandBinds.binds.value}" }
        context.takeScreenshot("command-binds-moved")

        // The heading's arrow folds the group away, and Right and Left unfold and fold the picked heading.
        press(headingOf("Dungeons"), "Fold")
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "The arrow left ${commandBinds.collapsedGroups.value}" }
        context.takeScreenshot("command-binds-collapsed")
        key(GLFW.GLFW_KEY_RIGHT)
        check(commandBinds.collapsedGroups.value.isEmpty()) { "Right left ${commandBinds.collapsedGroups.value}" }
        key(GLFW.GLFW_KEY_LEFT)
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "Left left ${commandBinds.collapsedGroups.value}" }
        key(GLFW.GLFW_KEY_RIGHT)

        // The heading's switch turns the whole group off, and Space on the heading back on.
        press(headingOf("Dungeons"), "Group")
        check(commandBinds.offGroups.value == listOf("Dungeons")) { "The heading's switch left ${commandBinds.offGroups.value}" }
        context.takeScreenshot("command-binds-group-off")
        key(GLFW.GLFW_KEY_SPACE)
        check(commandBinds.offGroups.value.isEmpty()) { "Space on the heading left ${commandBinds.offGroups.value}" }

        // Dungeons is exported from its details and taken back in as a new group, its binds off.
        press(headingOf("Dungeons"), "Settings")
        press(headingOf("Dungeons", details = true), "Export")
        val shared = context.computeOnClient<String, Nothing> { it.keyboardHandler.clipboard }
        check(shared.startsWith("dsmod-binds:")) { "Export put $shared on the clipboard" }
        press(foot(), "Import")
        check(commandBinds.groups.value == listOf("Dungeons", "Dungeons 2") && commandBinds.binds.value.count { it.group == "Dungeons 2" && !it.enabled } == 2) {
            "Import left ${commandBinds.groups.value} with ${commandBinds.binds.value}"
        }
        context.takeScreenshot("command-binds-imported")

        // Alt with Up moves the picked group above the one before it.
        press(headingOf("Dungeons 2"), dx = 60)
        key(GLFW.GLFW_KEY_UP, alt = true)
        check(commandBinds.groups.value == listOf("Dungeons 2", "Dungeons")) { "Alt + Up left ${commandBinds.groups.value}" }
        // Delete asks twice, and takes the group's binds with it.
        key(GLFW.GLFW_KEY_DELETE)
        check(commandBinds.groups.value.size == 2) { "One press on Delete deleted the group" }
        context.takeScreenshot("command-binds-delete-group")
        key(GLFW.GLFW_KEY_DELETE)
        check(commandBinds.groups.value == listOf("Dungeons") && commandBinds.binds.value.size == 4) { "Deleting Dungeons 2 left ${commandBinds.groups.value}, ${commandBinds.binds.value.size} binds" }

        // A double press on a heading folds it too. The game takes two presses within 250 ms as one.
        val heading = at(headingOf("Dungeons"))
        move(heading[0] + 60, heading[1] + 9)
        context.input.pressMouse(0)
        context.waitTick()
        context.input.pressMouse(0)
        context.waitTicks(2)
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "A double press left ${commandBinds.collapsedGroups.value}" }
    }

    /** The feature guide's picture: a few binds with the server's own commands, one group folded, the mouse away. */
    private fun BindsDriver.guide() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        context.runOnClient<Nothing> {
            commandBinds.binds.value = listOf(
                CommandBind("Warp End", "/warp end", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)),
                CommandBind("Stash", "/viewstash", listOf(GLFW.GLFW_KEY_F7), where = Where.BOTH),
                CommandBind("", "/sbmenu on", listOf(GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9), inOrder = true),
                CommandBind("Shared", "/warp end", listOf(GLFW.GLFW_KEY_KP_1), group = "From a Friend", enabled = false),
            )
            commandBinds.groups.value = listOf("From a Friend")
            commandBinds.collapsedGroups.value = listOf("From a Friend")
            commandBinds.offGroups.value = emptyList()
        }
        open(context, SettingsTabs(at("Binds")))
        list()
        context.point(0, 0, settle = 2)
        context.takeScreenshot("command-binds-guide")
    }
}
