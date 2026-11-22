package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.WardrobeKeys
import io.github.noxaur.dsmod.edge.input.ContainerClicks
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.util.CommonColors
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object Wardrobes {
    private const val GAP = 4

    private lateinit var feature: WardrobeKeys

    fun bind(wardrobeKeys: WardrobeKeys) {
        feature = wardrobeKeys
    }

    private val on get() = feature.feature.runs

    private val taken = TakenButtons()

    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            ScreenEvents.afterTick(screen).register {
                if (on) feature.due(System.currentTimeMillis())?.let { click(client, screen, it) }
            }
            ScreenEvents.afterBackground(screen).register { _, g, _, _, _ ->
                val wait = if (on && wardrobe(screen)) feature.wait(System.currentTimeMillis()) else 0
                if (wait <= 0) return@register
                val tenths = (wait + 99) / 100
                val glass = MenuSkin.glass(screen)
                val skinned = MenuSkin.skins(screen)
                val x = screen.containerLeft + screen.titleLabelX + Kit.font.width(screen.title) + GAP
                val y = screen.containerTop + screen.titleLabelY
                g.text(Kit.font, "${tenths / 10}.${tenths % 10}s", x, y, if (skinned) MenuSkin.ink(glass) else CommonColors.DARK_GRAY, skinned && MenuSkin.inkShadow(glass))
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

    private fun wardrobe(screen: ContainerScreen) = feature.open(screen.menu.containerId)

    private fun press(client: Minecraft, screen: ContainerScreen, bound: Int, fresh: Boolean): Boolean {
        if (!on || !wardrobe(screen)) return false
        val index = feature.index(bound) ?: return false
        if (fresh) feature.press(index, System.currentTimeMillis())?.let { click(client, screen, it) }
        return true
    }

    @InputApproved(by = "noxaur", reaches = ["ContainerClicks.click"])
    private fun click(client: Minecraft, screen: ContainerScreen, slot: Int) {
        val menu = screen.menu
        if (!wardrobe(screen) || slot !in menu.slots.indices) return
        if (client.gameMode == null || client.player == null) return
        ContainerClicks.click(menu, slot, 0, ContainerInput.PICKUP)
    }
}
