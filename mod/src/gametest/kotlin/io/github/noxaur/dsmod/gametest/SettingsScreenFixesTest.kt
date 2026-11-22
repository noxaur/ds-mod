package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

class SettingsScreenFixesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        SettingsDriver(context).rows()
        BindsDriver(context).lists()
        context.worldBuilder().create().use { TabsDriver(context).item() }
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun SettingsDriver.rows() {
        val distance = { DsModClient.feature<Camera>().distance.value }
        val tabs = SettingsTabs(at("Camera"))
        open(context, tabs)
        val d = spot("cameraDistance")

        click(d.extraX + 4, d.controlY + 6)
        context.input.typeChars("42")
        context.waitTicks(2)
        relayout()
        check(distance() == 42f) { "Resizing dropped the typed distance, leaving ${distance()}" }

        val again = spot("cameraDistance")
        click(again.extraX + 4, again.controlY + 6)
        move(again.extraX + 4, again.controlY + 6)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        key(GLFW.GLFW_KEY_ESCAPE)
        check(distance() == 42f) { "The wheel stepped the open field, and Escape kept ${distance()}" }

        val named = spot("cameraDistance")
        click(named.x + 8, named.y + 8)
        context.input.typeChars("7")
        context.waitTicks(2)
        check(tabs.current.place.query.isEmpty()) { "A digit after a click searched for \"${tabs.current.place.query}\"" }
        key(GLFW.GLFW_KEY_ENTER)
        check(distance() == 7f) { "The digit never opened the field, leaving ${distance()}" }

        context.runOnClient<Nothing> { DsModClient.feature<Animations>().itemSize.value = 2f }
        open(context, SettingsTabs(at("General")))
        val animations = spot("animations")
        click(animations.x + 8, animations.y + 8)
        key(GLFW.GLFW_KEY_RIGHT)
        val held = spot("itemPosition")
        click(held.x + 20, held.y + 10)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).marked("itemSize") }) { "Item Size showed no changed marker" }
        val reset = spot("resetAnimations")
        click(reset.controlX + reset.controlWidth / 2, reset.controlY + reset.controlHeight / 2)
        check(DsModClient.feature<Animations>().itemSize.value == 1f) { "Reset Animations left Item Size at ${DsModClient.feature<Animations>().itemSize.value}" }
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).marked("itemSize") }.not()) {
            "Reset Animations left Item Size's changed marker up"
        }
    }

    private fun BindsDriver.lists() {
        val commandBinds = DsModClient.feature<CommandBinds>()
        val menuTabs = DsModClient.feature<MenuTabs>()
        commandBinds.binds.value = listOf(CommandBind(command = "/a"), CommandBind(command = "/b"))
        commandBinds.groups.value = emptyList()
        open(context, SettingsTabs(at("Binds")))
        list()
        val from = at(lineOf(0))
        val gx = from[0] + 12
        val gy = from[1] + from[3] / 2
        move(gx, gy)
        context.input.holdMouse(0)
        context.waitTick()
        move(gx, gy + 18)
        context.waitTick()
        key(GLFW.GLFW_KEY_ESCAPE)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(commandBinds.binds.value.map { it.command } == listOf("/a", "/b")) { "Escape during the drag left ${commandBinds.binds.value}" }
        key(GLFW.GLFW_KEY_DELETE)
        key(GLFW.GLFW_KEY_DELETE)
        check(commandBinds.binds.value.map { it.command } == listOf("/b")) { "Delete after a cancelled drag removed ${commandBinds.binds.value}" }

        commandBinds.binds.value = listOf(CommandBind(command = "/c", group = "Dungeons"))
        commandBinds.groups.value = listOf("Dungeons")
        open(context, SettingsTabs(at("Binds")))
        list()
        press(headingOf("Dungeons"))
        context.runOnClient<Nothing> { Minecraft.getInstance().keyboardHandler.clipboard = "none" }
        press(foot(), "Export")
        val copied = context.computeOnClient<String, Nothing> { Minecraft.getInstance().keyboardHandler.clipboard }
        check(copied.startsWith("dsmod-binds:")) { "Export copied \"$copied\" instead of the Dungeons bind" }
        relayout {
            // @keep An empty clipboard does not replace the previous copy, so the marker has to be text.
            Minecraft.getInstance().keyboardHandler.clipboard = "none"
        }
        press(foot(), "Export")
        val again = context.computeOnClient<String, Nothing> { Minecraft.getInstance().keyboardHandler.clipboard }
        check(again.startsWith("dsmod-binds:")) { "After a relayout Export copied \"$again\" instead of the Dungeons bind" }

        commandBinds.binds.value = emptyList()
        commandBinds.groups.value = emptyList()
        DsModClient.saveSettings()
    }

    private fun TabsDriver.item() {
        val menuTabs = DsModClient.feature<MenuTabs>()
        context.runOnClient<Nothing> { menuTabs.tabs.value = TabLayout.DEFAULT }
        context.setScreen { SettingsScreen(null, SettingsTabs(at("Menus")), reveal = "tabs") }
        context.waitTicks(2)
        val storage = place(TabEdge.BOTTOM, 0)
        click(storage.first, storage.second)
        val itemField = field(1)
        click(itemField[0] + itemField[2] / 2, itemField[1] + itemField[3] / 2)
        key(GLFW.GLFW_KEY_END)
        repeat(16) { key(GLFW.GLFW_KEY_BACKSPACE) }
        relayout()
        val item = context.computeOnClient<String, Nothing> { menuTabs.tabs.value.first { it.command == "storage" }.item }
        check(item.isEmpty()) { "Clearing the item left \"$item\"" }
        context.runOnClient<Nothing> {
            menuTabs.tabs.value = TabLayout.DEFAULT
            DsModClient.saveSettings()
        }
    }
}

