package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.utilities.commandbinds.CommandBinds
import io.github.noxaur.dragen.core.input.CommandBind
import io.github.noxaur.dragen.core.input.Where
import io.github.noxaur.dragen.core.ui.SettingsTabs
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.gametest.SettingsSteps.at
import io.github.noxaur.dragen.gametest.SettingsSteps.clearBinds
import io.github.noxaur.dragen.gametest.SettingsSteps.open
import io.github.noxaur.dragen.gametest.SettingsSteps.restore
import io.github.noxaur.dragen.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class CommandBindsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.runOnClient<Nothing> { DragenClient.feature<CommandBinds>().fill() }
        BindsDriver(context).binds()
        clearBinds(context)
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun BindsDriver.binds() {
        val commandBinds = DragenClient.feature<CommandBinds>()
        open(context, SettingsTabs(at("Utilities")))
        list()
        context.takeScreenshot("command-binds-empty")
        check(lines() == 2) { "An empty list shows ${lines()} lines, not its message and its buttons" }

        press(line(foot()), "Add Bind")
        check(commandBinds.binds.value == listOf(CommandBind())) { "Add Bind left ${commandBinds.binds.value}" }
        context.input.typeChars("/me bound")
        context.waitTicks(2)
        check(commandBinds.binds.value.single().command == "/me bound") { "Typing into the command left ${commandBinds.binds.value}" }
        key(GLFW.GLFW_KEY_TAB, shift = true)
        context.input.typeChars("Warp End")
        key(GLFW.GLFW_KEY_ENTER)
        check(commandBinds.binds.value.single().name == "Warp End") { "Typing into the name left ${commandBinds.binds.value}" }

        press(lineOf(0), "Keys")
        context.input.holdShift()
        context.waitTick()
        context.input.pressKey(GLFW.GLFW_KEY_F6)
        context.waitTick()
        context.takeScreenshot("command-binds-recording")
        context.input.releaseShift()
        context.waitTicks(2)
        check(commandBinds.binds.value.single().keys == listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)) { "Recording left ${commandBinds.binds.value}" }

        press(lineOf(0), "Where")
        press(lineOf(0), "Where")
        check(commandBinds.binds.value.single().where == Where.BOTH) { "Two presses on Where left ${commandBinds.binds.value.single().where}" }
        press(lineOf(0), "Where", button = 1)
        check(commandBinds.binds.value.single().where == Where.MENUS) { "A right press on Where left ${commandBinds.binds.value.single().where}" }
        context.takeScreenshot("command-binds-one")

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

        press(details, "Duplicate")
        check(commandBinds.binds.value.size == 2 && !commandBinds.binds.value[1].enabled) { "The copy is ${commandBinds.binds.value.getOrNull(1)}" }
        press(lineOf(1), "On")
        check(commandBinds.binds.value[1].enabled) { "The copy's switch did not turn it on" }
        context.shotInBothThemes("command-binds-clash")

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

        press(lineOf(0), "Keys")
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_4)
        context.waitTicks(2)
        check(commandBinds.binds.value.single().keys == listOf(GLFW.GLFW_MOUSE_BUTTON_4)) { "Recording the back button left ${commandBinds.binds.value.single().keys}" }
    }
}

