package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.ui.MenuTrail
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu

object Trail {
    private val trail = MenuTrail()
    private var menu: AbstractContainerMenu? = null
    private var back: String? = null

    fun register() {
        ClientSendMessageEvents.COMMAND.register(::sent)
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*> || screen is InventoryScreen || screen is CreativeModeInventoryScreen) return@register
            if (screen.menu !== menu) {
                menu = screen.menu
                back = trail.opened(System.currentTimeMillis(), screen.title.string)
            }
            ScreenEvents.remove(screen).register { trail.closed(System.currentTimeMillis()) }
        }
    }

    fun opener(screen: AbstractContainerScreen<*>) = if (screen.menu === menu) trail.opener else null

    fun back(screen: AbstractContainerScreen<*>) = if (screen.menu === menu) back else null

    private fun sent(command: String) = trail.sent(System.currentTimeMillis(), command)
}
