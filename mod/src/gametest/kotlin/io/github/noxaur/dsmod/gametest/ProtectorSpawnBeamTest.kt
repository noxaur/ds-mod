package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Waypoint
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

private const val SKIN = """{profile:{id:[I;1,2,3,4],name:"DsHead",properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6e319"}]}}"""

class ProtectorSpawnBeamTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext) = context.computeOnClient<List<Mark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == "protector-spawn-beam" }.marks
    }

    private fun tab(context: ClientGameTestContext, vararg rows: String) {
        context.runOnClient<Nothing> {
            DsModClient.game.post(System.currentTimeMillis(), TabList(rows.map { ServerText(ServerSpan(it)) }))
        }
        context.waitTicks(3)
    }

    override fun runTest(context: ClientGameTestContext) {
        val save = context.worldBuilder().create().use { world ->
            tab(context, "Area: The End")
            context.input.lookAt(0f, 0f)
            world.server.runCommand("execute at @a run setblock ~ ~ ~12 minecraft:player_head$SKIN")
            context.waitTicks(10)
            val first = marks(context).single() as Waypoint
            check(first.label == "Protector") { "The beam is labeled ${first.label}" }
            world.server.runCommand("execute at @a run setblock ~ ~1 ~12 minecraft:player_head$SKIN")
            context.waitTicks(10)
            check(marks(context).single() == first) { "The beam moved when the head climbed: ${marks(context)}" }
            context.waitTicks(10)
            context.takeScreenshot("protector-spawn-beam")

            tab(context, "Area: The End", "Protector: Rising (Tier 2/5, 120)")
            check(marks(context).isEmpty()) { "The beam shows with 120 kills left" }
            tab(context, "Area: The End", "Protector: Rising (Tier 4/5, 25)")
            check(marks(context).single() == first) { "The beam does not show with 25 kills left" }
            tab(context, "Area: The End", "Protector: Spawned")
            check(marks(context).isEmpty()) { "The beam shows during the fight" }
            DsModClient.feature<ProtectorSpawnBeam>().inFight.value = true
            context.waitTicks(10)
            check(marks(context).single() == first) { "The beam does not show during the fight when asked to" }
            DsModClient.feature<ProtectorSpawnBeam>().inFight.value = false

            world.server.runCommand("execute at @a run setblock ~ ~ ~12 minecraft:air")
            world.server.runCommand("execute at @a run setblock ~6 ~ ~12 minecraft:player_head")
            context.waitTicks(10)
            tab(context, "Area: The End")
            first to world.worldSave
        }

        save.second.open().use {
            context.waitTicks(40)
            tab(context, "Area: The End")
            val back = marks(context)
            val expected = save.first.copy(at = save.first.at.copy(y = save.first.at.y + 1))
            check(back == listOf(expected)) { "Back in the world, the beam is $back and not $expected" }
        }
    }
}
