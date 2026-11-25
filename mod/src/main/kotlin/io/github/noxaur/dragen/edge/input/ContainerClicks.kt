package io.github.noxaur.dragen.edge.input

import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

@InputApproved(hash = "b6256edf3429")
object ContainerClicks {
    fun click(menu: AbstractContainerMenu, slot: Int, button: Int, input: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        client.gameMode?.handleContainerInput(menu.containerId, slot, button, input, player)
    }
}
