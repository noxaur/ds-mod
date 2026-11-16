package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.inventory.RarityMarks
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import java.util.BitSet
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
private const val HOTBAR_PITCH = 20
private const val FILL_ALPHA = 110

/**
 * Marks on the slots of the open container. They go away when the container closes. With no menu open the
 * container is the player's own inventory, and the marks on its last nine slots are drawn on the hotbar.
 * A hidden or covered slot draws nothing here: `ContainerScreenMixin` asks [hides] and skips its item, highlight and
 * tooltip.
 *
 * A mark is drawn in two parts: its back behind the item, and for a rarity mark with a gem, the gem over it.
 * A hidden slot draws nothing here: `ContainerScreenMixin` asks [hides] and skips its item, highlight and tooltip.
 */
object Slots {
    /** [rarity] is the mark's role when the player's rarity style draws it, or null for a plain fill in [color]. */
    private class Shown(val color: Int, val rarity: Role?, val hide: Boolean, val label: Component?)

    private val marks = HashMap<String, Map<Int, Shown>>()
    private val hidden = BitSet()
    private var covered = -1
    private lateinit var settings: DsModConfig.Inventory

    /** Marks slots by their index in the open container's menu, replacing what [id] marked before. */
    fun mark(id: String, slots: Map<Int, SlotMark>) {
        marks[id] = slots.mapValues { (_, mark) ->
            Shown(mark.role.argb(FILL_ALPHA), mark.role.takeIf { mark.rarity }, mark.hide, mark.label?.toComponent())
        }
        rehide()
    }

    fun clear(id: String) {
        marks.remove(id)
        rehide()
    }

    /** Whether the slot at [index] in the open menu is hidden, asked for each slot each frame. */
    fun hides(index: Int) = hidden[index] || index == covered

    /** Hides the slot at [index] under a control the mod draws over it, or none at -1, until the menu closes. */
    fun cover(index: Int) {
        covered = index
    }

    private fun rehide() {
        hidden.clear()
        for (slots in marks.values) for ((index, shown) in slots) if (shown.hide) hidden.set(index)
    }

    /** The rarity that marks slot [index] of the open menu, if one does. */
    fun rarity(index: Int): Role? = marks.values.firstNotNullOfOrNull { it[index]?.rarity }

    fun register(settings: DsModConfig.Inventory) {
        this.settings = settings
        // The backs go under the items from `HudHotbarMixin`. This draws the gems, after vanilla drew the items.
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("dsmod", "hotbar")) { graphics, _ ->
            if (!onHotbar() || !SlotArts.style(settings, Surface.HOTBAR).gem) return@attachElementAfter
            DsModClient.game.metrics.time("slots.draw") {
                val left = graphics.guiWidth() / 2 - 88
                val top = graphics.guiHeight() - 19
                for (i in 0 until HOTBAR_SLOTS) drawFront(graphics, Surface.HOTBAR, HOTBAR_FIRST + i, left + i * HOTBAR_PITCH, top)
            }
        }
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            SlotArts.checkPacks()
            val font = Minecraft.getInstance().font
            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { slot, shown -> back(graphics, Surface.CONTAINER, shown, slot.x, slot.y) }
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { slot, shown ->
                    front(graphics, Surface.CONTAINER, shown, slot.x, slot.y)
                    shown.label?.let { graphics.text(font, it, slot.x, slot.y, -1, true) }
                }
            }
            ScreenEvents.remove(screen).register {
                marks.clear()
                hidden.clear()
                covered = -1
            }
        }
    }

    /** Called by `HudHotbarMixin` as vanilla is about to draw the hotbar item at [x], [y]. */
    @JvmStatic
    fun hotbarSlot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!onHotbar()) return
        val from = x - (graphics.guiWidth() / 2 - 88)
        val i = from / HOTBAR_PITCH
        // The offhand slot comes through here too, left of the first slot.
        if (from < 0 || from % HOTBAR_PITCH != 0 || i >= HOTBAR_SLOTS || y != graphics.guiHeight() - 19) return
        drawBack(graphics, Surface.HOTBAR, HOTBAR_FIRST + i, x, y)
    }

    /** Draws what goes behind the item in slot [index] of the open menu, whose 16x16 starts at [x], [y]. */
    fun drawBack(graphics: GuiGraphicsExtractor, surface: Surface, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.takeUnless { it.hide }?.let { back(graphics, surface, it, x, y) }
    }

    /** Draws what goes over the item in slot [index]: the gem of a rarity mark, when the style has one. */
    fun drawFront(graphics: GuiGraphicsExtractor, surface: Surface, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.takeUnless { it.hide }?.let { front(graphics, surface, it, x, y) }
    }

    /**
     * Whether a slot the mod draws itself, such as the profile viewer's, marks [rarity] as Rarity Marks marks a menu's,
     * by the same switches.
     */
    fun marks(rarity: Role?) = rarity != null && DsModClient.runs<RarityMarks>() && (rarity != Role.COMMON || settings.markCommon)

    /** Draws [rarity]'s mark behind an item at [x], [y] that the mod draws itself, in the player's style for menus. */
    fun rarityBack(graphics: GuiGraphicsExtractor, rarity: Role, x: Int, y: Int) =
        Kit.slotArt(graphics, x, y, SlotArts.atlas(settings, Surface.CONTAINER), rarity.ordinal)

    /** Draws [rarity]'s gem over that item, when the player's style has one. */
    fun rarityFront(graphics: GuiGraphicsExtractor, rarity: Role, x: Int, y: Int) {
        if (SlotArts.style(settings, Surface.CONTAINER).gem) Kit.slotGem(graphics, x, y, SlotArts.atlas(settings, Surface.CONTAINER), rarity.ordinal)
    }

    private fun back(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, x: Int, y: Int) {
        val rarity = shown.rarity
        if (rarity == null) Kit.slotFill(graphics, x, y, shown.color) else Kit.slotArt(graphics, x, y, SlotArts.atlas(settings, surface), rarity.ordinal)
    }

    private fun front(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, x: Int, y: Int) {
        val rarity = shown.rarity ?: return
        if (SlotArts.style(settings, surface).gem) Kit.slotGem(graphics, x, y, SlotArts.atlas(settings, surface), rarity.ordinal)
    }

    private fun onHotbar(): Boolean {
        if (marks.isEmpty() || !settings.hotbarMarks) return false
        val player = Minecraft.getInstance().player ?: return false
        // A mark's slot is an index in the open menu, which is the inventory's only while no other menu is open.
        return player.containerMenu === player.inventoryMenu && !player.isSpectator
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
                    if (slot != null && !shown.hide) draw(slot, shown)
                }
            }
            graphics.pose().popMatrix()
        }
    }
}
