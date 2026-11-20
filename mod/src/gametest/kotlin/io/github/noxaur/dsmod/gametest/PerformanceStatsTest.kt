package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.PerformanceStats
import io.github.noxaur.dsmod.core.feature.general.Stat
import io.github.noxaur.dsmod.core.feature.general.StatsLayout
import io.github.noxaur.dsmod.core.feature.general.StatsLook
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The real server answers the mod's ping request and sends its clock, and the HUD shows the frame rate, the round
 * trip and the server's ticks per second. Slowed with `/tick rate`, the server's rate falls and turns to a warning.
 * A change of world starts the count again. Read the screenshots: the row at full speed, the column with TPS in the bad
 * color, and the column on nothing in the new world.
 */
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

                // A hop starts the count again, and the new world's rate shows once two seconds of it have passed.
                context.runOnClient<Nothing> { performanceStats.look.value = StatsLook.CLEAR }
                world.server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0")
                context.waitFor { shown().lastOrNull()?.plain == "TPS: -" }
                context.waitFor { RATE.matches(shown().lastOrNull()?.plain.orEmpty()) }
                context.waitForScreen(null)
                context.takeScreenshot("performance-stats-clear")

                // Vanilla logs every answer to a ping request, so an empty log means none was asked.
                context.runOnClient<Nothing> {
                    performanceStats.values.value = listOf(Stat.FPS, Stat.TPS)
                    it.debugOverlay.pingLogger.reset()
                }
                // Long enough for one more request, which the mod asks once a second.
                context.waitTicks(30)
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

        // A local server answers within a few milliseconds and keeps its 20 ticks a second.
        val FULL_SPEED = Regex("""FPS: \d+ {3}Ping: \d+ ms {3}TPS: (19\.\d|20\.0)""")
        val RATE = Regex("""TPS: \d+\.\d""")
    }
}
