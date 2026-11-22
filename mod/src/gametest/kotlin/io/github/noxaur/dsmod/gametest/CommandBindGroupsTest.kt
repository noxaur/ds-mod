package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.debug.friendBind
import io.github.noxaur.dsmod.debug.sampleBinds
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.clearBinds
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class CommandBindGroupsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        BindsDriver(context).groups()
        BindsDriver(context).guide()
        clearBinds(context)
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun BindsDriver.groups() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        val commands = { context.computeOnClient<List<String>, Nothing> { commandBinds.binds.value.map { it.command } } }
        context.runOnClient<Nothing> { commandBinds.fill(listOf("other", "/me bound", "/me seq", "/me end").map { CommandBind(command = it) }) }
        open(context, SettingsTabs(at("Binds")))
        list()

        press(foot(), "Add Group")
        context.input.typeChars("Dungeons")
        key(GLFW.GLFW_KEY_ENTER)
        check(commandBinds.groups.value == listOf("Dungeons")) { "Typing a group's name left ${commandBinds.groups.value}" }
        press(headingOf("Dungeons"), "Settings")

        val from = at(lineOf(3))
        val empty = at(headingOf("Dungeons") + 1)
        hold(from[0] + 13, from[1] + 9)
        for (y in listOf(from[1] + 12, from[1] + 20, empty[1] + 4, empty[1] + 9)) move(from[0] + 13, y)
        context.shotInBothThemes("command-binds-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(commandBinds.binds.value.single { it.command == "/me end" }.group == "Dungeons") { "Dragging under Dungeons left ${commandBinds.binds.value}" }

        val first = at(lineOf(0))
        hold(first[0] + 13, first[1] + 9)
        for (dy in listOf(12, 24, 40)) move(first[0] + 13, first[1] + dy)
        escapeHeld()
        check(commands() == listOf("other", "/me bound", "/me seq", "/me end")) { "Escape during a drag left ${commands()}" }

        press(lineOf(0), button = 1)
        context.takeScreenshot("command-binds-menu")
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_ENTER)
        check(commands() == listOf("/me bound", "other", "/me seq", "/me end")) { "Move Down in the menu left ${commands()}" }
        key(GLFW.GLFW_KEY_DOWN, alt = true)
        key(GLFW.GLFW_KEY_DOWN, alt = true)
        check(commandBinds.binds.value.single { it.command == "other" }.group == "Dungeons") { "Alt + Down left ${commandBinds.binds.value}" }
        context.takeScreenshot("command-binds-moved")

        press(headingOf("Dungeons"), "Fold")
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "The arrow left ${commandBinds.collapsedGroups.value}" }
        context.takeScreenshot("command-binds-collapsed")
        key(GLFW.GLFW_KEY_RIGHT)
        check(commandBinds.collapsedGroups.value.isEmpty()) { "Right left ${commandBinds.collapsedGroups.value}" }
        key(GLFW.GLFW_KEY_LEFT)
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "Left left ${commandBinds.collapsedGroups.value}" }
        key(GLFW.GLFW_KEY_RIGHT)

        press(headingOf("Dungeons"), "Group")
        check(commandBinds.offGroups.value == listOf("Dungeons")) { "The heading's switch left ${commandBinds.offGroups.value}" }
        context.takeScreenshot("command-binds-group-off")
        key(GLFW.GLFW_KEY_SPACE)
        check(commandBinds.offGroups.value.isEmpty()) { "Space on the heading left ${commandBinds.offGroups.value}" }

        press(headingOf("Dungeons"), "Settings")
        press(headingOf("Dungeons", details = true), "Export")
        val shared = context.computeOnClient<String, Nothing> { it.keyboardHandler.clipboard }
        check(shared.startsWith("dsmod-binds:")) { "Export put $shared on the clipboard" }
        press(foot(), "Import")
        check(commandBinds.groups.value == listOf("Dungeons", "Dungeons 2") && commandBinds.binds.value.count { it.group == "Dungeons 2" && !it.enabled } == 2) {
            "Import left ${commandBinds.groups.value} with ${commandBinds.binds.value}"
        }
        context.takeScreenshot("command-binds-imported")

        press(headingOf("Dungeons 2"), dx = 60)
        key(GLFW.GLFW_KEY_UP, alt = true)
        check(commandBinds.groups.value == listOf("Dungeons 2", "Dungeons")) { "Alt + Up left ${commandBinds.groups.value}" }
        key(GLFW.GLFW_KEY_DELETE)
        check(commandBinds.groups.value.size == 2) { "One press on Delete deleted the group" }
        context.takeScreenshot("command-binds-delete-group")
        key(GLFW.GLFW_KEY_DELETE)
        check(commandBinds.groups.value == listOf("Dungeons") && commandBinds.binds.value.size == 4) { "Deleting Dungeons 2 left ${commandBinds.groups.value}, ${commandBinds.binds.value.size} binds" }

        // @keep The game takes two presses within 250 ms as one.
        val heading = at(headingOf("Dungeons"))
        move(heading[0] + 60, heading[1] + 9)
        context.input.pressMouse(0)
        context.waitTick()
        context.input.pressMouse(0)
        context.waitTicks(2)
        check(commandBinds.collapsedGroups.value == listOf("Dungeons")) { "A double press left ${commandBinds.collapsedGroups.value}" }
    }

    private fun BindsDriver.guide() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        context.runOnClient<Nothing> { commandBinds.fill(sampleBinds("/sbmenu on") + friendBind(), listOf("From a Friend"), listOf("From a Friend")) }
        open(context, SettingsTabs(at("Binds")))
        list()
        context.point(0, 0, settle = 2)
        context.takeScreenshot("command-binds-guide")
    }
}
