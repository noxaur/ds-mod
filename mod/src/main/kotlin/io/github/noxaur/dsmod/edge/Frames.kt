package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.FrameRate
import net.minecraft.client.Minecraft

object Frames {
    private const val EVERY_TICKS = 20
    private var ticks = 0

    fun tick(client: Minecraft, game: Game) {
        if (++ticks % EVERY_TICKS != 0) return
        if (game.wants(FrameRate::class.java)) game.post(System.currentTimeMillis(), FrameRate(client.fps))
    }
}
