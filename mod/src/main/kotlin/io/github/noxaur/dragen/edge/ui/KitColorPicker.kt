package io.github.noxaur.dragen.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.ui.ColorPick
import io.github.noxaur.dragen.core.ui.ColorPick.Part
import io.github.noxaur.dragen.core.ui.Colors
import io.github.noxaur.dragen.core.ui.RecentColors
import io.github.noxaur.dragen.edge.Keys
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class KitColorPicker(
    private val start: Int,
    private val onChange: (Int) -> Unit,
    private val onClose: () -> Unit,
    private val recent: RecentColors,
) : AbstractWidget(0, 0, PAD * 2 + WIDTH, 0, plain("Color")) {
    internal val pick = ColorPick(start)
    internal val swatches = recent.colors + Colors.PRESETS
    private val recentRows = (recent.colors.size + PER_ROW - 1) / PER_ROW
    private val swatchRows = recentRows + (Colors.PRESETS.size + PER_ROW - 1) / PER_ROW
    private val hex = field("Hex", HEX_WIDTH, HEX_LENGTH) { Colors.parseHex(it) != null }
    private val percent = field("Opacity", WIDTH - HEX_WIDTH - Kit.GAP, PERCENT_LENGTH) { Colors.parseOpacity(it) != null }

    private var part = Part.FIELD
    private var lit = swatches.indexOf(start).coerceAtLeast(0)
    private var drag: Part? = null
    private var before = pick.hold()
    private var keyboard = false

    internal val left get() = x + PAD
    internal val fieldY get() = y + PAD
    internal val hueY get() = fieldY + FIELD_HEIGHT + Kit.GAP
    internal val opacityY get() = hueY + STRIP + Kit.GAP
    internal val rowY get() = opacityY + STRIP + Kit.GAP
    private val swatchesY get() = rowY + Kit.BUTTON_HEIGHT + Kit.GAP

    init {
        height = swatchesY - y + swatchRows * SWATCH + (swatchRows - 1) * SWATCH_GAP + PAD
        sync()
    }

    fun below(anchorX: Int, anchorY: Int, anchorWidth: Int, anchorHeight: Int, screenWidth: Int, screenHeight: Int) = apply {
        x = belowX(anchorX, anchorWidth, width, screenWidth)
        y = belowY(anchorY, anchorHeight, height, screenHeight)
        hex.place(left, rowY)
        percent.place(left + HEX_WIDTH + Kit.GAP, rowY)
    }

    fun leave() {
        commit()
        if (pick.argb != start) recent.add(pick.argb)
    }

    fun type(text: String) {
        open(Part.HEX, selectAll = false)
        hex.value = text
        hex.moveCursorToEnd(false)
    }

    private fun field(label: String, width: Int, length: Int, valid: (String) -> Boolean): KitTextField {
        val field = KitTextField(0, 0, width, label, "", length) {}
        field.setResponder { field.setTextColor(if (valid(it)) Role.VALUE.argb() else Role.BAD.argb()) }
        return field
    }

    private fun text(part: Part) = if (part == Part.HEX) hex else percent

    private fun typing() = if (hex.isFocused) hex else if (percent.isFocused) percent else null

    private fun shown(field: KitTextField) = if (field === hex) Colors.hex(pick.argb) else "${Colors.opacity(pick.argb)}%"

    private fun sync() {
        if (!hex.isFocused) hex.value = shown(hex)
        if (!percent.isFocused) percent.value = shown(percent)
    }

    private fun changed() {
        sync()
        onChange(pick.argb)
    }

    private fun choose(argb: Int) {
        pick.take(argb)
        changed()
    }

    private fun open(part: Part, selectAll: Boolean = true) {
        commit()
        this.part = part
        val field = text(part)
        field.setFocused(true)
        field.moveCursorToEnd(false)
        if (selectAll) field.setHighlightPos(0)
    }

    private fun commit() {
        val field = typing() ?: return
        field.setFocused(false)
        if (field.value == shown(field)) return sync()
        if (field === hex) {
            val next = Colors.parseHex(hex.value, Colors.alpha(pick.argb))
            if (next != null && next != pick.argb) choose(next) else sync()
            return
        }
        val opacity = Colors.parseOpacity(percent.value) ?: return sync()
        pick.opacityAt(Colors.alpha(Colors.withOpacity(pick.argb, opacity)) / 255.0)
        changed()
    }

    private fun cancel() {
        typing()?.setFocused(false)
        sync()
    }

    private fun cycle(by: Int) {
        commit()
        part = Part.entries[Math.floorMod(part.ordinal + by, Part.entries.size)]
        if (part == Part.HEX || part == Part.PERCENT) open(part)
    }

    private fun inside(mx: Double, my: Double, x: Int, y: Int, width: Int, height: Int) = mx >= x && mx < x + width && my >= y && my < y + height

    internal fun swatchX(i: Int) = left + column(i) * (SWATCH + SWATCH_GAP)

    internal fun swatchY(i: Int) = swatchesY + row(i) * (SWATCH + SWATCH_GAP)

    private fun column(i: Int) = if (i < recent.colors.size) i % PER_ROW else (i - recent.colors.size) % PER_ROW

    private fun row(i: Int) = if (i < recent.colors.size) i / PER_ROW else recentRows + (i - recent.colors.size) / PER_ROW

    private fun swatchAt(mx: Double, my: Double): Int {
        for (i in swatches.indices) if (inside(mx, my, swatchX(i), swatchY(i), SWATCH, SWATCH)) return i
        return -1
    }

    private fun partAt(mx: Double, my: Double): Part? = when {
        inside(mx, my, left, fieldY, WIDTH, FIELD_HEIGHT) -> Part.FIELD
        inside(mx, my, left, hueY - 1, WIDTH, STRIP + 2) -> Part.HUE
        inside(mx, my, left, opacityY - 1, WIDTH, STRIP + 2) -> Part.OPACITY
        hex.isMouseOver(mx, my) -> Part.HEX
        percent.isMouseOver(mx, my) -> Part.PERCENT
        swatchAt(mx, my) >= 0 -> Part.SWATCHES
        else -> null
    }

    private fun slide(part: Part, mx: Double, my: Double) {
        val fx = (mx - left) / (WIDTH - 1)
        when (part) {
            Part.FIELD -> pick.field(fx, 1 - (my - fieldY) / (FIELD_HEIGHT - 1))
            Part.HUE -> pick.hueAt(fx)
            Part.OPACITY -> pick.opacityAt(fx)
            else -> return
        }
        changed()
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        keyboard = false
        val at = partAt(event.x, event.y) ?: return commit()
        when (at) {
            Part.HEX, Part.PERCENT -> {
                val field = text(at)
                if (field.isFocused) field.mouseClicked(event, doubleClick) else open(at)
            }
            Part.SWATCHES -> {
                commit()
                part = at
                lit = swatchAt(event.x, event.y)
                choose(swatches[lit])
            }
            else -> {
                commit()
                part = at
                drag = at
                before = pick.hold()
                slide(at, event.x, event.y)
            }
        }
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        drag?.let { slide(it, event.x, event.y) }
    }

    override fun onRelease(event: MouseButtonEvent) {
        drag = null
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0 || typing() != null) return isMouseOver(mouseX, mouseY)
        val by = if (scrollY > 0) 1 else -1
        val shift = Minecraft.getInstance().hasShiftDown()
        val moved = when (partAt(mouseX, mouseY)) {
            Part.FIELD -> pick.nudge(Part.FIELD, 0, -by, shift)
            Part.HUE -> pick.nudge(Part.HUE, by, 0, shift)
            Part.OPACITY, Part.PERCENT -> pick.nudge(Part.OPACITY, by, 0, shift)
            else -> false
        }
        if (moved) changed()
        return isMouseOver(mouseX, mouseY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        keyboard = true
        val shift = Keys.shift(event)
        val tab = event.key() == GLFW.GLFW_KEY_TAB
        if (typing() != null) {
            when {
                event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER -> commit()
                event.isEscape -> cancel()
                tab -> cycle(if (shift) -1 else 1)
                else -> typing()?.keyPressed(event)
            }
            return true
        }
        val dx = if (event.isLeft) -1 else if (event.isRight) 1 else 0
        val dy = if (event.isUp) -1 else if (event.isDown) 1 else 0
        when {
            event.isEscape -> {
                if (drag != null) {
                    drag = null
                    pick.restore(before)
                    changed()
                } else {
                    onClose()
                }
            }
            tab -> cycle(if (shift) -1 else 1)
            event.isSelection || event.isConfirmation -> when (part) {
                Part.SWATCHES -> choose(swatches[lit])
                Part.HEX, Part.PERCENT -> open(part)
                else -> onClose()
            }
            dx != 0 || dy != 0 -> when (part) {
                Part.SWATCHES -> lit = litAfter(dx, dy)
                else -> if (pick.nudge(part, dx, dy, shift)) changed()
            }
            else -> return false
        }
        return true
    }

    private fun litAfter(dx: Int, dy: Int): Int {
        if (dx != 0) return Math.floorMod(lit + dx, swatches.size)
        val row = row(lit) + dy
        val column = column(lit)
        return swatches.indices.firstOrNull { row(it) == row && column(it) == column } ?: swatches.indices.lastOrNull { row(it) == row } ?: lit
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        typing()?.let { return it.charTyped(event) }
        val c = event.codepoint()
        if (c != '#'.code && Character.digit(c, 16) < 0) return false
        type(Character.toString(c))
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val over = if (isHovered && drag == null) partAt(mx, my) else null
        Kit.panel(graphics, x, y, width, height)
        Kit.colorField(graphics, left, fieldY, WIDTH, FIELD_HEIGHT, pick.pureHue)
        Kit.fieldMarker(graphics, left + (pick.saturation * (WIDTH - 1)).roundToInt(), fieldY + ((1 - pick.brightness) * (FIELD_HEIGHT - 1)).roundToInt())
        Kit.hueStrip(graphics, left, hueY, WIDTH, STRIP)
        Kit.stripKnob(graphics, left + (pick.hueFraction * (WIDTH - 1)).roundToInt(), hueY, STRIP)
        Kit.opacityStrip(graphics, left, opacityY, WIDTH, STRIP, pick.argb)
        Kit.stripKnob(graphics, left + (pick.opacityFraction * (WIDTH - 1)).roundToInt(), opacityY, STRIP)
        ring(graphics, Part.FIELD, over, fieldY, FIELD_HEIGHT)
        ring(graphics, Part.HUE, over, hueY, STRIP)
        ring(graphics, Part.OPACITY, over, opacityY, STRIP)
        hex.extractRenderState(graphics, mouseX, mouseY, partialTick)
        percent.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val under = if (over == Part.SWATCHES) swatchAt(mx, my) else -1
        val keys = if (keyboard && part == Part.SWATCHES) lit else -1
        for (i in swatches.indices) {
            val sx = swatchX(i)
            val sy = swatchY(i)
            Kit.colorSwatch(graphics, sx, sy, SWATCH, swatches[i])
            when {
                i == under || i == keys -> Kit.ring(graphics, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, Kit.theme.hoverOutline)
                swatches[i] == pick.argb -> Kit.ring(graphics, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, Kit.theme.accent)
            }
        }
        when (drag ?: over) {
            Part.FIELD -> graphics.requestCursor(CursorTypes.CROSSHAIR)
            Part.HUE, Part.OPACITY -> graphics.requestCursor(CursorTypes.RESIZE_EW)
            Part.HEX, Part.PERCENT -> graphics.requestCursor(CursorTypes.IBEAM)
            Part.SWATCHES -> graphics.requestCursor(CursorTypes.POINTING_HAND)
            null -> Unit
        }
    }

    private fun ring(graphics: GuiGraphicsExtractor, at: Part, over: Part?, top: Int, height: Int) {
        if (over == at || drag == at || keyboard && part == at) Kit.focusRing(graphics, left, top, WIDTH, height, Kit.State.HOVER)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, pick.narration(part, swatches, lit))
        output.add(NarratedElementType.HINT, HINT)
    }

    companion object {
        private const val PAD = 4
        private const val SWATCH = 12
        private const val SWATCH_GAP = 2
        private const val PER_ROW = 8
        internal const val WIDTH = PER_ROW * SWATCH + (PER_ROW - 1) * SWATCH_GAP
        internal const val FIELD_HEIGHT = 80
        private const val STRIP = 8
        internal const val HEX_WIDTH = 64
        private const val HEX_LENGTH = 9
        private const val PERCENT_LENGTH = 4
        private const val HINT = "Tab moves between the parts, and the arrow keys change the one picked."
    }
}
