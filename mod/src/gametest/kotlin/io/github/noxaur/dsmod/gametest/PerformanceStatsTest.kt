package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Stat
import io.github.noxaur.dsmod.core.feature.general.StatsLayout
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The real server answers the mod's ping request and sends its clock, and the HUD shows the frame rate, the round
 * trip and the server's ticks per second. Slowed with `/tick rate`, the server's rate falls and turns to a warning.
 * Read the screenshots: the row at full speed, then the column with TPS in the warning color.
 */
class PerformanceStatsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val general = DsModClient.settings.general
        context.runOnClient<Nothing> { general.performanceStats = true }
        try {
            context.worldBuilder().create().use { world ->
                fun shown() = context.computeOnClient<List<Text>, Nothing> {
                    DsModClient.game.readouts.single { it.feature.id == ID }.hud?.rows?.map { it.text }.orEmpty()
                }
                fun line() = shown().joinToString(" / ") { it.plain }

                val capture = context.computeOnClient<java.nio.file.Path, Nothing> { io.github.noxaur.dsmod.debug.DsModDebug.toggleCapture(it)!! } // TEMP
                context.waitFor { FULL_SPEED.matches(line()) }
                context.takeScreenshot("performance-stats")

                context.runOnClient<Nothing> { general.performanceLayout = StatsLayout.COLUMN }
                world.server.runCommand("tick rate 5")
                context.waitFor { shown().lastOrNull()?.spans?.any { it.rgb == Role.WARNING.rgb || it.rgb == Role.BAD.rgb } == true }
                check(shown().map { it.plain.substringBefore(':') } == Stat.entries.map { it.toString() }) { "The column does not list FPS, Ping and TPS: ${line()}" }
                context.takeScreenshot("performance-stats-slow")
                world.server.runCommand("tick rate 20")
                var said: String? = null // TEMP
                context.runOnClient<Nothing> { client -> io.github.noxaur.dsmod.debug.DsModDebug.toggleCapture(client) { said = it } } // TEMP
                context.waitFor { said != null } // TEMP
                println("PERF CAPTURE $capture $said") // TEMP
            }
        } finally {
            context.runOnClient<Nothing> {
                general.performanceStats = false
                general.performanceLayout = StatsLayout.ROW
            }
        }
    }

    private companion object {
        const val ID = "performance-stats"

        // A local server answers within a few milliseconds and keeps its 20 ticks a second.
        val FULL_SPEED = Regex("""FPS: \d+ {3}Ping: \d+ ms {3}TPS: (19\.\d|20\.0)""")
    }
}
