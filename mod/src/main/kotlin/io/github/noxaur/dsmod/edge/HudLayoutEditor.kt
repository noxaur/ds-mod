package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.hud.HudMap
import io.github.noxaur.dsmod.core.hud.HudScale
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.edge.ui.Editor
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.edge.ui.fit
import io.github.noxaur.dsmod.edge.ui.wrap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * The HUD's layout in the settings: a small copy of the screen with every HUD block drawn where it sits, by the
 * HUD itself, and the hotbar for a landmark. A point on the copy stands for a point of the game's screen, so
 * [HudMover] moves the blocks by the same rules as the HUD editor and the chat screen: a drag lines up, docks and
 * stays on the screen, the wheel resizes and a right press resets. A press gives the map the keys: the arrows nudge
 * the picked block, + and - resize it, Backspace puts it back in the stack, Tab picks the next, and Escape gives the
 * keys back, or puts a dragged block back. Under the map, the picked block's name and size, with Smaller, Bigger
 * and Reset.
 */
class HudLayoutEditor(
    private val panel: Panel<Map<String, DsModConfig.Position>>,
    private val readouts: Readouts,
    private val host: Editor.Host,
) : Editor {
    private var x = 0
    private var top = 0
    private var width = 0
    private var origin = 0
    private var clipTop = 0
    private var clipBottom = 0
    private var map = HudMap(1, 1, 1, 1)
    private var mapX = 0
    private var footY = 0
    private var height = 0
    private val buttons = IntArray(BUTTONS.size * 2)

    private val mover = HudMover(readouts)
    private var selected: String? = null
    private val grabbed get() = mover.held
    private var entered = false
    private var name = ""
    private var nameText: Component = Component.empty()
    private var empty = ""
    private var hint = ""
    private var hintText = emptyList<Component>()
    private var seen: Map<String, DsModConfig.Position> = emptyMap()

    override val capturing get() = entered || grabbed != null

    override fun layout(x: Int, y: Int, width: Int): Int {
        // While the layout shows, a block with nothing to show still shows its header, so it can be placed.
        if (!readouts.editing) readouts.editing = true
        this.x = x
        top = y
        this.width = width
        val window = Minecraft.getInstance().window
        map = HudMap(window.guiScaledWidth, window.guiScaledHeight, width, MAP_TALL)
        mapX = x + (width - map.width) / 2
        empty = fit("Your HUD shows here once you are in a world.", map.width - 8)
        footY = y + map.height + Kit.GAP
        var bx = x + width
        for (i in BUTTONS.indices.reversed()) {
            val w = if (i == RESET) Kit.font.width(BUTTONS[i]) + 16 else Kit.BUTTON_HEIGHT
            bx -= w
            buttons[i * 2] = bx
            buttons[i * 2 + 1] = w
            bx -= Kit.GAP
        }
        height = footY - y + Kit.BUTTON_HEIGHT + Kit.GAP + HINT_LINES * 9 + 2
        refresh()
        return height
    }

    override fun refresh() {
        // A reset in the settings changes the places under the HUD, so it is placed again.
        val now = panel.value
        if (now != seen) {
            seen = now
            if (grabbed == null) readouts.replace()
        }
        if (selected != null && Hud.find(selected!!) == null) selected = null
        val id = selected
        name = id?.let { "${readouts.title(it)} · ${HudScale.label(readouts.scale(it))}" } ?: ""
        nameText = text { body(fit(name, buttons[0] - x - Kit.GAP)) }.toComponent()
        hint = when {
            grabbed != null -> "Shift drags it without lining up. Escape puts it back."
            Hud.placed().isEmpty() -> ""
            id == null -> "Click a block to pick it, and right-click it to stack it again."
            readouts.isPlaced(id) -> "Arrows nudge it, + and - resize it, Backspace stacks it again."
            else -> "It stacks with the others until you drag it."
        }
        hintText = wrap(hint, width, HINT_LINES).map { text { muted(it) }.toComponent() }
    }

    override fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int) {
        this.origin = origin
        this.clipTop = clipTop
        this.clipBottom = clipBottom
    }

    override fun isOver(mouseX: Double, mouseY: Double) =
        mouseY >= clipTop && mouseY < clipBottom && mouseX >= x && mouseX < x + width && mouseY >= origin + top && mouseY < origin + top + height

    private fun overMap(mouseX: Double, mouseY: Double) =
        mouseY >= clipTop && mouseY < clipBottom && mouseX >= mapX && mouseX < mapX + map.width && mouseY >= origin + top && mouseY < origin + top + map.height

    /** The block under a point of the screen showing the settings, by the point of the game's screen it stands for. */
    private fun blockAt(mouseX: Double, mouseY: Double): Hud.Placed? {
        if (!overMap(mouseX, mouseY)) return null
        return Hud.at(map.screen(mouseX - mapX).toDouble(), map.screen(mouseY - (origin + top)).toDouble())
    }

    /** Where the middle of [id]'s block is drawn on the map, on the screen, so a test can grab it. */
    fun pointOf(id: String): Pair<Int, Int>? {
        val block = Hud.find(id) ?: return null
        return (mapX + (block.x + block.width * block.scale / 2) * map.scale).toInt() to (origin + top + (block.y + block.height * block.scale / 2) * map.scale).toInt()
    }

    /** How many screen pixels of the game one pixel of the map stands for. */
    val scale get() = 1 / map.scale

    /** The middle of the button named [name] under the map. */
    fun buttonAt(name: String): Pair<Int, Int> {
        val i = BUTTONS.indexOf(name)
        return buttons[i * 2] + buttons[i * 2 + 1] / 2 to origin + footY + Kit.BUTTON_HEIGHT / 2
    }

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        val my = origin + top
        val mx = mapX
        val font = Kit.font
        Kit.well(g, mx, my, map.width, map.height)
        val hovered = if (grabbed == null) blockAt(mouseX.toDouble(), mouseY.toDouble()) else null
        g.enableScissor(mx + 1, maxOf(my + 1, clipTop), mx + map.width - 1, minOf(my + map.height - 1, clipBottom))
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(mx.toFloat(), my.toFloat())
        pose.scale(map.scale, map.scale)
        // The hotbar shows where the bottom of the screen is, as the player sees it in the world.
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

        val fy = origin + footY
        g.text(font, nameText, x, fy + (Kit.BUTTON_HEIGHT - 8) / 2, -1, true)
        for (i in BUTTONS.indices) {
            val bx = buttons[i * 2]
            val bw = buttons[i * 2 + 1]
            val active = buttonActive(i)
            val over = mouseX >= bx && mouseX < bx + bw && mouseY >= fy && mouseY < fy + Kit.BUTTON_HEIGHT
            Kit.button(g, bx, fy, bw, Kit.BUTTON_HEIGHT, if (!active) Kit.State.OFF else if (over) Kit.State.HOVER else Kit.State.IDLE, BUTTONS[i], font.width(BUTTONS[i]))
            if (over && active) g.requestCursor(CursorTypes.POINTING_HAND)
        }
        for (i in hintText.indices) g.text(font, hintText[i], x, fy + Kit.BUTTON_HEIGHT + Kit.GAP + i * 9, -1, true)
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
        if (!isOver(px, py)) {
            // A press elsewhere takes the keys back and still does its own job.
            leave()
            return false
        }
        entered = true
        val fy = origin + footY
        for (i in BUTTONS.indices) {
            val bx = buttons[i * 2]
            if (px >= bx && px < bx + buttons[i * 2 + 1] && py >= fy && py < fy + Kit.BUTTON_HEIGHT) {
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
            0 -> mover.grab(block, map.screen(px - mapX).toDouble(), map.screen(py - (origin + top)).toDouble())
            1 -> press(RESET)
        }
        refresh()
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!mover.drag(map.screen(event.x - mapX).toDouble(), map.screen(event.y - (origin + top)).toDouble())) return false
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
            // Escape during a drag puts the block back where it was, an arrow nudges it, and the drag keeps every other key.
            if (event.isEscape && mover.cancel() || Hud.find(grabbed!!)?.let { mover.nudge(it, event) } == true) changed()
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
            block != null && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) -> resize(1)
            block != null && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) -> resize(-1)
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
