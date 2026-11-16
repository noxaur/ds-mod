package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.general.PerformanceStats
import io.github.noxaur.dsmod.core.feature.general.Stat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.util.Util

/**
 * The one place the mod asks the server for its ping. dragsim.co writes 0 for every player's ping in the tab list, and
 * nothing the client already sends and receives gives a round trip while the player stands still: the client answers
 * keep-alives and the server's own pings, but sees only one leg of them, and a block or use acknowledgement comes only
 * when the player acts. So once a second, while Performance Stats shows Ping and the player can see the HUD, this sends
 * the request the F3 network chart sends, and `Packets.pong` reads the answer. The chart, while it is open, sends one
 * each tick itself, so this one waits.
 */
object Pings {
    private const val EVERY_MILLIS = 1_000L
    private var sent = 0L

    fun register(general: DsModConfig.General) {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val connection = client.connection ?: return@register
            val now = Util.getMillis()
            if (now - sent < EVERY_MILLIS || client.debugOverlay.showNetworkCharts()) return@register
            if (!DsModClient.runs<PerformanceStats>() || Stat.PING !in general.performanceValues || !Hud.visible) return@register
            sent = now
            connection.send(ServerboundPingRequestPacket(now))
        }
    }
}
