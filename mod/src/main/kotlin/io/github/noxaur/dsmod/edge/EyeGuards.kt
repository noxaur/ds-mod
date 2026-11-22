package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.end.EyeGuard
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult

private const val GUARD_MILLIS = 900L

/** @keep In a capture the frame's new state reached the client 130 ms after the click, so a double click cannot be told from
 * the block. The game repeats a held click five times a second, so each dropped click guards the block again. */
object EyeGuards {
    private lateinit var guard: EyeGuard

    fun bind(guard: EyeGuard) {
        this.guard = guard
    }

    private var guarded: BlockPos? = null
    private var until = 0L

    @InputApproved(by = "noxaur", reaches = ["Refusals.useBlock"])
    fun register() {
        Refusals.useBlock().register { player, level, hand, hit ->
            if (!level.isClientSide || !guard.feature.runs) return@register InteractionResult.PASS
            val now = System.currentTimeMillis()
            val again = now < until && hit.blockPos == guarded
            // @keep The name carries the server's color code in front, so match its end.
            val held = player.getItemInHand(hand).hoverName.string
            val eye = hand == InteractionHand.MAIN_HAND && (held.endsWith("Summoning Eye") || held.endsWith("Golden Eye"))
            // @keep The server clears the hand before the held button's repeats stop, and the next repeat would take the eye.
            if (again) until = now + GUARD_MILLIS
            else if (eye) {
                guarded = hit.blockPos.immutable()
                until = now + GUARD_MILLIS
            }
            // @keep Fabric sends nothing to the server for a failed use.
            if (again) InteractionResult.FAIL else InteractionResult.PASS
        }
    }
}
