package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.ChatHudEditor
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
import io.github.noxaur.dsmod.edge.mixin.ChatScreenAccessor
import io.github.noxaur.dsmod.edge.mixin.CommandSuggestionsAccessor
import io.github.noxaur.dsmod.edge.mixin.SuggestionsListAccessor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

private const val TRACKER = "zealot-tracker"

/**
 * Where the chat is drawn over a block, the chat keeps the mouse: a link under a block fills the input and the wheel
 * scrolls the chat. The command suggestions keep only their own list, so a block elsewhere still drags while they are
 * open. Typing reaches the input over a block, and F1 and the switch turn Chat HUD Editor off. Read the screenshots for
 * a block under the chat and one under the suggestions.
 */
class ChatHudEditorPriorityTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.inWorldWithoutTimerPlace { world ->
            forgetHudPlace(TRACKER)
            context.showDragonTimer(world)
            context.waitFor { Hud.find(DRAGON_TIMER) != null && Hud.find(TRACKER) != null }
            val chat = InChat(context)
            chat.open()

            // Under a row of chat, the chat takes the press and the wheel: a link fills the input, and nothing moves.
            chat.drag(DRAGON_TIMER, 2.0, chat.height - 40.0 - 9 - 2)
            val under = chat.block(DRAGON_TIMER)
            context.runOnClient<Nothing> { client ->
                val messages = client.gui.hud.chat
                for (i in 1..40) messages.addClientSystemMessage(Component.literal("Filler $i"))
                messages.addClientSystemMessage(Component.literal("Click to fill the input").withStyle { it.withClickEvent(ClickEvent.SuggestCommand("hello")) })
            }
            chat.move(under.x + 5.0, chat.height - 40.0 - 4)
            context.takeScreenshot("chat-hud-editor-under-chat")
            context.input.pressMouse(0)
            context.waitTick()
            check(chat.input() == "hello") { "The link under the mouse filled the input with '${chat.input()}'" }
            context.input.scroll(1.0)
            context.waitTick()
            check(context.computeOnClient<Int, Nothing> { (it.gui.hud.chat as ChatComponentAccessor).chatScrollbarPos } > 0) { "The wheel over the chat did not scroll it" }
            chat.block(DRAGON_TIMER).let { check(it.x == under.x && it.y == under.y && it.scale == under.scale) { "The chat's press or wheel moved the block" } }
            context.runOnClient<Nothing> { it.gui.hud.chat.clearMessages(false) }

            // Typing still goes to the input, also with the mouse on a block.
            chat.clearInput()
            chat.move(under.x + 5.0, under.y + 5.0)
            context.input.typeChars("hi")
            context.waitTick()
            check(chat.input() == "hi") { "Typing over a block gave '${chat.input()}'" }

            // With the suggestions open, a block outside their list drags into it, and a press on the list there picks a command.
            chat.clearInput()
            context.input.typeChars("/")
            context.waitFor { client -> ((client.gui.screen() as ChatScreenAccessor).commandSuggestions as CommandSuggestionsAccessor).suggestions != null }
            val list = context.computeOnClient<IntArray, Nothing> { client ->
                val rect = ((((client.gui.screen() as ChatScreenAccessor).commandSuggestions as CommandSuggestionsAccessor).suggestions) as SuggestionsListAccessor).rect
                intArrayOf(rect.x, rect.y, rect.width, rect.height)
            }
            context.input.holdShift()
            chat.drag(TRACKER, list[0] + 20, list[1] + 2)
            context.input.releaseShift()
            val moved = chat.block(TRACKER)
            check(moved.x == list[0] + 20f && moved.y == list[1] + 2f) { "A block outside the suggestions did not drag: ${moved.x}, ${moved.y}, wanted ${list[0] + 20}, ${list[1] + 2}" }
            chat.move(moved.x + 5.0, moved.y + 5.0)
            context.takeScreenshot("chat-hud-editor-under-suggestions")
            context.input.pressMouse(0)
            context.waitTick()
            check(chat.input().length > 1) { "The press on the suggestions picked nothing: '${chat.input()}'" }
            chat.checkAt(TRACKER, moved.x, moved.y) { "The press on the suggestions moved the block" }
            chat.clearInput()

            // With the HUD hidden or the switch off, a drag moves nothing.
            val still = chat.block(DRAGON_TIMER)
            context.runOnClient<Nothing> { it.gui.hud.toggle() }
            chat.drag(DRAGON_TIMER, still.x + 60, still.y - 60)
            context.runOnClient<Nothing> { it.gui.hud.toggle() }
            chat.checkAt(DRAGON_TIMER, still.x, still.y) { "A drag with F1 moved the block" }
            DsModClient.feature<ChatHudEditor>().feature.enabled = false
            try {
                chat.drag(DRAGON_TIMER, still.x + 60, still.y - 60)
                chat.checkAt(DRAGON_TIMER, still.x, still.y) { "A drag with the switch off moved the block" }
            } finally {
                DsModClient.feature<ChatHudEditor>().feature.enabled = true
            }
            context.setScreen { null }
            forgetHudPlace(DRAGON_TIMER)
            forgetHudPlace(TRACKER)
        }
    }
}
