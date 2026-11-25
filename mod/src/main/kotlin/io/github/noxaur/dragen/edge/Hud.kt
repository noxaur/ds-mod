package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.feature.hud.layout.hideinf3.HideInF3
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.Metrics
import io.github.noxaur.dragen.core.hud.BarRow
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.hud.HudBox
import io.github.noxaur.dragen.core.hud.HudDock
import io.github.noxaur.dragen.core.hud.HudGroup
import io.github.noxaur.dragen.core.hud.HudLook
import io.github.noxaur.dragen.core.hud.ItemsRow
import io.github.noxaur.dragen.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap
import kotlin.math.roundToInt

private const val BAR_WIDTH = 80
private const val BAR_GAP = 1
private const val BLOCK_GAP = 4

private const val ITEM = 16
private const val ITEM_PITCH = ITEM + 1
private const val TRAILING_SPACING = 1

private const val ICON_GAP = 4

private const val HOTBAR_INSET = 3

private const val PADDING = 4

/* @keep Vanilla's hotbar is 182 wide in the middle of the screen. The offhand slot takes 29px beside it, and the attack indicator, when the player shows it on the hotbar, 24px on the other side. A docked block keeps the 7px vanilla leaves between the offhand slot and the hotbar. */
private const val HOTBAR_HALF = 91
private const val OFFHAND = 29
private const val INDICATOR_RIGHT = 24
private const val INDICATOR_LEFT = 22
private const val DOCK_GAP = 7

object Hud {
    private lateinit var game: Game
    private lateinit var hideInF3: HideInF3

    fun bind(game: Game, hideInF3: HideInF3) {
        this.game = game
        this.hideInF3 = hideInF3
    }

    private class Row(val text: Component, val bar: BarRow?, val barColor: Int, val stacks: List<ItemStack?>?, val y: Int, val x: Int = 0)

    private class Block(
        val header: Component?,
        val icon: ItemStack?,
        val iconY: Int,
        val rows: List<Row>,
        val look: HudLook,
        val width: Int,
        val height: Int,
        val placeable: Boolean,
        val order: Int,
    ) {
        val across = across(look)
        val down = down(look)
        val pitch = pitch(look)

        fun iconX(row: Row, i: Int) = PADDING + across + row.x + i * pitch

        fun iconY(row: Row) = row.y + down
    }

    private class Cell(val x: Float, val y: Float, val size: Float, val stack: ItemStack)
    private class Group(var place: HudGroup) {
        val blocks = LinkedHashMap<String, Block>()

        var shown = emptyList<Block>()
        var width = 0
        var height = 0
        var x = 0
        var y = 0

        var scale = 1f

        var plates = IntArray(0)
    }

    class Placed(val id: String, val x: Float, val y: Float, val width: Int, val height: Int, val scale: Float, val dock: HudDock) {
        fun contains(px: Double, py: Double) = px >= x && px < x + width * scale && py >= y && py < y + height * scale

        fun box() = HudBox(id, x, y, width * scale, height * scale)
    }

    private val groups = LinkedHashMap<String, Group>()

    /* @keep Building a stack costs tens of microseconds, so each block reuses the stack of an item it showed before. */
    private val stacksByItem = HashMap<String, IdentityHashMap<Item, ItemStack>>()
    private var shown = emptyList<Group>()
    private var placed = emptyList<Placed>()
    private var cells = emptyList<Cell>()


    fun show(id: String, group: HudGroup, block: HudBlock, placeable: Boolean = true, order: Int = 0) {
        val font = Minecraft.getInstance().font
        val line = font.lineHeight + 1
        val hotbar = block.look == HudLook.HOTBAR
        val header = block.header.takeIf { it.spans.isNotEmpty() }?.toComponent()
        var height = if (header != null) line else 0
        var width = header?.let(font::width) ?: 0
        val built = stacksByItem[id].orEmpty()
        val kept = IdentityHashMap<Item, ItemStack>()
        val icon = block.icon?.let { kept.getOrPut(it) { built[it] ?: it.toStack() } }
        val iconY = height
        val indent = if (icon != null) ITEM + ICON_GAP else 0
        val rows = block.rows.map { row ->
            val bar = row as? BarRow
            val itemsRow = row as? ItemsRow
            val items = itemsRow?.items?.map { item -> item?.let { kept.getOrPut(it) { built[it] ?: it.toStack() } } }
            val drawn = Row(row.text.toComponent(), bar, bar?.role?.argb() ?: 0, items, height, indent)
            width = maxOf(
                width,
                indent + when {
                    items == null -> maxOf(font.width(drawn.text), if (bar != null) BAR_WIDTH else 0)
                    hotbar -> Kit.hotbarWidth(items.size)
                    else -> items.size * ITEM_PITCH - 1
                },
            )
            height += when {
                items == null -> line + if (bar != null) Kit.BAR_HEIGHT + BAR_GAP else 0
                hotbar -> Kit.HOTBAR_HEIGHT
                else -> ITEM_PITCH
            }
            drawn
        }
        if (icon != null) height = maxOf(height, iconY + ITEM_PITCH)

        stacksByItem[id] = kept
        groups.values.forEach { if (it.place.id != group.id) it.blocks.remove(id) }
        val target = groups.getOrPut(group.id) { Group(group) }
        target.place = group
        val moved = target.blocks[id]?.order != order
        target.blocks[id] = Block(
            header, icon, iconY, rows, block.look, width, height, placeable, order,
        )
        if (moved) {
            val sorted = target.blocks.entries.sortedBy { it.value.order }
            target.blocks.clear()
            for ((key, value) in sorted) target.blocks[key] = value
        }
        layout()
    }

    fun hide(id: String) {
        stacksByItem.remove(id)
        groups.values.forEach { it.blocks.remove(id) }
        layout()
    }

    val visible: Boolean get() = !Minecraft.getInstance().gui.hud.isHidden && !hiddenInF3()

    private fun hiddenInF3() = hideInF3.feature.runs && Minecraft.getInstance().debugOverlay.showDebugScreen()

    fun at(px: Double, py: Double): Placed? {
        if (hiddenInF3()) return null
        for (i in placed.size - 1 downTo 0) if (placed[i].contains(px, py)) return placed[i]
        return null
    }

    fun itemAt(px: Double, py: Double): ItemStack? {
        if (hiddenInF3()) return null
        for (i in cells.indices.reversed()) {
            val cell = cells[i]
            if (px >= cell.x && px < cell.x + cell.size && py >= cell.y && py < cell.y + cell.size) return cell.stack
        }
        return null
    }

    fun placed(): List<Placed> = if (hiddenInF3()) emptyList() else placed

    fun find(id: String): Placed? {
        for (i in placed.indices) if (placed[i].id == id) return placed[i]
        return null
    }

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
            group.shown = group.blocks.values.toList()
            if (group.shown.isEmpty()) return@forEach
            group.width = group.shown.maxOf { it.width }
            group.height = group.shown.sumOf { it.height } + BLOCK_GAP * (group.shown.size - 1)
            group.plates = plates(group)
            place(group)
            val scale = group.scale
            var y = 0
            for ((id, block) in group.blocks) {
                if (block.placeable) all += Placed(id, group.x.toFloat(), group.y + y * scale, plate(block.width), plate(block.height), scale, group.place.dock)
                block.icon?.let { items += Cell(group.x + PADDING * scale, group.y + (y + PADDING + block.iconY) * scale, ITEM * scale, it) }
                for (row in block.rows) {
                    row.stacks?.forEachIndexed { i, stack ->
                        if (stack != null) {
                            items += Cell(group.x + block.iconX(row, i) * scale, group.y + (y + PADDING + block.iconY(row)) * scale, ITEM * scale, stack)
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

    private fun plates(group: Group): IntArray {
        if (!group.place.background) return IntArray(0)
        val runs = ArrayList<Int>()
        var y = 0
        var start = -1
        for (block in group.shown) {
            val plated = block.look == HudLook.PLATE
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

    private fun place(group: Group) {
        val dock = group.place.dock
        group.scale = group.place.scale
        if (dock == HudDock.FREE) {
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

    private class Spot(val x: Int, val y: Int, val scale: Float)

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
        x = x.coerceIn(-inset.toInt(), (screenWidth - drawnWidth - inset).toInt().coerceAtLeast(0))
        return Spot(x, (client.window.guiScaledHeight - drawnHeight - inset).toInt(), fitted)
    }

    private fun across(look: HudLook) = when (look) {
        HudLook.HOTBAR -> HOTBAR_INSET
        else -> 0
    }

    private fun down(look: HudLook) = when (look) {
        HudLook.HOTBAR -> HOTBAR_INSET
        else -> 0
    }

    private fun pitch(look: HudLook) = when (look) {
        HudLook.HOTBAR -> Kit.HOTBAR_CELL
        else -> ITEM_PITCH
    }

    private fun onScreen(saved: Int, room: Int, size: Int, scale: Float) = saved.coerceIn(0, (room - plate(size) * scale).roundToInt().coerceAtLeast(0))

    private fun plate(size: Int) = size + PADDING * 2 - TRAILING_SPACING

    fun register() {
        // @keep Minecraft draws the frame's tooltip at the end of the screen's own pass, so it is set before that pass.
        ScreenHooks.onEveryLayout<ChatScreen> { screen, _ ->
            val client = Minecraft.getInstance()
            ScreenEvents.beforeExtract(screen).register { _, graphics, x, y, _ ->
                if (client.gui.hud.isHidden) return@register
                itemAt(x.toDouble(), y.toDouble())?.let { graphics.setTooltipForNextFrame(client.font, it, x, y) }
            }
        }
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("dragen", "hud")) { graphics, _ ->
            if (shown.isEmpty()) return@attachElementBefore
            if (hiddenInF3()) return@attachElementBefore
            game.metrics.time("hud.draw", Metrics.FRAME) { draw(graphics) }
        }
    }

    fun draw(graphics: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val font = client.font
        val line = font.lineHeight + 1
        val pose = graphics.pose()
        for (g in shown.indices) {
            val group = shown[g]
            pose.pushMatrix()
            pose.translate(group.x.toFloat(), group.y.toFloat())
            pose.scale(group.scale, group.scale)
            val plates = group.plates
            for (p in 0 until plates.size / 2) Kit.plate(graphics, 0, plates[p * 2], plate(group.width), plate(plates[p * 2 + 1]))
            var top = PADDING
            for (b in group.shown.indices) {
                val block = group.shown[b]
                val hotbar = block.look == HudLook.HOTBAR
                if (block.header != null) graphics.text(font, block.header, PADDING, top, -1, true)
                block.icon?.let {
                    graphics.item(it, PADDING, top + block.iconY)
                    graphics.itemDecorations(font, it, PADDING, top + block.iconY)
                }
                for (r in block.rows.indices) {
                    val row = block.rows[r]
                    val y = top + row.y
                    val stacks = row.stacks
                    if (stacks != null) {
                        if (hotbar) Kit.hotbar(graphics, PADDING, y, stacks.size)
                        val itemY = top + block.iconY(row)
                        for (i in stacks.indices) {
                            val stack = stacks[i] ?: continue
                            val x = block.iconX(row, i)
                            graphics.item(stack, x, itemY)
                            graphics.itemDecorations(font, stack, x, itemY)
                        }
                        continue
                    }
                    graphics.text(font, row.text, PADDING + row.x, y, -1, true)
                    val bar = row.bar
                    if (bar != null) Kit.bar(graphics, PADDING + row.x, y + line, BAR_WIDTH, bar.fraction, row.barColor)
                }
                top += block.height + BLOCK_GAP
            }
            pose.popMatrix()
        }
    }
}
