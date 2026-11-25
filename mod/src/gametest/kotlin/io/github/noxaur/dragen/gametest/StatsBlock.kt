package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.hud.performancestats.PerformanceStats
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.nio.file.Path

object StatsBlock {
    const val ID = "performance-stats"

    // @keep The middle of the screen is clear of the F3 debug text, which draws over the HUD's top left corner.
    private const val MIDDLE = 0.5f

    fun with(context: ClientGameTestContext, test: () -> Unit) {
        val stats = DragenClient.feature<PerformanceStats>()
        val positions = DragenClient.settings.hud.positions
        context.worldBuilder().create().use {
            context.runOnClient<Nothing> {
                positions.value += ID to DragenConfig.Position(MIDDLE, MIDDLE)
                stats.feature.enabled = true
            }
            try {
                context.waitFor { Hud.find(ID) != null }
                test()
            } finally {
                context.runOnClient<Nothing> {
                    stats.feature.enabled = false
                    positions.value -= ID
                }
            }
        }
    }

    // @keep Two pixels in sits on the plate's fill, clear of the plate's edge and of the values' changing digits.
    fun plateColor(context: ClientGameTestContext, shot: Path): Int {
        val block = context.placed(ID)
        return context.guiPixel(shot, block.x + 2 * block.scale, block.y + 2 * block.scale)
    }
}
