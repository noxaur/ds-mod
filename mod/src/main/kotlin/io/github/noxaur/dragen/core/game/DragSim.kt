package io.github.noxaur.dragen.core.game

import java.net.InetAddress

object DragSim {
    const val HOST = "dragsim.co"

    fun hosts(address: String?): Boolean {
        val host = address?.trim()?.substringBefore(':')?.trimEnd('.')?.lowercase() ?: return false
        return host == HOST || host.endsWith(".$HOST")
    }

    fun serves(typed: String?, connectedTo: InetAddress?, dragSimAt: Set<InetAddress>) = hosts(typed) || connectedTo in dragSimAt
}
