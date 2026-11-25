package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.menus.betterui.skyblockmenuclick.SkyBlockMenuClick
import io.github.noxaur.dragen.edge.input.InputApproved
import io.github.noxaur.dragen.edge.input.PlayerCommands
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

object SkyBlockMenuClicks {
    private const val SLOT = 8
    private const val COMMAND = "sbmenu"

    private lateinit var click: SkyBlockMenuClick

    fun bind(click: SkyBlockMenuClick) {
        this.click = click
    }

    @InputApproved(reaches = ["PlayerCommands.send"])
    fun opens(slot: Slot?, input: ContainerInput): Boolean {
        if (input != ContainerInput.PICKUP || slot == null || slot.container !is Inventory || slot.containerSlot != SLOT) return false
        if (!click.feature.runs) return false
        if (slot.item.itemId != SkyBlockMenuClick.ITEM_ID) return false
        PlayerCommands.send(COMMAND)
        return true
    }
}
