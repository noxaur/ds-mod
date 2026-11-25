package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.menus.betterui.hotdbutton.HotdButton
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import io.github.noxaur.dsmod.edge.ui.KitSlotButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object HotdShortcut {
    private const val MENU = "Dragon Sim Menu"
    const val SLOT = 10
    private const val ROWS = 6
    private const val NAME = "Open Heart of the Dragon"

    private lateinit var button: HotdButton

    fun bind(button: HotdButton) {
        this.button = button
    }

    private val shown get() = button.feature.runs

    @InputApproved(reaches = ["PlayerCommands.send"])
    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            if (screen.menu.rowCount != ROWS || screen.title.string != MENU) return@onEveryLayout
            SlotControls.add(screen, screen.menu.slots[SLOT], { shown }) { x, y ->
                KitSlotButton(x, y, NAME, ItemStack(Items.DRAGON_EGG)) { PlayerCommands.send("hotd") }.apply {
                    setTooltip(Tooltip.create(text { body(NAME) }.toComponent()))
                }
            }
        }
    }
}
