package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

fun Minecraft.showChat() = gui.setScreen(ChatScreen("", false))

fun ClientGameTestContext.openChat() {
    runOnClient<Nothing> { it.showChat() }
    waitForScreen(ChatScreen::class.java)
}

class InChat(private val context: ClientGameTestContext) {
    val width = context.computeOnClient<Int, Nothing> { it.window.guiScaledWidth }
    val height = context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight }

    fun open() {
        context.runOnClient<Nothing> { client ->
            client.gui.hud.chat.clearMessages(false)
            client.showChat()
        }
        context.waitForScreen(ChatScreen::class.java)
        context.waitTick()
    }

    fun move(x: Double, y: Double) = context.point(x, y)

    fun block(id: String) = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(id)) { "$id is not on the HUD" } }

    private fun box(client: Minecraft) = (client.gui.screen() as? ChatScreen)?.widgets<EditBox>()?.singleOrNull() ?: error("The chat closed")

    fun input() = context.computeOnClient<String, Nothing> { box(it).value }

    fun cursor() = context.computeOnClient<Int, Nothing> { box(it).cursorPosition }

    fun clearInput() = context.runOnClient<Nothing> { box(it).value = "" }

    fun checkAt(id: String, x: Float, y: Float, message: (Hud.Placed) -> String) = block(id).let { check(it.x == x && it.y == y) { message(it) } }

    fun checkNear(id: String, x: Double, y: Double, message: (Hud.Placed) -> String) =
        block(id).let { check(abs(it.x - x) < 0.6 && abs(it.y - y) < 0.6) { message(it) } }

    fun dragEscaped(id: String, x: Number, y: Number) = drag(id, x, y) {
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTick()
    }

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
