package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotCooldown
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Span
import io.github.noxaur.dsmod.core.text.Text
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

private const val COUNT_RIGHT = 17
private const val COUNT_TOP = 9

object Slots {
    private class Shown(
        val role: Role, val color: Int, val rarity: Role?, val label: Component?, val labelWidth: Int, val cooldown: SlotCooldown?, val outline: Boolean,
        val fill: Boolean, val dim: Boolean,
    )

    private val marks = HashMap<String, Map<Int, Shown>>()

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
        val font = Minecraft.getInstance().font
        marks[id] = slots.mapValues { (_, mark) ->
            val label = mark.label?.toComponent() ?: mark.count?.takeIf { it > 1 }?.let { Text(listOf(Span("$it", mark.role.rgb))).toComponent() }
            Shown(
                mark.role, mark.role.argb(FILL_ALPHA), mark.role.takeIf { mark.rarity }, label, label?.let(font::width) ?: 0, mark.cooldown, mark.outline,
                fill = mark.label == null && !mark.dim, dim = mark.dim,
            )
        }
        recount()
    }

    fun clear(id: String) {
        marks.remove(id)
        recount()
    }

    fun hides(index: Int) = covered[index]

    fun cover(index: Int, on: Boolean) {
        covered[index] = on
    }

    private fun recount() {
        cooldowns = marks.values.any { slots -> slots.values.any { it.cooldown != null } }
    }

    fun register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("dsmod", "hotbar")) { graphics, _ ->
            if (!onHotbar() || !cooldowns) return@attachElementAfter
            game.metrics.time("slots.draw", Metrics.FRAME) {
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
                    if (shown.dim) Kit.slotDim(graphics, slot.x, slot.y)
                    shown.label?.let { graphics.text(font, it, slot.x + COUNT_RIGHT - shown.labelWidth, slot.y + COUNT_TOP, -1, true) }
                    shown.cooldown?.let { cooldown(graphics, it, slot.x, slot.y) }
                    if (shown.outline) Kit.slotOutline(graphics, slot.x, slot.y, if (shown.role == Role.ACTION) Kit.theme.accent else shown.role.argb())
                }
            }
            ScreenEvents.remove(screen).register {
                marks.clear()
                cooldowns = false
                covered.clear()
            }
        }
    }

    @JvmStatic
    fun hotbarSlot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!onHotbar() || !settings.hotbarMarks.value) return
        val from = x - (graphics.guiWidth() / 2 - 88)
        val i = from / HOTBAR_PITCH
        if (from < 0 || from % HOTBAR_PITCH != 0 || i >= Inventory.SELECTION_SIZE || y != graphics.guiHeight() - 19) return
        drawBack(graphics, Surface.HOTBAR, InventoryMenu.USE_ROW_SLOT_START + i, x, y)
    }

    fun drawBack(graphics: GuiGraphicsExtractor, surface: Surface, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.let { back(graphics, surface, it, index, x, y) }
    }

    fun drawFront(graphics: GuiGraphicsExtractor, index: Int, x: Int, y: Int) {
        for (slots in marks.values) slots[index]?.cooldown?.let { cooldown(graphics, it, x, y) }
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
        graphics.text(font, text, x + COUNT_RIGHT - font.width(text), y + COUNT_TOP, -1, true)
    }

    fun marks(rarity: Role?) = rarity != null && settings.feature.runs && (rarity != Role.COMMON || settings.markCommon.value)

    fun rarityBack(graphics: GuiGraphicsExtractor, rarity: Role, index: Int, x: Int, y: Int) {
        art(graphics, Surface.CONTAINER, rarity, index, x, y)
    }

    private fun back(graphics: GuiGraphicsExtractor, surface: Surface, shown: Shown, index: Int, x: Int, y: Int) {
        if (shown.cooldown != null || shown.outline) return
        val rarity = shown.rarity
        if (rarity != null) art(graphics, surface, rarity, index, x, y)
        else if (shown.fill) Kit.slotFill(graphics, x, y, shown.color)
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
        game.metrics.time("slots.draw", Metrics.FRAME) {
            screen.drawInContainer(graphics) {
                for (slots in marks.values) {
                    for ((index, shown) in slots) {
                        val slot = screen.menu.slots.getOrNull(index)
                        if (slot != null) draw(index, slot, shown)
                    }
                }
            }
        }
    }
}
