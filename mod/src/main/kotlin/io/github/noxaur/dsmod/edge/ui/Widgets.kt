package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.MutableComponent

private fun plain(label: String) = text { body(label) }.toComponent()

/** A button with a [label], or a 16x16 one with an [icon] and [label] for narration. It runs [action] when pressed. */
class KitButton private constructor(
    x: Int,
    y: Int,
    width: Int,
    private val label: String,
    private val icon: Icon?,
    action: () -> Unit,
) : Button(x, y, width, Kit.BUTTON_HEIGHT, plain(label), { action() }, { it.get() }) {
    constructor(x: Int, y: Int, label: String, action: () -> Unit, width: Int = Kit.font.width(label) + 16) :
        this(x, y, width, label, null, action)

    constructor(x: Int, y: Int, icon: Icon, label: String, action: () -> Unit) :
        this(x, y, Kit.BUTTON_HEIGHT, label, icon, action)

    private val labelWidth = Kit.font.width(label)
    private var pressed = false

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        pressed = true
        super.onClick(event, doubleClick)
    }

    override fun onRelease(event: MouseButtonEvent) {
        pressed = false
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = Kit.state(active, isHoveredOrFocused, pressed && isHovered)
        if (icon != null) {
            Kit.button(graphics, x, y, width, height, state, icon)
        } else {
            Kit.button(graphics, x, y, width, height, state, label, labelWidth)
        }
    }
}

/** A checkbox with its [label] 4px to the right. [onChange] gets the new value. */
class KitCheckbox(x: Int, y: Int, private val label: String, checked: Boolean, private val onChange: (Boolean) -> Unit) :
    AbstractButton(x, y, Kit.CHECKBOX_SIZE + Kit.GAP + Kit.font.width(label), Kit.CHECKBOX_SIZE, plain(label)) {
    var checked = checked

    override fun onPress(input: InputWithModifiers) {
        checked = !checked
        onChange(checked)
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = Kit.state(active, isHoveredOrFocused, false)
        Kit.checkbox(graphics, x, y, checked, state)
        Kit.label(graphics, label, x + Kit.CHECKBOX_SIZE + Kit.GAP, y + 1, if (active) Kit.Ink.NORMAL else Kit.Ink.OFF)
    }

    override fun createNarrationMessage(): MutableComponent = CommonComponents.optionStatus(message, checked)

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

/**
 * A minus button, a [value] and a plus button, for a number with few useful steps. [onStep] gets -1 or 1, and
 * the screen sets [value] after. The arrow keys step it while it has focus.
 */
class KitStepper(x: Int, y: Int, private val name: String, valueWidth: Int, private val onStep: (Int) -> Unit) :
    AbstractWidget(x, y, Kit.BUTTON_HEIGHT * 2 + Kit.GAP * 2 + valueWidth, Kit.BUTTON_HEIGHT, plain(name)) {
    private var valueX = 0
    private var pressed = 0

    var value = ""
        set(text) {
            if (text == field) return
            field = text
            valueX = (width - Kit.font.width(text)) / 2
            message = plain("$name: $text")
        }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        pressed = side(event.x)
        if (pressed != 0) onStep(pressed)
    }

    override fun onRelease(event: MouseButtonEvent) {
        pressed = 0
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!active) return false
        val by = if (event.isLeft) -1 else if (event.isRight) 1 else return false
        onStep(by)
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val over = if (isHovered) side(mouseX.toDouble()) else 0
        val size = Kit.BUTTON_HEIGHT
        Kit.button(graphics, x, y, size, size, Kit.state(active, isFocused || over == -1, pressed == -1 && over == -1), "-", MINUS_WIDTH)
        Kit.button(graphics, right - size, y, size, size, Kit.state(active, isFocused || over == 1, pressed == 1 && over == 1), "+", PLUS_WIDTH)
        Kit.label(graphics, value, x + valueX, y + (size - 8) / 2, if (active) Kit.Ink.NORMAL else Kit.Ink.OFF)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, createNarrationMessage())
    }

    private fun side(mouseX: Double) = when {
        mouseX < x + Kit.BUTTON_HEIGHT -> -1
        mouseX >= right - Kit.BUTTON_HEIGHT -> 1
        else -> 0
    }

    private companion object {
        val MINUS_WIDTH = Kit.font.width("-")
        val PLUS_WIDTH = Kit.font.width("+")
    }
}

/**
 * A track from 0 to [max] with any number of switched-on [sections], and a tick at each of [ticks].
 *
 * On the empty track, a click adds a section around itself, and a press and drag draws one from where the button
 * went down to where it comes up. A drag on a section's end resizes it, a drag on its middle slides it, and a
 * right click removes it. Sections that meet become one, and come apart again if the drag backs off before the
 * button is released. The cursor shows which of these a press would do. With focus, the arrow keys slide the
 * section last touched and Delete removes it. The screen reads [sections] and [selected] after [onChange].
 */
class KitRangeSlider(
    x: Int,
    y: Int,
    width: Int,
    name: String,
    private val max: Int,
    private val ticks: List<Int>,
    sections: List<Section>,
    private val onChange: () -> Unit,
) : AbstractWidget(x, y, width, Kit.BUTTON_HEIGHT, plain(name)) {
    private enum class Grab { START, END, MIDDLE, NEW }

    var sections = sections
        set(value) {
            field = value
            selected = selected.coerceAtMost(value.size - 1)
        }

    /** The index of the section last touched, or -1. */
    var selected = -1
        private set

    // A drag is worked out from the sections as the press found them, so a merge on the way can come undone.
    private var grab: Grab? = null
    private var base = sections
    private var grabbed = -1
    private var pressedAt = 0
    private var pressedX = 0.0
    private var drew = false

    private fun pixel(value: Int) = x + 1 + value * (width - 2) / max

    private fun value(mouseX: Double) = (((mouseX - x - 1) * max / (width - 2)) + 0.5).toInt().coerceIn(0, max)

    /** The section under the mouse and the part of it, where the last [EDGE_GRIP] pixels of each end resize. */
    private fun hit(mouseX: Double): Pair<Int, Grab>? {
        for (i in sections.indices) {
            val from = pixel(sections[i].from)
            val to = pixel(sections[i].to)
            if (mouseX < from || mouseX >= to) continue
            return i to when {
                mouseX < from + EDGE_GRIP -> Grab.START
                mouseX >= to - EDGE_GRIP -> Grab.END
                else -> Grab.MIDDLE
            }
        }
        return null
    }

    /** Shows [next] with the section that holds [anchor] selected. */
    private fun show(next: List<Section>, anchor: Int) {
        val changed = next != sections
        sections = next
        val now = Sections.at(next, anchor)
        if (!changed && now == selected) return
        selected = now
        onChange()
    }

    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == 1

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        val hit = hit(event.x)
        if (event.button() == 1) {
            if (hit != null) {
                selected = -1
                sections = Sections.remove(sections, hit.first)
                onChange()
            }
            return
        }
        base = sections
        pressedAt = value(event.x)
        pressedX = event.x
        drew = false
        grab = hit?.second ?: Grab.NEW
        grabbed = hit?.first ?: -1
        if (hit != null) show(sections, sections[hit.first].from)
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        val at = value(event.x)
        when (grab ?: return) {
            Grab.START -> show(Sections.resize(base, grabbed, Sections.Edge.START, at, max), base[grabbed].to)
            Grab.END -> show(Sections.resize(base, grabbed, Sections.Edge.END, at, max), base[grabbed].from)
            Grab.MIDDLE -> {
                val size = base[grabbed].to - base[grabbed].from
                val from = (base[grabbed].from + at - pressedAt).coerceIn(0, max - size)
                show(Sections.move(base, grabbed, from, max), from)
            }
            Grab.NEW -> {
                // A press that has not moved yet is still a click.
                if (!drew && Math.abs(event.x - pressedX) < DRAG_START) return
                drew = true
                show(Sections.draw(base, pressedAt, at, max), pressedAt)
            }
        }
    }

    override fun onRelease(event: MouseButtonEvent) {
        if (grab == Grab.NEW && !drew) show(Sections.add(base, pressedAt, max / 8, max), pressedAt)
        grab = null
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (selected < 0) return false
        val section = sections[selected]
        val by = if (event.hasShiftDown()) 10 else 1
        when {
            event.isLeft -> (section.from - by).coerceAtLeast(0).let { show(Sections.move(sections, selected, it, max), it) }
            event.isRight -> (section.from + by).coerceAtMost(max - (section.to - section.from)).let { show(Sections.move(sections, selected, it, max), it) }
            event.key() == KEY_DELETE || event.key() == KEY_BACKSPACE -> {
                val removed = selected
                selected = -1
                sections = Sections.remove(sections, removed)
                onChange()
            }
            else -> return false
        }
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.well(graphics, x, y, width, height)
        for (i in ticks.indices) Kit.tick(graphics, pixel(ticks[i]), y + 1, height - 2)
        val over = if (isHovered) hit(mouseX.toDouble()) else null
        for (i in sections.indices) {
            val from = pixel(sections[i].from)
            val state = Kit.state(active, i == over?.first || isFocused && i == selected, grab != null && i == selected)
            Kit.span(graphics, from, y + 1, pixel(sections[i].to) - from, height - 2, state)
        }
        if (!active || !isHovered && grab == null) return
        graphics.requestCursor(
            when (grab ?: over?.second ?: Grab.NEW) {
                Grab.START, Grab.END -> CursorTypes.RESIZE_EW
                Grab.MIDDLE -> CursorTypes.RESIZE_ALL
                Grab.NEW -> CursorTypes.CROSSHAIR
            },
        )
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, createNarrationMessage())
    }

    private companion object {
        const val EDGE_GRIP = 3
        const val DRAG_START = 2.0
        const val KEY_DELETE = 261
        const val KEY_BACKSPACE = 259
    }
}
