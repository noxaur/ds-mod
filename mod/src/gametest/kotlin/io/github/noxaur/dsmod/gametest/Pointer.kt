package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item

fun ClientGameTestContext.point(x: Number, y: Number, settle: Int = 1) {
    val (sx, sy) = computeOnClient<Pair<Double, Double>, Nothing> { it.window.let { w -> w.screenWidth.toDouble() / w.guiScaledWidth to w.screenHeight.toDouble() / w.guiScaledHeight } }
    input.setCursorPos(x.toDouble() * sx, y.toDouble() * sy)
    waitTicks(settle)
}

fun ClientGameTestContext.click(x: Number, y: Number, button: Int = 0, settle: Int = 1) {
    point(x, y, settle)
    input.pressMouse(button)
    waitTicks(settle)
}

fun ClientGameTestContext.click(widget: AbstractWidget, button: Int = 0, settle: Int = 1) =
    click(widget.x + widget.width / 2.0, widget.y + widget.height / 2.0, button, settle)

fun ClientGameTestContext.slotCorner(index: Int) = computeOnClient<Pair<Int, Int>, Nothing> { client ->
    val screen = client.gui.screen() as AbstractContainerScreen<*>
    val slot = screen.menu.slots[index]
    val at = screen as ContainerScreenAccessor
    at.leftPos + slot.x to at.topPos + slot.y
}

fun ClientGameTestContext.menuCorner() = computeOnClient<Pair<Int, Int>, Nothing> { client ->
    val screen = client.gui.screen() as ContainerScreenAccessor
    screen.leftPos to screen.topPos
}

fun ClientGameTestContext.hoverSlot(index: Int, settle: Int = 3) {
    val (x, y) = slotCorner(index)
    point(x + 8, y + 8, settle)
}

fun ClientGameTestContext.ownSlot(slot: Int) = computeOnClient<Int, Nothing> { client ->
    (client.gui.screen() as AbstractContainerScreen<*>).menu.slots.first { it.container is Inventory && it.containerSlot == slot }.index
}

fun ClientGameTestContext.hoverOwnSlot(slot: Int, settle: Int = 3) = hoverSlot(ownSlot(slot), settle)

fun ClientGameTestContext.openInventory() = setScreen { InventoryScreen(checkNotNull(Minecraft.getInstance().player)) }

fun ClientGameTestContext.carried() = computeOnClient<Int, Nothing> { checkNotNull(it.player).containerMenu.carried.count }

fun ClientGameTestContext.ownItem(slot: Int) = computeOnClient<Pair<Item, Int>, Nothing> { client ->
    checkNotNull(client.player).inventory.getItem(slot).let { it.item to it.count }
}
