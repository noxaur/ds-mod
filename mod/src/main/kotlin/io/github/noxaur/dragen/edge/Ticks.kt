package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.game.Metrics
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object Ticks {
    private lateinit var game: Game

    fun bind(game: Game) {
        this.game = game
    }

    fun start(listener: (Minecraft) -> Unit) =
        ClientTickEvents.START_CLIENT_TICK.register { client -> game.metrics.measure(Metrics.TICK) { listener(client) } }

    fun end(listener: (Minecraft) -> Unit) =
        ClientTickEvents.END_CLIENT_TICK.register { client -> game.metrics.measure(Metrics.TICK) { listener(client) } }
}
