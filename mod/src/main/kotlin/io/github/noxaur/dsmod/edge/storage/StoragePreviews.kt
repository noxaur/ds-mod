package io.github.noxaur.dsmod.edge.storage

import io.github.noxaur.dsmod.core.feature.menus.helpers.storageoverview.StorageMode
import io.github.noxaur.dsmod.core.feature.menus.helpers.storageoverview.StorageOverview
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.overview.StoragePages
import io.github.noxaur.dsmod.core.player.Seen
import io.github.noxaur.dsmod.core.ui.StorageLayout
import io.github.noxaur.dsmod.edge.slotUnderMouse
import io.github.noxaur.dsmod.edge.toText
import io.github.noxaur.dsmod.edge.ui.Kit
import java.util.Optional
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack

object StoragePreviews {
    private lateinit var storage: StorageOverview

    private var named: ItemStack? = null
    private var namedPage: String? = null

    fun bind(storage: StorageOverview) {
        this.storage = storage
    }

    fun register() {
        ClientTooltipComponentCallback.EVENT.register { data -> (data as? PageTooltip)?.let(::ClientPageTooltip) }
    }

    fun tooltip(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        if (screen !is ContainerScreen || !storage.feature.runs || storage.mode.value != StorageMode.PREVIEW) return false
        if (screen.title.string != StoragePages.ROOT || !screen.menu.carried.isEmpty) return false
        val slot = screen.slotUnderMouse ?: return false
        val stack = slot.item
        if (stack.isEmpty || slot.index >= screen.menu.rowCount * ContainerState.ROW_SLOTS) return false
        val client = Minecraft.getInstance()
        if (client.hasShiftDown()) return false
        if (stack !== named) {
            named = stack
            namedPage = StoragePages.pageOf(stack.hoverName.toText().plain)
        }
        val id = namedPage ?: return false
        val page = storage.pages.page(id) ?: return false
        graphics.setTooltipForNextFrame(client.font, listOf(stack.styledHoverName), Optional.of(PageTooltip(id, page)), mouseX, mouseY, stack.get(DataComponents.TOOLTIP_STYLE))
        return true
    }

    class PageTooltip(val id: String, val page: Seen) : TooltipComponent

    private class ClientPageTooltip(private val tip: PageTooltip) : ClientTooltipComponent {
        private val rows = (tip.page.items.size + StorageLayout.ROW - 1) / StorageLayout.ROW

        override fun getHeight(font: Font) = rows * StorageLayout.CELL + 2 * PAD

        override fun getWidth(font: Font) = StorageLayout.GRID

        override fun extractImage(font: Font, x: Int, y: Int, w: Int, h: Int, graphics: GuiGraphicsExtractor) {
            val stacks = PageStacks.of(tip.id, tip.page)
            for (i in 0 until rows * StorageLayout.ROW) {
                val cx = x + i % StorageLayout.ROW * StorageLayout.CELL
                val cy = y + PAD + i / StorageLayout.ROW * StorageLayout.CELL
                Kit.slot(graphics, cx, cy)
                PageStacks.draw(graphics, font, stacks, i, cx + 1, cy + 1)
            }
        }

        private companion object {
            const val PAD = 2
        }
    }
}
