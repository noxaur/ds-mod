package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.SkyBlockMenuClick
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

object SkyBlockMenuClicks {
    private const val SLOT = 8
    private const val ITEM_ID = "skyblock_menu"
    private const val COMMAND = "sbmenu"

    @InputApproved(reaches = ["PlayerCommands.send"])
    fun opens(slot: Slot?, input: ContainerInput): Boolean {
        if (input != ContainerInput.PICKUP || slot == null || slot.container !is Inventory || slot.containerSlot != SLOT) return false
        if (!DsModClient.runs<SkyBlockMenuClick>()) return false
        if (slot.item.itemId != ITEM_ID) return false
        PlayerCommands.send(COMMAND)
        return true
    }
}
