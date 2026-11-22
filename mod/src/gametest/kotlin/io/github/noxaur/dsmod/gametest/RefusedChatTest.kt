package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.resources.Identifier
import java.util.concurrent.CopyOnWriteArrayList

class RefusedChatTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val seen = CopyOnWriteArrayList<String>()
        val refused = "Refused by another mod: counted once"
        val cooldown = "This ability is on cooldown for 4.35s"
        val refusedCooldown = "This ability is on cooldown for 9.99s"
        val plain = "A plain server line"
        val other = Identifier.fromNamespaceAndPath("dsmod_test", "other_mod")
        ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(other, Event.DEFAULT_PHASE)
        ClientReceiveMessageEvents.ALLOW_GAME.register(other) { message, _ -> message.string != refused && message.string != refusedCooldown }
        val (kept, hidden) = watchChat()
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> {
                DsModClient.game.tap = { _, event -> if (event is SystemMessage) seen += event.text.plain }
            }
            listOf(refused, cooldown, refusedCooldown, plain).forEach { world.server.runCommand("""tellraw @a {"text":"$it","color":"red"}""") }
            context.waitTicks(15)
            context.runOnClient<Nothing> { DsModClient.game.tap = null }
            val lines = setOf(refused, cooldown, refusedCooldown, plain)
            check(seen.filter { it in lines }.groupingBy { it }.eachCount() == lines.associateWith { 1 }) { "The features saw $seen" }
            check(kept.filter { it in lines } == listOf(plain)) { "Chat kept $kept" }
            check(hidden.filter { it in lines }.toSet() == setOf(refused, cooldown, refusedCooldown)) { "Hidden $hidden" }
            context.takeScreenshot("refused-chat")
        }
    }
}
