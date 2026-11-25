package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.concurrent.CopyOnWriteArrayList

fun watchChat(): Pair<List<String>, List<String>> {
    val kept = CopyOnWriteArrayList<String>()
    val hidden = CopyOnWriteArrayList<String>()
    ClientReceiveMessageEvents.GAME.register { message, _ -> kept += message.string }
    ClientReceiveMessageEvents.GAME_CANCELED.register { message, _ -> hidden += message.string }
    return kept to hidden
}
