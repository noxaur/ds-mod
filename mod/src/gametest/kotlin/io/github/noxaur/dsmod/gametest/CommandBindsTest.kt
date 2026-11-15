package io.github.noxaur.dsmod.gametest

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.CommandBindsScreen
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitKeyButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import kotlin.io.path.readText
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Opens Command Binds' screen from its button in the settings and works it with the real keyboard and mouse:
 * New Bind, typing a command, binding a key and a mouse button, a clash, Escape clearing a key, a press elsewhere
 * keeping one, removing a row, and the list scrolling. Then in a world each key sends its command once per press,
 * a held key's repeat sends nothing, chat swallows the key, and the switch stops it all. Read the screenshots.
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

        fun <T : AbstractWidget> widgets(type: Class<T>) =
            context.computeOnClient<List<T>, Nothing> { client -> client.gui.screen()!!.children().filterIsInstance(type) }
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
        fun key(row: Int) = widgets(KitKeyButton::class.java)[row]
        fun add(command: String) {
            context.clickScreenButton("New Bind")
            context.waitTicks(2)
            context.input.typeChars(command)
            context.waitTicks(2)
        }
        fun binds(vararg expected: CommandBind, step: String) =
            check(general.binds == expected.toList()) { "$step left ${general.binds}, not ${expected.toList()}" }

        add("/me bound")
        binds(CommandBind("/me bound"), step = "New Bind and typing")
        click(key(0))
        check(key(0).listening) { "A click on the key button did not make it listen" }
        context.input.pressKey(GLFW.GLFW_KEY_F6)
        context.waitTicks(2)
        binds(CommandBind("/me bound", GLFW.GLFW_KEY_F6), step = "F6 on the listening button")

        add("other")
        click(key(1))
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        context.waitTicks(2)
        binds(CommandBind("/me bound", GLFW.GLFW_KEY_F6), CommandBind("other", GLFW.GLFW_MOUSE_BUTTON_MIDDLE), step = "The middle button")

        add("gg")
        click(key(2))
        context.input.pressKey(GLFW.GLFW_KEY_F6)
        context.waitTicks(2)
        check(key(0).clash == Role.BAD && key(2).clash == Role.BAD && key(1).clash == Role.WARNING) { "Two rows on F6 were not both ringed, or the middle button, which picks a block, was not marked" }
        context.takeScreenshot("command-binds")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("command-binds-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

        // Escape on a listening button clears its key, and the screen stays open.
        click(key(2))
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Screen?, Nothing> { it.gui.screen() } is CommandBindsScreen) { "Escape on the listening button closed the screen" }
        check(general.binds[2].key == CommandBind.NONE && key(0).clash == null) { "Escape left ${general.binds[2]}, and F6 still clashes" }
        // A left click elsewhere stops it listening, and the key it had stays.
        click(key(0))
        click(widgets(KitTextField::class.java)[1])
        check(!key(0).listening && general.binds[0].key == GLFW.GLFW_KEY_F6) { "A click elsewhere left ${general.binds[0]}, listening ${key(0).listening}" }
        // A key the game uses too is ringed as a warning: Q drops the held item.
        click(key(2))
        context.input.pressKey(GLFW.GLFW_KEY_Q)
        context.waitTicks(2)
        check(key(2).clash == Role.WARNING) { "Q, the game's drop key, was not ringed" }

        val remove = widgets(KitButton::class.java).filter { it.message.string == "Remove" }
        click(remove[2])
        binds(CommandBind("/me bound", GLFW.GLFW_KEY_F6), CommandBind("other", GLFW.GLFW_MOUSE_BUTTON_MIDDLE), step = "Removing the third row")

        // Past eight rows the list scrolls, and New Bind shows the row it adds.
        repeat(8) { add("/me row $it") }
        val fields = widgets(KitTextField::class.java)
        check(!fields.first().visible && fields.last().visible) { "Adding a tenth row did not scroll to it" }
        context.input.setCursorPos(10.0, 10.0)
        context.input.scroll(1.0)
        context.waitTicks(2)
        check(!fields[0].visible && fields[1].visible && !fields.last().visible) { "One notch up did not scroll one row" }
        context.runOnClient<Nothing> { it.gui.screen()!!.setFocused(null) }
        context.input.pressKey(GLFW.GLFW_KEY_HOME)
        context.waitTicks(2)
        check(fields[0].visible) { "Home did not go to the top" }
        context.input.pressKey(GLFW.GLFW_KEY_PAGE_DOWN)
        context.waitTicks(2)
        check(!fields[1].visible && fields.last().visible) { "Page Down did not go to the end" }
        context.takeScreenshot("command-binds-scrolled")
        repeat(8) { click(widgets(KitButton::class.java).filter { it.message.string == "Remove" && it.visible }.last()) }
        check(general.binds.size == 2) { "Removing the extra rows left ${general.binds}" }

        context.clickScreenButton("Done")
        context.waitTicks(20)
        val after = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        check(after === settings && after != null) { "Done went to $after, not back to the settings $settings" }
        val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
        check(""""binds":[{"command":"/me bound","key":295},{"command":"other","key":2}]""" in saved) { "The binds were not saved: $saved" }
        context.setScreen { null }

        context.worldBuilder().create().use { world ->
            fun said(word: String) = context.computeOnClient<Int, Nothing> { client ->
                (client.gui.hud.chat as ChatComponentAccessor).allMessages.count { it.content().string.endsWith(word) }
            }
            context.waitTicks(20)
            try {
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(10)
                check(said("bound") == 1) { "F6 sent /me bound ${said("bound")} times" }

                // A held key repeats, and the game counts each repeat as a press.
                context.input.holdKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(2)
                context.runOnClient<Nothing> { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6)) }
                context.waitTicks(2)
                context.input.releaseKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(10)
                check(said("bound") == 2) { "Holding F6 through a repeat sent /me bound ${said("bound") - 1} times" }

                context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
                context.waitTicks(10)
                check(said("other") == 1) { "The middle button sent other in chat ${said("other")} times" }

                context.setScreen { ChatScreen("", false) }
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(2)
                context.setScreen { null }
                context.waitTicks(10)
                check(said("bound") == 2) { "F6 in the chat box sent /me bound" }

                general.commandBinds = false
                context.input.pressKey(GLFW.GLFW_KEY_F6)
                context.waitTicks(10)
                check(said("bound") == 2) { "With Command Binds off F6 sent /me bound" }
            } finally {
                general.commandBinds = true
                general.binds = emptyList()
            }
        }
    }
}
