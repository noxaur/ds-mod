package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

private const val BAR_WIDTH = 80
private const val BAR_GAP = 1
private const val BLOCK_GAP = 4

// The plate's ring, then 3px of air before the text.
private const val PADDING = 4

object Hud {
    private class Row(val text: Component, val bar: BarRow?, val barColor: Int)
    private class Block(val header: Component, val rows: List<Row>, val width: Int, val height: Int)
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

    /** Puts [block] in [group], replacing what [id] showed before. The group's corner is its plate's corner. */
    fun show(id: String, group: HudGroup, block: HudBlock) {
        val font = Minecraft.getInstance().font
        val line = font.lineHeight + 1
        val header = block.header.toComponent()
        val rows = block.rows.map { row ->
            val bar = row as? BarRow
            Row(row.text.toComponent(), bar, bar?.role?.argb() ?: 0)
        }
        val width = rows.maxOfOrNull { maxOf(font.width(it.text), if (it.bar != null) BAR_WIDTH else 0) } ?: 0
        val height = line + rows.sumOf { line + if (it.bar != null) Kit.BAR_HEIGHT + BAR_GAP else 0 }

        groups.values.forEach { if (it.place.id != group.id) it.blocks.remove(id) }
        val target = groups.getOrPut(group.id) { Group(group) }
        target.place = group
        target.blocks[id] = Block(header, rows, maxOf(width, font.width(header)), height)
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

    fun find(id: String): Placed? {
        for (i in placed.indices) if (placed[i].id == id) return placed[i]
        return null
    }

    private fun layout() {
        groups.values.removeIf { it.blocks.isEmpty() }
        val all = ArrayList<Placed>()
        groups.values.forEach { group ->
            group.shown = group.blocks.values.toList()
            group.width = group.shown.maxOf { it.width }
            group.height = group.shown.sumOf { it.height } + BLOCK_GAP * (group.shown.size - 1)
            val scale = group.place.scale
            var y = 0
            for ((id, block) in group.blocks) {
                all += Placed(id, group.place.x.toFloat(), group.place.y + y * scale, plate(block.width), plate(block.height), scale)
                y += block.height + BLOCK_GAP
            }
        }
        shown = groups.values.toList()
        placed = all
    }

    // Each line ends in a pixel of spacing, which the plate's air already gives.
    private fun plate(size: Int) = size + PADDING * 2 - 1

    fun register() {
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
                    var y = PADDING
                    for (b in group.shown.indices) {
                        val block = group.shown[b]
                        graphics.text(font, block.header, PADDING, y, -1, true)
                        y += line
                        for (r in block.rows.indices) {
                            val row = block.rows[r]
                            graphics.text(font, row.text, PADDING, y, -1, true)
                            y += line
                            val bar = row.bar
                            if (bar != null) {
                                Kit.bar(graphics, PADDING, y, BAR_WIDTH, bar.fraction, row.barColor)
                                y += Kit.BAR_HEIGHT + BAR_GAP
                            }
                        }
                        y += BLOCK_GAP
                    }
                    pose.popMatrix()
                }
            }
        }
    }
}
