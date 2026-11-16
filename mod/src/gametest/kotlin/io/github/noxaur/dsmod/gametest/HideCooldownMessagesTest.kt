package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The server refuses an ability twice, and a player types the same words. The edge keeps both refusals out of
 * chat and lets the typed line through. Read the screenshot: chat must hold the typed line and one notice.
 */
class HideCooldownMessagesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val kept = CopyOnWriteArrayList<String>()
        val hidden = CopyOnWriteArrayList<String>()
        ClientReceiveMessageEvents.GAME.register { message, _ -> kept += message.string }
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, _ -> hidden += message.string }
        context.worldBuilder().create().use { world ->
            val refusal = "This ability is on cooldown for 4.35s"
            repeat(2) { world.server.runCommand("""tellraw @a {"text":"$refusal","color":"red"}""") }
            world.server.runCommand("""tellraw @a "Player1: $refusal"""")
            context.waitTicks(15)
            check(hidden == listOf(refusal, refusal)) { "Hidden: $hidden" }
            check("Player1: $refusal" in kept && refusal !in kept) { "Kept: $kept" }
            context.takeScreenshot("hide-cooldown-messages")
        }
    }
}
