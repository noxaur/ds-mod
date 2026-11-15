package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ID = "dragon-timer"

/**
 * Works Drag In Chat with the real mouse and keyboard on a live block: a drag moves it and lines it up, Shift held
 * during the drag skips that, Escape puts it back and keeps the chat open, the wheel resizes it and a right press
 * resets it. Over the chat's own rows the chat keeps its clicks and its wheel, nothing reaches the input, F1 and
 * the switch turn it off, and closing the chat saves. Read the screenshots for the hover and the drag.
 */
class DragInChatTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            DsModClient.settings.hud.positions.remove(ID)
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            context.runOnClient<Nothing> { client ->
                client.gui.hud.chat.clearMessages(false)
                client.gui.setScreen(ChatScreen("", false))
            }
            context.waitForScreen(ChatScreen::class.java)
            context.waitTicks(2)

            val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }.toDouble()
            fun move(x: Double, y: Double) {
                context.input.setCursorPos(x * scale, y * scale)
                context.waitTick()
            }
            fun block() = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) { "The Dragon Timer is not on the HUD" } }
            fun input() = context.computeOnClient<String, Nothing> { client ->
                (client.gui.screen() as? ChatScreen)?.children()?.filterIsInstance<EditBox>()?.single()?.value ?: error("The chat closed")
            }
            fun drag(x: Number, y: Number, during: () -> Unit = {}) {
                val toX = x.toDouble()
                val toY = y.toDouble()
                val from = block()
                move(from.x + 5.0, from.y + 5.0)
                context.input.holdMouse(0)
                context.waitTick()
                move((from.x + toX) / 2 + 5, (from.y + toY) / 2 + 5)
                move(toX + 5, toY + 5)
                during()
                context.input.releaseMouse(0)
                context.waitTick()
            }

            val start = block()
            move(start.x + 5.0, start.y + 5.0)
            context.takeScreenshot("drag-in-chat-hover")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("drag-in-chat-hover-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            // Escape during a drag out of the stack puts the block back in its place there, and the stack as it was.
            val stack = context.computeOnClient<List<Pair<Float, Float>>, Nothing> { Hud.placed().map { it.x to it.y } }
            check(stack.size > 1) { "The stack holds one block, so its order is not tested" }
            // The stack follows the features' order, so the Dragon Timer tops it although the Zealot Tracker showed first.
            context.runOnClient<Nothing> { check(Hud.placed().first().id == ID) { "The stack starts with ${Hud.placed().first().id}" } }
            drag(start.x + 80, start.y + 60) { context.input.pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTick() }
            context.runOnClient<Nothing> { check(Hud.placed().map { it.x to it.y } == stack) { "Escape left the stack at ${Hud.placed().map { it.x to it.y }}, not $stack" } }
            check(ID !in DsModClient.settings.hud.positions) { "Escape kept a position for a block from the stack" }

            // Let go three pixels off the middle of the screen, the block centers itself.
            val (width, height) = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.guiScaledWidth to it.window.guiScaledHeight }
            val centered = (width - start.width * start.scale) / 2.0
            val middle = (height - start.height * start.scale) / 2.0
            drag(centered + 3, middle - 3) { context.takeScreenshot("drag-in-chat-dragging") }
            block().let { check(abs(it.x - centered) < 0.6 && abs(it.y - middle) < 0.6) { "The block did not center: ${it.x}, ${it.y}, wanted $centered, $middle" } }
            check(ID in DsModClient.settings.hud.positions) { "The drag was not kept" }

            // Shift pressed after the press still skips the lining up.
            drag(centered + 4, middle - 4) { context.input.holdShift(); move(centered + 8.0, middle + 2.0) }
            context.input.releaseShift()
            block().let { check(abs(it.x - (centered + 3)) < 0.6 && abs(it.y - (middle - 3)) < 0.6) { "Shift did not skip the snap: ${it.x}, ${it.y}" } }

            // Escape during a drag puts the block back and keeps the chat open.
            val before = block()
            drag(before.x + 40, before.y + 30) { context.input.pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTick() }
            block().let { check(it.x == before.x && it.y == before.y) { "Escape left the block at ${it.x}, ${it.y}, not ${before.x}, ${before.y}" } }
            context.runOnClient<Nothing> { check(it.gui.screen() is ChatScreen) { "Escape during a drag closed the chat" } }

            // One notch of the wheel is one screen pixel more per HUD pixel.
            fun pixels() = (block().scale * scale).roundToInt()
            val small = pixels()
            move(block().x + 5.0, block().y + 5.0)
            context.input.scroll(1.0)
            context.waitTick()
            check(pixels() == small + 1) { "A notch drew ${pixels()} pixels, not ${small + 1}" }
            context.input.scroll(-1.0)
            context.waitTick()
            check(pixels() == small) { "A notch back drew ${pixels()} pixels, not $small" }

            // A right press puts the block back in the stack.
            context.input.pressMouse(1)
            context.waitTick()
            check(ID !in DsModClient.settings.hud.positions) { "The right press kept the position" }
            block().let { check(it.x == start.x && it.y == start.y) { "The reset block is at ${it.x}, ${it.y}, not ${start.x}, ${start.y}" } }
            check(input().isEmpty()) { "Dragging put '${input()}' in the input" }

            // Under a row of chat, the chat takes the press and the wheel: a link fills the input, and nothing moves.
            drag(2.0, height - 40.0 - 9 - 2)
            val under = block()
            context.runOnClient<Nothing> { client ->
                val chat = client.gui.hud.chat
                for (i in 1..40) chat.addClientSystemMessage(Component.literal("Filler $i"))
                chat.addClientSystemMessage(Component.literal("Click to fill the input").withStyle { it.withClickEvent(ClickEvent.SuggestCommand("/hello")) })
            }
            context.waitTick()
            move(under.x + 5.0, height - 40.0 - 4)
            context.takeScreenshot("drag-in-chat-under-chat")
            context.input.pressMouse(0)
            context.waitTick()
            check(input() == "/hello") { "The link under the mouse filled the input with '${input()}'" }
            context.runOnClient<Nothing> { client ->
                (client.gui.screen() as ChatScreen).children().filterIsInstance<EditBox>().single().value = ""
            }
            context.input.scroll(1.0)
            context.waitTick()
            check(context.computeOnClient<Int, Nothing> { (it.gui.hud.chat as ChatComponentAccessor).chatScrollbarPos } > 0) { "The wheel over the chat did not scroll it" }
            block().let { check(it.x == under.x && it.y == under.y && it.scale == under.scale) { "The chat's press or wheel moved the block" } }
            context.runOnClient<Nothing> { it.gui.hud.chat.clearMessages(false) }
            context.waitTick()

            // Typing still goes to the input, also with the mouse on a block.
            move(under.x + 5.0, under.y + 5.0)
            context.input.typeChars("hi")
            context.waitTick()
            check(input() == "hi") { "Typing over a block gave '${input()}'" }

            // With the HUD hidden or the switch off, a drag moves nothing.
            context.runOnClient<Nothing> { it.gui.hud.toggle() }
            drag(under.x + 60, under.y - 60)
            context.runOnClient<Nothing> { it.gui.hud.toggle() }
            block().let { check(it.x == under.x && it.y == under.y) { "A drag with F1 moved the block" } }
            DsModClient.settings.hud.dragInChat = false
            try {
                drag(under.x + 60, under.y - 60)
                block().let { check(it.x == under.x && it.y == under.y) { "A drag with the switch off moved the block" } }
            } finally {
                DsModClient.settings.hud.dragInChat = true
            }

            // Closing the chat saves where the block went.
            drag(centered, middle)
            val placed = DsModClient.settings.hud.positions.getValue(ID)
            context.setScreen { null }
            context.waitTicks(20)
            val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
            check(""""$ID":{"x":${placed.x},"y":${placed.y},"scale":${placed.scale}}""" in saved) { "The position was not saved: $saved" }
            DsModClient.settings.hud.positions.remove(ID)
        }
    }
}
