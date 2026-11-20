package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.CopyChats
import io.github.noxaur.dsmod.edge.CopyChats.Format
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Works Copy Chat in the open chat with the real mouse and keyboard. A right click selects a message and opens
 * the menu beside the mouse, a right drag selects all between, Shift extends, Ctrl adds and removes,
 * the keys a menu and a selection take work, and a drag held past the chat's edge scrolls it. Each button copies
 * its form. Read the screenshots for the selection, the menu in both themes and the flash.
 */
class CopyChatTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val long = "A message long enough to run over two rows of the chat, so that a click on either row has to find all of it."
        val copies = ArrayList<String>()
        val clipboard = CopyChats.clipboard
        CopyChats.clipboard = { copies += it }
        try {
            context.worldBuilder().create().use {
                context.runOnClient<Nothing> { client ->
                    val chat = client.gui.hud.chat
                    chat.clearMessages(false)
                    for (i in 1..30) chat.addClientSystemMessage(Component.literal("Filler $i"))
                    // A block as dragsim.co sends one: a message per line, with empty messages between.
                    for (line in listOf("Top", "", "Middle", "", "Bottom")) chat.addClientSystemMessage(Component.literal(line))
                    // A block sent the other way: several lines in one message, one empty and one centered with spaces.
                    chat.addClientSystemMessage(
                        Component.literal("ACTIVE BOOSTERS\n\n      Everyone: +50% drops.\n").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Expires in 1h").withStyle(ChatFormatting.GRAY)),
                    )
                    chat.addClientSystemMessage(
                        Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("Summoning Eye").withStyle { it.withBold(false).withColor(0x12AB34) }),
                    )
                    chat.addClientSystemMessage(Component.literal(long))
                    chat.addClientSystemMessage(Component.literal("The last message."))
                    client.gui.setScreen(ChatScreen("", false))
                }
                context.waitForScreen(ChatScreen::class.java)
                context.waitTicks(2)

                fun move(x: Int, y: Int) {
                    val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
                    context.input.setCursorPos(x * scale.toDouble(), y * scale.toDouble())
                    context.waitTick()
                }
                // The chat's rows are 9 tall and end 40 above the screen's bottom. Row 0 is the newest line.
                fun row(row: Int, x: Int = 60) = move(x, context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight } - 40 - row * 9 - 4)
                fun press(format: Format) {
                    val menu = checkNotNull(CopyChats.menu) { "No menu is open to press $format in" }
                    move(menu.buttonX(format) + menu.buttonWidth(format) / 2, menu.buttonY + 8)
                    context.input.pressMouse(0)
                    context.waitTick()
                }
                fun copied(expected: String, step: String) {
                    check(copies == listOf(expected)) { "$step copied $copies, not [$expected]" }
                    check(CopyChats.menu == null) { "$step left the menu open" }
                    copies.clear()
                }

                row(2)
                context.input.pressMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy Message") { "A right click opened ${CopyChats.menu?.header}" }
                check(copies.isEmpty()) { "A right click alone copied $copies" }
                context.takeScreenshot("copy-chat-beside")
                // The menu is beside the mouse, so the row under it is still there to press: one down is the next message.
                row(0)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.TEXT)
                copied("The last message.", "A right click one row under the first, with the menu open")
                row(2)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.TEXT)
                copied(long, "A right click on the message's first row")
                context.takeScreenshot("copy-chat-copied")

                // Dragged from the newest line up to the drop, over the wrapped message.
                row(0)
                context.input.holdMouse(1)
                row(3)
                context.waitTick()
                context.takeScreenshot("copy-chat-selecting")
                context.input.releaseMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy 3 Messages") { "The drag opened ${CopyChats.menu?.header}" }
                context.takeScreenshot("copy-chat-menu")
                DsModClient.settings.hud.theme = Theme.VANILLA
                context.waitTick()
                context.takeScreenshot("copy-chat-menu-vanilla")
                DsModClient.settings.hud.theme = Theme.CHARCOAL
                press(Format.FORMATTED)
                copied("&6&lRARE DROP! &#12ab34Summoning Eye\n$long\nThe last message.", "Formatted, for three messages")

                // Dragged downward gives the same order, oldest first.
                row(3)
                context.input.holdMouse(1)
                row(1)
                context.input.releaseMouse(1)
                context.waitTick()
                press(Format.TEXT)
                copied("RARE DROP! Summoning Eye\n$long", "Text, dragged downward over two messages")

                row(3)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.JSON)
                val json = copies.single()
                check(json.startsWith("{") && "\"RARE DROP! \"" in json && "\"gold\"" in json && "Summoning Eye" in json) { "JSON for one message was $json" }
                copies.clear()

                row(1)
                context.input.holdMouse(1)
                row(0)
                context.input.releaseMouse(1)
                context.waitTick()
                press(Format.JSON)
                check(copies.single().startsWith("[") && copies.single().endsWith("]")) { "JSON for two messages was ${copies.single()}" }
                copies.clear()

                // One line of a block is one press, without the spaces that centered it.
                row(5)
                context.input.pressMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy Line") { "A right click in a block opened ${CopyChats.menu?.header}" }
                context.takeScreenshot("copy-chat-block-line")
                press(Format.TEXT)
                copied("Everyone: +50% drops.", "A line of a block")
                row(6)
                context.input.pressMouse(1)
                context.waitTick()
                check(CopyChats.menu == null) { "A right click on a block's empty line opened the menu" }
                // Dragged over the whole block and the message under it.
                row(7)
                context.input.holdMouse(1)
                row(3)
                context.input.releaseMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy 4 Lines") { "A drag over a block opened ${CopyChats.menu?.header}" }
                press(Format.FORMATTED)
                copied("&6ACTIVE BOOSTERS\n\n&6Everyone: +50% drops.\n&7Expires in 1h\n&6&lRARE DROP! &#12ab34Summoning Eye", "Formatted, for a block and a message")
                row(7)
                context.input.holdMouse(1)
                row(4)
                context.input.releaseMouse(1)
                context.waitTick()
                press(Format.JSON)
                check(copies.single().startsWith("{") && "\\n" in copies.single()) { "JSON for a whole block was ${copies.single()}" }
                copies.clear()
                row(7)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.JSON)
                check("ACTIVE BOOSTERS" in copies.single() && "Everyone" !in copies.single()) { "JSON for a block's first line was ${copies.single()}" }
                copies.clear()

                // Empty messages are all the same to look at, and each still belongs to its own place in the drag.
                row(10)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.TEXT)
                copied("Middle", "A message between two empty ones")
                row(12)
                context.input.holdMouse(1)
                row(8)
                context.waitTick()
                context.takeScreenshot("copy-chat-block")
                context.input.releaseMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy 3 Messages") { "A drag over a block of messages opened ${CopyChats.menu?.header}" }
                press(Format.TEXT)
                copied("Top\n\nMiddle\n\nBottom", "A block of five messages, two of them empty")

                // Let go on the empty message under Middle: the drag ends there, and the copy leaves the empty edge out.
                row(12)
                context.input.holdMouse(1)
                row(9)
                context.input.releaseMouse(1)
                context.waitTick()
                check(CopyChats.menu?.header == "Copy 2 Messages") { "A drag that ended on an empty message opened ${CopyChats.menu?.header}" }
                press(Format.TEXT)
                copied("Top\n\nMiddle", "A drag that ended on an empty message")

                fun click(row: Int) {
                    row(row)
                    context.input.pressMouse(1)
                    context.waitTick()
                }
                fun key(key: Int) {
                    context.input.pressKey(key)
                    context.waitTick()
                }
                fun header(expected: String, step: String) = check(CopyChats.menu?.header == expected) { "$step opened ${CopyChats.menu?.header}, not $expected" }
                val drop = "RARE DROP! Summoning Eye"

                // Shift extends from the first press, and the anchor outlives the copy.
                click(0)
                context.input.holdShift()
                click(3)
                header("Copy 3 Messages", "A Shift press three messages up")
                context.input.releaseShift()
                // Ctrl+C is Text.
                context.input.holdControl()
                key(GLFW.GLFW_KEY_C)
                context.input.releaseControl()
                copied("$drop\n$long\nThe last message.", "Shift and Ctrl+C")
                context.input.holdShift()
                click(2)
                context.input.releaseShift()
                header("Copy 2 Messages", "A Shift press after a copy")
                key(GLFW.GLFW_KEY_ESCAPE)

                // Ctrl adds a message that is not next to the first, and takes one out again.
                click(0)
                context.input.holdControl()
                click(3)
                header("Copy 2 Messages", "A Ctrl press")
                context.takeScreenshot("copy-chat-ctrl")
                context.input.releaseControl()
                key(GLFW.GLFW_KEY_RIGHT)
                key(GLFW.GLFW_KEY_ENTER)
                copied("&6&lRARE DROP! &#12ab34Summoning Eye\nThe last message.", "Ctrl on two messages apart, then Right and Enter")
                click(0)
                context.input.holdControl()
                click(3)
                click(0)
                context.input.releaseControl()
                header("Copy Message", "A Ctrl press on a selected message")
                key(GLFW.GLFW_KEY_ENTER)
                copied(drop, "Enter with the menu just opened")

                // A press inside a selection of several keeps it.
                row(0)
                context.input.holdMouse(1)
                row(3)
                context.input.releaseMouse(1)
                context.waitTick()
                click(1)
                header("Copy 3 Messages", "A press inside a selection of three")
                key(GLFW.GLFW_KEY_ESCAPE)

                // Shift with an arrow moves the end, and Ctrl+A takes everything.
                click(0)
                context.input.holdShift()
                key(GLFW.GLFW_KEY_UP)
                key(GLFW.GLFW_KEY_UP)
                header("Copy 3 Messages", "Shift and Up twice")
                key(GLFW.GLFW_KEY_DOWN)
                header("Copy 2 Messages", "Shift and Down")
                context.input.releaseShift()
                context.input.holdControl()
                key(GLFW.GLFW_KEY_A)
                context.input.releaseControl()
                header("Copy 39 Lines", "Ctrl+A")
                key(GLFW.GLFW_KEY_ESCAPE)

                // Held above the chat, the drag scrolls it and keeps selecting.
                row(0)
                context.input.holdMouse(1)
                move(60, 5)
                context.waitTicks(20)
                context.input.releaseMouse(1)
                context.waitTick()
                val scrolled = context.computeOnClient<Int, Nothing> { (it.gui.hud.chat as ChatComponentAccessor).chatScrollbarPos }
                check(scrolled > 5) { "A drag held above the chat scrolled it by $scrolled" }
                check(CopyChats.menu!!.header.removePrefix("Copy ").substringBefore(' ').toInt() > 20) { "A drag held above the chat opened ${CopyChats.menu?.header}" }
                key(GLFW.GLFW_KEY_ESCAPE)
                context.runOnClient<Nothing> { it.gui.hud.chat.resetChatScroll() }
                context.waitTick()

                // Escape and a click elsewhere put the menu away, copy nothing and leave the chat open.
                row(0)
                context.input.pressMouse(1)
                context.waitTick()
                context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
                context.waitTick()
                check(CopyChats.menu == null) { "Escape left the menu open" }
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ChatScreen }) { "Escape closed the chat with the menu" }
                row(0)
                context.input.pressMouse(1)
                context.waitTick()
                move(400, 20)
                context.input.pressMouse(0)
                context.waitTick()
                check(CopyChats.menu == null && copies.isEmpty()) { "A click beside the menu left it open or copied $copies" }

                row(1, x = 400)
                context.input.pressMouse(1)
                context.waitTick()
                check(CopyChats.menu == null) { "A right click beside the chat opened the menu" }

                context.runOnClient<Nothing> { it.gui.hud.chat.scrollChat(4) }
                context.waitTick()
                row(0)
                context.input.pressMouse(1)
                context.waitTick()
                press(Format.TEXT)
                copied("Expires in 1h", "The bottom row of the chat scrolled up by four")

                DsModClient.feature<CopyChat>().feature.enabled = false
                row(0)
                context.input.pressMouse(1)
                context.waitTick()
                check(CopyChats.menu == null) { "A right click with Copy Chat off opened the menu" }
            }
        } finally {
            DsModClient.feature<CopyChat>().feature.enabled = true
            DsModClient.settings.hud.theme = Theme.CHARCOAL
            CopyChats.clipboard = clipboard
        }
    }
}
