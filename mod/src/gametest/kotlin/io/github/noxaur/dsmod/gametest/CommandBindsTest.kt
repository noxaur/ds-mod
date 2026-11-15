package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.BindEditScreen
import io.github.noxaur.dsmod.edge.CommandBindsScreen
import io.github.noxaur.dsmod.edge.CommandKeys
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText

/**
 * Works Command Binds' two screens with the real keyboard and mouse: a bind on Shift + F6 recorded by holding
 * the keys, one on the middle button that works in menus too under More, a sequence, one limited to The End, a
 * copy that clashes, a delete that asks twice, rows dragged and moved with Alt, the rows scrolling, and groups
 * that appear with the second one, renamed, exported, imported and deleted. Then in a world each bind sends where it should and nowhere else. Read the screenshots.
 */
class CommandBindsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        val general = DsModClient.settings.general
        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.General::class.java.getField("editBinds")) }
        context.waitTicks(20)
        val settings = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        context.takeScreenshot("command-binds-settings")
        context.runOnClient<Nothing> { general.editBinds.run() }
        context.waitForScreen(CommandBindsScreen::class.java)
        context.waitTicks(2)
        context.takeScreenshot("command-binds-empty")

        fun screen() = context.computeOnClient<Screen, Nothing> { it.gui.screen()!! }
        fun <T : AbstractWidget> widgets(type: Class<T>) = context.computeOnClient<List<T>, Nothing> { client ->
            client.gui.screen()!!.children().filterIsInstance(type).filter { it.visible }
        }
        fun named(label: String) = widgets(AbstractWidget::class.java).filter { it.message.string == label }
        fun click(widget: AbstractWidget, button: Int = 0) {
            val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val scale = client.window.guiScale.toDouble()
                (widget.x + widget.width / 2.0) * scale to (widget.y + widget.height / 2.0) * scale
            }
            context.input.setCursorPos(x, y)
            context.waitTick()
            context.input.pressMouse(button)
            context.waitTicks(2)
        }
        fun field(index: Int, typed: String) {
            click(widgets(KitTextField::class.java)[index])
            context.input.typeChars(typed)
            context.waitTicks(2)
        }
        fun vanilla(name: String) {
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot(name)
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
        }
        fun keys() = widgets(KitKeysButton::class.java).single()
        fun newBind() {
            context.clickScreenButton("New Bind")
            context.waitForScreen(BindEditScreen::class.java)
        }
        fun saveBind() {
            context.clickScreenButton("Save")
            context.waitForScreen(CommandBindsScreen::class.java)
        }

        // Shift + F6, recorded by holding Shift and pressing F6.
        newBind()
        context.input.typeChars("Warp End")
        field(1, "/me bound")
        click(keys())
        check(keys().recording) { "A click on Keys did not start recording" }
        context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.input.pressKey(GLFW.GLFW_KEY_F6)
        context.waitTicks(2)
        check(keys().recording) { "Recording ended while Shift was still held" }
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.waitTicks(2)
        check(!keys().recording && keys().keys == listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)) { "Holding Shift and F6 recorded ${keys().keys}" }
        context.takeScreenshot("command-binds-edit")
        vanilla("command-binds-edit-vanilla")
        saveBind()
        check(general.binds == listOf(CommandBind("Warp End", "/me bound", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)))) { "Saving left ${general.binds}" }

        // Cancel keeps nothing. Then the middle button, in the world and in menus, under More.
        newBind()
        context.clickScreenButton("Cancel")
        context.waitForScreen(CommandBindsScreen::class.java)
        check(general.binds.size == 1) { "Cancel kept a bind: ${general.binds}" }
        newBind()
        check(named("Where").isEmpty() && widgets(KitCycleButton::class.java).isEmpty()) { "Where showed before More was pressed" }
        field(1, "other")
        click(keys())
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        context.waitTicks(2)
        context.clickScreenButton("More")
        context.waitTicks(2)
        val where = widgets(KitCycleButton::class.java).first { it.message.string.startsWith("Where") }
        click(where)
        click(where)
        context.takeScreenshot("command-binds-more")
        saveBind()
        check(general.binds[1].keys == listOf(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && general.binds[1].where == Where.BOTH) { "The second bind is ${general.binds[1]}" }

        // F7 then F8, in order, ended with Escape. More stays open from the last bind.
        newBind()
        context.input.typeChars("Sequence")
        field(1, "/me seq")
        click(named("Keys in Order").single())
        click(keys())
        context.input.pressKey(GLFW.GLFW_KEY_F7)
        context.input.pressKey(GLFW.GLFW_KEY_F8)
        context.waitTicks(2)
        check(keys().recording) { "Keys in order stopped recording when the keys were let go" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(screen() is BindEditScreen && keys().keys == listOf(GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8)) { "Escape left ${keys().keys} on ${screen()}" }
        saveBind()

        // F9, only in The End, where the test world is not. Less folds the rest away again.
        newBind()
        field(1, "/me end")
        click(keys())
        context.input.pressKey(GLFW.GLFW_KEY_F9)
        context.waitTicks(2)
        click(named(Area.END.title).single())
        context.clickScreenButton("Less")
        context.waitTicks(2)
        saveBind()
        check(general.binds.map { it.inOrder } == listOf(false, false, true, false) && general.binds[3].areas == listOf(Area.END)) { "The binds are ${general.binds}" }

        // Save as Copy adds a copy that starts off. Once on, it clashes.
        fun edit(row: Int) {
            click(named("Edit")[row])
            context.waitForScreen(BindEditScreen::class.java)
        }
        edit(0)
        context.clickScreenButton("Save as Copy")
        context.waitForScreen(CommandBindsScreen::class.java)
        check(general.binds.size == 5 && general.binds[1].name == "Warp End" && !general.binds[1].enabled) { "The copy is ${general.binds[1]}" }
        click(widgets(KitCheckbox::class.java)[1])
        check(CommandBinds.clashes(general.binds[0], general.binds).single() === general.binds[1]) { "Two binds on Shift + F6 did not clash" }
        context.takeScreenshot("command-binds")
        vanilla("command-binds-vanilla")

        // Delete asks twice.
        edit(1)
        context.clickScreenButton("Delete")
        check(general.binds.size == 5 && screen() is BindEditScreen) { "One press on Delete deleted the bind" }
        context.takeScreenshot("command-binds-delete")
        context.clickScreenButton("Delete")
        context.waitForScreen(CommandBindsScreen::class.java)
        check(general.binds.size == 4) { "Two presses on Delete left ${general.binds}" }

        // A row is dragged by its name to move it, and Escape during a drag puts it back.
        fun rowAt(row: Int) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
            val box = client.gui.screen()!!.children().filterIsInstance<KitCheckbox>()[row]
            (box.x + 40.0) * client.window.guiScale to (box.y + 5.0) * client.window.guiScale
        }
        fun drag(from: Int, to: Int, escape: Boolean = false) {
            rowAt(from).let { context.input.setCursorPos(it.first, it.second) }
            // Far enough from the last press that this one is not a double click, which edits the row.
            context.waitTicks(10)
            context.input.holdMouse(0)
            val target = rowAt(to)
            context.input.setCursorPos(target.first, target.second)
            context.waitTicks(2)
            if (escape) context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.input.releaseMouse(0)
            context.waitTicks(2)
        }
        drag(1, 0)
        check(general.binds.map { it.command } == listOf("other", "/me bound", "/me seq", "/me end")) { "Dragging the second row up left ${general.binds.map { it.command }}" }
        drag(0, 3, escape = true)
        check(general.binds[0].command == "other") { "Escape during a drag left ${general.binds.map { it.command }}" }
        context.runOnClient<Nothing> { it.gui.screen()!!.setFocused(it.gui.screen()!!.children().filterIsInstance<KitCheckbox>()[0]) }
        // The test's keyboard carries no modifiers, so Alt + Down goes to the screen the way GLFW sends it.
        context.runOnClient<Nothing> { it.gui.screen()!!.keyPressed(KeyEvent(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_ALT)) }
        context.waitTicks(2)
        check(general.binds[0].command == "/me bound") { "Alt + Down left ${general.binds.map { it.command }}" }

        // Past eight rows the list scrolls.
        val four = general.binds
        context.runOnClient<Nothing> { client ->
            general.binds = four + List(6) { CommandBind("Spare $it", "/me spare", listOf(GLFW.GLFW_KEY_KP_0 + it)) }
            client.gui.screen()!!.resize(client.window.guiScaledWidth, client.window.guiScaledHeight)
        }
        context.waitTicks(2)
        context.runOnClient<Nothing> { it.gui.screen()!!.setFocused(null) }
        context.input.pressKey(GLFW.GLFW_KEY_END)
        context.waitTicks(2)
        check(!widgets(KitCheckbox::class.java).any { it.y < 0 } && widgets(KitCheckbox::class.java).size == 8 && named("Edit").size == 8) { "The list shows more than eight rows" }
        val lastShown = { context.computeOnClient<Boolean, Nothing> { client -> client.gui.screen()!!.children().filterIsInstance<KitCheckbox>().last().visible } }
        check(lastShown()) { "End did not show the last row" }
        context.takeScreenshot("command-binds-scrolled")
        context.input.pressKey(GLFW.GLFW_KEY_HOME)
        context.waitTicks(2)
        check(!lastShown()) { "Home did not go back to the top" }
        val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { it.window.width * 0.5 to it.window.height * 0.5 }
        context.input.setCursorPos(x, y)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { client -> !client.gui.screen()!!.children().filterIsInstance<KitCheckbox>().first().visible }) { "The wheel did not scroll" }
        context.runOnClient<Nothing> { client ->
            general.binds = four
            client.gui.screen()!!.resize(client.window.guiScaledWidth, client.window.guiScaledHeight)
        }

        // The groups column appears with a second group, which is named by typing over its name. General is
        // exported and taken back in, then the import is deleted with a second press on its cross.
        check(named("General").isEmpty()) { "The groups column showed with one group" }
        context.clickScreenButton("New Group")
        context.input.typeChars("Dungeons")
        context.waitTicks(2)
        check(general.bindGroups == listOf("Dungeons")) { "Typing a group's name left ${general.bindGroups}" }
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.clickScreenButton("General")
        context.clickScreenButton("Export")
        val shared = context.computeOnClient<String, Nothing> { it.keyboardHandler.clipboard }
        check(shared.startsWith("dsmod-binds:")) { "Export put $shared on the clipboard" }
        context.clickScreenButton("Import")
        check(general.bindGroups == listOf("Dungeons", "Imported") && general.binds.count { it.group == "Imported" && !it.enabled } == 4) {
            "Import left ${general.bindGroups} and ${general.binds.filter { it.group == "Imported" }}"
        }
        context.takeScreenshot("command-binds-imported")
        val cross = named("Delete Group").single()
        click(cross)
        check(general.bindGroups.size == 2) { "One press on the group's cross deleted it" }
        click(cross)
        check(general.bindGroups == listOf("Dungeons") && general.binds.size == 4) { "Deleting Imported left ${general.bindGroups}, ${general.binds.size} binds" }

        context.clickScreenButton("Done")
        context.waitTicks(20)
        val after = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        check(after === settings && after != null) { "Done went to $after, not back to the settings $settings" }
        val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
        check(""""name":"Warp End","command":"/me bound","keys":[340,295]""" in saved && """"bindGroups":["Dungeons"]""" in saved) { "The binds were not saved: $saved" }
        context.setScreen { null }

        context.worldBuilder().create().use { _ ->
            fun said(word: String) = context.computeOnClient<Int, Nothing> { client ->
                (client.gui.hud.chat as ChatComponentAccessor).allMessages.count { it.content().string.endsWith(word) }
            }
            fun shiftF6() {
                context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.waitTicks(10)
            }
            context.waitTicks(20)
            try {
                shiftF6()
                check(said("bound") == 1) { "Shift + F6 sent /me bound ${said("bound")} times" }
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(10)
                check(said("bound") == 1) { "F6 without Shift sent /me bound" }
                // A held key's repeat is not a press.
                context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.input.holdKey(GLFW.GLFW_KEY_F6)
                context.runOnClient<Nothing> { CommandKeys.input(GLFW.GLFW_KEY_F6, GLFW.GLFW_REPEAT) }
                context.input.releaseKey(GLFW.GLFW_KEY_F6)
                context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
                context.waitTicks(10)
                check(said("bound") == 2) { "Holding Shift + F6 through a repeat sent /me bound ${said("bound") - 1} times" }

                context.input.pressKey(GLFW.GLFW_KEY_F8)
                context.input.pressKey(GLFW.GLFW_KEY_F7)
                context.waitTicks(10)
                check(said("seq") == 0) { "F8 then F7 sent the sequence F7, F8" }
                context.input.pressKey(GLFW.GLFW_KEY_F7)
                context.input.pressKey(GLFW.GLFW_KEY_F8)
                context.waitTicks(10)
                check(said("seq") == 1) { "F7 then F8 sent /me seq ${said("seq")} times" }

                context.input.pressKey(GLFW.GLFW_KEY_F9)
                context.waitTicks(10)
                check(said("end") == 0) { "F9, limited to The End, sent outside it" }

                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                context.waitTicks(10)
                check(said("other") == 1) { "The middle button said other ${said("other")} times in the world" }
                context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
                context.waitTicks(2)
                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                context.waitTicks(2)
                shiftF6()
                check(said("other") == 2 && said("bound") == 2) { "In the inventory the menu bind said other ${said("other") - 1} times and the world bind sent ${said("bound") - 2}" }

                context.setScreen { ChatScreen("", false) }
                shiftF6()
                context.setScreen { null }
                context.waitTicks(10)
                check(said("bound") == 2) { "Shift + F6 in the chat box sent /me bound" }

                general.commandBinds = false
                shiftF6()
                check(said("bound") == 2) { "With Command Binds off Shift + F6 sent /me bound" }
            } finally {
                general.commandBinds = true
                general.binds = emptyList()
                general.bindGroups = emptyList()
                context.setScreen { null }
            }
        }
    }
}
