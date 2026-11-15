package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Fill
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.container.step
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitStepper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

private const val TITLE = "Rarity Style"
private const val LABEL_HEIGHT = 8
private const val ROW_LABEL_GAP = 2
private const val MATCH = "Match Inventory"
private const val HOTBAR_HALF = 91
private const val HOTBAR_HEIGHT = 22
private const val PREVIEW_SCALE = 2
private const val PREVIEW_SLOTS = 3
private const val ENTITY_SIZE = 30
private const val ENTITY_OFFSET = 0.0625f

/**
 * Edits how rarity marks look, beside what they mark: the player's inventory with a panel for its style on the
 * left, a preview of three slots at twice the size on the right, and the real hotbar under it all with a panel for
 * its own style, which can follow the inventory's. Every change shows at once. Items can't be moved here. Closing
 * saves through [onSave] and goes back to [parent].
 */
class RarityStyleScreen(
    private val settings: DsModConfig.Inventory,
    private val parent: Screen?,
    private val onSave: () -> Unit,
) : Screen(text { body(TITLE) }.toComponent()) {
    private class Label(val text: String, val x: Int, val y: Int)
    private class Panel(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun overlaps(other: Panel) = x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height
    }
    private class Sample(val stack: ItemStack, val rarity: Role)

    private var leftPos = 0
    private var topPos = 0
    private val panels = ArrayList<Panel>()
    private val labels = ArrayList<Label>()
    private val refreshers = ArrayList<() -> Unit>()
    private var preview: Panel? = null
    private var samples = emptyList<Sample>()
    private var valueWidth = 0

    override fun init() {
        panels.clear()
        labels.clear()
        refreshers.clear()
        SlotArts.checkPacks()
        valueWidth = (Border.entries.map { it.label } + Fill.entries.map { it.label } + Floor.entries.map { it.label }).maxOf(font::width)
        val doneWidth = font.width("Done") + 16
        // Narrow enough that a panel fits beside the hotbar on a 427 wide screen, which is 1280x720 at GUI scale 3.
        val content = maxOf(
            Kit.BUTTON_HEIGHT * 2 + Kit.GAP * 2 + valueWidth,
            font.width("Inventory") + Kit.GAP + doneWidth,
            Kit.CHECKBOX_SIZE + Kit.GAP + font.width(MATCH),
        )
        val panelWidth = content + Kit.INSET * 2
        val styleHeight = (LABEL_HEIGHT + ROW_LABEL_GAP + Kit.BUTTON_HEIGHT + Kit.GAP) * 3 + Kit.CHECKBOX_SIZE
        val headHeight = Kit.INSET + Kit.BUTTON_HEIGHT + Kit.GAP

        leftPos = (width - Kit.INVENTORY_WIDTH) / 2
        topPos = maxOf(Kit.GAP, (height - HOTBAR_HEIGHT - Kit.INVENTORY_HEIGHT) / 2)

        // The inventory's panel, left of it.
        val inventory = Panel(leftPos - Kit.GAP - panelWidth, topPos, panelWidth, headHeight + styleHeight + Kit.GAP + Kit.CHECKBOX_SIZE + Kit.INSET)
        panels += inventory
        val ix = inventory.x + Kit.INSET
        labels += Label("Inventory", ix, inventory.y + Kit.INSET + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        addRenderableWidget(KitButton(inventory.x + inventory.width - Kit.INSET - doneWidth, inventory.y + Kit.INSET, "Done", ::onClose, doneWidth))
        // Editing the inventory makes the player's own style the one shown, starting from the preset they had.
        var y = styleControls(ix, inventory.y + headHeight, settings::containerStyle) {
            settings.inventoryStyle = it
            settings.rarityPreset = RarityPreset.CUSTOM
        }
        addRenderableWidget(KitCheckbox(ix, y + Kit.GAP, "Mark Common", settings.markCommon) { settings.markCommon = it })

        // The hotbar's panel, right of the hotbar, or left of it, or above it on the right where neither side has room.
        val hotbarHeight = Kit.INSET * 2 + LABEL_HEIGHT + (Kit.GAP + Kit.CHECKBOX_SIZE) * 2 + if (settings.hotbarMatches) 0 else Kit.GAP + styleHeight
        val right = width / 2 + HOTBAR_HALF + Kit.GAP
        val left = width / 2 - HOTBAR_HALF - Kit.GAP - panelWidth
        val hotbar = when {
            right + panelWidth <= width - Kit.GAP -> Panel(right, height - hotbarHeight, panelWidth, hotbarHeight)
            left >= Kit.GAP -> Panel(left, height - hotbarHeight, panelWidth, hotbarHeight)
            else -> Panel(width - Kit.GAP - panelWidth, height - HOTBAR_HEIGHT - Kit.GAP - hotbarHeight, panelWidth, hotbarHeight)
        }
        panels += hotbar
        val hx = hotbar.x + Kit.INSET
        labels += Label("Hotbar", hx, hotbar.y + Kit.INSET)
        y = hotbar.y + Kit.INSET + LABEL_HEIGHT + Kit.GAP
        addRenderableWidget(KitCheckbox(hx, y, "Show Marks", settings.hotbarMarks) { settings.hotbarMarks = it })
        y += Kit.CHECKBOX_SIZE + Kit.GAP
        addRenderableWidget(
            KitCheckbox(hx, y, MATCH, settings.hotbarMatches) {
                // Unlinking starts the hotbar from what it showed, so nothing jumps.
                if (!it) settings.hotbarStyle = settings.containerStyle()
                settings.hotbarMatches = it
                rebuildWidgets()
            },
        )
        if (!settings.hotbarMatches) {
            styleControls(hx, y + Kit.CHECKBOX_SIZE + Kit.GAP, { settings.hotbarStyle }) { settings.hotbarStyle = it }
        }

        // The preview, right of the inventory, where it has room.
        val previewSize = Kit.SLOT_SIZE * PREVIEW_SCALE
        val previewWidth = Kit.INSET * 2 + previewSize * PREVIEW_SLOTS + Kit.GAP * (PREVIEW_SLOTS - 1)
        val previewHeight = Kit.INSET * 2 + LABEL_HEIGHT + Kit.GAP + previewSize + if (settings.rarityMarks) 0 else Kit.GAP + LABEL_HEIGHT
        preview = Panel(leftPos + Kit.INVENTORY_WIDTH + Kit.GAP, topPos, previewWidth, previewHeight)
            .takeIf { it.x + it.width <= width - Kit.GAP && !it.overlaps(hotbar) }
            ?.also {
                panels += it
                labels += Label("Preview", it.x + Kit.INSET, it.y + Kit.INSET)
            }
        samples = samples()
        refresh()
    }

    /** Adds the three steppers and the gem for one style, from [top] down, and returns the bottom. */
    private fun styleControls(x: Int, top: Int, get: () -> SlotStyle, set: (SlotStyle) -> Unit): Int {
        val change = { style: SlotStyle ->
            set(style)
            refresh()
        }
        var y = top
        fun row(name: String, valueOf: (SlotStyle) -> String, onStep: (Int) -> Unit) {
            labels += Label(name, x, y)
            y += LABEL_HEIGHT + ROW_LABEL_GAP
            val stepper = addRenderableWidget(KitStepper(x, y, name, valueWidth, onStep))
            refreshers += { stepper.value = valueOf(get()) }
            y += Kit.BUTTON_HEIGHT + Kit.GAP
        }
        row("Border", { it.border.label }) { by -> change(get().copy(border = get().border.step(by, Border.entries))) }
        row("Fill", { it.fill.label }) { by -> change(get().copy(fill = get().fill.step(by, Fill.entries))) }
        row("Floor", { it.floor.label }) { by -> change(get().copy(floor = get().floor.step(by, Floor.entries))) }
        val gem = addRenderableWidget(KitCheckbox(x, y, "Gem", get().gem) { change(get().copy(gem = it)) })
        refreshers += { gem.checked = get().gem }
        return y + Kit.CHECKBOX_SIZE
    }

    private fun refresh() = refreshers.forEach { it() }

    /** The player's three rarest marked items, topped up with stand-ins of the rarities they lack. */
    private fun samples(): List<Sample> {
        val player = minecraft.player ?: return STAND_INS
        val own = player.inventoryMenu.slots.mapNotNull { slot -> Slots.rarity(slot.index)?.let { Sample(slot.item, it) } }
            .sortedByDescending { it.rarity.ordinal }.distinctBy { it.rarity }.take(PREVIEW_SLOTS)
        return own + STAND_INS.filter { stand -> own.none { it.rarity == stand.rarity } }.take(PREVIEW_SLOTS - own.size)
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    // The hotbar under this screen is what the player is editing, so nothing dims or blurs it.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val player = minecraft.player
        Kit.inventory(graphics, leftPos, topPos)
        var hovered: ItemStack? = null
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78, ENTITY_SIZE, ENTITY_OFFSET, mouseX.toFloat(), mouseY.toFloat(), player,
            )
            for (slot in player.inventoryMenu.slots) {
                val x = leftPos + slot.x
                val y = topPos + slot.y
                val stack = slot.item
                Slots.drawBack(graphics, Surface.CONTAINER, slot.index, x, y)
                if (stack.isEmpty) continue
                graphics.item(stack, x, y)
                graphics.itemDecorations(font, stack, x, y)
                Slots.drawFront(graphics, Surface.CONTAINER, slot.index, x, y)
                if (mouseX in x until x + 16 && mouseY in y until y + 16) hovered = stack
            }
        }
        for (panel in panels) Kit.panel(graphics, panel.x, panel.y, panel.width, panel.height)
        for (label in labels) Kit.label(graphics, label.text, label.x, label.y)
        preview?.let { previewSlots(graphics, it) }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        hovered?.let { graphics.setTooltipForNextFrame(font, it, mouseX, mouseY) }
    }

    /** The samples at twice the size, each on a slot cut from the inventory's own texture. */
    private fun previewSlots(graphics: GuiGraphicsExtractor, panel: Panel) {
        val atlas = SlotArts.atlas(settings, Surface.CONTAINER)
        val gem = settings.containerStyle().gem
        val top = panel.y + Kit.INSET + LABEL_HEIGHT + Kit.GAP
        samples.forEachIndexed { i, sample ->
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate((panel.x + Kit.INSET + i * (Kit.SLOT_SIZE * PREVIEW_SCALE + Kit.GAP)).toFloat(), top.toFloat())
            pose.scale(PREVIEW_SCALE.toFloat(), PREVIEW_SCALE.toFloat())
            Kit.slotAt(graphics, 0, 0)
            Kit.slotArt(graphics, 1, 1, atlas, sample.rarity.ordinal)
            graphics.item(sample.stack, 1, 1)
            if (gem) Kit.slotGem(graphics, 1, 1, atlas, sample.rarity.ordinal)
            pose.popMatrix()
        }
        if (!settings.rarityMarks) {
            Kit.label(graphics, "Rarity Marks is off.", panel.x + Kit.INSET, top + Kit.SLOT_SIZE * PREVIEW_SCALE + Kit.GAP, Kit.Ink.MUTED)
        }
    }

    private companion object {
        val STAND_INS = listOf(
            Sample(ItemStack(Items.DIAMOND_SWORD), Role.LEGENDARY),
            Sample(ItemStack(Items.ENDER_EYE), Role.EPIC),
            Sample(ItemStack(Items.BOW), Role.RARE),
        )
    }
}
