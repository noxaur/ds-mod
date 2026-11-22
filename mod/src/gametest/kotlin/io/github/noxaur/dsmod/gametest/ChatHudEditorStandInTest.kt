package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import kotlin.math.abs

private const val EQUIPMENT = "equipment-display"

class ChatHudEditorStandInTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.inWorldWithoutTimerPlace { world ->
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { Hud.find(DRAGON_TIMER) == null }) { "The Dragon Timer shows outside The End" }
            val chat = InChat(context)
            chat.open()
            context.runOnClient<Nothing> {
                check(Hud.find(DRAGON_TIMER) != null) { "The Dragon Timer does not stand in with the chat open" }
                check(Hud.find("inventory-hud") == null) { "Inventory HUD, which is off, stands in" }
            }
            context.takeScreenshot("chat-hud-editor-stand-ins")

            chat.drag(DRAGON_TIMER, chat.width / 2.0 + 40, chat.height / 2.0 + 20)
            chat.block(DRAGON_TIMER).let { check(abs(it.x - (chat.width / 2.0 + 40)) < 0.6) { "The stand-in did not drag: ${it.x}, ${it.y}" } }
            check(DRAGON_TIMER in DsModClient.settings.hud.positions.value) { "The stand-in's place was not kept" }
            context.takeScreenshot("chat-hud-editor-stand-in-moved")

            context.setScreen { null }
            context.waitTick()
            check(context.computeOnClient<Boolean, Nothing> { Hud.find(DRAGON_TIMER) == null }) { "The stand-in stayed after the chat closed" }
            forgetHudPlace(DRAGON_TIMER)

            world.server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate")
            context.showDragonTimer(world)
            context.waitFor { Hud.find(DRAGON_TIMER) != null && Hud.find(EQUIPMENT) != null }
            chat.open()
            fun order() = context.computeOnClient<List<String>, Nothing> { Hud.placed().map { it.id } }
            check(order().indexOf(DRAGON_TIMER) < order().indexOf(EQUIPMENT)) { "The live Dragon Timer does not lead the stack: ${order()}" }
            context.runOnClient<Nothing> { DsModClient.game.post(System.currentTimeMillis(), TabList(emptyList())) }
            context.waitFor { DsModClient.game.readouts.first { it.feature.id == DRAGON_TIMER }.hud == null }
            context.waitTick()
            check(order().indexOf(DRAGON_TIMER) > order().indexOf(EQUIPMENT)) { "The Dragon Timer's stand-in kept its live place: ${order()}" }
            context.setScreen { null }
        }
    }
}
