package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudBox
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap
import kotlin.math.roundToInt

private const val BAR_WIDTH = 80
private const val BAR_GAP = 1
private const val BLOCK_GAP = 4

// An item is 16px, and a pixel of air follows it as one follows a line of text.
private const val ITEM = 16
private const val ITEM_PITCH = ITEM + 1

// The inventory leaves this much between its rows and the hotbar.
private const val ROWS_GAP = 4

// The inventory menu's number for the hotbar's first slot.
private const val HOTBAR_FIRST = 36

// The inventory menu's first main slot, and its offhand slot, which ends the slots that hold a picked-up stack.
private const val MAIN_FIRST = 9
private const val OFFHAND_SLOT = 45

// Vanilla's hotbar squashes a stack that lands about a point 12px down it, over a span of 5 ticks per unit of growth.
private const val POP_FOOT = 12f
private const val POP_SPAN = 5f

// An item sits this far into a hotbar cell.
private const val HOTBAR_INSET = 3

// The plate's ring, then 3px of air before the text.
private const val PADDING = 4

// Vanilla's hotbar is 182 wide in the middle of the screen. The offhand slot takes 29px beside it, and the attack
// indicator, when the player shows it on the hotbar, 24px on the other side. A docked block keeps the 7px vanilla
// leaves between the offhand slot and the hotbar.
private const val HOTBAR_HALF = 91
private const val OFFHAND = 29
private const val INDICATOR_RIGHT = 24
private const val INDICATOR_LEFT = 22
private const val DOCK_GAP = 7

object Hud {
    /**
     * A line of text, with a bar under it when [bar] is set, or a row of item icons when [stacks] is, the player's
     * inventory from its [slot] on when that is not -1. [y] is from the block's top, and [x] from its first column.
     */
    private class Row(val text: Component, val bar: BarRow?, val barColor: Int, val stacks: List<ItemStack?>?, val y: Int, val slot: Int, val x: Int = 0)

    /** [alpha] is how solid the Inventory look's panel and slots are, and [pops] bobs an item as it lands, as the hotbar does. */
    private class Block(
        val header: Component?,
        val rows: List<Row>,
        val look: HudLook,
        val width: Int,
        val height: Int,
        val placeable: Boolean,
        val overMenus: Boolean,
        val columns: Boolean,
        val alpha: Int,
        val pops: Boolean,
        val order: Int,
    ) {
        val across = across(look)
        val down = down(look)
        val pitch = pitch(look)

        /** Where icon [i] of [row] sits, from the block's plate corner and its top. A row of a block in [columns] runs down. */
        fun iconX(row: Row, i: Int) = PADDING + across + row.x + if (columns) 0 else i * pitch

        fun iconY(row: Row, i: Int) = row.y + down + if (columns) i * pitch else 0
    }

    /** An item icon as it sits on the screen, in GUI pixels. */
    private class Cell(val x: Float, val y: Float, val size: Float, val stack: ItemStack)
    private class Group(var place: HudGroup) {
        val blocks = LinkedHashMap<String, Block>()

        // Read every frame, so these are rebuilt on change and the frame loop allocates nothing.
        var shown = emptyList<Block>()
        var width = 0
        var height = 0
        var x = 0
        var y = 0

        // The scale it is drawn at: the player's, or for a group docked beside the hotbar, as much of it as fits there.
        var scale = 1f

        // The top and height of each run of blocks that sit on the plate, from the group's corner, and the runs while a
        // menu is open, without the blocks that step aside for it.
        var plates = IntArray(0)
        var menuPlates = IntArray(0)
    }

    /**
     * A block as it sits on the screen. [x] and [y], in GUI pixels, are where its plate's corner would be if it
     * stood alone, and [width] and [height] are that plate's size in HUD pixels, before [scale]. [dock] is where it
     * sits beside the hotbar, or [HudDock.FREE].
     */
    class Placed(val id: String, val x: Float, val y: Float, val width: Int, val height: Int, val scale: Float, val dock: HudDock) {
        fun contains(px: Double, py: Double) = px >= x && px < x + width * scale && py >= y && py < y + height * scale

        /** Its box as drawn, at its scale. */
        fun box() = HudBox(id, x, y, width * scale, height * scale)
    }

    private val groups = LinkedHashMap<String, Group>()

    // Each block's stacks by the item they were built from. A block shown again reuses the stack of an item it showed
    // before, because building one costs tens of microseconds and an inventory changes one slot at a time. A slot that
    // did not change holds the very same item, so identity finds it without hashing its lore.
    private val stacksByItem = HashMap<String, IdentityHashMap<Item, ItemStack>>()
    private var shown = emptyList<Group>()
    private var placed = emptyList<Placed>()
    private var cells = emptyList<Cell>()

    // Blocks the player hid with a key. They keep their place in their group and come back as they were.
    private val concealed = HashSet<String>()

    /**
     * Puts [block] in [group], replacing what [id] showed before. The group's corner is its plate's corner.
     * A block that is not [placeable] is one no feature owns, so the HUD editor cannot grab it. A group stacks its
     * blocks by [order], so a block that leaves the stack and comes back takes its old place in it.
     */
    fun show(id: String, group: HudGroup, block: HudBlock, placeable: Boolean = true, order: Int = 0) {
        val font = Minecraft.getInstance().font
        val line = font.lineHeight + 1
        val hotbar = block.look == HudLook.HOTBAR
        val inventory = block.look == HudLook.INVENTORY
        val header = block.header.takeIf { it.spans.isNotEmpty() }?.toComponent()
        // The inventory's panel leaves its inset around the slots.
        var height = (if (header != null) line else 0) + if (inventory) Kit.INSET else 0
        var width = header?.let(font::width) ?: 0
        val built = stacksByItem[id].orEmpty()
        val kept = IdentityHashMap<Item, ItemStack>()
        // In columns the rows of icons stand side by side from where the first one starts. The hotbar's sprite only lies across.
        val columns = block.columns && !hotbar
        val pitch = pitch(block.look)
        var columnX = 0
        var columnsTop = -1
        var columnsHeight = 0
        val rows = block.rows.map { row ->
            val bar = row as? BarRow
            val itemsRow = row as? ItemsRow
            val items = itemsRow?.items?.map { item -> item?.let { kept.getOrPut(it) { built[it] ?: it.toStack() } } }
            if (columns && items != null) {
                if (columnsTop < 0) columnsTop = height
                if (itemsRow.apart) columnX += ROWS_GAP
                val drawn = Row(row.text.toComponent(), null, 0, items, columnsTop, itemsRow.slot, columnX)
                columnX += pitch
                columnsHeight = maxOf(columnsHeight, items.size * pitch)
                width = maxOf(width, columnX + if (inventory) 2 * Kit.INSET else -1)
                return@map drawn
            }
            if (itemsRow?.apart == true) height += ROWS_GAP
            val drawn = Row(row.text.toComponent(), bar, bar?.role?.argb() ?: 0, items, height, itemsRow?.slot ?: -1)
            width = maxOf(
                width,
                when {
                    items == null -> maxOf(font.width(drawn.text), if (bar != null) BAR_WIDTH else 0)
                    hotbar -> Kit.hotbarWidth(items.size)
                    inventory -> items.size * Kit.SLOT_SIZE + 2 * Kit.INSET
                    else -> items.size * ITEM_PITCH - 1
                },
            )
            height += when {
                items == null -> line + if (bar != null) Kit.BAR_HEIGHT + BAR_GAP else 0
                hotbar -> Kit.HOTBAR_HEIGHT
                inventory -> Kit.SLOT_SIZE
                else -> ITEM_PITCH
            }
            drawn
        }
        if (columnsTop >= 0) height = maxOf(height, columnsTop + columnsHeight)
        if (inventory) height += Kit.INSET

        stacksByItem[id] = kept
        groups.values.forEach { if (it.place.id != group.id) it.blocks.remove(id) }
        val target = groups.getOrPut(group.id) { Group(group) }
        target.place = group
        // A block that joins, or whose place in the order changed, as when a live block turns into a stand-in, is sorted in.
        val moved = target.blocks[id]?.order != order
        target.blocks[id] = Block(
            header, rows, block.look, width, height, placeable, block.overMenus, columns,
            (block.opacity.coerceIn(0f, 1f) * Kit.OPAQUE).roundToInt(), block.pops, order,
        )
        if (moved) {
            val sorted = target.blocks.entries.sortedBy { it.value.order }
            target.blocks.clear()
            for ((key, value) in sorted) target.blocks[key] = value
        }
        layout()
    }

    /** Takes [id]'s block off the screen while [hidden], keeping it to show again, as a key the player bound does. */
    fun conceal(id: String, hidden: Boolean) {
        if (if (hidden) concealed.add(id) else concealed.remove(id)) layout()
    }

    fun hide(id: String) {
        stacksByItem.remove(id)
        groups.values.forEach { it.blocks.remove(id) }
        layout()
    }

    /** Whether Hide In F3 takes the HUD off now. A HUD the player cannot see can't be pointed at either. */
    private fun hiddenInF3() = DsModClient.settings.hud.hideInF3 && Minecraft.getInstance().debugOverlay.showDebugScreen()

    /** The block under a point in GUI pixels, topmost first. */
    fun at(px: Double, py: Double): Placed? {
        if (hiddenInF3()) return null
        for (i in placed.size - 1 downTo 0) if (placed[i].contains(px, py)) return placed[i]
        return null
    }

    /** The item drawn under a point in GUI pixels, or null. */
    fun itemAt(px: Double, py: Double): ItemStack? {
        if (hiddenInF3()) return null
        for (i in cells.indices.reversed()) {
            val cell = cells[i]
            if (px >= cell.x && px < cell.x + cell.size && py >= cell.y && py < cell.y + cell.size) return cell.stack
        }
        return null
    }

    /** Every block on the screen that the HUD editor can move, in the order they are drawn. */
    fun placed(): List<Placed> = if (hiddenInF3()) emptyList() else placed

    fun find(id: String): Placed? {
        for (i in placed.indices) if (placed[i].id == id) return placed[i]
        return null
    }

    /** The box block [id] would fill docked at [dock] beside the hotbar, drawn at [scale] or as much of it as fits, in GUI pixels. */
    fun dockSpot(id: String, dock: HudDock, scale: Float): HudBox? {
        val group = groups.values.firstOrNull { id in it.blocks } ?: return null
        val block = group.blocks.getValue(id)
        val plated = group.place.background && block.look == HudLook.PLATE
        val spot = corner(dock, block.width, block.height, plated, scale)
        return HudBox(id, spot.x.toFloat(), spot.y.toFloat(), plate(block.width) * spot.scale, plate(block.height) * spot.scale)
    }

    private fun layout() {
        groups.values.removeIf { it.blocks.isEmpty() }
        val all = ArrayList<Placed>()
        val items = ArrayList<Cell>()
        groups.values.forEach { group ->
            val visible = group.blocks.filterKeys { it !in concealed }
            group.shown = visible.values.toList()
            if (group.shown.isEmpty()) return@forEach
            group.width = group.shown.maxOf { it.width }
            group.height = group.shown.sumOf { it.height } + BLOCK_GAP * (group.shown.size - 1)
            group.plates = plates(group, false)
            group.menuPlates = plates(group, true)
            place(group)
            val scale = group.scale
            var y = 0
            for ((id, block) in visible) {
                if (block.placeable) all += Placed(id, group.x.toFloat(), group.y + y * scale, plate(block.width), plate(block.height), scale, group.place.dock)
                for (row in block.rows) {
                    row.stacks?.forEachIndexed { i, stack ->
                        if (stack != null) {
                            items += Cell(group.x + block.iconX(row, i) * scale, group.y + (y + PADDING + block.iconY(row, i)) * scale, ITEM * scale, stack)
                        }
                    }
                }
                y += block.height + BLOCK_GAP
            }
        }
        shown = groups.values.filter { it.shown.isNotEmpty() }
        placed = all
        cells = items
    }

    /**
     * Each run of blocks that sit on the plate, as a top and a height, so a block of another look breaks the plate, as
     * does one that steps aside for an open [menu].
     */
    private fun plates(group: Group, menu: Boolean): IntArray {
        if (!group.place.background) return IntArray(0)
        val runs = ArrayList<Int>()
        var y = 0
        var start = -1
        for (block in group.shown) {
            val plated = block.look == HudLook.PLATE && (block.overMenus || !menu)
            if (plated && start < 0) start = y
            if (!plated && start >= 0) {
                runs += start
                runs += y - BLOCK_GAP - start
                start = -1
            }
            y += block.height + BLOCK_GAP
        }
        if (start >= 0) {
            runs += start
            runs += y - BLOCK_GAP - start
        }
        return runs.toIntArray()
    }

    /**
     * Sets the group's corner: where the player put it, or flush beside the hotbar with its bottom on the screen's,
     * past whatever vanilla draws on that side. A group with a plate puts the plate's edge there, and one without its
     * contents'. A docked group too wide for the room beside the hotbar is drawn smaller, by whole screen pixels per
     * HUD pixel as the rest of the HUD is, so a big one such as the inventory still fits on a narrow screen.
     */
    private fun place(group: Group) {
        val dock = group.place.dock
        group.scale = group.place.scale
        if (dock == HudDock.FREE) {
            // The saved place is still the fraction of the screen. A smaller GUI uses that fraction, then pulls the
            // corner back so the plate that is about to be drawn stays on the screen.
            val window = Minecraft.getInstance().window
            group.x = onScreen(group.place.x, window.guiScaledWidth, group.width, group.scale)
            group.y = onScreen(group.place.y, window.guiScaledHeight, group.height, group.scale)
            return
        }
        val spot = corner(dock, group.width, group.height, group.plates.isNotEmpty(), group.place.scale)
        group.x = spot.x
        group.y = spot.y
        group.scale = spot.scale
    }

    /** Where a docked block's corner goes, in GUI pixels, and the [scale] it is drawn at there. */
    private class Spot(val x: Int, val y: Int, val scale: Float)

    /**
     * The corner of contents [width] by [height] HUD pixels, drawn at [scale] or smaller where the room beside the
     * hotbar is narrower, docked at [dock], with a plate when [plated].
     */
    private fun corner(dock: HudDock, width: Int, height: Int, plated: Boolean, scale: Float): Spot {
        val client = Minecraft.getInstance()
        val screenWidth = client.window.guiScaledWidth
        val middle = screenWidth / 2
        val offhandLeft = client.options.mainHand().get() == HumanoidArm.RIGHT
        val indicator = client.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR
        val left = dock == HudDock.LEFT_OF_HOTBAR
        val beside = when {
            left == offhandLeft -> OFFHAND
            indicator -> if (left) INDICATOR_LEFT else INDICATOR_RIGHT
            else -> 0
        }
        val room = middle - HOTBAR_HALF - beside - DOCK_GAP + if (left) 0 else screenWidth % 2
        // The box that lines up with the hotbar: the plate, or with none the contents, which start PADDING in.
        val box = if (plated) plate(width) else width
        val guiScale = client.window.guiScale
        val fitted = if (box * scale > room) maxOf(1, room * guiScale / box) / guiScale.toFloat() else scale
        val inset = if (plated) 0f else PADDING * fitted
        val drawnWidth = box * fitted
        val drawnHeight = (if (plated) plate(height) else height) * fitted
        var x = if (left) {
            (middle - HOTBAR_HALF - beside - DOCK_GAP - drawnWidth - inset).toInt()
        } else {
            (middle + HOTBAR_HALF + beside + DOCK_GAP - inset).toInt()
        }
        // On a narrow screen there may be no room beside the hotbar, and a block cut off by the edge reads worse than one over the offhand slot.
        x = x.coerceIn(-inset.toInt(), (screenWidth - drawnWidth - inset).toInt().coerceAtLeast(0))
        return Spot(x, (client.window.guiScaledHeight - drawnHeight - inset).toInt(), fitted)
    }

    /** How far into a row of [look] its first item sits, across and down, and how far apart its items are. */
    private fun across(look: HudLook) = when (look) {
        HudLook.HOTBAR -> HOTBAR_INSET
        HudLook.INVENTORY -> Kit.INSET + 1
        else -> 0
    }

    private fun down(look: HudLook) = when (look) {
        HudLook.HOTBAR -> HOTBAR_INSET
        HudLook.INVENTORY -> 1
        else -> 0
    }

    private fun pitch(look: HudLook) = when (look) {
        HudLook.HOTBAR -> Kit.HOTBAR_CELL
        HudLook.INVENTORY -> Kit.SLOT_SIZE
        else -> ITEM_PITCH
    }

    // Each line ends in a pixel of spacing, which the plate's air already gives.
    /** [saved] pulled back so a plate around [size] drawn at [scale] ends on the screen's [room]. */
    private fun onScreen(saved: Int, room: Int, size: Int, scale: Float) = saved.coerceIn(0, (room - plate(size) * scale).roundToInt().coerceAtLeast(0))

    private fun plate(size: Int) = size + PADDING * 2 - 1

    fun register() {
        // With the chat open the mouse is free, so pointing at an icon shows the item's tooltip. Minecraft draws the
        // frame's tooltip at the end of the screen's own pass, so it is set before that pass.
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            ScreenEvents.beforeExtract(screen).register { _, graphics, x, y, _ ->
                if (client.gui.hud.isHidden) return@register
                itemAt(x.toDouble(), y.toDouble())?.let { graphics.setTooltipForNextFrame(client.font, it, x, y) }
            }
        }
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("dsmod", "hud")) { graphics, delta ->
            if (shown.isEmpty()) return@attachElementBefore
            if (hiddenInF3()) return@attachElementBefore
            DsModClient.game.metrics.time("hud.draw") { draw(graphics, delta.getGameTimeDeltaPartialTick(false)) }
        }
    }

    /** Draws every block where it sits, as the game's HUD does each frame, and as the settings' layout draws it small. */
    fun draw(graphics: GuiGraphicsExtractor, partial: Float = 0f) {
        val client = Minecraft.getInstance()
        val font = client.font
        val line = font.lineHeight + 1
        // The Inventory look matches the inventory screen, which Menu Theme draws with the kit, solid or as glass.
        val themed = MenuSkin.themed
        val glass = MenuSkin.glassAlpha
        val menu = client.gui.screen() is AbstractContainerScreen<*>
        // The hotbar slot the player holds, numbered as the inventory menu numbers it.
        val player = client.player
        val held = player?.let { HOTBAR_FIRST + it.inventory.selectedSlot } ?: -1
        val pose = graphics.pose()
        for (g in shown.indices) {
            val group = shown[g]
            pose.pushMatrix()
            pose.translate(group.x.toFloat(), group.y.toFloat())
            pose.scale(group.scale, group.scale)
            val plates = if (menu) group.menuPlates else group.plates
            for (p in 0 until plates.size / 2) Kit.plate(graphics, 0, plates[p * 2], plate(group.width), plate(plates[p * 2 + 1]))
            var top = PADDING
            for (b in group.shown.indices) {
                val block = group.shown[b]
                if (menu && !block.overMenus) {
                    top += block.height + BLOCK_GAP
                    continue
                }
                val hotbar = block.look == HudLook.HOTBAR
                val inventory = block.look == HudLook.INVENTORY
                val alpha = block.alpha
                if (inventory) {
                    when {
                        glass >= 0 -> Kit.glass(graphics, PADDING, top, block.width, block.height, glass * alpha / Kit.OPAQUE)
                        themed -> Kit.panel(graphics, PADDING, top, block.width, block.height, alpha)
                        else -> Kit.inventoryPanel(graphics, PADDING, top, block.width, block.height, alpha)
                    }
                }
                if (block.header != null) graphics.text(font, block.header, PADDING, top, -1, true)
                for (r in block.rows.indices) {
                    val row = block.rows[r]
                    val y = top + row.y
                    val stacks = row.stacks
                    if (stacks != null) {
                        if (hotbar) Kit.hotbar(graphics, PADDING, y, stacks.size)
                        // The inventory's look takes the inventory screen's rarity art, and the others the hotbar's.
                        val surface = if (inventory) Surface.CONTAINER else Surface.HOTBAR
                        for (i in stacks.indices) {
                            val x = block.iconX(row, i)
                            val itemY = top + block.iconY(row, i)
                            if (inventory) {
                                when {
                                    glass >= 0 -> Kit.glassSlot(graphics, x - 1, itemY - 1, alpha = alpha)
                                    themed -> Kit.slot(graphics, x - 1, itemY - 1, alpha = alpha)
                                    else -> Kit.slotAt(graphics, x - 1, itemY - 1, alpha)
                                }
                            }
                            val slot = if (row.slot < 0) -1 else row.slot + i
                            val stack = stacks[i]
                            if (stack != null) {
                                if (slot >= 0) Slots.drawBack(graphics, surface, slot, x, itemY)
                                // The hotbar bobs a stack as it lands, from the inventory's own stack, which counts the bob down.
                                val pop = if (block.pops && player != null && slot in MAIN_FIRST until OFFHAND_SLOT) {
                                    player.inventory.getItem(if (slot >= HOTBAR_FIRST) slot - HOTBAR_FIRST else slot).popTime - partial
                                } else {
                                    0f
                                }
                                if (pop > 0f) {
                                    val grow = 1f + pop / POP_SPAN
                                    pose.pushMatrix()
                                    pose.translate(x + ITEM / 2f, itemY + POP_FOOT)
                                    pose.scale(1f / grow, (grow + 1f) / 2f)
                                    pose.translate(-(x + ITEM / 2f), -(itemY + POP_FOOT))
                                    graphics.item(stack, x, itemY)
                                    pose.popMatrix()
                                } else {
                                    graphics.item(stack, x, itemY)
                                }
                                graphics.itemDecorations(font, stack, x, itemY)
                                if (slot >= 0) Slots.drawFront(graphics, surface, slot, x, itemY)
                            }
                            if (slot == held) Kit.held(graphics, x, itemY, inventory && glass < 0 && !themed)
                        }
                        continue
                    }
                    graphics.text(font, row.text, PADDING, y, -1, true)
                    val bar = row.bar
                    if (bar != null) Kit.bar(graphics, PADDING, y + line, BAR_WIDTH, bar.fraction, row.barColor)
                }
                top += block.height + BLOCK_GAP
            }
            pose.popMatrix()
        }
    }
}
