package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.feature.inventory.HotdButton
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.KitSlotButton
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object HotdShortcut {
    private const val MENU = "Dragon Sim Menu"
    const val SLOT = 10
    private const val ROWS = 6
    private const val NAME = "Open Heart of the Dragon"

    private val shown get() = DsModClient.runs<BetterUi>() && DsModClient.runs<HotdButton>()

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is ContainerScreen || screen.menu.rowCount != ROWS || screen.title.string != MENU) return@register
            val slot = screen.menu.slots[SLOT]
            screen as ContainerScreenAccessor
            val frame = { if (MenuSkin.skins(screen)) MenuSkin.glass(screen) else null }
            val button = KitSlotButton(screen.leftPos + slot.x, screen.topPos + slot.y, NAME, ItemStack(Items.DRAGON_EGG), frame) { MenuBack.send("hotd") }
            button.setTooltip(Tooltip.create(text { body(NAME); muted("\nSends /hotd.") }.toComponent()))
            button.visible = false
            Screens.getWidgets(screen).add(button)
            ScreenEvents.afterTick(screen).register {
                button.visible = shown && MenuBack.filler(slot.item) && screen.menu.carried.isEmpty
                Slots.cover(SLOT, button.visible)
            }
        }
    }
}
