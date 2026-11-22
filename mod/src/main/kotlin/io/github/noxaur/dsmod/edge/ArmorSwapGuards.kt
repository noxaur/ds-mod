package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult

/** @keep A right click on a block and the use of the held item reach the server as two messages, and only the second swaps armor. */
object ArmorSwapGuards {
    fun register() {
        UseItemCallback.EVENT.register { player, level, hand ->
            val swaps = level.isClientSide && DsModClient.runs<ArmorSwapGuard>() && !player.isShiftKeyDown &&
                player.getItemInHand(hand).get(DataComponents.EQUIPPABLE)?.swappable == true
            // @keep Fabric sends nothing to the server for a failed use.
            if (swaps) InteractionResult.FAIL else InteractionResult.PASS
        }
    }
}
