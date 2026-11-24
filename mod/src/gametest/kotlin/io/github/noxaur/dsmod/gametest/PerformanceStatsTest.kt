package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.ui.performancestats.PerformanceStats
import io.github.noxaur.dsmod.core.feature.ui.performancestats.Stat
import io.github.noxaur.dsmod.core.feature.ui.performancestats.StatsLayout
import io.github.noxaur.dsmod.core.feature.ui.performancestats.StatsLook
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class PerformanceStatsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val performanceStats = DsModClient.feature<PerformanceStats>()
        context.runOnClient<Nothing> { performanceStats.feature.enabled = true }
        try {
            context.worldBuilder().create().use { world ->
                fun shown() = context.computeOnClient<List<Text>, Nothing> {
                    DsModClient.game.readouts.single { it.feature.id == ID }.hud?.rows?.map { it.text }.orEmpty()
                }
                fun line() = shown().joinToString(" / ") { it.plain }

                context.waitFor { FULL_SPEED.matches(line()) }
                context.takeScreenshot("performance-stats")

                context.runOnClient<Nothing> { performanceStats.layout.value = StatsLayout.COLUMN }
                world.server.runCommand("tick rate 5")
                context.waitFor { shown().lastOrNull()?.spans?.any { it.rgb == Role.WARNING.rgb || it.rgb == Role.BAD.rgb } == true }
                check(shown().map { it.plain.substringBefore(':') } == Stat.entries.map { it.toString() }) { "The column does not list FPS, Ping and TPS: ${line()}" }
                context.takeScreenshot("performance-stats-slow")
                world.server.runCommand("tick rate 20")

                context.runOnClient<Nothing> { performanceStats.look.value = StatsLook.CLEAR }
                world.server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0")
                context.waitFor { shown().lastOrNull()?.plain == "TPS: -" }
                context.waitFor { RATE.matches(shown().lastOrNull()?.plain.orEmpty()) }
                context.waitForScreen(null)
                context.takeScreenshot("performance-stats-clear")

                // @keep Vanilla logs every answer to a ping request, so an empty log means none was asked.
                context.runOnClient<Nothing> {
                    performanceStats.values.value = listOf(Stat.FPS, Stat.TPS)
                    it.debugOverlay.pingLogger.reset()
                }
                context.waitTicks(TICKS_PAST_A_PING_REQUEST)
                check(context.computeOnClient<Int, Nothing> { it.debugOverlay.pingLogger.size() } == 0) { "The mod asked for ping while Ping was not shown" }
                check(shown().map { it.plain.substringBefore(':') } == listOf("FPS", "TPS")) { "Ping is still shown: ${line()}" }
            }
        } finally {
            context.runOnClient<Nothing> {
                performanceStats.feature.enabled = false
                performanceStats.layout.value = StatsLayout.ROW
                performanceStats.look.value = StatsLook.PLATE
                performanceStats.values.value = Stat.entries
            }
        }
    }

    private companion object {
        const val ID = "performance-stats"
        const val TICKS_PAST_A_PING_REQUEST = 30

        val FULL_SPEED = Regex("""FPS: \d+ {3}Ping: \d+ ms {3}TPS: (19\.\d|20\.0)""")
        val RATE = Regex("""TPS: \d+\.\d""")
    }
}
