package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

private const val GUARD_MILLIS = 900L

/**
 * Protect Placed Eyes. A click on an altar frame that holds the player's eye takes the eye back out, and the
 * server says nothing about it. In a capture the frame's new state reached the client 130 ms after the click,
 * so a double click cannot be told from the block. Instead, a click made holding an eye guards the block it
 * landed on, and further clicks on that block are dropped until the guard runs out. Each dropped click starts
 * the guard again, because the game repeats the click five times a second while the button is held, and the
 * first repeat after a fixed guard would take the eye. A click meant to take the eye back works after a pause.
 */
object EyeGuards {
    private var guarded: BlockPos? = null
    private var until = 0L

    fun register(settings: DsModConfig.End) {
        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            if (!level.isClientSide || !settings.protectPlacedEyes) return@register InteractionResult.PASS
            val now = System.currentTimeMillis()
            val again = now < until && hit.blockPos == guarded
            // The name carries the server's color code in front, so match its end.
            val held = player.getItemInHand(hand).hoverName.string
            // A dropped click guards the block anew, so a held button never gets one through.
            if (hand == InteractionHand.MAIN_HAND && (held.endsWith("Summoning Eye") || held.endsWith("Golden Eye"))) {
                guarded = hit.blockPos.immutable()
                until = now + GUARD_MILLIS
            }
            // Fabric sends nothing to the server for a failed use.
            if (again) InteractionResult.FAIL else InteractionResult.PASS
        }
    }
}
