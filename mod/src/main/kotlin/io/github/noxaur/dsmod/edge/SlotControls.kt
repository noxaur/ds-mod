package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot

object SlotControls {
    fun add(screen: AbstractContainerScreen<*>, slot: Slot, shows: () -> Boolean, make: (x: Int, y: Int) -> AbstractWidget) {
        screen as ContainerScreenAccessor
        val control = make(screen.leftPos + slot.x, screen.topPos + slot.y)
        control.visible = false
        Screens.getWidgets(screen).add(control)
        fun place() {
            control.visible = screen.menu.carried.isEmpty && slot.item.filler && shows()
            Slots.cover(slot.index, control.visible)
        }
        ScreenEvents.afterTick(screen).register { place() }
        ScreenMouseEvents.beforeMouseClick(screen).register { _, _ -> place() }
    }
}
