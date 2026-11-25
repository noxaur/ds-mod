package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.edge.mixin.ContainerScreenAccessor
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

// @keep The slot a click lands on, by the screen's own hover test, since the hovered slot is only updated when a frame is drawn
fun AbstractContainerScreen<*>.slotAt(x: Double, y: Double): Slot? = menu.slots.firstOrNull {
    it.isActive && x >= screenX(it) - 1 && x < screenX(it) + SLOT_SIZE + 1 && y >= screenY(it) - 1 && y < screenY(it) + SLOT_SIZE + 1
}

private const val SLOT_SIZE = 16

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
