package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.feature.inventory.SkyBlockMenuClick
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

object SkyBlockMenuClicks {
    private const val SLOT = 8
    private const val ITEM_ID = "skyblock_menu"
    private const val COMMAND = "sbmenu"

    fun opens(slot: Slot?, input: ContainerInput): Boolean {
        if (input != ContainerInput.PICKUP || slot == null || slot.container !is Inventory || slot.containerSlot != SLOT) return false
        if (!DsModClient.runs<BetterUi>() || !DsModClient.runs<SkyBlockMenuClick>()) return false
        val id = slot.item.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty("item_data")?.getStringOr("item_id", "")
        if (id != ITEM_ID) return false
        PlayerCommands.send(COMMAND)
        return true
    }
}
