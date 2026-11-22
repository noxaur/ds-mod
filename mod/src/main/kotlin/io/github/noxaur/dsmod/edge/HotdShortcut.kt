package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.HotdButton
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.KitSlotButton
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object HotdShortcut {
    private const val MENU = "Dragon Sim Menu"
    const val SLOT = 10
    private const val ROWS = 6
    private const val NAME = "Open Heart of the Dragon"

    private val shown get() = DsModClient.runs<HotdButton>()

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is ContainerScreen || screen.menu.rowCount != ROWS || screen.title.string != MENU) return@register
            val frame = { if (MenuSkin.skins(screen)) MenuSkin.glass(screen) >= 0 else null }
            SlotControls.add(screen, screen.menu.slots[SLOT], { shown }) { x, y ->
                KitSlotButton(x, y, NAME, ItemStack(Items.DRAGON_EGG), frame) { PlayerCommands.send("hotd") }.apply {
                    setTooltip(Tooltip.create(text { body(NAME) }.toComponent()))
                }
            }
        }
    }
}
