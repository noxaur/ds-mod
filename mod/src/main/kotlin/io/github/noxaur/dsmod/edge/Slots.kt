package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotCooldown
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
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
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import kotlin.math.ceil

private const val HOTBAR_PITCH = 20
private const val FILL_ALPHA = 110

object Slots {
    private class Shown(val role: Role, val color: Int, val rarity: Role?, val hide: Boolean, val label: Component?, val cooldown: SlotCooldown?, val outline: Boolean)

    private val marks = HashMap<String, Map<Int, Shown>>()
    private val hidden = BitSet()

    private var cooldowns = false

    private val counts = HashMap<String, Component>()
    private val covered = BitSet()
    private lateinit var game: Game
    private lateinit var settings: ItemRarity

    fun bind(game: Game, itemRarity: ItemRarity) {
        this.game = game
        settings = itemRarity
    }

    fun mark(id: String, slots: Map<Int, SlotMark>) {
        marks[id] = slots.mapValues { (_, mark) ->
            Shown(mark.role, mark.role.argb(FILL_ALPHA), mark.role.takeIf { mark.rarity }, mark.hide, mark.label?.toComponent(), mark.cooldown, mark.outline)
        }
        recount()
    }

    fun clear(id: String) {
        marks.remove(id)
        recount()
    }

    fun hides(index: Int) = hidden[index] || covered[index]

    fun cover(index: Int, on: Boolean) {
        covered[index] = on
    }

    private fun recount() {
        cooldowns = marks.values.any { slots -> slots.values.any { it.cooldown != null } }
        hidden.clear()
        for (slots in marks.values) for ((index, shown) in slots) if (shown.hide) hidden.set(index)
    }

    fun register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("dsmod", "hotbar")) { graphics, _ ->
            if (!onHotbar() || !cooldowns) return@attachElementAfter
            game.metrics.time("slots.draw") {
                val left = graphics.guiWidth() / 2 - 88
                val top = graphics.guiHeight() - 19
                for (i in 0 until Inventory.SELECTION_SIZE) drawFront(graphics, InventoryMenu.USE_ROW_SLOT_START + i, left + i * HOTBAR_PITCH, top)
            }
        }
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, _ ->
            val font = Minecraft.getInstance().font
            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { index, slot, shown -> back(graphics, Surface.CONTAINER, shown, index, slot.x, slot.y) }
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ ->
                each(screen, graphics) { _, slot, shown ->
                    shown.label?.let { graphics.text(font, it, slot.x, slot.y, -1, true) }
                    shown.cooldown?.let { cooldown(graphics, it, slot.x, slot.y) }
                    if (shown.outline) Kit.slotOutline(graphics, slot.x, slot.y, if (shown.role == Role.ACTION) Kit.theme.accent else shown.role.argb())
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

    @JvmStatic
    fun hotbarSlot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!onHotbar() || !settings.hotbarMarks.value) return
        val from = x - (graphics.guiWidth() / 2 - 88)
        val i = from / HOTBAR_PITCH
        // @keep The offhand slot comes through here too, left of the first slot.
        if (from < 0 || from % HOTBAR_PITCH != 0 || i >= Inventory.SELECTION_SIZE || y != graphics.guiHeight() - 19) return
        drawBack(graphics, Surface.HOTBAR, InventoryMenu.USE_ROW_SLOT_START + i, x, y)
    }

    fun drawBack(graphics: GuiGraphicsExtractor, surface: Surface, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.takeUnless { it.hide }?.let { back(graphics, surface, it, index, x, y) }
    }

    fun drawFront(graphics: GuiGraphicsExtractor, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.takeUnless { it.hide }?.cooldown?.let { cooldown(graphics, it, x, y) }
    }

    private fun cooldown(graphics: GuiGraphicsExtractor, cooldown: SlotCooldown, x: Int, y: Int) {
        val now = System.currentTimeMillis()
        val left = cooldown.end - now
        if (left <= 0) return
        Kit.cooldown(graphics, x, y, left.toFloat() / (cooldown.end - cooldown.start))
        val seconds = ceil(left / 1000.0).toLong()
        val label = if (seconds < 60) "$seconds" else "${seconds / 60}m"
        val text = counts.getOrPut(label) { text { value(label) }.toComponent() }
        val font = Minecraft.getInstance().font
        graphics.text(font, text, x + 17 - font.width(text), y + 9, -1, true)
    }

    fun marks(rarity: Role?) = rarity != null && settings.feature.runs && (rarity != Role.COMMON || settings.markCommon.value)

    fun rarityBack(graphics: GuiGraphicsExtractor, rarity: Role, index: Int, x: Int, y: Int) {
        art(graphics, Surface.CONTAINER, rarity, index, x, y)
    }

    private fun back(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, index: Int, x: Int, y: Int) {
        if (shown.cooldown != null || shown.outline) return
        val rarity = shown.rarity
        if (rarity == null) Kit.slotFill(graphics, x, y, shown.color)
        else art(graphics, surface, rarity, index, x, y)
    }

    private fun art(graphics: GuiGraphicsExtractor, surface: Surface, rarity: Role, index: Int, x: Int, y: Int) {
        val atlas = SlotArts.atlas(settings, surface) ?: return
        Kit.slotArt(graphics, x, y, atlas, rarity.ordinal, index % SlotArt.VARIANTS, SlotArts.style(settings, surface).opacity)
    }

    private fun onHotbar(): Boolean {
        if (marks.isEmpty() || !settings.hotbarMarks.value && !cooldowns) return false
        val player = Minecraft.getInstance().player ?: return false
        return player.containerMenu === player.inventoryMenu && !player.isSpectator
    }

    private inline fun each(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, draw: (Int, Slot, Shown) -> Unit) {
        if (marks.isEmpty()) return
        game.metrics.time("slots.draw") {
            screen.drawInContainer(graphics) {
                for (slots in marks.values) {
                    for ((index, shown) in slots) {
                        val slot = screen.menu.slots.getOrNull(index)
                        if (slot != null && !shown.hide) draw(index, slot, shown)
                    }
                }
            }
        }
    }
}
