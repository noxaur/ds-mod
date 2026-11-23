package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.Action
import io.github.noxaur.dsmod.core.config.Choice
import io.github.noxaur.dsmod.core.config.Color
import io.github.noxaur.dsmod.core.config.Fold
import io.github.noxaur.dsmod.core.config.Items
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.config.Number
import io.github.noxaur.dsmod.core.config.Order
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.Preview
import io.github.noxaur.dsmod.core.config.Pick
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.Span
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.RecentColors
import io.github.noxaur.dsmod.core.ui.SettingsSearch
import io.github.noxaur.dsmod.edge.Keys
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

class SettingRows(
    val opened: MutableSet<String>,
    private val relayout: () -> Unit,
    private val scrollTo: (top: Int, bottom: Int) -> Unit,
    private val editors: (Setting, Editor.Host) -> Editor = Editors::of,
    private val stars: Stars? = null,
    private val recent: RecentColors,
) {
    class Tip(val lines: List<FormattedCharSequence>)

    class Stars(val on: (String) -> Boolean, val toggle: (String) -> Unit, val key: () -> Int)

    internal class Line(val setting: Setting, val x: Int, val y: Int, val width: Int, val heading: Boolean, val rule: Boolean, val entry: Int = -1) {
        var height = 0
        var nameX = 0
        var name: Component = Component.empty()
        var description: List<Component> = emptyList()
        var cut = false

        var cx = 0
        var cy = 0
        var cw = 0
        var ch = 0

        var ex = 0
        var ew = 0

        var open = false

        var on = false
        var locked = false

        var room = 0
        var guideTo = 0
        var changed = false
        var dimmed = false
        var label = ""

        var marked: Kit.Marked? = null
        var muted = ""
        var mutedX = 0
        var value: Component = Component.empty()
        var valueX = 0
        var look: Look? = null
        var fillFrom = 0
        var fillTo = 0
        var zero = -1
        var slider: KitRangeSlider? = null
        var editor: Editor? = null
        var tip: Tip? = null
        var resetTip: Tip? = null

        var starX = -1
        var starY = 0
        var starred = false
        var starTip: Tip? = null

        val bottom get() = y + height

        val arrow get() = if (open) Icon.DOWN else Icon.RIGHT

        val key get() = if (entry < 0) setting.id else "${setting.id}#$entry"
    }

    internal val lines = ArrayList<Line>()
    private val font get() = Kit.font

    internal val made = HashMap<String, Editor>()
    private val conditional = HashMap<Setting, Boolean>()

    private val host = Editor.Host({ relayout() }, { top, bottom -> scrollTo(top, bottom) }, { refresh() }, { words })

    private var pressed: Editor? = null
    internal var origin = 0
    private var clipTop = 0
    private var clipBottom = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private var focus = -1
    private var focusId: String? = null

    var keys = false
        set(value) {
            field = value
            if (value && focus < 0 && lines.isNotEmpty()) focus = 0
            if (!value) {
                shownFocus = false
                for (line in lines) line.editor?.leave()
            }
        }

    private var shownFocus = false

    fun showFocus() {
        shownFocus = true
        spoken = focus
    }

    private sealed interface Mode {
        object Idle : Mode

        class Menu(val widget: AbstractWidget, val leave: () -> Unit = {}) : Mode

        class Field(val field: KitTextField, val line: Line) : Mode

        class Recording(val button: KitKeysButton, val line: Line) : Mode

        sealed interface Drag : Mode {
            val line: Line
        }

        class Slide(override val line: Line) : Drag

        class Reorder(override var line: Line, val pressY: Double, val toggleOnRelease: Boolean, val before: Pair<List<Any?>, List<Any?>>) : Drag {
            var moved = false
        }
    }

    private var mode: Mode = Mode.Idle

    private val dragging get() = (mode as? Mode.Drag)?.line

    internal val menu get() = (mode as? Mode.Menu)?.widget

    var found: MutableSet<String> = HashSet()

    val capturing get() = when (mode) {
        Mode.Idle, is Mode.Slide -> capturer() != null
        else -> true
    }

    val recordingKeys get() = mode is Mode.Recording || capturer()?.recording == true

    private fun capturer(): Editor? {
        for (line in lines) if (line.editor?.capturing == true) return line.editor
        return null
    }

    var words: List<String> = emptyList()

    private var spoken = -1
    private var spokenId: String? = null
    private var hoverId: String? = null

    private var swallow = false

    val narration = Narrated()

    inner class Narrated : GuiEventListener, NarratableEntry {
        private var focused = false

        override fun setFocused(focused: Boolean) {
            this.focused = focused
        }

        override fun isFocused() = focused

        override fun narrationPriority() = when {
            capturing || keys -> NarratableEntry.NarrationPriority.FOCUSED
            hoverId != null -> NarratableEntry.NarrationPriority.HOVERED
            else -> NarratableEntry.NarrationPriority.NONE
        }

        override fun updateNarration(output: NarrationElementOutput) = narrate(output)
    }

    fun view(originY: Int, top: Int, bottom: Int, width: Int, height: Int) {
        if (originY != origin) {
            when (mode) {
                is Mode.Menu -> closeMenu()
                is Mode.Field -> commit()
                is Mode.Recording -> finishRecording()
                else -> Unit
            }
        }
        origin = originY
        clipTop = top
        clipBottom = bottom
        screenWidth = width
        screenHeight = height
        for (line in lines) {
            line.slider?.y = origin + line.y + line.height - SPAN_BELOW - Kit.BUTTON_HEIGHT
            line.editor?.view(origin, top, bottom, width, height)
        }
    }

    fun begin() {
        // @keep A resize rebuilds the rows without a click outside the field, so the typed number is taken first. Escape closes the field without taking it, and that path does not come through here.
        if (mode is Mode.Field) commit()
        closeMenu()
        focusId = lines.getOrNull(focus)?.key
        spokenId = lines.getOrNull(spoken)?.key
        lines.clear()
        conditional.clear()
        mode = Mode.Idle
    }

    fun add(rows: List<Setting>, x: Int, y: Int, width: Int): Int {
        var at = y
        var first = true
        for (setting in rows) {
            if (setting.shownWhile != null) conditional[setting] = setting.visible
            if (!setting.visible) continue
            if (!first) at += 1
            val line = Line(setting, x, at, width, false, !first)
            first = false
            measure(line)
            lines += line
            at += line.height
            if ((setting.children.isNotEmpty() || setting is Order<*>) && (setting.id in opened || setting.id in found)) {
                line.open = true
                if (setting is Order<*>) {
                    for (entry in setting.entries.indices) {
                        val e = Line(setting, x + INDENT, at, width - INDENT, false, false, entry)
                        measure(e)
                        lines += e
                        at += e.height
                    }
                } else {
                    at = add(setting.children, x + INDENT, at, width - INDENT)
                }
                line.guideTo = at
                at += OPEN_BELOW
            }
        }
        return at
    }

    fun heading(switch: Switch, x: Int, y: Int, nameX: Int) {
        val line = Line(switch, x, y, Kit.SWITCH_WIDTH, true, false)
        line.height = Kit.SWITCH_HEIGHT
        line.nameX = nameX
        line.cx = x
        line.cy = y
        line.cw = Kit.SWITCH_WIDTH
        line.ch = Kit.SWITCH_HEIGHT
        if (stars != null) {
            line.starX = x + Kit.SWITCH_WIDTH + Kit.GAP
            line.starY = y + (Kit.SWITCH_HEIGHT - Icon.STAR.height) / 2
        }
        lines += line
    }

    fun end() {
        focus = focusId?.let { id -> lines.indexOfFirst { it.key == id } } ?: -1
        spoken = spokenId?.let { id -> lines.indexOfFirst { it.key == id } } ?: -1
        if (keys && focus < 0 && lines.isNotEmpty()) focus = 0
        refresh()
    }

    private fun measure(line: Line) {
        val s = line.setting
        val inner = line.width - PAD * 2
        var reserve = 0
        if (line.entry >= 0) {
            line.height = ENTRY_HEIGHT
            line.nameX = line.x + PAD + GRIP_ROOM
            line.cw = Kit.SWITCH_WIDTH
            line.ch = Kit.SWITCH_HEIGHT
            line.cx = line.x + line.width - PAD - line.cw
            line.cy = line.y + (ENTRY_HEIGHT - line.ch) / 2
            return
        }
        when (s) {
            is Switch -> {
                line.cw = Kit.SWITCH_WIDTH
                line.ch = Kit.SWITCH_HEIGHT
                if (s.children.isNotEmpty()) {
                    line.ew = SETTINGS
                    reserve = SUMMARY + Kit.GAP + Kit.GAP + SETTINGS
                }
            }
            is Number -> {
                line.ew = maxOf(font.width(s.format(s.min) + s.unit), font.width(s.format(s.max) + s.unit)) + FIELD_PAD * 2
                line.cw = TRACK
                line.ch = Kit.SWITCH_HEIGHT
                reserve = FIELD_GAP + line.ew
            }
            is Pick<*> -> {
                val lead = Looks.width(s.lookAt())
                line.cw = Kit.dropdownWidth(s.labels.maxOf(font::width), lead)
                line.ch = PICK_HEIGHT
            }
            is Choice<*> -> {
                line.cw = maxOf(DROPDOWN, Kit.dropdownWidth(s.labels.maxOf(font::width)))
                line.ch = Kit.BUTTON_HEIGHT
            }
            is Color -> {
                line.cw = Kit.dropdownWidth(font.width(COLOR_LABEL), Kit.COLOR_SWATCH)
                line.ch = Kit.BUTTON_HEIGHT
            }
            is KeyBind -> {
                line.cw = KEYS
                line.ch = Kit.BUTTON_HEIGHT
            }
            is Span -> {
                line.cw = SPAN_SUMMARY
                line.ch = 8
            }
            is Action -> {
                line.cw = font.width(s.button) + 16
                line.ch = Kit.BUTTON_HEIGHT
            }
            is Order<*> -> {
                line.cw = SETTINGS
                line.ch = SETTINGS
                line.room = minOf(ORDER_SUMMARY, inner / 3)
                reserve = line.room + Kit.GAP
            }
            is Fold, is Items<*>, is Panel<*>, is Preview -> Unit
        }
        if (s is Fold) {
            line.height = FOLD_HEIGHT + 4
            line.nameX = line.x + 2 + FOLD_PAD + Icon.RIGHT.width + Kit.GAP + 4
            line.name = text { body(s.name) }.toComponent()
            line.marked = Kit.Marked.of(s.name, words)
            line.cut = true
            if (stars != null) {
                line.starX = line.nameX + font.width(s.name) + STAR_GAP
                line.starY = line.y + 2 + (FOLD_HEIGHT - Icon.STAR.height) / 2
            }
            return
        }
        val textWidth = inner - line.cw - reserve - CONTROL_GAP
        val name = fit(s.name, if (stars == null) textWidth else textWidth - STAR_ROOM)
        val all = wrap(s.description, textWidth, Int.MAX_VALUE)
        val shown = wrap(s.description, textWidth, DESCRIPTION_LINES)
        line.nameX = line.x + PAD
        if (stars != null) {
            line.starX = line.nameX + font.width(name) + STAR_GAP
            line.starY = line.y + NAME_TOP
        }
        line.name = marked(name, muted = false)
        line.description = shown.map { marked(it, muted = true) }
        line.cut = name != s.name || all.size > DESCRIPTION_LINES
        val textHeight = NAME_TOP + 10 + LINE * shown.size + 3
        line.height = maxOf(MIN_HEIGHT, textHeight, line.ch + PAD_Y * 2)
        val right = line.x + line.width - PAD
        if (s is Items<*> || s is Panel<*> || s is Preview) {
            val editor = made.getOrPut(s.id) { editors(s, host) }
            line.editor = editor
            line.cx = line.x + PAD
            line.cy = line.y + textHeight + 1
            line.cw = inner
            line.ch = editor.layout(line.cx, line.cy, inner)
            line.height = textHeight + 1 + line.ch + EDITOR_BELOW
            return
        }
        if (s is Span) {
            line.height = textHeight + 1 + Kit.BUTTON_HEIGHT + SPAN_BELOW
            line.cx = right - line.cw
            line.cy = line.y + NAME_TOP
            val slider = KitRangeSlider(line.x + PAD, 0, inner, s.name, s.max, s.ticks, s.value) {}
            line.slider = slider
            return
        }
        line.cy = line.y + (line.height - line.ch) / 2
        when {
            s is Switch && line.ew > 0 -> {
                line.ex = right - SETTINGS
                line.cx = line.ex - Kit.GAP - line.cw
            }
            s is Number -> {
                line.ex = right - line.ew
                line.cx = line.ex - FIELD_GAP - line.cw
            }
            else -> line.cx = right - line.cw
        }
    }

    private fun marked(text: String, muted: Boolean) = marked(text, words, muted)

    fun refresh() {
        for (line in lines) {
            val s = line.setting
            line.changed = line.entry < 0 && s.changed
            line.starred = line.starX >= 0 && stars?.on(s.id) == true
            line.starTip = null
            line.dimmed = !line.heading && s.dimmed
            if (s is Order<*> && line.entry >= 0) {
                line.label = s.label(s.entries[line.entry])
                line.name = marked(fit(line.label, line.cx - CONTROL_GAP - line.nameX), muted = false)
                line.on = s.shown(line.entry)
                line.locked = !s.canSwitch(line.entry)
                continue
            }
            when (s) {
                is Switch -> {
                    summarize(line, if (line.ew > 0 && !line.open) fit(s.closed(Keys::name), SUMMARY) else "")
                }
                is Number -> {
                    val label = s.format() + s.unit
                    line.value = text { value(label) }.toComponent()
                    line.valueX = line.ex + line.ew - FIELD_PAD - font.width(label)
                    fun px(v: Float) = line.cx + 1 + Math.round(s.fraction(v) * (TRACK - 2))
                    line.fillFrom = px(minOf(s.zero, s.value))
                    line.fillTo = px(maxOf(s.zero, s.value))
                    line.zero = if (s.zero != s.min) px(s.zero) else -1
                }
                is Pick<*> -> {
                    line.look = s.lookAt()
                    line.label = fit(s.shown, Kit.dropdownLabel(line.cw, Looks.width(s.lookAt())))
                    line.marked = Kit.Marked.of(line.label, words)
                }
                is Choice<*> -> {
                    line.label = fit(s.shown, Kit.dropdownLabel(line.cw))
                    line.marked = Kit.Marked.of(line.label, words)
                }
                is Color -> line.label = s.summary(Keys::name).orEmpty()
                is KeyBind -> line.label = fit(Keys.name(s.value), KEYS - 8)
                is Span -> {
                    val summary = s.summary(Keys::name).orEmpty()
                    line.value = text { value(summary) }.toComponent()
                    line.valueX = line.cx + line.cw - font.width(summary)
                    line.slider?.let { if (dragging !== line && it.sections != s.value) it.sections = s.value }
                }
                is Fold -> {
                    val room = line.width - 4 - FOLD_PAD * 2 - (line.nameX - line.x) - font.width(s.name) - Kit.GAP * 2
                    line.muted = if (line.open) "" else fit(s.summary(Keys::name), room)
                    line.mutedX = line.x + line.width - 2 - FOLD_PAD - font.width(line.muted)
                }
                is Action -> line.label = s.button
                is Items<*>, is Panel<*>, is Preview -> line.editor?.refresh()
                is Order<*> -> {
                    summarize(line, if (line.open) "" else fit(s.summary(Keys::name).orEmpty(), line.room))
                }
            }
        }
    }

    private fun summarize(line: Line, summary: String) {
        line.muted = summary
        line.mutedX = line.cx - Kit.GAP - font.width(summary)
    }

    private fun changed() {
        if (conditional.any { (setting, shown) -> setting.visible != shown }) relayout() else refresh()
    }

    internal fun top(line: Line) = origin + line.y

    private fun over(line: Line, mouseX: Double, mouseY: Double): Boolean {
        val top = top(line)
        if (mouseY < clipTop || mouseY >= clipBottom) return false
        return mouseX >= line.x && mouseX < line.x + line.width && mouseY >= top && mouseY < top + line.height
    }

    private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int) = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private fun overControl(line: Line, mouseX: Double, mouseY: Double) = inside(mouseX, mouseY, line.cx, origin + line.cy, line.cw, line.ch)

    private fun overExtra(line: Line, mouseX: Double, mouseY: Double) = line.ew > 0 && inside(mouseX, mouseY, line.ex, origin + line.y + (line.height - extraHeight(line)) / 2, line.ew, extraHeight(line))

    private fun extraHeight(line: Line) = if (line.setting is Number) Kit.BUTTON_HEIGHT else SETTINGS

    private fun overGrip(line: Line, mouseX: Double) = line.entry >= 0 && mouseX < line.nameX - 1

    internal fun markerY(line: Line) = top(line) + if (line.setting is Fold) 2 + (FOLD_HEIGHT - 3) / 2 else if (line.heading) 2 + 2 else NAME_TOP + 2

    private fun overMarker(line: Line, mouseX: Double, mouseY: Double) = line.changed && inside(mouseX, mouseY, line.nameX - 6, markerY(line) - 3, 6, 9)

    private fun overStar(line: Line, mouseX: Double, mouseY: Double) =
        line.starX >= 0 && inside(mouseX, mouseY, line.starX - 1, origin + line.starY - 1, Icon.STAR.width + 2, Icon.STAR.height + 2)

    private fun lineAt(mouseX: Double, mouseY: Double): Int {
        for (i in lines.indices) {
            val line = lines[i]
            if (line.heading) {
                if (inside(mouseX, mouseY, line.cx - 2, top(line) - 1, line.cw + 4, line.ch + 2) || overMarker(line, mouseX, mouseY) || overStar(line, mouseX, mouseY)) return i
            } else if (over(line, mouseX, mouseY)) {
                return i
            }
        }
        return -1
    }

    fun reveal(id: String) {
        lines.firstOrNull { it.setting.id == id }?.let { reveal(it) }
    }

    private fun reveal(line: Line) = scrollTo(line.y - FOCUS_MARGIN, line.bottom + FOCUS_MARGIN)

    fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float): Tip? {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val menu = menu
        val free = menu == null || !menu.isMouseOver(mx, my)
        val hovered = if (free && dragging == null) lineAt(mx, my) else -1
        val hoverLine = lines.getOrNull(hovered)?.key
        if (hoverLine != hoverId) {
            hoverId = hoverLine
            if (hovered >= 0) spoken = hovered
        }
        var tip: Tip? = null
        for (i in lines.indices) {
            val line = lines[i]
            val top = top(line)
            if (top >= clipBottom) break
            if (top + line.height <= clipTop) continue
            val hot = i == hovered || dragging === line
            val lit = hot || shownFocus && keys && i == focus
            face(g, line, top, i == hovered, lit && line.editor == null, menu != null && i == focus, mx, my)
            val editorTip = line.editor?.draw(g, mouseX, mouseY, keys && i == focus)
            if (line.changed) Kit.marker(g, line.nameX - 5, markerY(line), if (i == hovered && overMarker(line, mx, my)) Kit.State.HOVER else Kit.State.IDLE)
            if (line.starX >= 0 && (line.starred || lit || i == hovered)) {
                val onStar = i == hovered && overStar(line, mx, my)
                val color = when {
                    line.starred -> Role.ACTION.argb()
                    onStar -> Role.BODY.argb()
                    else -> Role.MUTED.argb()
                }
                Kit.icon(g, Icon.STAR, line.starX, origin + line.starY, color)
                if (onStar) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            if (line.dimmed) Kit.dim(g, line.x, top, line.width, line.height)
            if (i == hovered) tip = editorTip ?: if (line.editor?.isOver(mx, my) == true) null else tip(line, mx, my)
        }
        return tip
    }

    fun drawMenu(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        menu?.extractRenderState(g, mouseX, mouseY, partialTick)
        for (line in lines) line.editor?.drawOver(g, mouseX, mouseY)
    }

    private fun face(g: GuiGraphicsExtractor, line: Line, top: Int, hovered: Boolean, lit: Boolean, menuOpen: Boolean, mx: Double, my: Double) {
        val s = line.setting
        if (line.heading) {
            Kit.switch(g, line.cx, top, (s as Switch).value, if (lit) Kit.State.HOVER else Kit.State.IDLE)
            if (hovered) g.requestCursor(CursorTypes.POINTING_HAND)
            return
        }
        if (line.rule) Kit.rule(g, line.x, top - 1, line.width, onWell = true)
        if (line.open) Kit.guide(g, line.x + GUIDE, top + line.height, line.guideTo - line.bottom)
        if (line.entry >= 0) {
            val held = dragging === line
            if (lit || held) Kit.wellRow(g, line.x, top, line.width, line.height)
            if (held) Kit.ring(g, line.x, top, line.width, line.height, Kit.theme.hoverOutline)
            Kit.grip(g, line.x + PAD, top + (line.height - Kit.GRIP_HEIGHT) / 2, lit || held)
            g.text(font, line.name, line.nameX, top + (line.height - 8) / 2, -1, true)
            val state = when {
                line.locked -> Kit.State.OFF
                lit && !(hovered && overGrip(line, mx)) -> Kit.State.HOVER
                else -> Kit.State.IDLE
            }
            Kit.switch(g, line.cx, origin + line.cy, line.on, state)
            if (held || hovered && overGrip(line, mx)) g.requestCursor(CursorTypes.RESIZE_NS) else if (hovered && !line.locked) g.requestCursor(CursorTypes.POINTING_HAND)
            return
        }
        if (s is Fold) {
            val state = if (lit) Kit.State.HOVER else Kit.State.IDLE
            Kit.button(g, line.x + 2, top + 2, line.width - 4, FOLD_HEIGHT, state, "", 0)
            val icon = line.arrow
            Kit.icon(g, icon, line.x + 2 + FOLD_PAD + (Icon.RIGHT.width - icon.width) / 2, top + 2 + (FOLD_HEIGHT - icon.height) / 2, Kit.theme.ink, Kit.theme.inkShadow)
            val marked = line.marked
            if (marked != null) Kit.label(g, marked, line.nameX, top + 2 + (FOLD_HEIGHT - 8) / 2) else Kit.label(g, s.name, line.nameX, top + 2 + (FOLD_HEIGHT - 8) / 2)
            if (line.muted.isNotEmpty()) Kit.label(g, line.muted, line.mutedX, top + 2 + (FOLD_HEIGHT - 8) / 2, Kit.Ink.MUTED)
            if (hovered) g.requestCursor(CursorTypes.POINTING_HAND)
            return
        }
        if (lit) Kit.wellRow(g, line.x, top, line.width, line.height)
        g.text(font, line.name, line.nameX, top + NAME_TOP, -1, true)
        for (k in line.description.indices) g.text(font, line.description[k], line.nameX, top + NAME_TOP + 10 + k * LINE, -1, true)
        val cy = origin + line.cy
        val overControl = hovered && overControl(line, mx, my)
        val controlState = if (overControl || lit && !hovered) Kit.State.HOVER else Kit.State.IDLE
        when (s) {
            is Switch -> {
                Kit.switch(g, line.cx, cy, s.value, if (lit && !(hovered && overExtra(line, mx, my))) Kit.State.HOVER else Kit.State.IDLE)
                if (line.ew > 0) {
                    val overButton = hovered && overExtra(line, mx, my)
                    openButton(g, line, line.ex, origin + line.y + (line.height - SETTINGS) / 2, if (overButton) Kit.State.HOVER else Kit.State.IDLE, top)
                }
                if (hovered) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Number -> {
                Kit.well(g, line.cx, cy, TRACK, Kit.SWITCH_HEIGHT)
                if (line.zero >= 0) Kit.tick(g, line.zero, cy + 1, Kit.SWITCH_HEIGHT - 2)
                val trackState = when {
                    dragging === line -> Kit.State.DOWN
                    overControl || lit && !hovered -> Kit.State.HOVER
                    else -> Kit.State.IDLE
                }
                if (line.fillTo > line.fillFrom) Kit.span(g, line.fillFrom, cy + 1, line.fillTo - line.fillFrom, Kit.SWITCH_HEIGHT - 2, trackState)
                val fieldY = origin + line.y + (line.height - Kit.BUTTON_HEIGHT) / 2
                val open = mode
                if (open is Mode.Field && open.line === line) {
                    open.field.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
                } else {
                    val overField = hovered && overExtra(line, mx, my)
                    Kit.field(g, line.ex, fieldY, line.ew, Kit.BUTTON_HEIGHT, if (overField) Kit.State.HOVER else Kit.State.IDLE)
                    g.text(font, line.value, line.valueX, fieldY + (Kit.BUTTON_HEIGHT - 8) / 2, -1, true)
                    if (overField) g.requestCursor(CursorTypes.IBEAM)
                }
                if (overControl || dragging === line) g.requestCursor(CursorTypes.RESIZE_EW)
            }
            is Pick<*>, is Choice<*> -> {
                val look = line.look
                Kit.dropdown(g, line.cx, cy, line.cw, line.ch, if (menuOpen) Kit.State.HOVER else controlState, line.label, if (look != null) Looks.width(look) else 0, line.marked)
                if (look != null) Looks.draw(g, look, line.cx + 4, cy + (line.ch - Looks.HEIGHT) / 2)
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Color -> {
                Kit.dropdown(g, line.cx, cy, line.cw, line.ch, if (menuOpen) Kit.State.HOVER else controlState, line.label, Kit.COLOR_SWATCH)
                Kit.colorSwatch(g, line.cx + 4, cy + (line.ch - Kit.COLOR_SWATCH) / 2, Kit.COLOR_SWATCH, s.value)
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is KeyBind -> {
                val open = mode
                if (open is Mode.Recording && open.line === line) {
                    open.button.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
                } else {
                    labelButton(g, line, cy, controlState)
                }
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Span -> {
                g.text(font, line.value, line.valueX, origin + line.cy, -1, true)
                line.slider?.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
            }
            is Action -> {
                labelButton(g, line, cy, controlState)
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Order<*> -> {
                openButton(g, line, line.cx, cy, controlState, top)
                if (hovered) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Items<*>, is Panel<*>, is Preview, is Fold -> Unit
        }
    }

    private fun labelButton(g: GuiGraphicsExtractor, line: Line, cy: Int, state: Kit.State) =
        Kit.button(g, line.cx, cy, line.cw, line.ch, state, line.label, font.width(line.label))

    private fun openButton(g: GuiGraphicsExtractor, line: Line, bx: Int, by: Int, state: Kit.State, top: Int) {
        Kit.button(g, bx, by, SETTINGS, SETTINGS, state, line.arrow)
        if (line.muted.isNotEmpty()) g.text(font, line.muted, line.mutedX, top + (line.height - 8) / 2, Role.MUTED.argb(), true)
    }

    private fun tipLines(build: TextBuilder.() -> Unit) = font.split(text(build).toComponent(), TIP_WIDTH)

    private fun tip(line: Line, mx: Double, my: Double): Tip? {
        val s = line.setting
        if (overStar(line, mx, my)) {
            return line.starTip ?: run {
                val verb = if (line.starred) "unstar" else "star"
                val key = stars?.key?.invoke()?.takeIf { it >= 0 }?.let(Keys::name)
                Tip(tipLines { action(if (key == null) "Click to $verb." else "Click or press $key to $verb.") })
            }.also { line.starTip = it }
        }
        if (overMarker(line, mx, my)) {
            return line.resetTip ?: Tip(
                tipLines { body("Default: "); value(s.default(Keys::name) ?: "Each part's own") } + tipLines { action("Click to reset.") },
            ).also { line.resetTip = it }
        }
        if (line.entry >= 0) {
            if (!line.locked) return null
            return line.tip ?: Tip(tipLines { body(line.label); muted(" stays, since one must show.") }).also { line.tip = it }
        }
        if (!line.cut && !line.heading) return null
        return line.tip ?: Tip(
            tipLines { body(s.name) } + tipLines { muted(s.description) },
        ).also { line.tip = it }
    }

    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        swallow = false
        val mx = event.x
        val my = event.y
        when (val open = mode) {
            is Mode.Menu -> {
                if (open.widget.takesPress(event, doubleClick)) return true
                closeMenu()
            }
            is Mode.Field -> {
                if (open.field.takesPress(event, doubleClick)) return true
                commit()
            }
            is Mode.Recording -> if (open.button.pressed(event) { finishRecording() }) return true
            else -> Unit
        }
        capturer()?.let { if (it.mouseClicked(event, doubleClick)) return true.also { _ -> pressed = it } }
        val i = lineAt(mx, my)
        if (i < 0) return false
        val line = lines[i]
        moveFocus(i)
        spoken = i
        keys = true
        shownFocus = false
        if (event.button() == 0 && overMarker(line, mx, my)) return true.also { reset(line) }
        if (event.button() == 0 && overStar(line, mx, my)) return true.also { star(line) }
        line.editor?.let { editor ->
            if (editor.isOver(mx, my) && editor.mouseClicked(event, doubleClick)) pressed = editor
            return true
        }
        val right = event.button() == 1
        val shift = Keys.shift(event)
        val order = line.setting
        if (order is Order<*> && line.entry >= 0) {
            if (right) return true
            if (overControl(line, mx, my)) return true.also { change { order.switch(line.entry) } }
            mode = Mode.Reorder(line, my, !overGrip(line, mx), order.entries.toList() to order.value.toList())
            return true
        }
        when (val s = line.setting) {
            is Switch -> when {
                right -> Unit
                line.ew > 0 && overExtra(line, mx, my) -> fold(line, !line.open)
                else -> change { s.toggle() }
            }
            is Number -> when {
                right -> Unit
                overExtra(line, mx, my) -> edit(line, null)
                overControl(line, mx, my) -> {
                    mode = Mode.Slide(line)
                    slide(line, mx)
                }
            }
            is Choice<*> -> if (overControl(line, mx, my)) {
                if (right) change { s.step(if (shift) -1 else 1) } else openMenu(line)
            }
            is Color -> if (!right && overControl(line, mx, my)) openPicker(line)
            is KeyBind -> if (overControl(line, mx, my)) {
                if (right) change { s.clear() } else record(line)
            }
            is Span -> line.slider?.let { slider ->
                if (slider.isMouseOver(mx, my)) {
                    mode = Mode.Slide(line)
                    slider.mouseClicked(event, doubleClick)
                    take(line)
                }
            }
            is Action -> if (!right && overControl(line, mx, my)) {
                s.run()
                refresh()
            }
            is Fold, is Order<*> -> if (!right) fold(line, !line.open)
            is Items<*>, is Panel<*>, is Preview -> Unit
        }
        return true
    }

    fun starAt(mouseX: Double, mouseY: Double, keyboard: Boolean): Boolean {
        val line = (if (keyboard && keys && shownFocus) lines.getOrNull(focus) else null) ?: lines.getOrNull(lineAt(mouseX, mouseY)) ?: return false
        if (line.starX < 0) return false
        star(line)
        return true
    }

    private fun star(line: Line) {
        stars?.toggle?.invoke(line.setting.id)
        refresh()
    }

    private fun moveFocus(i: Int) {
        if (i != focus) lines.getOrNull(focus)?.editor?.leave()
        focus = i
    }

    fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        pressed?.let { return it.mouseDragged(event, dx, dy) }
        when (val drag = mode) {
            is Mode.Menu -> return drag.widget.mouseDragged(event, dx, dy)
            is Mode.Reorder -> {
                if (!drag.moved && Math.abs(event.y - drag.pressY) < DRAG_START) return true
                drag.moved = true
                val line = drag.line
                val first = lines.indexOfFirst { it.setting === line.setting && it.entry == 0 }
                val to = Math.floorDiv((event.y - top(lines[first])).toInt(), ENTRY_HEIGHT)
                moveEntry(line, to)
            }
            is Mode.Slide -> {
                val line = drag.line
                when (line.setting) {
                    is Number -> slide(line, event.x)
                    is Span -> {
                        line.slider?.mouseDragged(event, dx, dy)
                        take(line)
                    }
                    else -> Unit
                }
            }
            else -> return false
        }
        return true
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        pressed?.let {
            pressed = null
            return it.mouseReleased(event)
        }
        capturer()?.mouseReleased(event)
        menu?.mouseReleased(event)
        (mode as? Mode.Recording)?.button?.release(event.button())
        when (val drag = mode) {
            is Mode.Reorder -> {
                mode = Mode.Idle
                val line = drag.line
                if (!drag.moved && drag.toggleOnRelease) change { (line.setting as Order<*>).switch(line.entry) }
            }
            is Mode.Slide -> {
                mode = Mode.Idle
                drag.line.slider?.let {
                    it.mouseReleased(event)
                    take(drag.line)
                }
            }
            else -> return false
        }
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean {
        menu?.let { return it.isMouseOver(mouseX, mouseY) && it.mouseScrolled(mouseX, mouseY, 0.0, amount) }
        // @keep Stepping an open field would leave the stepped value behind when Escape discards the text.
        if (mode is Mode.Field || amount == 0.0) return false
        val i = lineAt(mouseX, mouseY).takeIf { it >= 0 } ?: return false
        val line = lines[i]
        line.editor?.let { return it.isOver(mouseX, mouseY) && it.mouseScrolled(mouseX, mouseY, amount, shift) }
        val by = if (amount > 0) 1 else -1
        when (val s = line.setting) {
            is Number -> if (overControl(line, mouseX, mouseY) || overExtra(line, mouseX, mouseY)) change { s.step(if (shift) by * 10 else by) } else return false
            is Choice<*> -> if (overControl(line, mouseX, mouseY)) change { s.step(-by) } else return false
            else -> return false
        }
        return true
    }

    fun keyPressed(event: KeyEvent): Boolean {
        swallow = false
        when (val open = mode) {
            is Mode.Menu -> {
                open.widget.keyPressed(event)
                swallow = true
                return true
            }
            is Mode.Field -> {
                when {
                    event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER -> commit()
                    event.isEscape -> mode = Mode.Idle
                    else -> open.field.keyPressed(event)
                }
                return true
            }
            is Mode.Recording -> {
                open.button.capture(event.key())
                if (!open.button.recording) finishRecording()
                swallow = true
                return true
            }
            else -> Unit
        }
        capturer()?.let { return it.keyPressed(event) }
        (mode as? Mode.Reorder)?.let { drag ->
            if (event.isEscape) {
                change { restore(drag.line.setting as Order<*>, drag.before.first, drag.before.second) }
                mode = Mode.Idle
            }
            return true
        }
        if (!keys || lines.isEmpty()) return false
        val line = lines.getOrNull(focus) ?: return false
        if (line.editor?.keyPressed(event) == true) return true.also { shownFocus = true }
        val s = line.setting
        val shift = Keys.shift(event)
        if (s is Order<*> && line.entry >= 0 && (Keys.alt(event) || !(event.isUp || event.isDown || event.isEscape))) {
            when {
                event.isUp || event.isDown -> moveEntry(line, line.entry + if (event.isUp) -1 else 1)
                event.isLeft -> {
                    focus = lines.indexOfFirst { it.setting === s && it.entry < 0 }
                    spoken = focus
                }
                event.isSelection || event.isConfirmation -> change { s.switch(line.entry) }
                event.key() == GLFW.GLFW_KEY_BACKSPACE -> reset(line)
                else -> return false
            }
            shownFocus = true
            return true
        }
        when {
            event.isUp || event.isDown -> {
                val next = (focus + if (event.isUp) -1 else 1).coerceIn(0, lines.lastIndex)
                if (next != focus) {
                    moveFocus(next)
                    lines[next].editor?.enter(fromAbove = event.isDown)
                }
                spoken = focus
                shownFocus = true
                reveal(lines[focus])
                return true
            }
            event.isEscape -> {
                keys = false
                return true
            }
            event.isSelection || event.isConfirmation -> press(line)
            event.isLeft || event.isRight -> {
                val by = if (event.isLeft) -1 else 1
                when (s) {
                    is Number -> change { s.step(if (shift) by * 10 else by) }
                    is Choice<*> -> change { s.step(by) }
                    is Span -> line.slider?.let {
                        it.keyPressed(event)
                        take(line)
                    }
                    else -> if (s.children.isNotEmpty() || s is Order<*>) fold(line, by > 0) else return false
                }
            }
            event.key() == GLFW.GLFW_KEY_BACKSPACE -> reset(line)
            event.key() == GLFW.GLFW_KEY_DELETE -> when (s) {
                is KeyBind -> change { s.clear() }
                is Span -> line.slider?.let {
                    it.keyPressed(event)
                    take(line)
                }
                else -> return false
            }
            else -> return false
        }
        shownFocus = true
        return true
    }

    fun keyReleased(event: KeyEvent): Boolean {
        (capturer() ?: lines.getOrNull(focus)?.editor)?.let { if (it.keyReleased(event)) return true }
        val keys = (mode as? Mode.Recording)?.button ?: return false
        keys.release(event.key())
        if (!keys.recording) finishRecording()
        return true
    }

    fun charTyped(event: CharacterEvent): Boolean {
        (mode as? Mode.Field)?.let { return it.field.charTyped(event) }
        (capturer() ?: lines.getOrNull(focus)?.takeIf { keys }?.editor)?.let { if (it.charTyped(event)) return true }
        menu?.let {
            it.charTyped(event)
            return true
        }
        if (swallow) {
            swallow = false
            return true
        }
        val line = lines.getOrNull(focus) ?: return false
        if (keys && line.setting is Color && event.codepoint() == '#'.code) return true.also { openPicker(line, "#") }
        if (!keys || line.setting !is Number) return false
        val c = event.codepoint()
        if (c !in '0'.code..'9'.code && c != '-'.code && c != '.'.code) return false
        edit(line, Character.toString(c))
        return true
    }

    private fun press(line: Line) {
        when (val s = line.setting) {
            is Switch -> change { s.toggle() }
            is Number -> edit(line, null)
            is Choice<*> -> openMenu(line)
            is Color -> openPicker(line)
            is KeyBind -> record(line)
            is Span -> line.slider?.selectNext()
            is Action -> {
                s.run()
                refresh()
            }
            is Fold, is Order<*> -> fold(line, !line.open)
            is Items<*>, is Panel<*>, is Preview -> Unit
        }
    }

    private fun moveEntry(line: Line, to: Int) {
        val order = line.setting as Order<*>
        val at = order.move(line.entry, to)
        if (at == line.entry) return
        val target = lines.indexOfFirst { it.setting === order && it.entry == at }
        (mode as? Mode.Reorder)?.let { if (it.line === line) it.line = lines[target] }
        if (focus == lines.indexOf(line)) {
            focus = target
            spoken = target
        }
        changed()
        reveal(lines[target])
    }

    @Suppress("UNCHECKED_CAST")
    private fun restore(order: Order<*>, entries: List<Any?>, shown: List<Any?>) = (order as Order<Any?>).restore(entries, shown)

    private inline fun change(block: () -> Unit) {
        block()
        changed()
    }

    private fun reset(line: Line) = change { line.setting.reset() }

    private fun fold(line: Line, open: Boolean) {
        if (open == line.open) return
        if (open) {
            opened += line.setting.id
        } else {
            opened -= line.setting.id
            found -= line.setting.id
        }
        relayout()
    }

    private fun slide(line: Line, mouseX: Double) {
        val s = line.setting as Number
        change { s.at((mouseX - line.cx - 1) / (TRACK - 2)) }
    }

    private fun take(line: Line) {
        val s = line.setting as Span
        val sections = line.slider?.sections ?: return
        if (sections != s.value) change { s.value = sections }
    }

    private fun openMenu(line: Line) {
        val s = line.setting as Choice<*>
        val y = origin + line.cy
        val widget = if (s is Pick<*>) {
            KitPickMenu(s.values.indices.map(s::lookAt), s.labels, s.index, screenWidth - Kit.GAP * 2, { change { s.choose(it) } }, { mode = Mode.Idle }, words)
                .below(line.cx, y, line.cw, line.ch, screenWidth, screenHeight)
        } else {
            KitMenu(0, 0, screenWidth, screenHeight, s.labels.mapIndexed { i, label -> label to { change { s.choose(i) } } }, { mode = Mode.Idle }, s.index, words)
                .below(line.cx, y, line.cw, line.ch, screenWidth, screenHeight)
        }
        mode = Mode.Menu(widget)
    }

    private fun closeMenu() {
        val open = mode as? Mode.Menu ?: return
        mode = Mode.Idle
        open.leave()
    }

    private fun openPicker(line: Line, typed: String? = null) {
        val s = line.setting as Color
        val picker = KitColorPicker(s.value, { change { s.value = it } }, ::closeMenu, recent).below(line.cx, origin + line.cy, line.cw, line.ch, screenWidth, screenHeight)
        typed?.let(picker::type)
        mode = Mode.Menu(picker, picker::leave)
    }

    private fun edit(line: Line, typed: String?) {
        val s = line.setting as Number
        val y = origin + line.y + (line.height - Kit.BUTTON_HEIGHT) / 2
        val field = KitTextField(line.ex, y, line.ew, s.name, typed ?: s.format(), FIELD_LENGTH) { text ->
            (mode as? Mode.Field)?.field?.setTextColor(if (s.parse(text) == null) Role.BAD.argb() else Role.VALUE.argb())
        }
        field.setTextColor(Role.VALUE.argb())
        field.setFocused(true)
        field.moveCursorToEnd(false)
        if (typed == null) field.setHighlightPos(0)
        mode = Mode.Field(field, line)
    }

    private fun commit() {
        val open = mode as? Mode.Field ?: return
        val s = open.line.setting as Number
        mode = Mode.Idle
        s.parse(open.field.value)?.let { value -> change { s.value = value } }
    }

    private fun record(line: Line) {
        val s = line.setting as KeyBind
        val keys = KitKeysButton(line.cx, origin + line.cy, line.cw, s.name, listOf(s.value).filter { it >= 0 }, 1, line.ch) { keys ->
            change { s.value = keys.firstOrNull() ?: KeyBind.NONE }
        }
        keys.clicks = s.clicks
        keys.onPress(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        mode = Mode.Recording(keys, line)
    }

    private fun finishRecording() {
        val open = mode as? Mode.Recording ?: return
        if (open.button.recording) open.button.setFocused(false)
        mode = Mode.Idle
    }

    private fun narrate(output: NarrationElementOutput) {
        when (val open = mode) {
            is Mode.Menu -> return open.widget.updateNarration(output)
            is Mode.Field -> return open.field.updateNarration(output)
            is Mode.Recording -> return open.button.updateNarration(output)
            else -> Unit
        }
        capturer()?.let { if (it.narrate(output)) return }
        val at = if (spoken >= 0) spoken else focus
        val line = lines.getOrNull(at) ?: return
        if (at == focus && keys && line.editor?.narrate(output) == true) return
        val order = line.setting as? Order<*>
        if (order != null && line.entry >= 0) {
            output.add(NarratedElementType.TITLE, order.entryNarration(line.entry))
            output.add(NarratedElementType.HINT, ENTRY_HINT)
            return
        }
        val narration = line.setting.narration(Keys::name, line.open)
        output.add(NarratedElementType.TITLE, if (line.starred) Narration.join(narration, "starred") else narration)
        if (lines.size > 1) output.add(NarratedElementType.POSITION, I18n.get("narrator.position.list", at + 1, lines.size))
        output.add(NarratedElementType.HINT, line.setting.description)
    }

    fun dispose() {
        for (editor in made.values) editor.close()
    }

    companion object {
        const val PAD = 6
        private const val PAD_Y = 4
        private const val NAME_TOP = 4
        private const val LINE = 9
        private const val DESCRIPTION_LINES = 2
        private const val MIN_HEIGHT = 22
        private const val CONTROL_GAP = 8
        private const val INDENT = 11
        private const val GUIDE = 4
        private const val OPEN_BELOW = 2
        private const val TRACK = 72
        private const val FIELD_GAP = 3
        private const val FIELD_PAD = 4
        private const val FIELD_LENGTH = 12
        private const val SETTINGS = 14
        private const val SUMMARY = 60
        private const val DROPDOWN = 64
        private const val PICK_HEIGHT = Looks.HEIGHT + 4
        private const val KEYS = 92
        private const val COLOR_LABEL = "#DDDDDD 100%"
        private const val SPAN_SUMMARY = 80
        private const val SPAN_BELOW = 4
        private const val EDITOR_BELOW = 6
        private const val FOLD_HEIGHT = 16
        private const val FOLD_PAD = 5
        private const val FOCUS_MARGIN = 4
        private const val TIP_WIDTH = 170
        private const val ORDER_SUMMARY = 110
        private const val ENTRY_HEIGHT = 16
        private const val GRIP_ROOM = Kit.GRIP_WIDTH + 6
        private const val DRAG_START = 2.0
        private const val STAR_GAP = 4
        private const val STAR_ROOM = STAR_GAP + 7

        const val HEADING_STAR = Kit.GAP + 7

        private const val ENTRY_HINT = "Drag it by its grip, or press Alt with Up or Down, to move it."
    }
}

internal fun marked(text: String, words: List<String>, muted: Boolean) = text {
    for (piece in SettingsSearch.pieces(text, words)) {
        when {
            piece.match -> match(piece.text)
            muted -> muted(piece.text)
            else -> body(piece.text)
        }
    }
}.toComponent()

internal fun AbstractWidget.takesPress(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
    val over = isMouseOver(event.x, event.y)
    if (over) mouseClicked(event, doubleClick)
    return over
}

internal inline fun KitKeysButton.pressed(event: MouseButtonEvent, finish: () -> Unit): Boolean {
    val took = capture(event)
    if (!recording) finish()
    return took || isMouseOver(event.x, event.y)
}
