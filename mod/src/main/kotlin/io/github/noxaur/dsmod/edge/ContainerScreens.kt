package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.Slot

val AbstractContainerScreen<*>.containerLeft: Int get() = (this as ContainerScreenAccessor).leftPos
val AbstractContainerScreen<*>.containerTop: Int get() = (this as ContainerScreenAccessor).topPos
val AbstractContainerScreen<*>.containerWidth: Int get() = (this as ContainerScreenAccessor).imageWidth
val AbstractContainerScreen<*>.containerHeight: Int get() = (this as ContainerScreenAccessor).imageHeight
val AbstractContainerScreen<*>.containerBottom: Int get() = containerTop + containerHeight

val AbstractContainerScreen<*>.slotUnderMouse: Slot? get() = (this as ContainerScreenAccessor).hoveredSlot

fun AbstractContainerScreen<*>.screenX(slot: Slot) = containerLeft + slot.x
fun AbstractContainerScreen<*>.screenY(slot: Slot) = containerTop + slot.y

inline fun AbstractContainerScreen<*>.drawInContainer(graphics: GuiGraphicsExtractor, drawAtSlotCoordinates: () -> Unit) {
    val pose = graphics.pose()
    pose.pushMatrix()
    pose.translate(containerLeft.toFloat(), containerTop.toFloat())
    drawAtSlotCoordinates()
    pose.popMatrix()
}

val AbstractContainerScreen<*>.creative get() = this is CreativeModeInventoryScreen

val AbstractContainerScreen<*>.openedByServer get() = this !is InventoryScreen && !creative
