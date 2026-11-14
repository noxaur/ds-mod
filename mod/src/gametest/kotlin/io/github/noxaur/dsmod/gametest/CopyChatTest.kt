package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.CopyChats
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

/**
 * Right-clicks lines of the open chat with the real mouse. Either row of a message that wraps gives the whole
 * message, a click beside the chat or with the setting off gives nothing, and the scrolled chat gives the line
 * under the mouse. Read the screenshot for the flash and its label.
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
                    chat.addClientSystemMessage(Component.literal("The first message."))
                    chat.addClientSystemMessage(Component.literal(long))
                    chat.addClientSystemMessage(Component.literal("The last message."))
                    client.gui.setScreen(ChatScreen("", false))
                }
                context.waitForScreen(ChatScreen::class.java)
                context.waitTicks(2)

                // The chat's rows are 9 tall and end 40 above the screen's bottom. Row 0 is the newest line.
                fun click(row: Int, x: Int = 60) {
                    val (px, py) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                        val scale = client.window.guiScale
                        x * scale.toDouble() to (client.window.guiScaledHeight - 40 - row * 9 - 4) * scale.toDouble()
                    }
                    context.input.setCursorPos(px, py)
                    context.waitTick()
                    context.input.pressMouse(1)
                    context.waitTick()
                }
                fun copied(expected: List<String>, step: String) = check(copies == expected) { "$step copied $copies, not $expected" }

                click(0)
                copied(listOf("The last message."), "A click on the newest line")
                click(2)
                copied(listOf("The last message.", long), "A click on the message's first row")
                context.takeScreenshot("copy-chat")
                click(1)
                copied(listOf("The last message.", long, long), "A click on the message's second row")
                click(3)
                copied(listOf("The last message.", long, long, "The first message."), "A click on the line above it")

                copies.clear()
                click(1, x = 400)
                copied(emptyList(), "A click beside the chat")

                context.runOnClient<Nothing> { it.gui.hud.chat.scrollChat(4) }
                context.waitTick()
                click(0)
                copied(listOf("Filler 30"), "A click on the bottom row of the chat scrolled up by four")

                copies.clear()
                DsModClient.settings.chat.copyChat = false
                click(0)
                copied(emptyList(), "A click with Copy Chat off")
            }
        } finally {
            DsModClient.settings.chat.copyChat = true
            CopyChats.clipboard = clipboard
        }
    }
}
