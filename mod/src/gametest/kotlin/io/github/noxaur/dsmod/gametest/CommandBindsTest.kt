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
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText

/**
 * Works Command Binds' two screens with the real keyboard and mouse: a bind on Shift + F6 recorded by holding
 * the keys, one on the middle button that works in menus too, a sequence, one limited to The End, a duplicate
 * that clashes, a delete that asks twice, moving, groups renamed, exported, imported and deleted, and the cards
 * scrolling. Then in a world each bind sends where it should and nowhere else. Read the screenshots.
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

        // Cancel keeps nothing. Then the middle button, in the world and in menus.
        newBind()
        context.clickScreenButton("Cancel")
        context.waitForScreen(CommandBindsScreen::class.java)
        check(general.binds.size == 1) { "Cancel kept a bind: ${general.binds}" }
        newBind()
        field(1, "other")
        click(keys())
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        context.waitTicks(2)
        val where = widgets(KitCycleButton::class.java).first { it.message.string.startsWith("Where") }
        click(where)
        click(where)
        saveBind()
        check(general.binds[1].keys == listOf(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && general.binds[1].where == Where.BOTH) { "The second bind is ${general.binds[1]}" }

        // F7 then F8, in order, ended with Escape.
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

        // F9, only in The End, where the test world is not.
        newBind()
        field(1, "/me end")
        click(keys())
        context.input.pressKey(GLFW.GLFW_KEY_F9)
        context.waitTicks(2)
        click(named(Area.END.title).single())
        saveBind()
        check(general.binds.map { it.inOrder } == listOf(false, false, true, false) && general.binds[3].areas == listOf(Area.END)) { "The binds are ${general.binds}" }

        // A duplicate starts off. Once on, it clashes, and its cross asks twice.
        click(named("Duplicate").first())
        check(general.binds[1].name == "Warp End (Copy)" && !general.binds[1].enabled) { "The duplicate is ${general.binds[1]}" }
        click(named("On")[1])
        check(CommandBinds.clashes(general.binds[0], general.binds).single() === general.binds[1]) { "Two binds on Shift + F6 did not clash" }
        context.takeScreenshot("command-binds")
        vanilla("command-binds-vanilla")
        click(named("Delete")[1])
        check(general.binds.size == 5) { "One press on the cross deleted a bind" }
        context.takeScreenshot("command-binds-delete")
        click(named("Delete")[1])
        check(general.binds.size == 4 && general.binds.none { it.name.endsWith("(Copy)") }) { "Two presses on the cross left ${general.binds}" }

        click(named("Move Up")[1])
        check(general.binds[0].command == "other") { "Move Up left ${general.binds.map { it.command }}" }
        click(named("Move Down")[0])
        check(general.binds[0].command == "/me bound") { "Move Down left ${general.binds.map { it.command }}" }

        // Past four cards the list scrolls.
        repeat(3) { click(named("Duplicate").last()) }
        check(general.binds.size == 7) { "Three duplicates left ${general.binds.size} binds" }
        context.runOnClient<Nothing> { it.gui.screen()!!.setFocused(null) }
        context.input.pressKey(GLFW.GLFW_KEY_HOME)
        context.waitTicks(2)
        check(!named("Move Up").first().active) { "Home did not go to the first card" }
        val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { it.window.width * 0.7 to it.window.height * 0.5 }
        context.input.setCursorPos(x, y)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        check(named("Move Up").first().active) { "The wheel did not scroll the cards" }
        context.takeScreenshot("command-binds-scrolled")
        context.input.pressKey(GLFW.GLFW_KEY_END)
        context.waitTicks(2)
        check(!named("Move Down").last().active) { "End did not go to the last card" }
        repeat(3) {
            click(named("Delete").last())
            click(named("Delete").last())
        }
        check(general.binds.size == 4) { "Deleting the duplicates left ${general.binds.size}" }

        // A new group is named by typing over its name. General is exported and taken back in, then deleted.
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
