package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.loader.api.FabricLoader
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The server refuses an ability twice, and a player types the same words. The edge keeps both refusals out of
 * chat and lets the typed line through. Read the screenshot: chat must hold the typed line and one notice.
 */
class AbilitySpamTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        // An earlier run may have saved that the player was told already.
        val file = FabricLoader.getInstance().configDir.resolve("dsmod/data/ability-spam.json")
        file.parent.createDirectories()
        file.writeText("""{"told":false}""")
        context.runOnClient<Nothing> { DsModClient.saved.load() }

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
            context.takeScreenshot("ability-spam")
        }
    }
}
