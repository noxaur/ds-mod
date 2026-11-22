package io.github.noxaur.dsmod.edge.input

import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

@InputApproved(hash = "ef167c6fdbb0")
object ContainerClicks {
    fun click(menu: AbstractContainerMenu, slot: Int, button: Int, input: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        client.gameMode?.handleContainerInput(menu.containerId, slot, button, input, player)
    }
}
