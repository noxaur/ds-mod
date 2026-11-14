package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Waypoint
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The server sets a head, then one a block higher, as dragsim.co does while the Protector rises. The edge posts
 * each, and the beam stands on the first head's block. Read the screenshot: a beam labeled Protector with its distance.
 */
class BossBeaconTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext) = context.computeOnClient<List<Mark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == "boss-beacon" }.marks
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            // The beacon shows only in The End, and a local world's tab list names no area.
            context.runOnClient<Nothing> {
                DsModClient.game.post(System.currentTimeMillis(), TabList(listOf(ServerText(ServerSpan("Area: The End")))))
            }
            context.input.lookAt(0f, 0f)
            world.server.runCommand("execute at @a run setblock ~ ~ ~12 minecraft:player_head")
            context.waitTicks(10)
            val first = marks(context).single() as Waypoint
            check(first.label == "Protector") { "The beam is labeled ${first.label}" }
            world.server.runCommand("execute at @a run setblock ~ ~1 ~12 minecraft:player_head")
            context.waitTicks(10)
            check(marks(context).single() == first) { "The beam moved when the head climbed: ${marks(context)}" }
            context.waitTicks(10)
            context.takeScreenshot("boss-beacon")

            world.server.runCommand("execute at @a run setblock ~4 ~ ~12 minecraft:player_head")
            context.waitTicks(10)
            val next = marks(context).single() as Waypoint
            check(next.at.x == first.at.x + 4 && next.at.y == first.at.y) { "The beam did not follow the new head: $next" }
        }
    }
}
