package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.dsmod.dragsimonly.DragSimOnly
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.concurrent.CopyOnWriteArrayList

class DragSimOnlyTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val hidden = CopyOnWriteArrayList<String>()
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, _ -> hidden += message.string }
        val refusal = "This ability is on cooldown for 4.35s"
        context.worldBuilder().create().use { world ->
            try {
                DsModClient.anyServer = false
                check(!DsModClient.game.active) { "The mod runs in singleplayer with DragSim Only on" }
                world.server.runCommand("""tellraw @a "$refusal"""")
                context.waitTicks(10)
                check(hidden.isEmpty()) { "Hide Cooldown Messages hid $hidden off dragsim.co" }

                DsModClient.feature<DragSimOnly>().feature.enabled = false
                world.server.runCommand("""tellraw @a "$refusal"""")
                context.waitTicks(10)
                check(hidden == listOf(refusal)) { "With DragSim Only off, Hide Cooldown Messages hid $hidden" }
            } finally {
                DsModClient.feature<DragSimOnly>().feature.enabled = true
                DsModClient.anyServer = true
            }
        }
    }
}
