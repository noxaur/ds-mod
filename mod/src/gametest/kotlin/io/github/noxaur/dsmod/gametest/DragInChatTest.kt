package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ID = "dragon-timer"

/**
 * Works Drag In Chat with the real mouse and keyboard on a live block: a drag moves it and lines it up, Shift held
 * during the drag skips that, Escape puts it back and keeps the chat open, the arrows nudge it while it is held and
 * edit the input while it is not, the wheel resizes it, a right press resets it, and closing the chat saves. Read
 * the screenshots for the hover and the drag.
 */
class DragInChatTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            DsModClient.settings.hud.positions.remove(ID)
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitFor { Hud.find(ID) != null && Hud.placed().size > 1 }
            val chat = InChat(context)
            chat.open()

            val start = chat.block(ID)
            chat.move(start.x + 5.0, start.y + 5.0)
            context.takeScreenshot("drag-in-chat-hover")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTick()
            context.takeScreenshot("drag-in-chat-hover-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            // Escape during a drag out of the stack puts the block back in its place there, and the stack as it was.
            val stack = context.computeOnClient<List<Pair<Float, Float>>, Nothing> { Hud.placed().map { it.x to it.y } }
            // The stack follows the features' order, so the Dragon Timer tops it although the Zealot Tracker showed first.
            context.runOnClient<Nothing> { check(Hud.placed().first().id == ID) { "The stack starts with ${Hud.placed().first().id}" } }
            chat.drag(ID, start.x + 80, start.y + 60) { context.input.pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTick() }
            context.runOnClient<Nothing> { check(Hud.placed().map { it.x to it.y } == stack) { "Escape left the stack at ${Hud.placed().map { it.x to it.y }}, not $stack" } }
            context.runOnClient<Nothing> { check(it.gui.screen() is ChatScreen) { "Escape during a drag closed the chat" } }
            check(ID !in DsModClient.settings.hud.positions) { "Escape kept a position for a block from the stack" }

            // Let go three pixels off the middle of the screen, the block centers itself. Low on the screen, where no
            // other block's edge is near, so only the middle pulls it.
            val centered = (chat.width - start.width * start.scale) / 2.0
            val middle = (chat.height * 3 / 4).toDouble()
            chat.drag(ID, centered + 3, middle) { context.takeScreenshot("drag-in-chat-dragging") }
            chat.block(ID).let { check(abs(it.x - centered) < 0.6 && abs(it.y - middle) < 0.6) { "The block did not center: ${it.x}, ${it.y}, wanted $centered, $middle" } }

            // Shift pressed after the press still skips the lining up.
            chat.drag(ID, centered + 4, middle) { context.input.holdShift(); chat.move(centered + 8.0, middle + 5.0) }
            context.input.releaseShift()
            chat.block(ID).let { check(abs(it.x - (centered + 3)) < 0.6 && abs(it.y - middle) < 0.6) { "Shift did not skip the snap: ${it.x}, ${it.y}" } }

            // Escape during a drag of a placed block puts it back where it was.
            val placed = chat.block(ID)
            chat.drag(ID, placed.x + 40, placed.y + 30) { context.input.pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTick() }
            chat.block(ID).let { check(it.x == placed.x && it.y == placed.y) { "Escape left the block at ${it.x}, ${it.y}, not ${placed.x}, ${placed.y}" } }

            // While the block is held, the arrows nudge it a pixel, and ten with Shift, and the input stays empty.
            chat.drag(ID, placed.x, placed.y) {
                context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
                context.input.holdShift()
                context.input.pressKey(GLFW.GLFW_KEY_DOWN)
                context.input.releaseShift()
                context.waitTick()
            }
            chat.block(ID).let { check(it.x == placed.x + 1 && it.y == placed.y + 10) { "The arrows put the block at ${it.x}, ${it.y}, not ${placed.x + 1}, ${placed.y + 10}" } }
            check(chat.input().isEmpty()) { "Dragging and nudging put '${chat.input()}' in the input" }

            // With nothing held, the arrows are the input's, even with the mouse on the block.
            val nudged = chat.block(ID)
            chat.move(nudged.x + 5.0, nudged.y + 5.0)
            context.input.typeChars("ab")
            context.input.pressKey(GLFW.GLFW_KEY_LEFT)
            context.waitTick()
            val cursor = context.computeOnClient<Int, Nothing> { (it.gui.screen() as ChatScreen).children().filterIsInstance<EditBox>().single().cursorPosition }
            check(chat.input() == "ab" && cursor == 1) { "Left with nothing held gave '${chat.input()}' with the cursor at $cursor" }
            chat.block(ID).let { check(it.x == nudged.x && it.y == nudged.y) { "Left with nothing held moved the block" } }

            // One notch of the wheel is one screen pixel more per HUD pixel.
            val guiScale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
            fun pixels() = (chat.block(ID).scale * guiScale).roundToInt()
            val small = pixels()
            context.input.scroll(1.0)
            context.waitTick()
            check(pixels() == small + 1) { "A notch drew ${pixels()} pixels, not ${small + 1}" }
            context.input.scroll(-1.0)
            context.waitTick()
            check(pixels() == small) { "A notch back drew ${pixels()} pixels, not $small" }

            // Closing the chat saves where the block went.
            val kept = DsModClient.settings.hud.positions.getValue(ID)
            context.setScreen { null }
            context.waitFor { FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText().contains(""""$ID":{"x":${kept.x},"y":${kept.y},"scale":${kept.scale}}""") }

            // A right press puts the block back in the stack.
            chat.open()
            chat.move(nudged.x + 5.0, nudged.y + 5.0)
            context.input.pressMouse(1)
            context.waitTick()
            check(ID !in DsModClient.settings.hud.positions) { "The right press kept the position" }
            chat.block(ID).let { check(it.x == start.x && it.y == start.y) { "The reset block is at ${it.x}, ${it.y}, not ${start.x}, ${start.y}" } }
            context.setScreen { null }
        }
    }
}
