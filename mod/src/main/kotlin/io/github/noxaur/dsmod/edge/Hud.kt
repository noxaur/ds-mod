package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.hud.BarRow
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.Role
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

private const val BAR_WIDTH = 80
private const val BAR_HEIGHT = 2
private const val BAR_GAP = 2
private const val BLOCK_GAP = 4
private const val PADDING = 3

// Black at the strength of Minecraft's own chat background.
private const val PANEL = 128 shl 24

object Hud {
    private class Row(val text: Component, val bar: BarRow?)
    private class Block(val header: Component, val rows: List<Row>, val width: Int, val height: Int)
    private class Group(var place: HudGroup) {
        val blocks = LinkedHashMap<String, Block>()

        // Read every frame, so these are rebuilt on change and the frame loop allocates nothing.
        var shown = emptyList<Block>()
        var width = 0
        var height = 0
    }

    /** A block as it sits on the screen, in GUI pixels. */
    class Placed(val id: String, val x: Float, val y: Float, val width: Float, val height: Float, val scale: Float)

    private val groups = LinkedHashMap<String, Group>()
    private var shown = emptyList<Group>()

    /** Puts [block] in [group], replacing what [id] showed before. */
    fun show(id: String, group: HudGroup, block: HudBlock) {
        val font = Minecraft.getInstance().font
        val line = font.lineHeight + 1
        val header = block.header.toComponent()
        val rows = block.rows.map { Row(it.text.toComponent(), it as? BarRow) }
        val width = rows.maxOfOrNull { maxOf(font.width(it.text), if (it.bar != null) BAR_WIDTH else 0) } ?: 0
        val height = line + rows.sumOf { line + if (it.bar != null) BAR_HEIGHT + BAR_GAP else 0 }

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

    /** The block under a point, topmost first. The HUD editor asks, so it may allocate. */
    fun at(px: Double, py: Double): Placed? {
        for (group in groups.values.reversed()) {
            val scale = group.place.scale
            var y = group.place.y.toFloat()
            for ((id, block) in group.blocks) {
                val placed = Placed(id, group.place.x.toFloat(), y, block.width * scale, block.height * scale, scale)
                if (px >= placed.x && px <= placed.x + placed.width && py >= y && py <= y + placed.height) return placed
                y += (block.height + BLOCK_GAP) * scale
            }
        }
        return null
    }

    private fun layout() {
        groups.values.removeIf { it.blocks.isEmpty() }
        groups.values.forEach { group ->
            group.shown = group.blocks.values.toList()
            group.width = group.shown.maxOf { it.width }
            group.height = group.shown.sumOf { it.height } + BLOCK_GAP * (group.shown.size - 1)
        }
        shown = groups.values.toList()
    }

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
                    if (group.place.background) graphics.fill(-PADDING, -PADDING, group.width + PADDING, group.height + PADDING - 1, PANEL)
                    var y = 0
                    for (b in group.shown.indices) {
                        val block = group.shown[b]
                        graphics.text(font, block.header, 0, y, -1, true)
                        y += line
                        for (r in block.rows.indices) {
                            val row = block.rows[r]
                            graphics.text(font, row.text, 0, y, -1, true)
                            y += line
                            val bar = row.bar
                            if (bar != null) {
                                graphics.fill(0, y, BAR_WIDTH, y + BAR_HEIGHT, Role.MUTED.argb(120))
                                graphics.fill(0, y, (BAR_WIDTH * bar.fraction).toInt(), y + BAR_HEIGHT, bar.role.argb())
                                y += BAR_HEIGHT + BAR_GAP
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
