package io.github.noxaur.dsmod.edge.input

import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket

@InputApproved(hash = "32a2b6490e6c")
object PingRequests {
    fun send(connection: ClientPacketListener, now: Long) {
        connection.send(ServerboundPingRequestPacket(now))
    }
}
