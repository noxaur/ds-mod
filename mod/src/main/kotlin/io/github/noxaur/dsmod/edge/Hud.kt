package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

private const val BAR_WIDTH = 80
private const val BAR_GAP = 1
private const val BLOCK_GAP = 4

// An item is 16px, and a pixel of air follows it as one follows a line of text.
private const val ITEM = 16
private const val ITEM_PITCH = ITEM + 1

// The plate's ring, then 3px of air before the text.
private const val PADDING = 4

object Hud {
    /** A line of text, with a bar under it when [bar] is set, or a row of item icons when [stacks] is. [y] is from the block's top. */
    private class Row(val text: Component, val bar: BarRow?, val barColor: Int, val stacks: List<ItemStack?>?, val y: Int)
    private class Block(val header: Component?, val rows: List<Row>, val width: Int, val height: Int, val placeable: Boolean)

    /** An item icon as it sits on the screen, in GUI pixels. */
    private class Cell(val x: Float, val y: Float, val size: Float, val stack: ItemStack)
    private class Group(var place: HudGroup) {
        val blocks = LinkedHashMap<String, Block>()

        // Read every frame, so these are rebuilt on change and the frame loop allocates nothing.
        var shown = emptyList<Block>()
        var width = 0
        var height = 0
    }

    /**
     * A block as it sits on the screen. [x] and [y], in GUI pixels, are where its plate's corner would be if it
     * stood alone, and [width] and [height] are that plate's size in HUD pixels, before [scale].
     */
    class Placed(val id: String, val x: Float, val y: Float, val width: Int, val height: Int, val scale: Float) {
        fun contains(px: Double, py: Double) = px >= x && px < x + width * scale && py >= y && py < y + height * scale
    }

    private val groups = LinkedHashMap<String, Group>()
    private var shown = emptyList<Group>()
    private var placed = emptyList<Placed>()
    private var cells = emptyList<Cell>()
    private var stacks: (Item) -> ItemStack? = { null }

    /**
     * Puts [block] in [group], replacing what [id] showed before. The group's corner is its plate's corner.
     * A block that is not [placeable] is one no feature owns, so the HUD editor cannot grab it.
     */
    fun show(id: String, group: HudGroup, block: HudBlock, placeable: Boolean = true) {
        val font = Minecraft.getInstance().font
        val line = font.lineHeight + 1
        val header = block.header.takeIf { it.spans.isNotEmpty() }?.toComponent()
        var height = if (header != null) line else 0
        var width = header?.let(font::width) ?: 0
        val rows = block.rows.map { row ->
            val bar = row as? BarRow
            val items = (row as? ItemsRow)?.items?.map { item -> item?.let { stacks(it) ?: it.plainStack() } }
            val drawn = Row(row.text.toComponent(), bar, bar?.role?.argb() ?: 0, items, height)
            width = maxOf(width, if (items != null) items.size * ITEM_PITCH - 1 else maxOf(font.width(drawn.text), if (bar != null) BAR_WIDTH else 0))
            height += if (items != null) ITEM_PITCH else line + if (bar != null) Kit.BAR_HEIGHT + BAR_GAP else 0
            drawn
        }

        groups.values.forEach { if (it.place.id != group.id) it.blocks.remove(id) }
        val target = groups.getOrPut(group.id) { Group(group) }
        target.place = group
        target.blocks[id] = Block(header, rows, width, height, placeable)
        layout()
    }

    fun hide(id: String) {
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

    /** Every block on the screen, in the order they are drawn. */
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
            val scale = group.place.scale
            var y = 0
            for ((id, block) in group.blocks) {
                if (block.placeable) all += Placed(id, group.place.x.toFloat(), group.place.y + y * scale, plate(block.width), plate(block.height), scale)
                for (row in block.rows) {
                    row.stacks?.forEachIndexed { i, stack ->
                        if (stack != null) items += Cell(group.place.x + (PADDING + i * ITEM_PITCH) * scale, group.place.y + (y + PADDING + row.y) * scale, ITEM * scale, stack)
                    }
                }
                y += block.height + BLOCK_GAP
            }
        }
        shown = groups.values.toList()
        placed = all
        cells = items
    }

    // Each line ends in a pixel of spacing, which the plate's air already gives.
    private fun plate(size: Int) = size + PADDING * 2 - 1

    /** [stacks] finds the stack an item was read from, so its icon shows what the item leaves out, such as a head's skin. */
    fun register(stacks: (Item) -> ItemStack?) {
        this.stacks = stacks
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
                val pose = graphics.pose()
                for (g in shown.indices) {
                    val group = shown[g]
                    pose.pushMatrix()
                    pose.translate(group.place.x.toFloat(), group.place.y.toFloat())
                    pose.scale(group.place.scale, group.place.scale)
                    if (group.place.background) Kit.plate(graphics, 0, 0, plate(group.width), plate(group.height))
                    var top = PADDING
                    for (b in group.shown.indices) {
                        val block = group.shown[b]
                        if (block.header != null) graphics.text(font, block.header, PADDING, top, -1, true)
                        for (r in block.rows.indices) {
                            val row = block.rows[r]
                            val y = top + row.y
                            val stacks = row.stacks
                            if (stacks != null) {
                                for (i in stacks.indices) {
                                    val stack = stacks[i] ?: continue
                                    graphics.item(stack, PADDING + i * ITEM_PITCH, y)
                                    graphics.itemDecorations(font, stack, PADDING + i * ITEM_PITCH, y)
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
