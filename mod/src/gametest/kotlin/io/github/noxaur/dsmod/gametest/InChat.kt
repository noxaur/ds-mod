package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen

/** The real mouse and keyboard on the open chat, in GUI pixels, for the tests that drag HUD blocks from it. */
class InChat(private val context: ClientGameTestContext) {
    private val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }.toDouble()
    val width = context.computeOnClient<Int, Nothing> { it.window.guiScaledWidth }
    val height = context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight }

    /** Opens an empty chat over a cleared chat history. */
    fun open() {
        context.runOnClient<Nothing> { client ->
            client.gui.hud.chat.clearMessages(false)
            client.gui.setScreen(ChatScreen("", false))
        }
        context.waitForScreen(ChatScreen::class.java)
        context.waitTick()
    }

    fun move(x: Double, y: Double) {
        context.input.setCursorPos(x * scale, y * scale)
        context.waitTick()
    }

    fun block(id: String) = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(id)) { "$id is not on the HUD" } }

    fun input() = context.computeOnClient<String, Nothing> { client ->
        (client.gui.screen() as? ChatScreen)?.children()?.filterIsInstance<EditBox>()?.single()?.value ?: error("The chat closed")
    }

    /** Presses on [id] 5 in from its corner, drags the mouse until the corner would be at [x], [y], runs [during] and lets go. */
    fun drag(id: String, x: Number, y: Number, during: () -> Unit = {}) {
        val toX = x.toDouble()
        val toY = y.toDouble()
        val from = block(id)
        move(from.x + 5.0, from.y + 5.0)
        context.input.holdMouse(0)
        context.waitTick()
        move((from.x + toX) / 2 + 5, (from.y + toY) / 2 + 5)
        move(toX + 5, toY + 5)
        during()
        context.input.releaseMouse(0)
        context.waitTick()
    }
}
