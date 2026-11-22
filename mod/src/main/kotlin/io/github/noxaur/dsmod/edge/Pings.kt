package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.general.PerformanceStats
import io.github.noxaur.dsmod.core.feature.general.Stat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.util.Util

/** @keep dragsim.co writes 0 for every player's ping in the tab list, so once a second this sends the request the F3 network
 * chart sends, and `Packets.pong` reads the answer. The chart sends one each tick itself while it is open, so this one
 * waits. */
object Pings {
    private const val EVERY_MILLIS = 1_000L
    private var sent = 0L
    private lateinit var stats: PerformanceStats

    fun bind(stats: PerformanceStats) {
        this.stats = stats
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val connection = client.connection ?: return@register
            val now = Util.getMillis()
            if (now - sent < EVERY_MILLIS || client.debugOverlay.showNetworkCharts()) return@register
            if (!stats.feature.runs || Stat.PING !in stats.values.value || !Hud.visible) return@register
            sent = now
            connection.send(ServerboundPingRequestPacket(now))
        }
    }
}
