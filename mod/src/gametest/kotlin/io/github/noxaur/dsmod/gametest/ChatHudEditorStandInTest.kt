package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import kotlin.math.abs

private const val TIMER = "dragon-timer"
private const val EQUIPMENT = "equipment-display"

/**
 * Away from The End the Dragon Timer has nothing to show, so with the chat open it stands in as its outlined title,
 * and drags to a place it keeps. A feature that is off stands in for nothing, and closing the chat takes the stand-ins
 * away. Read the screenshot for the outlines.
 */
class ChatHudEditorStandInTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            DsModClient.settings.hud.positions.remove(TIMER)
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { Hud.find(TIMER) == null }) { "The Dragon Timer shows outside The End" }
            val chat = InChat(context)
            chat.open()
            context.runOnClient<Nothing> {
                check(Hud.find(TIMER) != null) { "The Dragon Timer does not stand in with the chat open" }
                check(Hud.find("inventory-hud") == null) { "Inventory HUD, which is off, stands in" }
            }
            context.takeScreenshot("chat-hud-editor-stand-ins")

            chat.drag(TIMER, chat.width / 2.0 + 40, chat.height / 2.0 + 20)
            chat.block(TIMER).let { check(abs(it.x - (chat.width / 2.0 + 40)) < 0.6) { "The stand-in did not drag: ${it.x}, ${it.y}" } }
            check(TIMER in DsModClient.settings.hud.positions) { "The stand-in's place was not kept" }
            context.takeScreenshot("chat-hud-editor-stand-in-moved")

            context.setScreen { null }
            context.waitTick()
            check(context.computeOnClient<Boolean, Nothing> { Hud.find(TIMER) == null }) { "The stand-in stayed after the chat closed" }
            DsModClient.settings.hud.positions.remove(TIMER)

            // A live block that runs out of things to show with the chat open moves behind the live ones, as a stand-in.
            world.server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate")
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitFor { Hud.find(TIMER) != null && Hud.find(EQUIPMENT) != null }
            chat.open()
            fun order() = context.computeOnClient<List<String>, Nothing> { Hud.placed().map { it.id } }
            check(order().indexOf(TIMER) < order().indexOf(EQUIPMENT)) { "The live Dragon Timer does not lead the stack: ${order()}" }
            context.runOnClient<Nothing> { DsModClient.game.post(System.currentTimeMillis(), TabList(emptyList())) }
            context.waitFor { DsModClient.game.readouts.first { it.feature.id == TIMER }.hud == null }
            context.waitTick()
            check(order().indexOf(TIMER) > order().indexOf(EQUIPMENT)) { "The Dragon Timer's stand-in kept its live place: ${order()}" }
            context.setScreen { null }
        }
    }
}
