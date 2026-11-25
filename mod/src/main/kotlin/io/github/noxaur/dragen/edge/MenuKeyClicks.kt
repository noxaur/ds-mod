package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.menus.MenuKeys
import io.github.noxaur.dragen.edge.input.ContainerClicks
import io.github.noxaur.dragen.edge.input.InputApproved
import io.github.noxaur.dragen.edge.input.Refusals
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object MenuKeyClicks {
    private var menus = emptyList<MenuKeys>()

    fun bind(vararg keys: MenuKeys) {
        menus = keys.toList()
    }

    private val taken = TakenButtons()

    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            ScreenEvents.afterTick(screen).register {
                val keys = open(screen) ?: return@register
                keys.due(System.currentTimeMillis())?.let { click(client, screen, keys, it) }
            }
            ScreenEvents.afterBackground(screen).register { _, g, _, _, _ ->
                val wait = open(screen)?.wait(System.currentTimeMillis()) ?: 0
                if (wait > 0) MenuTimers.draw(g, screen, MenuTimers.seconds(wait))
            }
            takeKeys(client, screen)
        }
    }

    @InputApproved(by = "noxaur", reaches = ["Refusals.allowKeyPress"])
    private fun takeKeys(client: Minecraft, screen: ContainerScreen) {
        Refusals.allowKeyPress(screen).register { _, event ->
            if (screen.focused is EditBox || Keys.isMouse(event.key())) return@register true
            !press(client, screen, event.key(), Keys.action == GLFW.GLFW_PRESS)
        }
        taken.register(screen) { event -> press(client, screen, event.button(), fresh = true) }
    }

    private fun open(screen: ContainerScreen) = menus.firstOrNull { it.runs && it.open(screen.menu.containerId) }

    private fun press(client: Minecraft, screen: ContainerScreen, bound: Int, fresh: Boolean): Boolean {
        val keys = open(screen) ?: return false
        val index = keys.index(bound) ?: return false
        if (fresh) keys.press(index, System.currentTimeMillis())?.let { click(client, screen, keys, it) }
        return true
    }

    @InputApproved(by = "noxaur", reaches = ["ContainerClicks.click"])
    private fun click(client: Minecraft, screen: ContainerScreen, keys: MenuKeys, slot: Int) {
        val menu = screen.menu
        if (!keys.open(menu.containerId) || slot !in menu.slots.indices) return
        if (client.gameMode == null || client.player == null) return
        ContainerClicks.click(menu, slot, 0, ContainerInput.PICKUP)
    }
}
