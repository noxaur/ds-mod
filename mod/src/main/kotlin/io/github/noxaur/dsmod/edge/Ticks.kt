package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Metrics
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object Ticks {
    fun start(listener: (Minecraft) -> Unit) =
        ClientTickEvents.START_CLIENT_TICK.register { client -> DsModClient.game.metrics.measure(Metrics.TICK) { listener(client) } }

    fun end(listener: (Minecraft) -> Unit) =
        ClientTickEvents.END_CLIENT_TICK.register { client -> DsModClient.game.metrics.measure(Metrics.TICK) { listener(client) } }
}
