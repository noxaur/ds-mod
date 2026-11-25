package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.performancestats.PerformanceStats
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.nio.file.Path

object StatsBlock {
    const val ID = "performance-stats"

    fun with(context: ClientGameTestContext, test: () -> Unit) {
        val stats = DsModClient.feature<PerformanceStats>()
        context.worldBuilder().create().use {
            context.runOnClient<Nothing> { stats.feature.enabled = true }
            try {
                context.waitFor { Hud.find(ID) != null }
                test()
            } finally {
                context.runOnClient<Nothing> { stats.feature.enabled = false }
            }
        }
    }

    // @keep Two pixels in sits on the plate's fill, clear of the plate's edge and of the values' changing digits.
    fun plateColor(context: ClientGameTestContext, shot: Path): Int {
        val block = context.placed(ID)
        return context.guiPixel(shot, block.x + 2 * block.scale, block.y + 2 * block.scale)
    }
}
