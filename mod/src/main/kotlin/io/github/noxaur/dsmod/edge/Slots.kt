package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.container.SlotCooldown
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
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
import kotlin.math.ceil

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
    /**
     * [rarity] is the mark's role when the player's rarity style draws it, or null for a plain fill in [color], and a
     * [cooldown] draws neither.
     */
    private class Shown(val color: Int, val rarity: Role?, val hide: Boolean, val label: Component?, val cooldown: SlotCooldown?)

    private val marks = HashMap<String, Map<Int, Shown>>()
    private val hidden = BitSet()

    /** Whether any mark is a cooldown, which the hotbar shows whatever the player's switch for marks there says. */
    private var cooldowns = false

    /** The seconds a cooldown shows, built once each. */
    private val counts = HashMap<String, Component>()
    private val covered = BitSet()
    private val settings get() = DsModClient.feature<ItemRarity>()

    /** Marks slots by their index in the open container's menu, replacing what [id] marked before. */
    fun mark(id: String, slots: Map<Int, SlotMark>) {
        marks[id] = slots.mapValues { (_, mark) ->
            Shown(mark.role.argb(FILL_ALPHA), mark.role.takeIf { mark.rarity }, mark.hide, mark.label?.toComponent(), mark.cooldown)
        }
        recount()
    }

    fun clear(id: String) {
        marks.remove(id)
        recount()
    }

    /** Whether the slot at [index] in the open menu is hidden, asked for each slot each frame. */
    fun hides(index: Int) = hidden[index] || covered[index]

    fun cover(index: Int, on: Boolean) {
        covered[index] = on
    }

    private fun recount() {
        cooldowns = marks.values.any { slots -> slots.values.any { it.cooldown != null } }
        hidden.clear()
        for (slots in marks.values) for ((index, shown) in slots) if (shown.hide) hidden.set(index)
    }

    /** The rarity that marks slot [index] of the open menu, if one does. */
    fun rarity(index: Int): Role? = marks.values.firstNotNullOfOrNull { it[index]?.rarity }

    fun register() {
        // The backs go under the items from `HudHotbarMixin`. This draws the gems and the cooldowns, after vanilla drew the items.
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("dsmod", "hotbar")) { graphics, _ ->
            if (!onHotbar() || !cooldowns && !(settings.hotbarMarks.value && SlotArts.style(settings, Surface.HOTBAR).gem)) return@attachElementAfter
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
                    shown.cooldown?.let { cooldown(graphics, it, slot.x, slot.y) }
                }
            }
            ScreenEvents.remove(screen).register {
                marks.clear()
                cooldowns = false
                hidden.clear()
                covered.clear()
            }
        }
    }

    /** Called by `HudHotbarMixin` as vanilla is about to draw the hotbar item at [x], [y]. */
    @JvmStatic
    fun hotbarSlot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!onHotbar() || !settings.hotbarMarks.value) return
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

    /**
     * Draws what goes over the item in slot [index]: the gem of a rarity mark, when the style has one, and a cooldown.
     * On the hotbar the gem follows the player's switch for marks there, and a cooldown its feature's own.
     */
    fun drawFront(graphics: GuiGraphicsExtractor, surface: Surface, index: Int, x: Int, y: Int) {
        for (slots in marks.values) {
            val shown = slots[index]?.takeUnless { it.hide } ?: continue
            if (surface != Surface.HOTBAR || settings.hotbarMarks.value) front(graphics, surface, shown, x, y)
            shown.cooldown?.let { cooldown(graphics, it, x, y) }
        }
    }

    /** The wash of a [cooldown] over the item at [x], [y], and its seconds where the stack count goes, as of this frame. */
    private fun cooldown(graphics: GuiGraphicsExtractor, cooldown: SlotCooldown, x: Int, y: Int) {
        val now = System.currentTimeMillis()
        val left = cooldown.end - now
        if (left <= 0) return
        Kit.cooldown(graphics, x, y, left.toFloat() / (cooldown.end - cooldown.start))
        val seconds = ceil(left / 1000.0).toLong()
        val label = if (seconds < 60) "$seconds" else "${seconds / 60}m"
        val text = counts.getOrPut(label) { text { value(label) }.toComponent() }
        val font = Minecraft.getInstance().font
        // Over the item, where the game writes a stack's count.
        graphics.text(font, text, x + 17 - font.width(text), y + 9, -1, true)
    }

    /**
     * Whether a slot the mod draws itself, such as the profile viewer's, marks [rarity] as Item Rarity marks a menu's,
     * by the same switches.
     */
    fun marks(rarity: Role?) = rarity != null && DsModClient.runs<ItemRarity>() && (rarity != Role.COMMON || settings.markCommon.value)

    /** Draws [rarity]'s mark behind an item at [x], [y] that the mod draws itself, in the player's style for menus. */
    fun rarityBack(graphics: GuiGraphicsExtractor, rarity: Role, x: Int, y: Int) {
        art(graphics, Surface.CONTAINER, rarity, x, y)
    }

    /** Draws [rarity]'s gem over that item, when the player's style has one. */
    fun rarityFront(graphics: GuiGraphicsExtractor, rarity: Role, x: Int, y: Int) {
        gem(graphics, Surface.CONTAINER, rarity, x, y)
    }

    private fun back(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, x: Int, y: Int) {
        if (shown.cooldown != null) return
        val rarity = shown.rarity
        if (rarity == null) Kit.slotFill(graphics, x, y, shown.color)
        else art(graphics, surface, rarity, x, y)
    }

    private fun front(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, x: Int, y: Int) {
        gem(graphics, surface, shown.rarity ?: return, x, y)
    }

    private fun art(graphics: GuiGraphicsExtractor, surface: Surface, rarity: Role, x: Int, y: Int) {
        SlotArts.atlas(settings, surface)?.let { Kit.slotArt(graphics, x, y, it, rarity.ordinal) }
    }

    private fun gem(graphics: GuiGraphicsExtractor, surface: Surface, rarity: Role, x: Int, y: Int) {
        if (!SlotArts.style(settings, surface).gem) return
        SlotArts.atlas(settings, surface)?.let { Kit.slotGem(graphics, x, y, it, rarity.ordinal) }
    }

    private fun onHotbar(): Boolean {
        if (marks.isEmpty() || !settings.hotbarMarks.value && !cooldowns) return false
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
