package io.github.noxaur.dragen.edge.input

import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket

@InputApproved(hash = "cd354a1b30a0")
object PingRequests {
    fun send(connection: ClientPacketListener, now: Long) {
        connection.send(ServerboundPingRequestPacket(now))
    }
}
