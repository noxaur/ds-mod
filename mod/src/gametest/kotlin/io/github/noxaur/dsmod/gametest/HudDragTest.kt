package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The HUD moves without the editor's mouse too: its key opens the editor, the arrows nudge the block clicked last, a
 * pixel or ten with Shift, + and - resize it, and Escape during a drag puts it back. With the chat open, a block drags
 * as in the editor once the mouse has moved a few pixels, and a click stays a click.
 */
class HudDragTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val hud = DsModClient.settings.hud
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }.toDouble()
        fun block() = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) { "The grid is not on the HUD" } }
        fun near(a: Float, b: Float) = abs(a - b) < 0.6f

        // Points at a spot 5 GUI pixels into the grid, in the window's pixels as the mouse moves in them.
        fun pointAt(block: Hud.Placed, dx: Double = 0.0, dy: Double = 0.0) = context.input.setCursorPos((block.x + 5 + dx) * scale, (block.y + 5 + dy) * scale)

        try {
            hud.editKey = GLFW.GLFW_KEY_J
            context.input.pressKey(GLFW.GLFW_KEY_J)
            context.waitForScreen(HudEditorScreen::class.java)

            // A click picks the grid, and the keys work on it from then on.
            val start = block()
            pointAt(start)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTick()
            context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.waitTick()
            val nudged = block()
            check(near(nudged.x, start.x + 1) && near(nudged.y, start.y + 1)) { "The arrows did not nudge the grid a pixel: ${start.x},${start.y} to ${nudged.x},${nudged.y}" }
            // The test's input sends no modifiers with a key, so the editor gets Shift and the arrow as the game sends them.
            context.runOnClient<Nothing> { it.gui.screen()!!.keyPressed(KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT)) }
            context.waitTick()
            check(near(block().x, nudged.x + 10)) { "Shift and an arrow did not nudge the grid ten pixels" }
            val pixels = { b: Hud.Placed -> (b.scale * scale).roundToInt() }
            val before = pixels(block())
            context.input.pressKey(GLFW.GLFW_KEY_EQUAL)
            context.waitTick()
            check(pixels(block()) == before + 1) { "+ did not make the grid bigger: ${pixels(block())} pixels, not ${before + 1}" }
            context.input.pressKey(GLFW.GLFW_KEY_MINUS)
            context.waitTick()
            check(pixels(block()) == before) { "- did not make the grid smaller" }

            // Escape during a drag puts the grid back where the drag started, and the editor stays open.
            val held = block()
            pointAt(held)
            context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.input.moveCursor(60 * scale, 40 * scale)
            context.waitTick()
            check(!near(block().x, held.x)) { "The drag did not move the grid" }
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTick()
            check(near(block().x, held.x) && near(block().y, held.y)) { "Escape did not put the grid back: ${block().x},${block().y}" }
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is HudEditorScreen }) { "Escape during a drag closed the editor" }
            context.runOnClient<Nothing> { it.gui.setScreen(null) }

            // With the chat open, a click on the grid leaves it where it is, and a drag moves it.
            context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
            context.waitForScreen(ChatScreen::class.java)
            val inChat = block()
            pointAt(inChat)
            context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.input.moveCursor(2 * scale, 0.0)
            // The game reads the mouse once a frame, so the move reaches it before the release.
            context.waitTick()
            context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTick()
            check(near(block().x, inChat.x)) { "A click with a 2px slip moved the grid" }
            pointAt(inChat)
            context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.input.moveCursor(30 * scale, 20 * scale)
            // The game reads the mouse once a frame, so the move reaches it before the release.
            context.waitTick()
            context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTick()
            val dragged = block()
            // It lines up with an edge it comes within 5px of, as in the editor.
            check(abs(dragged.x - inChat.x - 30) <= 5 && abs(dragged.y - inChat.y - 20) <= 5) { "The chat drag left the grid at ${dragged.x},${dragged.y}, not ${inChat.x + 30},${inChat.y + 20}" }
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is ChatScreen }) { "Dragging the grid closed the chat" }
            context.takeScreenshot("hud-dragged-in-chat")
            context.runOnClient<Nothing> { it.gui.setScreen(null) }
        } finally {
            hud.editKey = -1
            hud.positions.remove(ID)
        }
    }
}
