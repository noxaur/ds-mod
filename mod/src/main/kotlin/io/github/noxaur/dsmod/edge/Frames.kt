package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.FrameRate
import net.minecraft.client.Minecraft

/** Posts the game's own count of the frames it drew, which it makes once a second, so reading it once a second loses nothing. */
object Frames {
    private const val EVERY_TICKS = 20
    private var ticks = 0

    fun tick(client: Minecraft) {
        if (++ticks % EVERY_TICKS != 0) return
        val game = DsModClient.game
        if (game.wants(FrameRate::class.java)) game.post(System.currentTimeMillis(), FrameRate(client.fps))
    }
}
