package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.inventory.protection.armorswapguard.ArmorSwapGuard
import io.github.noxaur.dragen.edge.input.InputApproved
import io.github.noxaur.dragen.edge.input.Refusals
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult

/** @keep A right click on a block and the use of the held item reach the server as two messages, and only the second swaps armor. */
object ArmorSwapGuards {
    private lateinit var guard: ArmorSwapGuard

    fun bind(guard: ArmorSwapGuard) {
        this.guard = guard
    }

    @InputApproved(by = "noxaur", reaches = ["Refusals.useItem"])
    fun register() {
        Refusals.useItem().register { player, level, hand ->
            val swaps = level.isClientSide && guard.feature.runs && !player.isShiftKeyDown &&
                player.getItemInHand(hand).get(DataComponents.EQUIPPABLE)?.swappable == true
            // @keep Fabric sends nothing to the server for a failed use.
            if (swaps) InteractionResult.FAIL else InteractionResult.PASS
        }
    }
}
