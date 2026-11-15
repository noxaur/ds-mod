package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.hud.HudBlock
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
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap

private const val BAR_WIDTH = 80
private const val BAR_GAP = 1
private const val BLOCK_GAP = 4

// An item is 16px, and a pixel of air follows it as one follows a line of text.
private const val ITEM = 16
private const val ITEM_PITCH = ITEM + 1

// The inventory leaves this much between its rows and the hotbar.
private const val ROWS_GAP = 4

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
    /** A line of text, with a bar under it when [bar] is set, or a row of item icons when [stacks] is. [y] is from the block's top. */
    private class Row(val text: Component, val bar: BarRow?, val barColor: Int, val stacks: List<ItemStack?>?, val y: Int)
    private class Block(val header: Component?, val rows: List<Row>, val look: HudLook, val width: Int, val height: Int, val placeable: Boolean)

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

        // The top and height of each run of blocks that sit on the plate, from the group's corner.
        var plates = IntArray(0)
    }

    /**
     * A block as it sits on the screen. [x] and [y], in GUI pixels, are where its plate's corner would be if it
     * stood alone, and [width] and [height] are that plate's size in HUD pixels, before [scale].
     */
    class Placed(val id: String, val x: Float, val y: Float, val width: Int, val height: Int, val scale: Float) {
        fun contains(px: Double, py: Double) = px >= x && px < x + width * scale && py >= y && py < y + height * scale
    }

    private val groups = LinkedHashMap<String, Group>()

    // Each block's stacks by the item they were built from. A block shown again reuses the stack of an item it showed
    // before, because building one costs tens of microseconds and an inventory changes one slot at a time. A slot that
    // did not change holds the very same item, so identity finds it without hashing its lore.
    private val stacksByItem = HashMap<String, IdentityHashMap<Item, ItemStack>>()
    private var shown = emptyList<Group>()
    private var placed = emptyList<Placed>()
    private var cells = emptyList<Cell>()

    /**
     * Puts [block] in [group], replacing what [id] showed before. The group's corner is its plate's corner.
     * A block that is not [placeable] is one no feature owns, so the HUD editor cannot grab it.
     */
    fun show(id: String, group: HudGroup, block: HudBlock, placeable: Boolean = true) {
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
        val rows = block.rows.map { row ->
            val bar = row as? BarRow
            if ((row as? ItemsRow)?.apart == true) height += ROWS_GAP
            val items = (row as? ItemsRow)?.items?.map { item -> item?.let { kept.getOrPut(it) { built[it] ?: it.toStack() } } }
            val drawn = Row(row.text.toComponent(), bar, bar?.role?.argb() ?: 0, items, height)
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
        if (inventory) height += Kit.INSET

        stacksByItem[id] = kept
        groups.values.forEach { if (it.place.id != group.id) it.blocks.remove(id) }
        val target = groups.getOrPut(group.id) { Group(group) }
        target.place = group
        target.blocks[id] = Block(header, rows, block.look, width, height, placeable && group.dock == HudDock.FREE)
        layout()
    }

    fun hide(id: String) {
        stacksByItem.remove(id)
        groups.values.forEach { it.blocks.remove(id) }
        layout()
    }

    /** The block under a point in GUI pixels, topmost first. */
    fun at(px: Double, py: Double): Placed? {
        for (i in placed.size - 1 downTo 0) if (placed[i].contains(px, py)) return placed[i]
        return null
    }

    /** The item drawn under a point in GUI pixels, or null. */
    fun itemAt(px: Double, py: Double): ItemStack? {
        for (i in cells.indices) {
            val cell = cells[i]
            if (px >= cell.x && px < cell.x + cell.size && py >= cell.y && py < cell.y + cell.size) return cell.stack
        }
        return null
    }

    /** Every block on the screen that the HUD editor can move, in the order they are drawn. */
    fun placed(): List<Placed> = placed

    fun find(id: String): Placed? {
        for (i in placed.indices) if (placed[i].id == id) return placed[i]
        return null
    }

    private fun layout() {
        groups.values.removeIf { it.blocks.isEmpty() }
        val all = ArrayList<Placed>()
        val items = ArrayList<Cell>()
        groups.values.forEach { group ->
            group.shown = group.blocks.values.toList()
            group.width = group.shown.maxOf { it.width }
            group.height = group.shown.sumOf { it.height } + BLOCK_GAP * (group.shown.size - 1)
            group.plates = plates(group)
            place(group)
            val scale = group.place.scale
            var y = 0
            for ((id, block) in group.blocks) {
                if (block.placeable) all += Placed(id, group.x.toFloat(), group.y + y * scale, plate(block.width), plate(block.height), scale)
                val across = across(block.look)
                val down = down(block.look)
                val pitch = pitch(block.look)
                for (row in block.rows) {
                    row.stacks?.forEachIndexed { i, stack ->
                        if (stack != null) {
                            items += Cell(group.x + (PADDING + across + i * pitch) * scale, group.y + (y + PADDING + down + row.y) * scale, ITEM * scale, stack)
                        }
                    }
                }
                y += block.height + BLOCK_GAP
            }
        }
        shown = groups.values.toList()
        placed = all
        cells = items
    }

    /** Each run of blocks that sit on the plate, as a top and a height, so a block of another look breaks the plate. */
    private fun plates(group: Group): IntArray {
        if (!group.place.background) return IntArray(0)
        val runs = ArrayList<Int>()
        var y = 0
        var start = -1
        for (block in group.shown) {
            if (block.look == HudLook.PLATE && start < 0) start = y
            if (block.look != HudLook.PLATE && start >= 0) {
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
     * contents'.
     */
    private fun place(group: Group) {
        val dock = group.place.dock
        if (dock == HudDock.FREE) {
            group.x = group.place.x
            group.y = group.place.y
            return
        }
        val client = Minecraft.getInstance()
        val middle = client.window.guiScaledWidth / 2
        val offhandLeft = client.options.mainHand().get() == HumanoidArm.RIGHT
        val indicator = client.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR
        // The box that lines up with the hotbar: the plate, or with none the contents, which start PADDING in.
        val scale = group.place.scale
        val plated = group.plates.isNotEmpty()
        val inset = if (plated) 0f else PADDING * scale
        val width = (if (plated) plate(group.width) else group.width) * scale
        val height = (if (plated) plate(group.height) else group.height) * scale
        group.x = if (dock == HudDock.LEFT_OF_HOTBAR) {
            val beside = if (offhandLeft) OFFHAND else if (indicator) INDICATOR_LEFT else 0
            (middle - HOTBAR_HALF - beside - DOCK_GAP - width - inset).toInt()
        } else {
            val beside = if (!offhandLeft) OFFHAND else if (indicator) INDICATOR_RIGHT else 0
            (middle + HOTBAR_HALF + beside + DOCK_GAP - inset).toInt()
        }
        // On a narrow screen there may be no room beside the hotbar, and a block cut off by the edge reads worse than one over the offhand slot.
        group.x = group.x.coerceIn(-inset.toInt(), (client.window.guiScaledWidth - width - inset).toInt().coerceAtLeast(0))
        group.y = (client.window.guiScaledHeight - height - inset).toInt()
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
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("dsmod", "hud")) { graphics, _ ->
            if (shown.isEmpty()) return@attachElementBefore
            DsModClient.game.metrics.time("hud.draw") {
                val font = Minecraft.getInstance().font
                val line = font.lineHeight + 1
                // The Inventory look matches the inventory screen, which Themed Menus draws with the kit, solid or as glass.
                val themed = MenuSkin.themed
                val glass = MenuSkin.glassAlpha
                val pose = graphics.pose()
                for (g in shown.indices) {
                    val group = shown[g]
                    pose.pushMatrix()
                    pose.translate(group.x.toFloat(), group.y.toFloat())
                    pose.scale(group.place.scale, group.place.scale)
                    val plates = group.plates
                    for (p in 0 until plates.size / 2) Kit.plate(graphics, 0, plates[p * 2], plate(group.width), plate(plates[p * 2 + 1]))
                    var top = PADDING
                    for (b in group.shown.indices) {
                        val block = group.shown[b]
                        val hotbar = block.look == HudLook.HOTBAR
                        val inventory = block.look == HudLook.INVENTORY
                        if (inventory) {
                            when {
                                glass >= 0 -> Kit.glass(graphics, PADDING, top, block.width, block.height, glass)
                                themed -> Kit.panel(graphics, PADDING, top, block.width, block.height)
                                else -> Kit.inventoryPanel(graphics, PADDING, top, block.width, block.height)
                            }
                        }
                        if (block.header != null) graphics.text(font, block.header, PADDING, top, -1, true)
                        for (r in block.rows.indices) {
                            val row = block.rows[r]
                            val y = top + row.y
                            val stacks = row.stacks
                            if (stacks != null) {
                                if (hotbar) Kit.hotbar(graphics, PADDING, y, stacks.size)
                                val across = across(block.look)
                                val down = down(block.look)
                                val pitch = pitch(block.look)
                                for (i in stacks.indices) {
                                    val x = PADDING + across + i * pitch
                                    if (inventory) {
                                        when {
                                            glass >= 0 -> Kit.glassSlot(graphics, x - 1, y + down - 1)
                                            themed -> Kit.slot(graphics, x - 1, y + down - 1)
                                            else -> Kit.slotAt(graphics, x - 1, y + down - 1)
                                        }
                                    }
                                    val stack = stacks[i] ?: continue
                                    graphics.item(stack, x, y + down)
                                    graphics.itemDecorations(font, stack, x, y + down)
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
    }
}
