package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.Slot

private const val HOTBAR_FIRST = 36
private const val HOTBAR_SLOTS = 9

/**
 * Marks on the slots of the open container. They go away when the container closes. With no menu open the
 * container is the player's own inventory, and the marks on its last nine slots are drawn on the hotbar.
 */
object Slots {
    private class Shown(val color: Int, val border: Boolean, val label: Component?)

    private val marks = HashMap<String, Map<Int, Shown>>()

    /** Marks slots by their index in the open container's menu, replacing what [id] marked before. */
    fun mark(id: String, slots: Map<Int, SlotMark>) {
        marks[id] = slots.mapValues { (_, mark) ->
            Shown(mark.role.argb(if (mark.border) 255 else 110), mark.border, mark.label?.toComponent())
        }
    }

    fun clear(id: String) {
        marks.remove(id)
    }

    fun register(settings: DsModConfig.Inventory) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("dsmod", "hotbar")) { graphics, _ ->
            if (marks.isEmpty() || !settings.hotbarOutlines) return@attachElementAfter
            val client = Minecraft.getInstance()
            val player = client.player ?: return@attachElementAfter
            // A mark's slot is an index in the open menu, which is the inventory's only while no other menu is open.
            if (player.containerMenu !== player.inventoryMenu || player.isSpectator) return@attachElementAfter
            DsModClient.game.metrics.time("slots.draw") {
                val left = graphics.guiWidth() / 2 - 88
                val top = graphics.guiHeight() - 19
                for (slots in marks.values) {
                    for (i in 0 until HOTBAR_SLOTS) {
                        val shown = slots[HOTBAR_FIRST + i] ?: continue
                        if (shown.border) Kit.slotBorder(graphics, left + i * 20, top, shown.color) else Kit.slotFill(graphics, left + i * 20, top, shown.color)
                    }
                }
            }
        }
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            val font = Minecraft.getInstance().font
            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { slot, shown ->
                    if (shown.border) Kit.slotBorder(graphics, slot.x, slot.y, shown.color) else Kit.slotFill(graphics, slot.x, slot.y, shown.color)
                }
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { slot, shown -> shown.label?.let { graphics.text(font, it, slot.x, slot.y, -1, true) } }
            }
            ScreenEvents.remove(screen).register { marks.clear() }
        }
    }

    private inline fun each(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, draw: (Slot, Shown) -> Unit) {
        if (marks.isEmpty()) return
        screen as ContainerScreenAccessor
        DsModClient.game.metrics.time("slots.draw") {
            graphics.pose().pushMatrix()
            graphics.pose().translate(screen.leftPos.toFloat(), screen.topPos.toFloat())
            for (slots in marks.values) {
                for ((index, shown) in slots) {
                    val slot = screen.menu.slots.getOrNull(index)
                    if (slot != null) draw(slot, shown)
                }
            }
            graphics.pose().popMatrix()
        }
    }
}
