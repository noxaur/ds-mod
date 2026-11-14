package io.github.noxaur.dsmod.core.game

import java.net.InetAddress

/** The server ds-mod is made for. */
object DragSim {
    const val HOST = "dragsim.co"

    /** Whether [address], as the player typed it in the server list, with or without a port, is dragsim.co or one of its subdomains. */
    fun hosts(address: String?): Boolean {
        val host = address?.trim()?.substringBefore(':')?.trimEnd('.')?.lowercase() ?: return false
        return host == HOST || host.endsWith(".$HOST")
    }

    /**
     * Whether the player is on dragsim.co: they typed its name, or the machine they reached, [connectedTo], is one
     * of [HOST]'s addresses, [dragSimAt]. The second covers a player who typed the IP or a name of their own.
     */
    fun serves(typed: String?, connectedTo: InetAddress?, dragSimAt: Set<InetAddress>) = hosts(typed) || connectedTo in dragSimAt
}
