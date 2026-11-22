package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.WardrobeKeys
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.util.CommonColors
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW

object Wardrobes {
    private const val GAP = 4

    private val feature get() = DsModClient.feature<WardrobeKeys>()
    private val on get() = DsModClient.runs<WardrobeKeys>()

    private val swallowed = HashSet<Int>()

    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            ScreenEvents.afterTick(screen).register {
                swallowed.removeIf { !Keys.held(client, it) }
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
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                if (screen.focused is EditBox || Keys.isMouse(event.key())) return@register true
                !press(client, screen, event.key(), Keys.action == GLFW.GLFW_PRESS)
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                val button = event.button()
                val taken = press(client, screen, button, fresh = true)
                if (taken) swallowed += button
                !taken
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event -> !swallowed.remove(event.button()) }
        }
    }

    private fun wardrobe(screen: ContainerScreen) = feature.open(screen.menu.containerId)

    private fun press(client: Minecraft, screen: ContainerScreen, bound: Int, fresh: Boolean): Boolean {
        if (!on || !wardrobe(screen)) return false
        val index = feature.index(bound) ?: return false
        if (fresh) feature.press(index, System.currentTimeMillis())?.let { click(client, screen, it) }
        return true
    }

    private fun click(client: Minecraft, screen: ContainerScreen, slot: Int) {
        val menu = screen.menu
        if (!wardrobe(screen) || slot !in menu.slots.indices) return
        val mode = client.gameMode ?: return
        val player = client.player ?: return
        mode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
    }
}
