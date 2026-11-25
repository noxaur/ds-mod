package io.github.noxaur.dragen.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Panel
import io.github.noxaur.dragen.core.hud.HudMap
import io.github.noxaur.dragen.core.hud.HudScale
import io.github.noxaur.dragen.core.text.text
import io.github.noxaur.dragen.core.ui.Narration
import io.github.noxaur.dragen.edge.ui.Box
import io.github.noxaur.dragen.edge.ui.BoxEditor
import io.github.noxaur.dragen.edge.ui.Editor
import io.github.noxaur.dragen.edge.ui.Kit
import io.github.noxaur.dragen.edge.ui.SettingRows
import io.github.noxaur.dragen.edge.ui.fit
import io.github.noxaur.dragen.edge.ui.wrap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class HudLayoutEditor(
    private val panel: Panel<Map<String, DragenConfig.Position>>,
    private val readouts: Readouts,
    private val host: Editor.Host,
) : BoxEditor {
    override val box = Box()

    internal var map = HudMap(1, 1, 1, 1)
    internal var mapX = 0
    private var footY = 0
    private val buttons = EditorButtons(BUTTONS)

    private val mover = HudMover(readouts)
    private var selected: String? = null
    private val grabbed get() = mover.held
    private var entered = false
    private var name = ""
    private var nameText: Component = Component.empty()
    private var empty = ""
    private var hint = ""
    private var hintText = emptyList<Component>()
    private var seen: Map<String, DragenConfig.Position> = emptyMap()

    override val capturing get() = entered || grabbed != null

    override fun layout(x: Int, y: Int, width: Int): Int {
        if (!readouts.editing) readouts.editing = true
        box.place(x, y, width)
        val window = Minecraft.getInstance().window
        map = HudMap(window.guiScaledWidth, window.guiScaledHeight, width, MAP_TALL)
        mapX = x + (width - map.width) / 2
        empty = fit("Your HUD shows here once you are in a world.", map.width - 8)
        footY = y + map.height + Kit.GAP
        var bx = x + width
        for (i in BUTTONS.indices.reversed()) {
            val w = if (i == RESET) Kit.font.width(BUTTONS[i]) + 16 else Kit.BUTTON_HEIGHT
            bx -= w
            buttons.place(i, bx, w)
            bx -= Kit.GAP
        }
        box.height = footY - y + Kit.BUTTON_HEIGHT + Kit.GAP + HINT_LINES * 9 + 2
        refresh()
        return box.height
    }

    override fun refresh() {
        val now = panel.value
        if (now != seen) {
            seen = now
            if (grabbed == null) readouts.replace()
        }
        selected?.let { if (Hud.find(it) == null) selected = null }
        val id = selected
        name = id?.let { "${readouts.title(it)} · ${HudScale.label(readouts.scale(it))}" } ?: ""
        nameText = text { body(fit(name, buttons.x(0) - box.x - Kit.GAP)) }.toComponent()
        hint = when {
            grabbed != null -> "Shift drags it without lining up. Escape puts it back."
            Hud.placed().isEmpty() -> ""
            id == null -> "Click a block to pick it, and right-click it to stack it again."
            readouts.isPlaced(id) -> "Arrows nudge it, + and - resize it, Backspace stacks it again."
            else -> "It stacks with the others until you drag it."
        }
        hintText = wrap(hint, box.width, HINT_LINES).map { text { muted(it) }.toComponent() }
    }

    private fun overMap(mouseX: Double, mouseY: Double) = box.isOver(mouseX, mouseY, mapX, map.width, map.height)

    private fun blockAt(mouseX: Double, mouseY: Double): Hud.Placed? {
        if (!overMap(mouseX, mouseY)) return null
        return Hud.at(map.screen(mouseX - mapX).toDouble(), map.screen(mouseY - (box.origin + box.top)).toDouble())
    }

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        val my = box.origin + box.top
        val mx = mapX
        val font = Kit.font
        Kit.well(g, mx, my, map.width, map.height)
        val hovered = if (grabbed == null) blockAt(mouseX.toDouble(), mouseY.toDouble()) else null
        g.enableScissor(mx + 1, maxOf(my + 1, box.clipTop), mx + map.width - 1, minOf(my + map.height - 1, box.clipBottom))
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(mx.toFloat(), my.toFloat())
        pose.scale(map.scale, map.scale)
        Kit.hotbar(g, (map.screenWidth - Kit.hotbarWidth(HOTBAR_CELLS)) / 2, map.screenHeight - Kit.HOTBAR_HEIGHT, HOTBAR_CELLS)
        Hud.draw(g)
        val placed = Hud.placed()
        for (i in placed.indices) {
            val block = placed[i]
            val color = when {
                block.id == grabbed -> Kit.theme.hoverOutline
                block.id == selected -> Kit.theme.accent
                block === hovered -> Kit.theme.hoverOutline
                else -> continue
            }
            mover.ring(g, block, color, grabbable = false)
        }
        pose.popMatrix()
        g.disableScissor()
        if (placed.isEmpty()) {
            Kit.veil(g, mx + 1, my + 1, map.width - 2, map.height - 2)
            Kit.label(g, empty, mx + (map.width - font.width(empty)) / 2, my + (map.height - 8) / 2, Kit.Ink.MUTED)
        }
        if (entered && keys) Kit.ring(g, mx - 1, my - 1, map.width + 2, map.height + 2, Kit.theme.hoverOutline)

        val fy = box.origin + footY
        g.text(font, nameText, box.x, fy + (Kit.BUTTON_HEIGHT - 8) / 2, -1, true)
        for (i in BUTTONS.indices) {
            buttons.draw(g, i, fy, mouseX.toDouble(), mouseY.toDouble(), buttonActive(i))
        }
        for (i in hintText.indices) g.text(font, hintText[i], box.x, fy + Kit.BUTTON_HEIGHT + Kit.GAP + i * 9, -1, true)
        if (grabbed != null || hovered != null) g.requestCursor(CursorTypes.RESIZE_ALL)
        return null
    }

    private fun buttonActive(i: Int): Boolean {
        val id = selected ?: return false
        return when (i) {
            RESET -> readouts.isPlaced(id)
            else -> true
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val px = event.x
        val py = event.y
        if (!box.isOver(px, py)) {
            leave()
            return false
        }
        entered = true
        val fy = box.origin + footY
        for (i in BUTTONS.indices) {
            if (buttons.at(i, fy, px, py)) {
                if (buttonActive(i)) press(i)
                return true
            }
        }
        val block = blockAt(px, py)
        if (block == null) {
            selected = null
            refresh()
            return true
        }
        select(block.id)
        when (event.button()) {
            0 -> mover.grab(block, map.screen(px - mapX).toDouble(), map.screen(py - (box.origin + box.top)).toDouble())
            1 -> press(RESET)
        }
        refresh()
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!mover.drag(map.screen(event.x - mapX).toDouble(), map.screen(event.y - (box.origin + box.top)).toDouble())) return false
        changed()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!mover.release()) return false
        refresh()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean {
        if (amount == 0.0) return false
        val block = blockAt(mouseX, mouseY) ?: return false
        select(block.id)
        resize(if (amount > 0) 1 else -1)
        return true
    }

    private fun press(button: Int) {
        val id = selected ?: return
        when (button) {
            SMALLER -> resize(-1)
            BIGGER -> resize(1)
            RESET -> {
                readouts.reset(id)
                changed()
            }
        }
    }

    private fun resize(by: Int) {
        val block = selected?.let(Hud::find) ?: return
        mover.scroll(block, by.toDouble())
        changed()
    }

    private fun changed() {
        seen = panel.value
        host.changed()
    }

    private fun select(id: String?) {
        selected = id
        refresh()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (grabbed != null) {
            if (event.isEscape && mover.cancel() || grabbed?.let(Hud::find)?.let { mover.nudge(it, event) } == true) changed()
            return true
        }
        if (!entered) {
            if (!(event.isSelection || event.isConfirmation)) return false
            entered = true
            if (selected == null) select(Hud.placed().firstOrNull()?.id)
            return true
        }
        val key = event.key()
        val block = selected?.let(Hud::find)
        val step = Keys.resizeStep(event)
        when {
            event.isEscape -> leave()
            key == GLFW.GLFW_KEY_TAB -> {
                val placed = Hud.placed()
                if (placed.isNotEmpty()) {
                    val at = placed.indexOfFirst { it.id == selected }
                    select(placed[Math.floorMod(at + if (Keys.shift(event)) -1 else 1, placed.size)].id)
                }
            }
            block != null && mover.nudge(block, event) -> changed()
            block != null && step != 0 -> resize(step)
            block != null && (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) -> press(RESET)
            else -> return true
        }
        refresh()
        return true
    }

    override fun charTyped(event: CharacterEvent) = entered

    override fun leave() {
        entered = false
        if (mover.cancel()) changed()
        refresh()
    }

    override fun narrate(output: NarrationElementOutput): Boolean {
        if (!entered) return false
        output.add(NarratedElementType.TITLE, Narration.join(panel.name, "editor", name.ifEmpty { "no block picked" }))
        if (hint.isNotEmpty()) output.add(NarratedElementType.HINT, hint)
        return true
    }

    override fun close() {
        readouts.editing = false
    }

    private companion object {
        const val MAP_TALL = 180
        const val HOTBAR_CELLS = 9
        const val HINT_LINES = 2
        val BUTTONS = listOf("-", "+", "Reset")
        const val SMALLER = 0
        const val BIGGER = 1
        const val RESET = 2
    }
}
