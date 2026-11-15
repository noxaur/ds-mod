package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.Action
import io.github.noxaur.dsmod.core.config.Choice
import io.github.noxaur.dsmod.core.config.Fold
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.config.Number
import io.github.noxaur.dsmod.core.config.Pick
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.Span
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
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

/**
 * The rows of settings on a screen, each a [Setting] with the face of its kind: the name with its description under
 * it on the left, and a kit control on the right. A host lays them out with [begin], [add] and [heading] for each
 * group and [end], whenever what is shown changes, and draws them each frame with [draw] and [drawMenu]. A row that
 * opens, such as a fold, asks the host to lay out again through [relayout]. The host hands over its mouse and keys,
 * and [scrollTo] asks it to scroll a stretch of the content into view.
 *
 * Every row gets the same behaviors, whatever its kind: the changed marker, dimming, a summary while closed, the
 * keyboard, a tooltip when its text is cut short, the words of a search painted where they match, and narration
 * through [narration], which the host adds as a widget. `controls.md` has each kind's conventions.
 */
class SettingRows(
    /** The rows that are open, which the host keeps across screens. */
    val opened: MutableSet<String>,
    private val relayout: () -> Unit,
    private val scrollTo: (top: Int, bottom: Int) -> Unit,
) {
    /** A tooltip's lines, made once. */
    class Tip(val lines: List<FormattedCharSequence>)

    /**
     * Where a row is on the screen: the row, its control, the second part of its control (a number's field or a
     * switch's settings button) and its changed marker.
     */
    class Spot(
        val x: Int, val y: Int, val width: Int, val height: Int,
        val controlX: Int, val controlY: Int, val controlWidth: Int, val controlHeight: Int,
        val extraX: Int, val extraWidth: Int, val markerX: Int, val markerY: Int,
    )

    private class Line(val setting: Setting, val x: Int, val y: Int, val width: Int, val heading: Boolean, val rule: Boolean) {
        var height = 0
        var nameX = 0
        var name: Component = Component.empty()
        var description: List<Component> = emptyList()
        var cut = false

        // The control, in content coordinates: the dropdown, the button, or the track of a switch or a number.
        var cx = 0
        var cy = 0
        var cw = 0
        var ch = 0

        // A second part of the control: a number's field, or a switch's settings button.
        var ex = 0
        var ew = 0

        var open = false
        var guideTo = 0
        var changed = false
        var dimmed = false
        var label = ""

        /** [label] or a fold's name with what the search matched, or null when it matched nothing there. */
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
        var tip: Tip? = null
        var resetTip: Tip? = null

        val bottom get() = y + height
    }

    private val lines = ArrayList<Line>()
    private val font get() = Kit.font
    private var origin = 0
    private var clipTop = 0
    private var clipBottom = 0
    private var screenWidth = 0
    private var screenHeight = 0

    /** The line the keyboard is on, or -1. */
    private var focus = -1
    private var focusId: String? = null

    /** Whether the keyboard works the rows, which a press on one or Tab gives them. */
    var keys = false
        set(value) {
            field = value
            if (value && focus < 0 && lines.isNotEmpty()) focus = 0
            if (!value) shownFocus = false
        }

    /** Focus looks like hover, and shows once a key moved it. */
    private var shownFocus = false

    /** Shows the keyboard's row, as when Tab brings the keys to the rows. */
    fun showFocus() {
        shownFocus = true
        spoken = focus
    }

    private var dragging: Line? = null
    private var menu: AbstractWidget? = null
    private var menuOrigin = 0
    private var editor: KitTextField? = null
    private var editing: Line? = null
    private var recorder: KitKeysButton? = null
    private var recording: Line? = null

    /**
     * The rows a search opened to show a hit inside them. They belong to that query, so typing a longer one does not
     * leave open what a shorter one found, and the host sets them on each layout.
     */
    var found: MutableSet<String> = HashSet()

    /** Whether a menu, a field or a key button has the keys, which the host then hands over before its own. */
    val capturing get() = menu != null || editor != null || recorder != null

    /** The words of the search the rows show, painted where a row's text matches them. The host sets them on each layout. */
    var words: List<String> = emptyList()

    /** The line narration speaks of: the one the mouse came onto or the keyboard moved to last. */
    private var spoken = -1
    private var spokenId: String? = null
    private var hoverId: String? = null

    /** A key that a menu or a key button took, whose character must not reach the host's search. */
    private var swallow = false

    /**
     * The rows as one entry of the screen's narration, as a vanilla list is one: it speaks the row the mouse or the
     * keyboard is on, and the open menu, field or key button. It takes no input, so the host adds it with `addWidget`.
     */
    val narration = Narrated()

    /** The rows' entry in the screen's narration. */
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

    /** Where the content's top is on the screen, the stretch of the screen it shows, and the screen's size, for menus. */
    fun view(originY: Int, top: Int, bottom: Int, width: Int, height: Int) {
        if (originY != origin) {
            // A menu or a field belongs where it was opened, so a scroll closes it.
            if (menu != null && originY != menuOrigin) menu = null
            editor?.let { commit() }
            recorder?.let { finishRecording() }
        }
        origin = originY
        clipTop = top
        clipBottom = bottom
        screenWidth = width
        screenHeight = height
        for (line in lines) line.slider?.y = origin + line.y + line.height - SPAN_BELOW - Kit.BUTTON_HEIGHT
    }

    fun begin() {
        focusId = lines.getOrNull(focus)?.setting?.id
        spokenId = lines.getOrNull(spoken)?.setting?.id
        lines.clear()
        dragging = null
        menu = null
        editor = null
        recorder = null
    }

    /** Lays [rows] out from [y] down, [width] wide, and returns the content y under them. */
    fun add(rows: List<Setting>, x: Int, y: Int, width: Int): Int {
        var at = y
        for ((i, setting) in rows.withIndex()) {
            if (i > 0) at += 1
            val line = Line(setting, x, at, width, false, i > 0)
            measure(line)
            lines += line
            at += line.height
            if (setting.children.isNotEmpty() && (setting.id in opened || setting.id in found)) {
                line.open = true
                at = add(setting.children, x + INDENT, at, width - INDENT)
                line.guideTo = at
                at += OPEN_BELOW
            }
        }
        return at
    }

    /** A group's heading switch at [x], [y], beside the heading's name at [nameX]. */
    fun heading(switch: Switch, x: Int, y: Int, nameX: Int) {
        val line = Line(switch, x, y, Kit.SWITCH_WIDTH, true, false)
        line.height = Kit.SWITCH_HEIGHT
        line.nameX = nameX
        line.cx = x
        line.cy = y
        line.cw = Kit.SWITCH_WIDTH
        line.ch = Kit.SWITCH_HEIGHT
        lines += line
    }

    fun end() {
        focus = focusId?.let { id -> lines.indexOfFirst { it.setting.id == id } } ?: -1
        spoken = spokenId?.let { id -> lines.indexOfFirst { it.setting.id == id } } ?: -1
        if (keys && focus < 0 && lines.isNotEmpty()) focus = 0
        refresh()
    }

    /** Works out where each part of a row sits. Its words and widths are made here, once per layout. */
    private fun measure(line: Line) {
        val s = line.setting
        val inner = line.width - PAD * 2
        var reserve = 0
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
            is Fold -> Unit
        }
        if (s is Fold) {
            line.height = FOLD_HEIGHT + 4
            // Room between the arrow and the name for the changed marker.
            line.nameX = line.x + 2 + FOLD_PAD + Icon.RIGHT.width + Kit.GAP + 4
            line.name = text { body(s.name) }.toComponent()
            line.marked = Kit.Marked.of(s.name, words)
            line.cut = true
            return
        }
        val textWidth = inner - line.cw - reserve - CONTROL_GAP
        val name = fit(s.name, textWidth)
        val all = wrap(s.description, textWidth, Int.MAX_VALUE)
        val shown = wrap(s.description, textWidth, DESCRIPTION_LINES)
        line.nameX = line.x + PAD
        line.name = marked(name, muted = false)
        line.description = shown.map { marked(it, muted = true) }
        line.cut = name != s.name || all.size > DESCRIPTION_LINES
        val textHeight = NAME_TOP + 10 + LINE * shown.size + 3
        line.height = maxOf(MIN_HEIGHT, textHeight, line.ch + PAD_Y * 2)
        val right = line.x + line.width - PAD
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
            // The summary sits before the switch, and the switch before its settings button.
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

    /** [text] in body or muted text, with what the search matched in the match role. */
    private fun marked(text: String, muted: Boolean) = text {
        for (piece in SettingsSearch.pieces(text, words)) {
            when {
                piece.match -> match(piece.text)
                muted -> muted(piece.text)
                else -> body(piece.text)
            }
        }
    }.toComponent()

    /** Reads each row's value again into what its face shows. Call it after a change, never per frame. */
    fun refresh() {
        for (line in lines) {
            val s = line.setting
            line.changed = s.changed
            line.dimmed = !line.heading && s.dimmed
            when (s) {
                is Switch -> {
                    line.muted = if (line.ew > 0 && !line.open) fit(s.closed(Keys::name), SUMMARY) else ""
                    line.mutedX = line.cx - Kit.GAP - font.width(line.muted)
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
                is KeyBind -> line.label = fit(Keys.name(s.value), KEYS - 8)
                is Span -> {
                    val summary = s.summary(Keys::name)
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
            }
        }
    }

    private fun changed() {
        refresh()
    }

    private fun top(line: Line) = origin + line.y

    private fun over(line: Line, mouseX: Double, mouseY: Double): Boolean {
        val top = top(line)
        if (mouseY < clipTop || mouseY >= clipBottom) return false
        return mouseX >= line.x && mouseX < line.x + line.width && mouseY >= top && mouseY < top + line.height
    }

    private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int) = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private fun overControl(line: Line, mouseX: Double, mouseY: Double) = inside(mouseX, mouseY, line.cx, origin + line.cy, line.cw, line.ch)

    private fun overExtra(line: Line, mouseX: Double, mouseY: Double) = line.ew > 0 && inside(mouseX, mouseY, line.ex, origin + line.y + (line.height - extraHeight(line)) / 2, line.ew, extraHeight(line))

    private fun extraHeight(line: Line) = if (line.setting is Number) Kit.BUTTON_HEIGHT else SETTINGS

    private fun markerY(line: Line) = top(line) + if (line.setting is Fold) 2 + (FOLD_HEIGHT - 3) / 2 else if (line.heading) 2 + 2 else NAME_TOP + 2

    private fun overMarker(line: Line, mouseX: Double, mouseY: Double) = line.changed && inside(mouseX, mouseY, line.nameX - 6, markerY(line) - 3, 6, 9)

    private fun lineAt(mouseX: Double, mouseY: Double): Int {
        for (i in lines.indices) {
            val line = lines[i]
            if (line.heading) {
                if (inside(mouseX, mouseY, line.cx - 2, top(line) - 1, line.cw + 4, line.ch + 2) || overMarker(line, mouseX, mouseY)) return i
            } else if (over(line, mouseX, mouseY)) {
                return i
            }
        }
        return -1
    }

    /** Where the row [id] is, if it is laid out, so a link or a test can find it. */
    fun spot(id: String): Spot? {
        val l = lines.firstOrNull { it.setting.id == id } ?: return null
        return Spot(l.x, top(l), l.width, l.height, l.cx, origin + l.cy, l.cw, l.ch, l.ex, l.ew, l.nameX - 5, markerY(l))
    }

    /** Scrolls the row [id] into view. */
    fun reveal(id: String) {
        lines.firstOrNull { it.setting.id == id }?.let { scrollTo(it.y - FOCUS_MARGIN, it.bottom + FOCUS_MARGIN) }
    }

    /** Draws the rows that show, and returns the tooltip of what is under the mouse, if any. */
    fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float): Tip? {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val menu = menu
        val free = menu == null || !menu.isMouseOver(mx, my)
        val hovered = if (free && dragging == null) lineAt(mx, my) else -1
        val hoverLine = lines.getOrNull(hovered)?.setting?.id
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
            face(g, line, top, i == hovered, lit, menu != null && i == focus, mx, my)
            if (line.changed) Kit.marker(g, line.nameX - 5, markerY(line), if (i == hovered && overMarker(line, mx, my)) Kit.State.HOVER else Kit.State.IDLE)
            if (line.dimmed) Kit.dim(g, line.x, top, line.width, line.height)
            if (i == hovered) tip = tip(line, mx, my)
        }
        return tip
    }

    /** Draws the open menu over everything else. */
    fun drawMenu(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        menu?.extractRenderState(g, mouseX, mouseY, partialTick)
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
        if (s is Fold) {
            val state = if (lit) Kit.State.HOVER else Kit.State.IDLE
            Kit.button(g, line.x + 2, top + 2, line.width - 4, FOLD_HEIGHT, state, "", 0)
            val icon = if (line.open) Icon.DOWN else Icon.RIGHT
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
                    Kit.button(g, line.ex, origin + line.y + (line.height - SETTINGS) / 2, SETTINGS, SETTINGS, if (overButton) Kit.State.HOVER else Kit.State.IDLE, if (line.open) Icon.DOWN else Icon.RIGHT)
                    if (line.muted.isNotEmpty()) g.text(font, line.muted, line.mutedX, top + (line.height - 8) / 2, Role.MUTED.argb(), true)
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
                val editor = editor
                if (editor != null && editing === line) {
                    editor.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
                } else {
                    val overField = hovered && overExtra(line, mx, my)
                    Kit.field(g, line.ex, fieldY, line.ew, Kit.BUTTON_HEIGHT, if (overField) Kit.State.HOVER else Kit.State.IDLE)
                    g.text(font, line.value, line.valueX, fieldY + (Kit.BUTTON_HEIGHT - 8) / 2, -1, true)
                    if (overField) g.requestCursor(CursorTypes.IBEAM)
                }
                if (overControl || dragging === line) g.requestCursor(CursorTypes.RESIZE_EW)
            }
            is Pick<*> -> {
                Kit.dropdown(g, line.cx, cy, line.cw, line.ch, if (menuOpen) Kit.State.HOVER else controlState, line.label, Looks.width(line.look!!), line.marked)
                Looks.draw(g, line.look!!, line.cx + 4, cy + (line.ch - Looks.HEIGHT) / 2)
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Choice<*> -> {
                Kit.dropdown(g, line.cx, cy, line.cw, line.ch, if (menuOpen) Kit.State.HOVER else controlState, line.label, marked = line.marked)
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is KeyBind -> {
                val recorder = recorder
                if (recorder != null && recording === line) {
                    recorder.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
                } else {
                    Kit.button(g, line.cx, cy, line.cw, line.ch, controlState, line.label, font.width(line.label))
                }
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Span -> {
                g.text(font, line.value, line.valueX, origin + line.cy, -1, true)
                line.slider?.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
            }
            is Action -> {
                Kit.button(g, line.cx, cy, line.cw, line.ch, controlState, line.label, font.width(line.label))
                if (overControl) g.requestCursor(CursorTypes.POINTING_HAND)
            }
            is Fold -> Unit
        }
    }

    private fun tip(line: Line, mx: Double, my: Double): Tip? {
        val s = line.setting
        if (overMarker(line, mx, my)) {
            return line.resetTip ?: Tip(
                font.split(text { body("Default: "); value(s.default(Keys::name) ?: "Each part's own") }.toComponent(), TIP_WIDTH) +
                    font.split(text { action("Click to reset.") }.toComponent(), TIP_WIDTH),
            ).also { line.resetTip = it }
        }
        if (!line.cut && !line.heading) return null
        return line.tip ?: Tip(
            font.split(text { body(s.name) }.toComponent(), TIP_WIDTH) + font.split(text { muted(s.description) }.toComponent(), TIP_WIDTH),
        ).also { line.tip = it }
    }

    /** Takes a press, and returns whether it was on a row. A press outside an open menu or field closes it and still does its own job. */
    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x
        val my = event.y
        menu?.let { open ->
            if (open.isMouseOver(mx, my)) return true.also { open.mouseClicked(event, doubleClick) }
            menu = null
        }
        editor?.let { field ->
            if (field.isMouseOver(mx, my)) return true.also { field.mouseClicked(event, doubleClick) }
            commit()
        }
        recorder?.let { keys ->
            val took = keys.capture(event.button(), mouse = true)
            if (!keys.recording) finishRecording()
            if (took || keys.isMouseOver(mx, my)) return true
        }
        val i = lineAt(mx, my)
        if (i < 0) return false
        val line = lines[i]
        focus = i
        spoken = i
        keys = true
        shownFocus = false
        if (event.button() == 0 && overMarker(line, mx, my)) return true.also { reset(line) }
        val right = event.button() == 1
        val shift = Keys.shift(event)
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
                    dragging = line
                    slide(line, mx)
                }
            }
            is Choice<*> -> if (overControl(line, mx, my)) {
                if (right) change { s.step(if (shift) -1 else 1) } else openMenu(line)
            }
            is KeyBind -> if (overControl(line, mx, my)) {
                if (right) change { s.clear() } else record(line)
            }
            is Span -> line.slider?.let { slider ->
                if (slider.isMouseOver(mx, my)) {
                    dragging = line
                    slider.mouseClicked(event, doubleClick)
                    take(line)
                }
            }
            is Action -> if (!right && overControl(line, mx, my)) s.run()
            is Fold -> if (!right) fold(line, !line.open)
        }
        return true
    }

    fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val line = dragging ?: return false
        when (line.setting) {
            is Number -> slide(line, event.x)
            is Span -> {
                line.slider?.mouseDragged(event, dx, dy)
                take(line)
            }
            else -> Unit
        }
        return true
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        recorder?.release(event.button())
        val line = dragging ?: return false
        dragging = null
        line.slider?.let {
            it.mouseReleased(event)
            take(line)
        }
        return true
    }

    /** The wheel over a number or a dropdown steps it, by ten with Shift. Elsewhere it is the host's, to scroll. */
    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean {
        if (menu != null || amount == 0.0) return false
        val i = lineAt(mouseX, mouseY).takeIf { it >= 0 } ?: return false
        val line = lines[i]
        val by = if (amount > 0) 1 else -1
        when (val s = line.setting) {
            is Number -> if (overControl(line, mouseX, mouseY) || overExtra(line, mouseX, mouseY)) change { s.step(if (shift) by * 10 else by) } else return false
            is Choice<*> -> if (overControl(line, mouseX, mouseY)) change { s.step(-by) } else return false
            else -> return false
        }
        return true
    }

    /** Takes a key while the rows have the keyboard, or while a menu, field or key button is open. Returns whether it took it. */
    fun keyPressed(event: KeyEvent): Boolean {
        swallow = false
        menu?.let { open ->
            open.keyPressed(event)
            swallow = true
            return true
        }
        val field = editor
        if (field != null) {
            when {
                event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER -> commit()
                event.isEscape -> close()
                else -> field.keyPressed(event)
            }
            return true
        }
        recorder?.let { keys ->
            keys.capture(event.key())
            if (!keys.recording) finishRecording()
            swallow = true
            return true
        }
        if (!keys || lines.isEmpty()) return false
        val line = lines.getOrNull(focus) ?: return false
        val s = line.setting
        val shift = Keys.shift(event)
        when {
            event.isUp || event.isDown -> {
                focus = (focus + if (event.isUp) -1 else 1).coerceIn(0, lines.lastIndex)
                spoken = focus
                shownFocus = true
                lines[focus].let { scrollTo(it.y - FOCUS_MARGIN, it.bottom + FOCUS_MARGIN) }
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
                    else -> if (s.children.isNotEmpty()) fold(line, by > 0) else return false
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
        val keys = recorder ?: return false
        keys.release(event.key())
        if (!keys.recording) finishRecording()
        return true
    }

    /**
     * Typing goes to an open field or an open menu, and a digit typed on a number opens its field with it. The
     * character of a key that a menu or a key button took goes nowhere, so recording K does not start a search.
     */
    fun charTyped(event: CharacterEvent): Boolean {
        editor?.let { return it.charTyped(event) }
        menu?.let {
            it.charTyped(event)
            return true
        }
        if (swallow) {
            swallow = false
            return true
        }
        val line = lines.getOrNull(focus) ?: return false
        if (!keys || !shownFocus || line.setting !is Number) return false
        val c = event.codepoint()
        if (c !in '0'.code..'9'.code && c != '-'.code && c != '.'.code) return false
        edit(line, Character.toString(c))
        return true
    }

    /** Enter or Space on a row does what a press on its control does. */
    private fun press(line: Line) {
        when (val s = line.setting) {
            is Switch -> change { s.toggle() }
            is Number -> edit(line, null)
            is Choice<*> -> openMenu(line)
            is KeyBind -> record(line)
            is Span -> line.slider?.selectNext()
            is Action -> s.run()
            is Fold -> fold(line, !line.open)
        }
    }

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

    /** The span takes what its slider holds now. */
    private fun take(line: Line) {
        val s = line.setting as Span
        val sections = line.slider?.sections ?: return
        if (sections != s.value) change { s.value = sections }
    }

    private fun openMenu(line: Line) {
        val s = line.setting as Choice<*>
        val y = origin + line.cy
        menuOrigin = origin
        menu = if (s is Pick<*>) {
            KitPickMenu(s.values.indices.map(s::lookAt), s.labels, s.index, screenWidth - Kit.GAP * 2, { change { s.choose(it) } }, { menu = null }, words)
                .below(line.cx, y, line.cw, line.ch, screenWidth, screenHeight)
        } else {
            KitMenu(0, 0, screenWidth, screenHeight, s.labels.mapIndexed { i, label -> label to { change { s.choose(i) } } }, { menu = null }, s.index, words)
                .below(line.cx, y, line.cw, line.ch, screenWidth, screenHeight)
        }
    }

    /** Opens the number's field to type in, holding [typed] or else its value, selected. */
    private fun edit(line: Line, typed: String?) {
        val s = line.setting as Number
        val y = origin + line.y + (line.height - Kit.BUTTON_HEIGHT) / 2
        val field = KitTextField(line.ex, y, line.ew, s.name, typed ?: s.format(), FIELD_LENGTH) { text ->
            editor?.setTextColor(if (s.parse(text) == null) Role.BAD.argb() else Role.VALUE.argb())
        }
        field.setTextColor(Role.VALUE.argb())
        field.setFocused(true)
        field.moveCursorToEnd(false)
        if (typed == null) field.setHighlightPos(0)
        editor = field
        editing = line
    }

    /** Takes what the field holds, if it is a number, and closes it. */
    private fun commit() {
        val field = editor ?: return
        val s = editing?.setting as? Number
        close()
        s?.parse(field.value)?.let { value -> change { s.value = value } }
    }

    private fun close() {
        editor = null
        editing = null
    }

    private fun record(line: Line) {
        val s = line.setting as KeyBind
        val keys = KitKeysButton(line.cx, origin + line.cy, line.cw, s.name, listOf(s.value).filter { it >= 0 }, 1, line.ch) { keys ->
            change { s.value = keys.firstOrNull() ?: KeyBind.NONE }
        }
        keys.onPress(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        recorder = keys
        recording = line
    }

    private fun finishRecording() {
        recorder?.let { if (it.recording) it.setFocused(false) }
        recorder = null
        recording = null
    }

    /**
     * Says the open menu, field or key button, or else the row narration is on: its name, kind, value, changes and
     * dimming as [Setting.narration] words them, its description as a hint, as vanilla says a tooltip, and where it
     * is among the rows.
     */
    private fun narrate(output: NarrationElementOutput) {
        (menu ?: editor ?: recorder)?.let { return it.updateNarration(output) }
        val at = if (spoken >= 0) spoken else focus
        val line = lines.getOrNull(at) ?: return
        output.add(NarratedElementType.TITLE, line.setting.narration(Keys::name, line.open))
        if (lines.size > 1) output.add(NarratedElementType.POSITION, I18n.get("narrator.position.list", at + 1, lines.size))
        output.add(NarratedElementType.HINT, line.setting.description)
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
        private const val SPAN_SUMMARY = 80
        private const val SPAN_BELOW = 4
        private const val FOLD_HEIGHT = 16
        private const val FOLD_PAD = 5
        private const val FOCUS_MARGIN = 4
        private const val TIP_WIDTH = 170
    }
}
