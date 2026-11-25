package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.hud.performancestats.PerformanceStats
import io.github.noxaur.dragen.core.feature.hud.performancestats.Stat
import io.github.noxaur.dragen.edge.input.InputApproved
import io.github.noxaur.dragen.edge.input.PingRequests
import net.minecraft.util.Util

/** @keep dragsim.co writes 0 for every player's ping in the tab list, so this sends the request the F3 network chart sends, and `Packets.pong` reads the answer.
 * The chart sends one each tick itself while it is open, so this one waits. */
object Pings {
    private const val EVERY_MILLIS = 1_000L
    private var sent = 0L
    private lateinit var stats: PerformanceStats

    fun bind(stats: PerformanceStats) {
        this.stats = stats
    }

    @InputApproved(by = "noxaur", reaches = ["PingRequests.send"])
    fun register() {
        Ticks.end { client ->
            val connection = client.connection ?: return@end
            val now = Util.getMillis()
            if (now - sent < EVERY_MILLIS || client.debugOverlay.showNetworkCharts()) return@end
            if (!stats.feature.runs || Stat.PING !in stats.values.value || !Hud.visible) return@end
            sent = now
            PingRequests.send(connection, now)
        }
    }
}
