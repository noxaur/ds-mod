package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult

/**
 * Armor Swap Guard. A right click on a block and the use of the held item reach the server as two messages, and
 * only the second swaps armor, so only that one is dropped: a click on the altar still places the eye. Sneaking
 * lets it through, because some armor has an ability on sneak and right click.
 */
object ArmorSwapGuards {
    fun register(settings: DsModConfig.Inventory) {
        UseItemCallback.EVENT.register { player, level, hand ->
            val swaps = level.isClientSide && settings.armorSwapGuard && !player.isShiftKeyDown &&
                player.getItemInHand(hand).get(DataComponents.EQUIPPABLE)?.swappable == true
            // Fabric sends nothing to the server for a failed use.
            if (swaps) InteractionResult.FAIL else InteractionResult.PASS
        }
    }
}
