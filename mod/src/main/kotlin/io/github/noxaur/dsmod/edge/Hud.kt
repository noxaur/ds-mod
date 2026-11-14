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
            val font = Minecraft.getInstance().font
            val line = font.lineHeight + 1
            for (g in shown.indices) {
                val group = shown[g]
                val x = group.place.x
                var y = group.place.y
                if (group.place.background) {
                    graphics.fill(x - PADDING, y - PADDING, x + group.width + PADDING, y + group.height + PADDING - 1, PANEL)
                }
                for (b in group.shown.indices) {
                    val block = group.shown[b]
                    graphics.text(font, block.header, x, y, -1, true)
                    y += line
                    for (r in block.rows.indices) {
                        val row = block.rows[r]
                        graphics.text(font, row.text, x, y, -1, true)
                        y += line
                        val bar = row.bar ?: continue
                        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, Role.MUTED.argb(120))
                        graphics.fill(x, y, x + (BAR_WIDTH * bar.fraction).toInt(), y + BAR_HEIGHT, bar.role.argb())
                        y += BAR_HEIGHT + BAR_GAP
                    }
                    y += BLOCK_GAP
                }
            }
        }
    }
}
