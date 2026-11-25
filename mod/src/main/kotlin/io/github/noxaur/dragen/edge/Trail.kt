package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.ui.MenuTrail
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu

object Trail {
    private val trail = MenuTrail()
    private var menu: AbstractContainerMenu? = null

    fun register() {
        ClientSendMessageEvents.COMMAND.register(::sent)
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, opened ->
            if (!screen.openedByServer) return@onEveryLayout
            if (opened) {
                menu = screen.menu
                trail.opened(System.currentTimeMillis(), screen.title.string)
            }
            ScreenEvents.remove(screen).register { trail.closed(System.currentTimeMillis()) }
        }
    }

    fun opener(screen: AbstractContainerScreen<*>) = if (screen.menu === menu) trail.opener else null

    private fun sent(command: String) = trail.sent(System.currentTimeMillis(), command)
}
