package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot

/** Marks on the slots of the open container. They go away when the container closes. */
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

    fun register() {
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
