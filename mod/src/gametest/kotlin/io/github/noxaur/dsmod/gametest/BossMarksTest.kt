package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/** @keep Covers [io.github.noxaur.dsmod.edge.Entities]. */
class BossMarksTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext, id: String) = context.computeOnClient<List<Mark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == id }.marks
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> {
                DsModClient.feature<BossBeacon>().feature.enabled = true
                DsModClient.feature<BossTracer>().feature.enabled = true
            }
            world.freezeTime()
            world.server.runCommand("""execute at @a run summon iron_golem ~ ~ ~-12 {NoAI:1b,CustomName:"[Lv100] Endstone Protector 10M/10M"}""")
            context.runOnClient<Nothing> {
                DsModClient.game.post(System.currentTimeMillis(), SystemMessage(ServerText(ServerSpan("☠ SLAYER BOSS SPAWNING!"))))
            }
            world.server.runCommand("""execute at @a run summon zombie ~12 ~ ~ {NoAI:1b,CustomName:"☠ Revenant Horror I 3.8M/3.8M❤"}""")
            context.waitTicks(10)

            val beams = marks(context, "boss-beacon")
            check(beams.map { (it as EntityBeam).label }.sorted() == listOf("Endstone Protector", "Revenant Horror I")) { "The beams are $beams" }
            check(marks(context, "boss-tracer").size == 2 && marks(context, "boss-tracer").all { it is Tracer }) { "The tracers are ${marks(context, "boss-tracer")}" }

            context.input.lookAt(-135f, 0f)
            context.waitTicks(5)
            context.screenshot("boss-beacon")

            context.runOnClient<Nothing> { DsModClient.feature<BossBeacon>().feature.enabled = false }
            context.input.lookAt(45f, 0f)
            context.waitTicks(5)
            val away = context.screenshot("boss-tracer")
            val color = Role.BAD.rgb
            check(countNear(away, color) > 200) { "Looking away, the screenshot shows no tracer" }

            world.server.runCommand("""execute at @a run summon zombie ~-12 ~ ~ {NoAI:1b,CustomName:"☠ Atoned Horror V 50M/50M❤"}""")
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 2) { "Another player's slayer boss is traced: ${marks(context, "boss-tracer")}" }
            context.runOnClient<Nothing> { DsModClient.feature<BossTracer>().bosses.otherSlayers.value = true }
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 3) { "Other Slayer Bosses traces no third boss: ${marks(context, "boss-tracer")}" }

            world.server.runCommand("kill @e[type=iron_golem]")
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 2) { "The dead Protector is still traced: ${marks(context, "boss-tracer")}" }

            context.runOnClient<Nothing> { DsModClient.openSettings("tracerOtherSlayers") }
            context.waitTicks(20)
            context.takeScreenshot("boss-settings")
            context.setScreen { null }
        }
    }
}
