package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The release build stays out of a world that is not dragsim.co, here singleplayer, until the player turns
 * Only on DragSim off. The debug build lifts the gate for the other tests, so this one puts it back.
 */
class OnlyOnDragSimTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val hidden = CopyOnWriteArrayList<String>()
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, _ -> hidden += message.string }
        val refusal = "This ability is on cooldown for 4.35s"
        context.worldBuilder().create().use { world ->
            try {
                DsModClient.anyServer = false
                check(!DsModClient.active) { "The mod runs in singleplayer with Only on DragSim on" }
                world.server.runCommand("""tellraw @a "$refusal"""")
                context.waitTicks(10)
                check(hidden.isEmpty()) { "Ability Spam hid $hidden off dragsim.co" }

                DsModClient.settings.general.onlyOnDragSim = false
                world.server.runCommand("""tellraw @a "$refusal"""")
                context.waitTicks(10)
                check(hidden == listOf(refusal)) { "With Only on DragSim off, Ability Spam hid $hidden" }
            } finally {
                DsModClient.settings.general.onlyOnDragSim = true
                DsModClient.anyServer = true
            }
        }
    }
}
