package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TypeAhead
import io.github.noxaur.dsmod.edge.Keys
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStack

private fun plain(label: String) = text { body(label) }.toComponent()

class KitButton private constructor(
    x: Int,
    y: Int,
    width: Int,
    private val label: String,
    private var icon: Icon?,
    action: () -> Unit,
) : Button(x, y, width, Kit.BUTTON_HEIGHT, plain(label), { action() }, { it.get() }) {
    constructor(x: Int, y: Int, label: String, action: () -> Unit, width: Int = Kit.font.width(label) + 16) :
        this(x, y, width, label, null, action)

    constructor(x: Int, y: Int, icon: Icon, label: String, action: () -> Unit) :
        this(x, y, Kit.BUTTON_HEIGHT, label, icon, action)

    private val labelWidth = Kit.font.width(label)
    private var pressed = false

    fun show(icon: Icon, label: String) {
        if (icon == this.icon) return
        this.icon = icon
        message = plain(label)
        setTooltip(Tooltip.create(plain(label)))
    }

    var glass: () -> Boolean = { false }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        pressed = true
        super.onClick(event, doubleClick)
    }

    override fun onRelease(event: MouseButtonEvent) {
        pressed = false
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = Kit.state(active, isHoveredOrFocused, pressed && isHovered)
        val icon = icon
        if (icon != null) {
            Kit.button(graphics, x, y, width, height, state, icon, glass())
        } else {
            Kit.button(graphics, x, y, width, height, state, label, labelWidth, glass())
        }
    }
}

class KitTab(
    x: Int,
    private val panelY: Int,
    private val edge: TabEdge,
    name: String,
    private val item: ItemStack?,
    private val icon: Icon?,
    private val glass: () -> Int = { -1 },
    action: () -> Unit,
) : Button(
    x,
    if (edge == TabEdge.TOP) panelY - Kit.TAB_HEIGHT + 2 else panelY - 2,
    Kit.TAB_WIDTH,
    Kit.TAB_HEIGHT,
    plain(name),
    { action() },
    { it.get() },
) {
    var selected = false

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val alpha = glass()
        val top = if (alpha < 0) Kit.tab(graphics, x, panelY, selected, edge, isHoveredOrFocused) else Kit.glassTab(graphics, x, panelY, selected, alpha, edge, isHoveredOrFocused)
        if (item != null) {
            graphics.item(item, x + (width - 16) / 2, top)
        } else if (icon != null) {
            val t = Kit.theme
            val ink = if (alpha < 0) t.ink else Kit.GLASS_INK
            Kit.icon(graphics, icon, x + (width - icon.width) / 2, top + (16 - icon.height) / 2, ink, alpha >= 0 || t.inkShadow)
        }
    }
}

class KitSlotButton(
    x: Int,
    y: Int,
    name: String,
    private val item: ItemStack,
    private val frame: () -> Boolean?,
    action: () -> Unit,
) : Button(x - 1, y - 1, Kit.SLOT_SIZE, Kit.SLOT_SIZE, plain(name), { action() }, { it.get() }) {
    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == 1

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.slotFrame(graphics, x, y, frame())
        if (isHoveredOrFocused) Kit.slotHighlightBack(graphics, x + 1, y + 1)
        graphics.item(item, x + 1, y + 1)
        if (isHoveredOrFocused) Kit.slotHighlightFront(graphics, x + 1, y + 1)
    }
}

class KitCheckbox(
    x: Int,
    y: Int,
    private val label: String,
    checked: Boolean,
    private val onWell: Boolean = false,
    private val onChange: (Boolean) -> Unit,
) :
    AbstractButton(x, y, Kit.CHECKBOX_SIZE + Kit.GAP + Kit.font.width(label), Kit.CHECKBOX_SIZE, plain(label)) {
    var checked = checked

    override fun onPress(input: InputWithModifiers) {
        checked = !checked
        onChange(checked)
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = Kit.state(active, isHoveredOrFocused, false)
        Kit.checkbox(graphics, x, y, checked, state)
        if (onWell) {
            graphics.text(Kit.font, wellLabel, x + Kit.CHECKBOX_SIZE + Kit.GAP, y + 1, -1, true)
        } else {
            Kit.label(graphics, label, x + Kit.CHECKBOX_SIZE + Kit.GAP, y + 1, if (active) Kit.Ink.NORMAL else Kit.Ink.OFF)
        }
    }

    private val wellLabel = text { body(label) }.toComponent()

    override fun createNarrationMessage(): MutableComponent = CommonComponents.optionStatus(message, checked)

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

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
        stepButton(graphics, x, over, -1, "-", MINUS_WIDTH)
        stepButton(graphics, right - size, over, 1, "+", PLUS_WIDTH)
        Kit.label(graphics, value, x + valueX, y + (size - 8) / 2, if (active) Kit.Ink.NORMAL else Kit.Ink.OFF)
    }

    private fun stepButton(graphics: GuiGraphicsExtractor, buttonX: Int, over: Int, sign: Int, label: String, labelWidth: Int) {
        val size = Kit.BUTTON_HEIGHT
        Kit.button(graphics, buttonX, y, size, size, Kit.state(active, isFocused || over == sign, pressed == sign && over == sign), label, labelWidth)
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

    var selected = -1
        private set

    fun selectNext(): Boolean {
        if (sections.isEmpty()) return false
        selected = (selected + 1) % sections.size
        return true
    }

    private var grab: Grab? = null
    private var base = sections
    private var grabbed = -1
    private var pressedAt = 0
    private var pressedX = 0.0
    private var drew = false

    private fun pixel(value: Int) = x + 1 + value * (width - 2) / max

    private fun value(mouseX: Double) = (((mouseX - x - 1) * max / (width - 2)) + 0.5).toInt().coerceIn(0, max)

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
                // @keep The left button may still be down from a drag, and a set grab would rebuild the track from the list taken at the left press, putting this section back.
                grab = null
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
        when (val mode = grab ?: return) {
            Grab.START, Grab.END -> {
                val start = mode == Grab.START
                val edge = if (start) Sections.Edge.START else Sections.Edge.END
                show(Sections.resize(base, grabbed, edge, at, max), if (start) base[grabbed].to else base[grabbed].from)
            }
            Grab.MIDDLE -> {
                val size = base[grabbed].to - base[grabbed].from
                val from = (base[grabbed].from + at - pressedAt).coerceIn(0, max - size)
                show(Sections.move(base, grabbed, from, max), from)
            }
            Grab.NEW -> {
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

class KitTextField(x: Int, y: Int, width: Int, label: String, value: String, maxLength: Int, onChange: (String) -> Unit) :
    EditBox(Kit.font, x + PAD, y + TOP, width - PAD * 2, Kit.font.lineHeight, plain(label)) {
    private var icon: Icon? = null
    private var lead = 0

    init {
        isBordered = false
        setMaxLength(maxLength)
        this.value = value
        setResponder(onChange)
    }

    fun hint(text: String) = apply { setHint(text { muted(text) }.toComponent()) }

    fun icon(icon: Icon) = apply {
        val room = icon.width + PAD
        this.icon = icon
        x += room - lead
        width -= room - lead
        lead = room
    }

    fun place(x: Int, y: Int) {
        this.x = x + PAD + lead
        this.y = y + TOP
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double) =
        isActive && mouseX >= x - PAD - lead && mouseX < x + width + PAD && mouseY >= y - TOP && mouseY < y - TOP + Kit.BUTTON_HEIGHT

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!isVisible) return
        val over = isMouseOver(mouseX.toDouble(), mouseY.toDouble())
        Kit.field(graphics, x - PAD - lead, y - TOP, width + PAD * 2 + lead, Kit.BUTTON_HEIGHT, Kit.state(active, over || isFocused, false))
        icon?.let { Kit.icon(graphics, it, x - lead, y - TOP + (Kit.BUTTON_HEIGHT - it.height) / 2, Role.MUTED.argb()) }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private companion object {
        const val PAD = 4
        const val TOP = (Kit.BUTTON_HEIGHT - 8) / 2
    }
}

class KitKeysButton(
    x: Int,
    y: Int,
    width: Int,
    private val name: String,
    keys: List<Int>,
    private val max: Int,
    height: Int = Kit.BUTTON_HEIGHT,
    private val onChange: (List<Int>) -> Unit,
) : AbstractButton(x, y, width, height, plain(name)) {
    var keys = keys
        private set(value) {
            field = value
            label = names(value)
            message = plain("$name: $label")
        }
    private var label = ""
    private val recorded = ArrayList<Int>()
    private val down = HashSet<Int>()

    var inOrder = false
        set(value) {
            field = value
            label = names(keys)
        }

    var recording = false
        private set

    var clash: Role? = null

    var prompt: String? = null

    var clicks = true

    var glass: () -> Boolean = { false }

    init {
        this.keys = keys
    }

    override fun onPress(input: InputWithModifiers) {
        if (recording) finish() else start()
    }

    private fun start() {
        recording = true
        recorded.clear()
        down.clear()
    }

    /** @keep Left and right click record only on the button itself, so a click anywhere else still stops recording. A key
     *  used inside a menu takes neither, since one bound to them would take every click the menu needs. */
    fun capture(event: MouseButtonEvent): Boolean {
        if (recording && event.button() <= 1 && !(clicks && isMouseOver(event.x, event.y))) finish()
        return capture(event.button(), mouse = true)
    }

    fun capture(bound: Int, mouse: Boolean = false): Boolean {
        if (!recording) return false
        if (!mouse && bound == ESCAPE) {
            finish()
            return false
        }
        down += bound
        if (inOrder || bound !in recorded) recorded += bound
        if (recorded.size >= max) finish()
        return true
    }

    fun release(bound: Int) {
        if (!recording || !down.remove(bound)) return
        if (!inOrder && down.isEmpty() && recorded.isNotEmpty()) finish()
    }

    fun clear() {
        recording = false
        keys = emptyList()
        onChange(keys)
    }

    private fun finish() {
        recording = false
        if (recorded.isEmpty()) return
        keys = recorded.toList()
        onChange(keys)
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused && recording) finish()
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val prompt = prompt ?: if (max == 1) "Press a key" else "Press keys"
        val text = fit(if (recording) "> ${if (recorded.isEmpty()) prompt else names(recorded)} <" else label, width - 6)
        val state = if (recording) Kit.State.DOWN else Kit.state(active, isHoveredOrFocused, false)
        Kit.button(graphics, x, y, width, height, state, text, Kit.font.width(text), glass())
        clash?.let { Kit.ring(graphics, x - 1, y - 1, width + 2, height + 2, it.argb()) }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    private fun names(keys: List<Int>) = Keys.names(keys, inOrder)

    private companion object {
        const val ESCAPE = 256
    }
}

class KitCycleButton<T>(
    x: Int,
    y: Int,
    width: Int,
    private val name: String,
    private val values: List<T>,
    value: T,
    private val title: (T) -> String,
    private val onChange: (T) -> Unit,
) : AbstractButton(x, y, width, Kit.BUTTON_HEIGHT, plain(name)) {
    var value = value
        set(next) {
            field = next
            label = fit(title(next), width - 6)
            message = plain("$name: ${title(next)}")
        }
    private var label = ""
    private var pressed = false

    init {
        this.value = value
    }

    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == 1

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        pressed = true
        step(if (event.button() == 1 || event.hasShiftDown()) -1 else 1)
    }

    override fun onRelease(event: MouseButtonEvent) {
        pressed = false
    }

    override fun onPress(input: InputWithModifiers) = step(if (input.hasShiftDown()) -1 else 1)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        step(if (scrollY > 0) -1 else 1)
        return true
    }

    private fun step(by: Int) {
        value = values[(values.indexOf(value) + by).mod(values.size)]
        onChange(value)
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val state = Kit.state(active, isHoveredOrFocused, pressed && isHovered)
        Kit.button(graphics, x, y, width, height, state, label, Kit.font.width(label))
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

fun fit(text: String, width: Int): String {
    val font = Kit.font
    if (font.width(text) <= width) return text
    return font.plainSubstrByWidth(text, width - font.width("...")) + "..."
}

fun wrap(text: String, width: Int, max: Int): List<String> {
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(' ')) {
        val next = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && Kit.font.width(next) > width) {
            lines += line
            line = word
        } else {
            line = next
        }
    }
    lines += line
    return if (lines.size <= max) lines else lines.take(max - 1) + fit(lines.drop(max - 1).joinToString(" "), width)
}

class KitMenu(
    mouseX: Int,
    mouseY: Int,
    screenWidth: Int,
    screenHeight: Int,
    private val items: List<Pair<String, () -> Unit>>,
    private val onClose: () -> Unit,
    private val checked: Int = -1,
    words: List<String> = emptyList(),
) : AbstractWidget(0, 0, items.maxOf { Kit.font.width(it.first) } + PAD * 4 + if (checked >= 0) CHECK else 0, items.size * ITEM + PAD * 2, plain("Menu")) {
    private var lit = checked.coerceAtLeast(0)
    private val labels = items.map { it.first }
    private val marked = labels.map { Kit.Marked.of(it, words) }
    private val typeAhead = TypeAhead()
    private val pointer = Pointer()

    init {
        x = (mouseX + 2).let { if (it + width > screenWidth) mouseX - 2 - width else it }.coerceAtLeast(0)
        y = mouseY.coerceAtMost(screenHeight - height).coerceAtLeast(0)
    }

    fun below(anchorX: Int, anchorY: Int, anchorWidth: Int, anchorHeight: Int, screenWidth: Int, screenHeight: Int) = apply {
        width = maxOf(width, anchorWidth)
        x = belowX(anchorX, anchorWidth, width, screenWidth)
        y = belowY(anchorY, anchorHeight, height, screenHeight)
    }

    private fun at(mouseY: Double): Int {
        val index = (mouseY - y - PAD) / ITEM
        if (index < 0) return -1
        return index.toInt().takeIf { it in items.indices } ?: -1
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        val item = at(event.y)
        if (item >= 0) run(item)
    }

    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == 1

    override fun keyPressed(event: KeyEvent): Boolean {
        when {
            event.isEscape -> onClose()
            event.isUp -> lit = (lit - 1).mod(items.size)
            event.isDown -> lit = (lit + 1).mod(items.size)
            event.isSelection || event.isConfirmation -> run(lit)
            else -> return false
        }
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        lit = typeAhead.jump(event, labels, lit) ?: return false
        return true
    }

    private fun run(item: Int) {
        onClose()
        items[item].second()
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        lit = pointer.lit(isHovered, mouseX, mouseY, lit) { at(mouseY.toDouble()) }
        Kit.panel(graphics, x, y, width, height)
        val labelX = x + PAD * 2 + if (checked >= 0) CHECK else 0
        for (i in items.indices) {
            val itemY = y + PAD + i * ITEM
            if (i == lit) Kit.button(graphics, x + PAD, itemY, width - PAD * 2, ITEM, Kit.State.HOVER, "", 0)
            if (i == checked) Kit.icon(graphics, Icon.CHECK, x + PAD * 2, itemY + (ITEM - Icon.CHECK.height) / 2, Kit.theme.ink, Kit.theme.inkShadow)
            Kit.markedLabel(graphics, marked[i], labels[i], labelX, itemY + (ITEM - 8) / 2)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Narration.item(labels[lit], lit, items.size, lit == checked))
    }

    private companion object {
        const val PAD = 3
        const val ITEM = 14
        const val CHECK = Kit.GAP + 7
    }
}

class KitPickMenu(
    private val looks: List<Look>,
    private val labels: List<String>,
    private val selected: Int,
    screenWidth: Int,
    private val onPick: (Int) -> Unit,
    private val onClose: () -> Unit,
    words: List<String> = emptyList(),
) : AbstractWidget(0, 0, 0, 0, plain("Menu")) {
    private val marked = labels.map { Kit.Marked.of(it, words) }
    private val typeAhead = TypeAhead()
    private val pointer = Pointer()
    private val tileWidth = maxOf(looks.maxOf(Looks::width), labels.maxOf(Kit.font::width)) + TILE_PAD * 2
    private val perRow = ((screenWidth - PAD * 2 + GAP) / (tileWidth + GAP)).coerceIn(1, looks.size)
    private val rows = (looks.size + perRow - 1) / perRow
    private var lit = selected.coerceAtLeast(0)

    init {
        width = PAD * 2 + perRow * tileWidth + (perRow - 1) * GAP
        height = PAD * 2 + rows * TILE + (rows - 1) * GAP
    }

    fun below(anchorX: Int, anchorY: Int, anchorWidth: Int, anchorHeight: Int, screenWidth: Int, screenHeight: Int) = apply {
        x = belowX(anchorX, anchorWidth, width, screenWidth)
        y = belowY(anchorY, anchorHeight, height, screenHeight)
    }

    private fun tileX(i: Int) = x + PAD + i % perRow * (tileWidth + GAP)

    private fun tileY(i: Int) = y + PAD + i / perRow * (TILE + GAP)

    private fun at(mouseX: Double, mouseY: Double): Int {
        for (i in looks.indices) if (mouseX >= tileX(i) && mouseX < tileX(i) + tileWidth && mouseY >= tileY(i) && mouseY < tileY(i) + TILE) return i
        return -1
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        val tile = at(event.x, event.y)
        if (tile >= 0) pick(tile)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when {
            event.isEscape -> onClose()
            event.isLeft -> lit = (lit - 1).mod(looks.size)
            event.isRight -> lit = (lit + 1).mod(looks.size)
            event.isUp -> if (lit - perRow >= 0) lit -= perRow
            event.isDown -> if (lit + perRow < looks.size) lit += perRow
            event.isSelection || event.isConfirmation -> pick(lit)
            else -> return false
        }
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        lit = typeAhead.jump(event, labels, lit) ?: return false
        return true
    }

    private fun pick(tile: Int) {
        onClose()
        onPick(tile)
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        lit = pointer.lit(isHovered, mouseX, mouseY, lit) { at(mouseX.toDouble(), mouseY.toDouble()) }
        Kit.panel(graphics, x, y, width, height)
        for (i in looks.indices) {
            val tx = tileX(i)
            val ty = tileY(i)
            Kit.tile(graphics, tx, ty, tileWidth, TILE, i == lit, i == selected)
            Looks.draw(graphics, looks[i], tx + (tileWidth - Looks.width(looks[i])) / 2, ty + TILE_PAD)
            val labelX = tx + (tileWidth - Kit.font.width(labels[i])) / 2
            Kit.markedLabel(graphics, marked[i], labels[i], labelX, ty + TILE_PAD + Looks.HEIGHT + 3)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Narration.item(labels[lit], lit, labels.size, lit == selected))
    }

    private companion object {
        const val PAD = 3
        const val GAP = 2
        const val TILE_PAD = 3
        const val TILE = TILE_PAD + Looks.HEIGHT + 3 + 8 + TILE_PAD
    }
}

private class Pointer {
    private var x = Int.MIN_VALUE
    private var y = Int.MIN_VALUE

    fun moved(mouseX: Int, mouseY: Int) = (mouseX != x || mouseY != y).also {
        x = mouseX
        y = mouseY
    }

    inline fun lit(hovered: Boolean, mouseX: Int, mouseY: Int, lit: Int, at: () -> Int): Int {
        if (!hovered || !moved(mouseX, mouseY)) return lit
        return at().takeIf { it >= 0 } ?: lit
    }
}

private fun TypeAhead.jump(event: CharacterEvent, labels: List<String>, lit: Int): Int? {
    val char = typed(event) ?: return null
    return type(char, Util.getMillis(), labels, lit)
}

private fun belowX(anchorX: Int, anchorWidth: Int, width: Int, screenWidth: Int) = (anchorX + anchorWidth - width).coerceIn(0, (screenWidth - width).coerceAtLeast(0))

private fun belowY(anchorY: Int, anchorHeight: Int, height: Int, screenHeight: Int) =
    if (anchorY + anchorHeight + 1 + height <= screenHeight) anchorY + anchorHeight + 1 else (anchorY - 1 - height).coerceAtLeast(0)

private fun typed(event: CharacterEvent): Char? {
    val code = event.codepoint()
    return if (!event.isAllowedChatCharacter || Character.isWhitespace(code) || Character.isSupplementaryCodePoint(code)) null else code.toChar()
}
