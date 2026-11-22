package io.github.noxaur.dsmod.edge.ui

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.Cell
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Items
import io.github.noxaur.dsmod.core.config.Items.Entry
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.edge.Keys
import io.github.noxaur.dsmod.edge.toComponent
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
 * A list the player builds, edited where it sits in the settings, as `controls.md`'s list of things to edit says.
 * Each group is a heading that folds, with a switch for the group and a count, and each item a line of its cells,
 * such as a switch, a name, a command, keys and where they work. What does not fit a line opens under it from the
 * button at its end, with the line's actions. A line or a heading is dragged to move it, and a line dropped under
 * another heading joins that group. A right press opens a menu of what can be done to the line. Deletes ask for a
 * second press. Under the list are Add, Add Group, Import and Export, and a line that says what the picked line
 * lacks or what the last action did.
 *
 * Its rules are [Items]'s, so this lays out on change, draws what shows, and turns the mouse and the keys into them.
 */
class ItemsEditor<T>(private val items: Items<T>, private val host: Editor.Host) : Editor {
    private enum class Kind { HEADING, ITEM, EMPTY, DETAILS, FOOT, NOTHING }

    /** What a press on a part of a line does. */
    private enum class Does { CELL, DETAILS, FOLD, GROUP, UP, DOWN, DUPLICATE, DELETE, EXPORT, NAME, ADD, ADD_GROUP, IMPORT }

    /** A line of the list, in content coordinates. [entry] is the list's line it belongs to, which a details strip shares. */
    private class Line(val kind: Kind, val entry: Entry?, val y: Int, val height: Int) {
        val hits = ArrayList<Hit>()
        var text: Component = Component.empty()
        var muted: Component = Component.empty()
        var mutedX = 0
        var folded = false
        val bottom get() = y + height
        val held: Entry get() = checkNotNull(entry) { "a $kind line holds an entry" }
    }

    /** A control on a line: where it sits, what a press does, and what it shows, which [refresh] fills in. */
    private class Hit(val line: Line, val x: Int, val dy: Int, val width: Int, val height: Int, val does: Does, val cell: Int = -1, val caption: String = "") {
        /** The name drawn before or after the control, in the muted role, since it sits on the list's well. */
        var name: Component? = null
        var label = ""
        var text: Component = Component.empty()
        var on = false
        var active = true
        var clash: Role? = null
        val y get() = line.y + dy
    }

    private val lines = ArrayList<Line>()
    private var x = 0
    private var width = 0
    private var origin = 0
    private var clipTop = 0
    private var clipBottom = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var status = emptyList<Component>()
    private var statusY = 0

    /** The list's line the keys or the last press are on, of which kind, whether the keys are inside the list, and on which control. */
    private var picked: Entry? = null
    private var pickedKind = Kind.NOTHING

    /** The group Export means once the keys or the mouse have moved onto the foot, which has no group of its own. */
    private var footGroup: String? = null
    private var inside = false
    private var cursor = 0
    private var shownFocus = false

    /** The one line whose details are open, and the line whose Delete asks for its second press. */
    private var details: Entry? = null
    private var armed: Entry? = null
    private var said: String? = null
    private var saidBad = false

    /** The open field, and the line and control it belongs to, so a layout finds it again. */
    private var field: KitTextField? = null
    private var fieldHit: Hit? = null
    private var fieldEntry: Entry? = null
    private var fieldKind = Kind.NOTHING
    private var fieldIndex = -1
    private var recorder: KitKeysButton? = null
    private var recordHit: Hit? = null
    private var menu: KitMenu? = null

    /** A line pressed and maybe being dragged: where the press was, whether it moved, and the list before, for Escape. */
    private var dragged: Entry? = null

    /** The entry the drag started on, so cancelling it puts the selection back there. */
    private var draggedFrom: Entry? = null
    private var pressY = 0.0
    private var moved = false
    private var before: Grouped<T>? = null
    private var swallow = false

    private val type = items.type
    private val font get() = Kit.font
    private val list get() = items.value

    override val capturing get() = field != null || recorder != null || menu != null || dragged != null && moved

    override val recording get() = recorder != null

    override fun layout(x: Int, y: Int, width: Int): Int {
        this.x = x
        this.width = width
        lines.clear()
        recorder = null
        recordHit = null
        menu = null
        var at = y
        val entries = items.entries()
        if (entries.isEmpty()) lines += Line(Kind.NOTHING, null, at, MESSAGE).also { at += it.height }
        for (entry in entries) {
            val line = when (entry) {
                is Entry.Heading -> heading(entry, at)
                is Entry.Item -> item(entry, at)
                is Entry.Empty -> Line(Kind.EMPTY, entry, at, LINE)
            }
            lines += line
            at += line.height
            if (entry == details && entry !is Entry.Empty) lines += details(entry, at).also { at += it.height }
        }
        at += Kit.GAP
        lines += foot(at).also { at += it.height }
        statusY = at
        at += STATUS
        if (pickedKind != Kind.FOOT && pickedKind != Kind.NOTHING && lines.none { it.entry == picked && it.kind == pickedKind }) {
            pick(lines.firstOrNull { it.kind == Kind.HEADING || it.kind == Kind.ITEM }, keepCursor = false)
        }
        // An open field stays open across a layout, on the control it was opened on, as a group's name does while it is typed.
        if (field != null) {
            fieldHit = lines.firstOrNull { it.entry == fieldEntry && it.kind == fieldKind }?.hits?.getOrNull(fieldIndex)
            val hit = fieldHit
            if (hit == null) field = null else field?.place(hit.x, origin + hit.y)
        }
        refresh()
        return at - y
    }

    private fun heading(entry: Entry.Heading, y: Int): Line {
        val line = Line(Kind.HEADING, entry, y, LINE)
        line.hits += Hit(line, x + ARROW_X - 2, 0, ARROW_ROOM, LINE, Does.FOLD, caption = "Fold")
        line.hits += Hit(line, x + HEADING_SWITCH, (LINE - Kit.SWITCH_HEIGHT) / 2, Kit.SWITCH_WIDTH, Kit.SWITCH_HEIGHT, Does.GROUP, caption = "Group")
        line.hits += settingsHit(line)
        return line
    }

    private fun item(entry: Entry.Item, y: Int): Line {
        val line = Line(Kind.ITEM, entry, y, LINE)
        val cells = type.cells
        val first = cells.firstOrNull() as? Cell.Check
        if (first != null) line.hits += Hit(line, x + ITEM_SWITCH, (LINE - Kit.SWITCH_HEIGHT) / 2, Kit.SWITCH_WIDTH, Kit.SWITCH_HEIGHT, Does.CELL, 0, first.name)
        val start = x + ITEM_SWITCH + Kit.SWITCH_WIDTH + Kit.GAP
        val end = right() - SETTINGS - Kit.GAP
        val rest = cells.withIndex().drop(if (first != null) 1 else 0)
        val fixed = rest.sumOf { (_, cell) -> widthOf(cell) } + Kit.GAP * (rest.size - 1)
        // On a narrow pane the cells of a set width give up room, so the one that grows keeps enough to read.
        val room = end - start
        val shrink = if (room - fixed >= MIN_GROW) 1f else (room - MIN_GROW - Kit.GAP * (rest.size - 1)).toFloat() / (fixed - Kit.GAP * (rest.size - 1))
        val grow = room - rest.sumOf { (_, cell) -> (widthOf(cell) * shrink).toInt() } - Kit.GAP * (rest.size - 1)
        var cx = start
        for ((i, cell) in rest) {
            val w = widthOf(cell).takeIf { it > 0 }?.let { (it * shrink).toInt() } ?: grow
            line.hits += Hit(line, cx, 1, w, Kit.BUTTON_HEIGHT, Does.CELL, i, cell.name)
            cx += w + Kit.GAP
        }
        line.hits += settingsHit(line)
        return line
    }

    private fun settingsHit(line: Line) = Hit(line, right() - SETTINGS, (LINE - SETTINGS) / 2, SETTINGS, SETTINGS, Does.DETAILS, caption = "Settings")

    private fun widthOf(cell: Cell<T>) = when (cell) {
        is Cell.Text -> cell.width
        is Cell.Keys -> cell.width
        is Cell.Pick<T, *> -> cell.width
        is Cell.Check -> Kit.SWITCH_WIDTH
        is Cell.Count -> COUNT
    }

    /** The strip under a line: an item's other cells and its actions, or a group's name and its actions. */
    private fun details(entry: Entry, y: Int): Line {
        val placed = ArrayList<Triple<Int, Int, (Line, Int, Int) -> Hit>>()
        val start = x + if (entry is Entry.Item) ITEM_SWITCH else HEADING_SWITCH
        val end = right()
        var cx = start
        var row = 0
        fun put(w: Int, make: (Line, Int, Int) -> Hit) {
            if (cx > start && cx + w > end) {
                cx = start
                row++
            }
            placed += Triple(cx, row, make)
            cx += w + Kit.GAP * 2
        }
        if (entry is Entry.Item) {
            for ((i, cell) in type.details.withIndex()) {
                val label = font.width(cell.name) + Kit.GAP
                if (cell is Cell.Check) {
                    val w = Kit.SWITCH_WIDTH + label
                    put(w) { l, hx, dy -> Hit(l, hx, dy + (LINE - Kit.SWITCH_HEIGHT) / 2, w, Kit.SWITCH_HEIGHT, Does.CELL, i + DETAIL, cell.name) }
                } else {
                    val w = widthOf(cell)
                    put(label + w) { l, hx, dy -> Hit(l, hx + label, dy + 1, w, Kit.BUTTON_HEIGHT, Does.CELL, i + DETAIL, cell.name) }
                }
            }
        } else if (entry is Entry.Heading && entry.group.isNotEmpty()) {
            val label = font.width(NAME) + Kit.GAP
            val w = minOf(NAME_FIELD, end - start - label)
            put(label + w) { l, hx, dy -> Hit(l, hx + label, dy + 1, w, Kit.BUTTON_HEIGHT, Does.NAME, caption = NAME) }
        }
        // The actions sit together at the end of the last row.
        val named = entry is Entry.Item || entry is Entry.Heading && entry.group.isNotEmpty()
        val actions = buildList {
            if (named) {
                add(Does.UP to "Move Up")
                add(Does.DOWN to "Move Down")
            }
            if (entry is Entry.Item) add(Does.DUPLICATE to "Duplicate")
            if (entry is Entry.Heading) add(Does.EXPORT to "Export")
            if (named) add(Does.DELETE to "Delete")
        }
        val widths = actions.map { (does, label) -> if (does == Does.UP || does == Does.DOWN) Kit.BUTTON_HEIGHT else buttonWidth(label) }
        val all = widths.sum() + Kit.GAP * (actions.size - 1)
        if (cx + all > end) {
            cx = start
            row++
        }
        val line = Line(Kind.DETAILS, entry, y, (row + 1) * LINE + DETAILS_BELOW)
        for ((hx, r, make) in placed) line.hits += make(line, hx, r * LINE).also { hit ->
            val name = if (hit.does == Does.NAME) NAME else hit.caption
            hit.name = text { muted(name) }.toComponent()
        }
        var ax = end - all
        for ((i, action) in actions.withIndex()) {
            line.hits += Hit(line, ax, row * LINE + 1, widths[i], Kit.BUTTON_HEIGHT, action.first, caption = action.second)
            ax += widths[i] + Kit.GAP
        }
        return line
    }

    private fun foot(y: Int): Line {
        val line = Line(Kind.FOOT, null, y, Kit.BUTTON_HEIGHT)
        var cx = x
        val noun = type.noun.replaceFirstChar { it.uppercase() }
        for ((does, label) in listOf(Does.ADD to "Add $noun", Does.ADD_GROUP to "Add Group", Does.IMPORT to "Import", Does.EXPORT to "Export")) {
            val w = buttonWidth(label)
            line.hits += Hit(line, cx, 0, w, Kit.BUTTON_HEIGHT, does, caption = label)
            cx += w + Kit.GAP
        }
        return line
    }

    private fun right() = x + width

    private fun buttonWidth(label: String) = font.width(label) + 16

    /** Reads the list again into what each line shows. Call it after a change, never per frame. */
    override fun refresh() {
        tip = null
        val value = list
        val words = host.words()
        val clashing = BooleanArray(value.items.size) { items.clashes(it).isNotEmpty() }
        for (line in lines) {
            when (line.kind) {
                Kind.HEADING -> {
                    val group = (line.entry as Entry.Heading).group
                    val off = group in value.off
                    line.folded = group in value.folded
                    val count = items.count(group).toString()
                    val nameX = x + HEADING_SWITCH + Kit.SWITCH_WIDTH + Kit.GAP
                    line.text = marked(fit(items.groupName(group), right() - SETTINGS - Kit.GAP * 2 - font.width(count) - nameX), words, off)
                    line.muted = text { muted(count) }.toComponent()
                    line.mutedX = right() - SETTINGS - Kit.GAP - font.width(count)
                }
                Kind.EMPTY -> line.text = text { muted(fit("Drag ${type.noun}s here", width - EMPTY_X)) }.toComponent()
                Kind.NOTHING -> line.text = text { muted(fit("No ${type.noun}s yet. Add one, or import some.", width)) }.toComponent()
                else -> Unit
            }
            for (hit in line.hits) fill(hit, value, clashing, words)
        }
        val (said, bad) = statusText(value, clashing)
        status = wrap(said, width, STATUS_LINES).map { if (bad) text { bad(it) }.toComponent() else text { muted(it) }.toComponent() }
    }

    private fun fill(hit: Hit, value: Grouped<T>, clashing: BooleanArray, words: List<String>) {
        val entry = hit.line.entry
        hit.active = when (hit.does) {
            Does.UP -> entry != null && items.canNudge(entry, -1)
            Does.DOWN -> entry != null && items.canNudge(entry, 1)
            Does.EXPORT -> items.count(exportGroup(hit)) > 0
            else -> true
        }
        when (hit.does) {
            Does.DETAILS -> hit.on = entry == details
            Does.GROUP -> hit.on = group(entry) !in value.off
            Does.NAME -> hit.text = text { body(fit((entry as Entry.Heading).group, hit.width - 8)) }.toComponent()
            Does.CELL -> {
                val index = (entry as Entry.Item).index
                val item = value.items.getOrNull(index) ?: return
                val sends = items.live(index)
                when (val cell = cellAt(hit.cell)) {
                    is Cell.Check -> hit.on = cell.get(item)
                    is Cell.Text -> {
                        val typed = cell.get(item)
                        hit.text = if (typed.isEmpty()) text { muted(cell.hint) }.toComponent() else marked(fit(typed, hit.width - 8), words, !sends)
                    }
                    is Cell.Keys -> {
                        val keys = cell.get(item)
                        hit.label = fit(Keys.names(keys, cell.inOrder(item)), hit.width - 6)
                        hit.clash = when {
                            !sends -> null
                            clashing[index] -> Role.BAD
                            keys.size == 1 && Keys.gameKey(keys[0]) != null -> Role.WARNING
                            else -> null
                        }
                    }
                    is Cell.Pick<T, *> -> hit.label = fit(cell.shown(item), hit.width - 6)
                    is Cell.Count -> {
                        hit.label = cell.get(item).toString()
                        hit.text = text { value(hit.label) }.toComponent()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun cellAt(i: Int): Cell<T> = if (i >= DETAIL) type.details[i - DETAIL] else type.cells[i]

    private fun group(entry: Entry?) = when (entry) {
        is Entry.Heading -> entry.group
        is Entry.Empty -> entry.group
        is Entry.Item -> list.items.getOrNull(entry.index)?.let { items.home(it) } ?: ""
        null -> ""
    }

    /** What the line under the list says, and whether it is bad: what the last action did, what stops the picked line, or nothing. */
    private fun statusText(value: Grouped<T>, clashing: BooleanArray): Pair<String, Boolean> {
        when (val gone = armed) {
            is Entry.Item -> return "Press Delete again to delete this ${type.noun}." to false
            is Entry.Heading -> return "Press Delete again to delete ${items.groupName(gone.group)}" +
                (items.count(gone.group).takeIf { it > 0 }?.let { " and its ${items.plural(it)}" } ?: "") + "." to false
            else -> Unit
        }
        said?.let { return it to saidBad }
        recorder?.let { keys ->
            if (keys.recording) return (if (keys.inOrder) "Press the keys in turn, then Escape." else "Hold the keys together, then let go.") to false
        }
        if (dragged != null && moved) return "Drop it under a heading to move it to that group. Escape puts it back." to false
        if (value.items.isEmpty()) return "" to false
        return when (val entry = picked) {
            is Entry.Heading -> (if (entry.group in value.off) "${items.groupName(entry.group)} is off, so none of its ${type.noun}s send." else "") to false
            is Entry.Item -> (value.items.getOrNull(entry.index)?.let { readout(entry.index, it, value, clashing) } ?: "") to false
            else -> "" to false
        }
    }

    private fun readout(index: Int, item: T, value: Grouped<T>, clashing: BooleanArray): String {
        val keysCell = type.cells.filterIsInstance<Cell.Keys<T>>().firstOrNull()
        val keys = keysCell?.get(item).orEmpty()
        val names = keysCell?.let { Keys.names(keys, it.inOrder(item)) } ?: ""
        val command = type.cells.filterIsInstance<Cell.Text<T>>().lastOrNull()?.get(item).orEmpty()
        val clash = if (clashing[index]) items.clashes(index).firstOrNull() else null
        val game = keys.singleOrNull()?.let(Keys::gameKey)
        return when {
            command.isBlank() -> "Type a command, such as /warp end."
            keysCell != null && keys.isEmpty() -> "Press Keys, then the keys to use."
            items.home(item) in value.off -> "${items.groupName(items.home(item))} is off, so this ${type.noun} sends nothing."
            !type.on(item) -> "Off. Turn on its switch to use it."
            clash != null -> "$names also fires ${type.title(clash)}."
            game != null -> "$names is also the game's $game key."
            // A line that works needs no words: its cells already say what it does.
            else -> ""
        }
    }

    override fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int) {
        if (origin != this.origin) {
            // A field, a key button or a menu belongs where it was opened, so a scroll closes it.
            closeField()
            finishRecording()
            menu = null
        }
        this.origin = origin
        this.clipTop = clipTop
        this.clipBottom = clipBottom
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
    }

    override fun isOver(mouseX: Double, mouseY: Double): Boolean {
        val first = lines.firstOrNull() ?: return false
        return mouseY >= clipTop && mouseY < clipBottom && mouseX >= x && mouseX < right() && mouseY >= origin + first.y && mouseY < origin + statusY + STATUS
    }

    private fun lineAt(mouseX: Double, mouseY: Double): Line? {
        if (mouseY < clipTop || mouseY >= clipBottom || mouseX < x || mouseX >= right()) return null
        for (i in lines.indices) if (mouseY >= origin + lines[i].y && mouseY < origin + lines[i].bottom) return lines[i]
        return null
    }

    private fun hitAt(line: Line, mouseX: Double, mouseY: Double): Hit? {
        for (i in line.hits.indices) {
            val hit = line.hits[i]
            if (mouseX >= hit.x && mouseX < hit.x + hit.width && mouseY >= origin + hit.y && mouseY < origin + hit.y + hit.height) return hit
        }
        return null
    }

    /**
     * Where a control of line [line] is on the screen, as x, y, width and height, so a test can press it: the line
     * itself for a null [control], or the control named [control], such as "Command", "Settings" or "Delete".
     */
    fun spot(line: Int, control: String? = null): IntArray? {
        val at = lines.getOrNull(line) ?: return null
        if (control == null) return intArrayOf(x, origin + at.y, width, at.height)
        val hit = at.hits.firstOrNull { it.caption == control } ?: return null
        return intArrayOf(hit.x, origin + hit.y, hit.width, hit.height)
    }

    /** How many lines the list shows, details and the foot included. */
    val size get() = lines.size

    /** The line of item [index], or of its details when [details], or -1 when it does not show. */
    fun lineOf(index: Int, details: Boolean = false) = indexOf(Entry.Item(index), details)

    /** The line of [group]'s heading, or of its details when [details]. */
    fun headingOf(group: String, details: Boolean = false) = indexOf(Entry.Heading(group), details)

    private fun indexOf(entry: Entry, details: Boolean) = lines.indexOfFirst { it.entry == entry && (it.kind == Kind.DETAILS) == details }

    private fun linesOf(vararg kinds: Kind) = lines.filter { it.kind in kinds }

    private fun pickedLine(): Line? {
        for (i in lines.indices) if (lines[i].kind == pickedKind && lines[i].entry == picked) return lines[i]
        return null
    }

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val free = menu?.isMouseOver(mx, my) != true
        val over = if (free && dragged == null) lineAt(mx, my) else null
        val overHit = over?.let { hitAt(it, mx, my) }
        val focusLine = if (keys && inside && shownFocus) pickedLine() else null
        val holding = dragged != null && moved
        // Index loops, so a frame makes no iterators.
        for (l in lines.indices) {
            val line = lines[l]
            val y = origin + line.y
            if (y >= clipBottom) break
            if (y + line.height <= clipTop) continue
            val held = holding && line.entry == dragged && line.kind != Kind.DETAILS
            val listLine = line.kind == Kind.HEADING || line.kind == Kind.ITEM
            if (listLine && (line === over || held || line === focusLine)) Kit.wellRow(g, x, y, width, line.height)
            when {
                held -> Kit.ring(g, x, y, width, line.height, Kit.theme.hoverOutline)
                listLine && inside && line.entry == picked && line.kind == pickedKind -> Kit.ring(g, x, y, width, line.height, Kit.theme.accent)
                listLine && line === over -> Kit.ring(g, x, y, width, line.height, Kit.theme.hoverOutline)
            }
            when (line.kind) {
                Kind.HEADING -> {
                    if ((line.entry as Entry.Heading).group.isNotEmpty()) Kit.grip(g, x + GRIP_X, y + (LINE - Kit.GRIP_HEIGHT) / 2, line === over || held)
                    val arrow = if (line.folded) Icon.RIGHT else Icon.DOWN
                    Kit.icon(g, arrow, x + ARROW_X + (5 - arrow.width) / 2, y + (LINE - arrow.height) / 2, Role.MUTED.argb())
                    g.text(font, line.text, x + HEADING_SWITCH + Kit.SWITCH_WIDTH + Kit.GAP, y + (LINE - 8) / 2, -1, true)
                    g.text(font, line.muted, line.mutedX, y + (LINE - 8) / 2, -1, true)
                }
                Kind.ITEM -> Kit.grip(g, x + ITEM_GRIP, y + (LINE - Kit.GRIP_HEIGHT) / 2, line === over || held)
                Kind.EMPTY, Kind.NOTHING -> g.text(font, line.text, x + if (line.kind == Kind.EMPTY) EMPTY_X else 0, y + (line.height - 8) / 2, -1, true)
                Kind.DETAILS -> {
                    Kit.guide(g, x + if (line.entry is Entry.Item) ITEM_GRIP + 1 else GRIP_X + 1, y, line.height - DETAILS_BELOW)
                    for (h in line.hits.indices) {
                        val hit = line.hits[h]
                        val name = hit.name ?: continue
                        if (hit.does == Does.NAME || hit.cell >= 0 && cellAt(hit.cell) !is Cell.Check) {
                            g.text(font, name, hit.x - font.width(name) - Kit.GAP, origin + hit.y + (hit.height - 8) / 2, -1, true)
                        }
                    }
                }
                Kind.FOOT -> Unit
            }
            for (h in line.hits.indices) drawHit(g, line.hits[h], line.hits[h] === overHit, line === focusLine && h == cursor, mx, my)
        }
        if (origin + statusY < clipBottom) for (i in status.indices) g.text(font, status[i], x, origin + statusY + 2 + i * 9, -1, true)
        if (holding || overHit == null && over != null && (over.kind == Kind.ITEM || over.kind == Kind.HEADING && group(over.entry).isNotEmpty())) {
            g.requestCursor(CursorTypes.RESIZE_NS)
        }
        val index = (over?.entry as? Entry.Item)?.index ?: -1
        return if (overHit?.does == Does.CELL && cellAt(overHit.cell) is Cell.Keys && overHit.clash != null && index >= 0) clashTip(index) else null
    }

    /** Names the other use of a ringed key, as vanilla's controls name a clash. */
    /** The clash tip last made, and the item it is for, kept until a refresh so the screen's hover delay runs out. */
    private var tip: SettingRows.Tip? = null
    private var tipIndex = -1

    private fun clashTip(index: Int): SettingRows.Tip {
        tip?.let { if (tipIndex == index) return it }
        val other = items.clashes(index).firstOrNull()
        val keys = type.cells.filterIsInstance<Cell.Keys<T>>().first().get(list.items[index])
        val line = if (other != null) {
            text { body("Also fires "); value(type.title(other)); body(".") }
        } else {
            text { body("Also the game's "); value(keys.firstOrNull()?.let(Keys::gameKey) ?: ""); body(" key.") }
        }
        tipIndex = index
        return SettingRows.Tip(font.split(line.toComponent(), TIP)).also { tip = it }
    }

    private fun drawHit(g: GuiGraphicsExtractor, hit: Hit, hovered: Boolean, focused: Boolean, mx: Double, my: Double) {
        val y = origin + hit.y
        if (y >= clipBottom || y + hit.height <= clipTop) return
        val state = when {
            !hit.active -> Kit.State.OFF
            hovered || focused -> Kit.State.HOVER
            else -> Kit.State.IDLE
        }
        when (hit.does) {
            Does.FOLD -> Unit
            Does.GROUP -> Kit.switch(g, hit.x, y, hit.on, state)
            Does.DETAILS, Does.UP, Does.DOWN -> Kit.button(g, hit.x, y, hit.width, hit.height, state, when (hit.does) {
                Does.UP -> Icon.UP
                Does.DOWN -> Icon.DOWN
                else -> if (hit.on) Icon.DOWN else Icon.RIGHT
            })
            Does.NAME -> drawField(g, hit, y, state, mx, my)
            Does.CELL -> drawCell(g, hit, y, state, mx, my)
            else -> {
                labelButton(g, hit, y, state, hit.caption)
                if (hit.does == Does.DELETE && armed == hit.line.entry) ringHit(g, hit, y, Role.BAD.argb())
            }
        }
        if (hovered && hit.active) g.requestCursor(if (hit.does == Does.NAME || hit.does == Does.CELL && cellAt(hit.cell) is Cell.Text) CursorTypes.IBEAM else CursorTypes.POINTING_HAND)
    }

    private fun labelButton(g: GuiGraphicsExtractor, hit: Hit, y: Int, state: Kit.State, label: String) =
        Kit.button(g, hit.x, y, hit.width, hit.height, state, label, font.width(label))

    private fun ringHit(g: GuiGraphicsExtractor, hit: Hit, y: Int, argb: Int) = Kit.ring(g, hit.x - 1, y - 1, hit.width + 2, hit.height + 2, argb)

    private fun drawField(g: GuiGraphicsExtractor, hit: Hit, y: Int, state: Kit.State, mx: Double, my: Double) {
        val open = field
        if (fieldHit === hit && open != null) {
            open.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
        } else {
            Kit.field(g, hit.x, y, hit.width, hit.height, state)
            g.text(font, hit.text, hit.x + 4, y + (hit.height - 8) / 2, -1, true)
        }
    }

    private fun drawCell(g: GuiGraphicsExtractor, hit: Hit, y: Int, state: Kit.State, mx: Double, my: Double) {
        val recording = recorder
        when (val cell = cellAt(hit.cell)) {
            is Cell.Check -> {
                Kit.switch(g, hit.x, y, hit.on, state)
                hit.name?.takeIf { hit.width > Kit.SWITCH_WIDTH }?.let { g.text(font, it, hit.x + Kit.SWITCH_WIDTH + Kit.GAP, y + (hit.height - 8) / 2, -1, true) }
            }
            is Cell.Text -> drawField(g, hit, y, state, mx, my)
            is Cell.Keys -> if (recordHit === hit && recording != null) {
                recording.extractRenderState(g, mx.toInt(), my.toInt(), 0f)
            } else {
                labelButton(g, hit, y, state, hit.label)
                hit.clash?.let { ringHit(g, hit, y, it.argb()) }
            }
            is Cell.Pick<T, *> -> labelButton(g, hit, y, state, hit.label)
            is Cell.Count -> {
                val lit = state == Kit.State.HOVER
                val side = if (!lit) 0 else if (mx < hit.x + Kit.BUTTON_HEIGHT) -1 else if (mx >= hit.x + hit.width - Kit.BUTTON_HEIGHT) 1 else 0
                Kit.button(g, hit.x, y, Kit.BUTTON_HEIGHT, hit.height, if (side == -1) Kit.State.HOVER else Kit.State.IDLE, "-", font.width("-"))
                Kit.button(g, hit.x + hit.width - Kit.BUTTON_HEIGHT, y, Kit.BUTTON_HEIGHT, hit.height, if (side == 1) Kit.State.HOVER else Kit.State.IDLE, "+", font.width("+"))
                g.text(font, hit.text, hit.x + (hit.width - font.width(hit.label)) / 2, y + (hit.height - 8) / 2, -1, true)
            }
        }
    }

    override fun drawOver(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        menu?.extractRenderState(g, mouseX, mouseY, 0f)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x
        val my = event.y
        menu?.let {
            if (it.takesPress(event, doubleClick)) return true
            // A press outside closes the menu and still does its own job.
            menu = null
        }
        field?.let {
            // A field scrolled out of the pane, such as under the tab bar, takes no press.
            if (isOver(mx, my) && it.takesPress(event, doubleClick)) return true
            closeField()
        }
        if (recorder?.pressed(event) { finishRecording() } == true) return true
        val line = lineAt(mx, my) ?: return false.also { disarm() }
        val hit = hitAt(line, mx, my)
        if (armed != null && !(hit?.does == Does.DELETE && hit.line.entry == armed)) armed = null
        said = null
        inside = true
        shownFocus = false
        if (line.kind == Kind.ITEM || line.kind == Kind.HEADING || line.kind == Kind.DETAILS || line.kind == Kind.FOOT) {
            pick(line, keepCursor = false)
            hit?.let { cursor = line.hits.indexOf(it) }
        }
        if (event.button() == 1) {
            val cell = hit?.takeIf { it.does == Does.CELL }?.let { cellAt(it.cell) }
            when {
                // A right press clears keys and steps a cycle back, as on any row. Elsewhere it opens the line's menu.
                cell is Cell.Keys -> change { set(hit, cell.set(itemAt(hit), emptyList())) }
                cell is Cell.Pick<T, *> -> stepPick(hit, if (Keys.shift(event)) 1 else -1)
                line.kind == Kind.ITEM || line.kind == Kind.HEADING || line.kind == Kind.EMPTY -> openMenu(line, mx.toInt(), my.toInt())
                else -> refresh()
            }
            return true
        }
        if (event.button() != 0) return true.also { refresh() }
        if (hit != null && hit.active) {
            press(hit, Keys.shift(event), mx)
            return true
        }
        when (line.kind) {
            Kind.HEADING -> {
                val group = (line.entry as Entry.Heading).group
                // A double press anywhere on the heading folds and unfolds it, as the arrow does.
                if (doubleClick) return true.also { fold(group, group !in list.folded) }
                if (group.isNotEmpty()) startDrag(line.entry, my)
            }
            Kind.ITEM -> startDrag(line.held, my)
            else -> Unit
        }
        refresh()
        return true
    }

    private fun startDrag(entry: Entry, mouseY: Double) {
        dragged = entry
        draggedFrom = entry
        pressY = mouseY
        moved = false
        before = list
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val entry = dragged ?: return false
        if (!moved) {
            if (Math.abs(event.y - pressY) < DRAG_START) return true
            moved = true
            // Every line is one height while a drag moves them, so the line under the mouse is found by its place.
            if (details != null) {
                details = null
                host.relayout()
            }
        }
        val listed = linesOf(Kind.HEADING, Kind.ITEM, Kind.EMPTY)
        val first = listed.firstOrNull() ?: return true
        // Past the pane's edge the line stays at the end of the list, and the pane follows it.
        val to = Math.floorDiv((event.y - (origin + first.y)).toInt(), LINE).coerceIn(0, listed.lastIndex)
        if (to == items.entries().indexOf(entry)) return true
        when (entry) {
            is Entry.Item -> {
                val at = Entry.Item(items.move(entry.index, to))
                dragged = at
                picked = at
                pickedKind = Kind.ITEM
            }
            is Entry.Heading -> items.moveGroup(entry.group, to)
            is Entry.Empty -> Unit
        }
        host.relayout()
        host.changed()
        pickedLine()?.let { host.scrollTo(it.y - LINE, it.bottom + LINE) }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        recorder?.let {
            it.release(event.button())
            if (!it.recording) finishRecording()
        }
        if (dragged == null) return false
        dragged = null
        draggedFrom = null
        before = null
        moved = false
        refresh()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean {
        if (menu != null || amount == 0.0) return false
        val line = lineAt(mouseX, mouseY) ?: return false
        val hit = hitAt(line, mouseX, mouseY)?.takeIf { it.does == Does.CELL } ?: return false
        return when (cellAt(hit.cell)) {
            is Cell.Pick<T, *> -> true.also { stepPick(hit, if (amount > 0) -1 else 1) }
            is Cell.Count -> true.also { stepCount(hit, if (amount > 0) 1 else -1) }
            else -> false
        }
    }

    /** What a press on [hit] does, or Enter and Space on it. A press on a count steps by the side it lands on, and a key steps up. */
    private fun press(hit: Hit, shift: Boolean, mouseX: Double? = null) {
        val entry = hit.line.entry
        when (hit.does) {
            Does.CELL -> when (val cell = cellAt(hit.cell)) {
                is Cell.Check -> change { set(hit, cell.set(itemAt(hit), !cell.get(itemAt(hit)))) }
                is Cell.Text -> openText(hit, cell)
                is Cell.Keys -> record(hit, cell)
                is Cell.Pick<T, *> -> stepPick(hit, if (shift) -1 else 1)
                is Cell.Count -> stepCount(hit, if (mouseX == null) (if (shift) -1 else 1) else if (mouseX < hit.x + Kit.BUTTON_HEIGHT) -1 else if (mouseX >= hit.x + hit.width - Kit.BUTTON_HEIGHT) 1 else 0)
            }
            Does.FOLD -> fold(group(entry), group(entry) !in list.folded)
            Does.GROUP -> change { items.switchGroup(group(entry), group(entry) in list.off) }
            Does.DETAILS -> toggleDetails(hit.line.held)
            Does.UP, Does.DOWN -> nudge(hit.line.held, if (hit.does == Does.UP) -1 else 1)
            Does.DUPLICATE -> duplicate(entry as Entry.Item)
            Does.DELETE -> delete(hit.line.held)
            Does.EXPORT -> export(exportGroup(hit))
            Does.NAME -> openField(hit, (entry as Entry.Heading).group, type.groupLength, NAME, "")
            Does.ADD -> add()
            Does.ADD_GROUP -> addGroup()
            Does.IMPORT -> import()
        }
    }

    private fun reveal(line: Line) = host.scrollTo(line.y - Kit.GAP, line.bottom + Kit.GAP)

    private fun itemOf(hit: Hit): T? = (hit.line.entry as? Entry.Item)?.let { list.items.getOrNull(it.index) }

    private fun itemAt(hit: Hit): T = checkNotNull(itemOf(hit)) { "a cell's line holds an item" }

    private fun set(hit: Hit, item: T) = items.set((hit.line.entry as Entry.Item).index, item)

    private fun stepPick(hit: Hit, by: Int) {
        val cell = cellAt(hit.cell) as Cell.Pick<T, *>
        change { set(hit, cell.step(itemAt(hit), by)) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun stepCount(hit: Hit, by: Int) {
        if (by == 0) return
        val cell = cellAt(hit.cell) as Cell.Count<T>
        change { set(hit, cell.step(itemAt(hit), by)) }
    }

    /** A change to the values: every line and row reads them again. */
    private inline fun change(block: () -> Unit) {
        block()
        said = null
        armed = null
        host.changed()
    }

    /** A change to what the list holds: it lays out again, then the keys' line comes into view. */
    private inline fun restructure(block: () -> Unit) {
        block()
        host.relayout()
        host.changed()
        pickedLine()?.let(::reveal)
    }

    private fun pick(line: Line?, keepCursor: Boolean) {
        // The foot line has no entry of its own. Export still means the group that was picked before it.
        if (line?.kind == Kind.FOOT) {
            if (pickedKind != Kind.FOOT) footGroup = group(picked)
        } else {
            footGroup = null
        }
        picked = line?.entry
        pickedKind = line?.kind ?: Kind.NOTHING
        if (!keepCursor) cursor = 0
    }

    private fun pickEntry(entry: Entry?) {
        picked = entry
        pickedKind = when (entry) {
            is Entry.Heading -> Kind.HEADING
            is Entry.Item -> Kind.ITEM
            is Entry.Empty -> Kind.EMPTY
            null -> Kind.NOTHING
        }
    }

    private fun fold(group: String, folded: Boolean) {
        // An item folded away cannot stay picked, so its group is picked instead.
        val p = picked
        if (folded && p is Entry.Item && group(p) == group) pickEntry(Entry.Heading(group))
        if (folded && details is Entry.Item && group(details) == group) details = null
        restructure { items.fold(group, folded) }
    }

    private fun toggleDetails(entry: Entry) {
        details = if (details == entry) null else entry
        host.relayout()
    }

    private fun nudge(entry: Entry, by: Int) {
        if (!items.canNudge(entry, by)) return
        val open = details == entry
        restructure {
            val at = items.nudge(entry, by)
            if (open) details = at
            pickEntry(at)
        }
    }

    private fun duplicate(entry: Entry.Item) {
        restructure { pickEntry(Entry.Item(items.duplicate(entry.index))) }
        say("Duplicated. The copy starts off.")
    }

    private fun delete(entry: Entry) {
        if (entry is Entry.Heading && entry.group.isEmpty() || entry is Entry.Empty) return
        if (armed != entry) {
            // The first press rings Delete, which the details hold, so they open to show it.
            armed = entry
            said = null
            if (details != entry) {
                details = entry
                host.relayout()
            } else {
                refresh()
            }
            return
        }
        armed = null
        val home = group(entry)
        if (entry is Entry.Item) {
            val title = type.title(list.items[entry.index])
            restructure {
                details = null
                items.delete(entry.index)
                pickEntry(Entry.Heading(home))
            }
            say("Deleted $title.")
        } else {
            var gone = 0
            restructure {
                details = null
                gone = items.deleteGroup(home)
                pickEntry(Entry.Heading(""))
            }
            say("Deleted $home${if (gone > 0) " and its ${items.plural(gone)}" else ""}.")
        }
    }

    private fun disarm() {
        if (armed == null) return
        armed = null
        refresh()
    }

    private fun add() {
        val group = group(picked)
        var at = 0
        restructure {
            at = items.add(group)
            pickEntry(Entry.Item(at))
        }
        // A new one wants its command first.
        val line = pickedLine() ?: return
        val command = line.hits.lastOrNull { it.does == Does.CELL && cellAt(it.cell) is Cell.Text } ?: return
        cursor = line.hits.indexOf(command)
        val cell = cellAt(command.cell) as Cell.Text<T>
        openText(command, cell, "")
    }

    private fun addGroup() {
        var name = ""
        restructure {
            name = items.newGroup()
            details = Entry.Heading(name)
            pickEntry(Entry.Heading(name))
        }
        rename(name)
    }

    /** Opens the group's details with its name selected, to type over. */
    private fun rename(group: String) {
        if (details != Entry.Heading(group)) {
            details = Entry.Heading(group)
            host.relayout()
        }
        val hit = lines.firstOrNull { it.kind == Kind.DETAILS && it.entry == Entry.Heading(group) }?.hits?.firstOrNull { it.does == Does.NAME } ?: return
        openField(hit, group, type.groupLength, NAME, "")
        field?.setHighlightPos(0)
    }

    /** The group Export copies. On the foot it is the group picked before the foot, which [pick] remembers. */
    private fun exportGroup(hit: Hit): String =
        if (hit.line.kind == Kind.FOOT) footGroup ?: group(picked) else group(hit.line.entry ?: picked)

    private fun export(group: String) {
        val text = items.export(group) ?: return say("${items.groupName(group)} has no ${type.noun}s to export.", bad = true)
        Minecraft.getInstance().keyboardHandler.clipboard = text
        say("Copied ${items.plural(items.count(group))} from ${items.groupName(group)}. Paste them with Import.")
    }

    private fun import() {
        var result: Pair<String, Int>? = null
        restructure {
            result = items.import(Minecraft.getInstance().keyboardHandler.clipboard)
            result?.let { pickEntry(Entry.Heading(it.first)) }
        }
        val (_, count) = result ?: return say("No ${type.noun}s on the clipboard. Use Export first.", bad = true)
        say("Imported ${items.plural(count)}. Each starts off, so read them before you turn them on.")
    }

    private fun say(text: String, bad: Boolean = false) {
        said = text
        saidBad = bad
        armed = null
        refresh()
    }

    private fun openMenu(line: Line, mouseX: Int, mouseY: Int) {
        val entry = line.entry ?: return
        val menuItems = ArrayList<Pair<String, () -> Unit>>()
        if (entry is Entry.Item) {
            val item = list.items[entry.index]
            menuItems += "Duplicate" to { duplicate(entry) }
            menuItems += (if (type.on(item)) "Turn Off" else "Turn On") to {
                change { items.set(entry.index, type.switched(list.items[entry.index], !type.on(list.items[entry.index]))) }
            }
            val keys = type.cells.filterIsInstance<Cell.Keys<T>>().firstOrNull()
            if (keys != null && keys.get(item).isNotEmpty()) menuItems += "Clear Keys" to { change { items.set(entry.index, keys.set(list.items[entry.index], emptyList())) } }
            moveItems(menuItems, entry)
            menuItems += "Delete" to { delete(entry) }
        } else {
            val group = group(entry)
            val folded = group in list.folded
            menuItems += (if (folded) "Expand" else "Collapse") to { fold(group, !folded) }
            val off = group in list.off
            menuItems += (if (off) "Turn On" else "Turn Off") to { change { items.switchGroup(group, off) } }
            if (group.isNotEmpty()) menuItems += "Rename" to { rename(group) }
            moveItems(menuItems, Entry.Heading(group))
            if (items.count(group) > 0) menuItems += "Export" to { export(group) }
            if (group.isNotEmpty()) menuItems += "Delete" to { delete(Entry.Heading(group)) }
        }
        menu = KitMenu(mouseX, mouseY, screenWidth, screenHeight, menuItems, { menu = null })
        refresh()
    }

    /** Move Up and Move Down, where each would move the line. */
    private fun moveItems(menuItems: MutableList<Pair<String, () -> Unit>>, entry: Entry) {
        if (items.canNudge(entry, -1)) menuItems += "Move Up" to { nudge(entry, -1) }
        if (items.canNudge(entry, 1)) menuItems += "Move Down" to { nudge(entry, 1) }
    }

    private fun openText(hit: Hit, cell: Cell.Text<T>, value: String = cell.get(itemAt(hit))) = openField(hit, value, cell.max, cell.name, cell.hint)

    private fun openField(hit: Hit, value: String, max: Int, name: String, hint: String) {
        closeField()
        finishRecording()
        val open = KitTextField(hit.x, origin + hit.y, hit.width, name, value, max) { typed -> typedInto(typed) }
        if (hint.isNotEmpty()) open.hint(hint)
        open.setFocused(true)
        open.moveCursorToEnd(false)
        field = open
        fieldHit = hit
        fieldEntry = hit.line.entry
        fieldKind = hit.line.kind
        fieldIndex = hit.line.hits.indexOf(hit)
        refresh()
    }

    /** What is typed applies at once, as everything in the settings does. A group takes a new name once no other has it. */
    private fun typedInto(typed: String) {
        val hit = fieldHit ?: return
        if (hit.does == Does.NAME) {
            val group = (hit.line.entry as Entry.Heading).group
            val trouble = items.rename(group, typed)
            said = trouble
            saidBad = trouble != null
            val name = typed.trim()
            if (trouble == null && name != group) {
                val renamed = Entry.Heading(name)
                if (details == hit.line.entry) details = renamed
                if (picked == hit.line.entry) picked = renamed
                fieldEntry = renamed
                host.relayout()
            }
            host.changed()
            return
        }
        val cell = cellAt(hit.cell) as Cell.Text<T>
        val item = itemOf(hit) ?: return
        set(hit, cell.set(item, typed))
        said = null
        host.changed()
    }

    private fun closeField() {
        val open = field ?: return
        field = null
        fieldHit = null
        // A group's name that could not be taken goes back to the one it kept.
        if (saidBad) said = null
        open.setFocused(false)
        refresh()
    }

    private fun record(hit: Hit, cell: Cell.Keys<T>) {
        closeField()
        finishRecording()
        val item = itemOf(hit) ?: return
        val keys = KitKeysButton(hit.x, origin + hit.y, hit.width, cell.name, cell.get(item), cell.max, hit.height) { recorded ->
            itemOf(hit)?.let { change { set(hit, cell.set(it, recorded)) } }
        }
        keys.inOrder = cell.inOrder(item)
        keys.onPress(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        recorder = keys
        recordHit = hit
        refresh()
    }

    private fun finishRecording() {
        val keys = recorder ?: return
        if (keys.recording) keys.setFocused(false)
        recorder = null
        recordHit = null
        refresh()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        swallow = false
        menu?.let { open ->
            open.keyPressed(event)
            swallow = true
            return true
        }
        field?.let { open ->
            val key = event.key()
            when {
                key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || event.isEscape -> closeField()
                key == GLFW.GLFW_KEY_TAB -> nextField(if (Keys.shift(event)) -1 else 1)
                else -> open.keyPressed(event)
            }
            return true
        }
        recorder?.let { keys ->
            keys.capture(event.key())
            if (!keys.recording) finishRecording() else refresh()
            swallow = true
            return true
        }
        if (dragged != null && moved) {
            // Escape during a drag puts the list back, and the drag keeps every other key until it ends.
            if (event.isEscape) {
                val home = draggedFrom
                before?.let { items.value = it }
                dragged = null
                draggedFrom = null
                before = null
                moved = false
                host.relayout()
                pickEntry(home)
                host.changed()
            }
            return true
        }
        if (!inside) {
            if (event.isDown || event.isRight || event.isSelection || event.isConfirmation) return true.also { enterAt(first = true) }
            return false
        }
        val line = pickedLine()
        val key = event.key()
        shownFocus = true
        when {
            Keys.alt(event) && (event.isUp || event.isDown) -> picked?.let { if (pickedKind == Kind.ITEM || pickedKind == Kind.HEADING) nudge(it, if (event.isUp) -1 else 1) }
            event.isUp || event.isDown -> return step(if (event.isUp) -1 else 1)
            key == GLFW.GLFW_KEY_DELETE -> picked?.let { if (pickedKind == Kind.ITEM || pickedKind == Kind.HEADING) delete(it) }
            key == GLFW.GLFW_KEY_MENU || key == GLFW.GLFW_KEY_F10 && Keys.shift(event) -> line?.let { openMenu(it, x + Kit.GAP, origin + it.bottom) }
            line?.kind == Kind.HEADING -> {
                val group = group(line.entry)
                when {
                    event.isLeft -> fold(group, true)
                    event.isRight -> fold(group, false)
                    key == GLFW.GLFW_KEY_SPACE -> change { items.switchGroup(group, group in list.off) }
                    event.isSelection || event.isConfirmation -> toggleDetails(line.held)
                    else -> return false
                }
            }
            line != null && line.hits.isNotEmpty() -> when {
                event.isLeft || event.isRight -> cursor = (cursor + if (event.isLeft) -1 else 1).coerceIn(0, line.hits.lastIndex)
                event.isSelection || event.isConfirmation -> line.hits.getOrNull(cursor)?.takeIf { it.active }?.let { press(it, Keys.shift(event)) }
                else -> return false
            }
            else -> return false
        }
        refresh()
        return true
    }

    /** Moves the keys a line, past the lines that hold nothing to press, and out of the list at either end. */
    private fun step(by: Int): Boolean {
        val stops = linesOf(Kind.HEADING, Kind.ITEM, Kind.DETAILS, Kind.FOOT)
        val at = stops.indexOfFirst { it.entry == picked && it.kind == pickedKind }
        val next = at + by
        if (at < 0 || next !in stops.indices) {
            // Up from the first line gives the keys back to the row's name, and Down from the last goes on to the next row.
            inside = false
            refresh()
            return by < 0 && at >= 0
        }
        val line = stops[next]
        val column = pickedLine()?.hits?.getOrNull(cursor)?.x
        pick(line, keepCursor = true)
        // The keys keep to the control nearest the one they were on, as a grid's do.
        cursor = if (column == null || line.hits.isEmpty()) 0 else line.hits.indices.minBy { Math.abs(line.hits[it].x - column) }
        reveal(line)
        refresh()
        return true
    }

    private fun enterAt(first: Boolean) {
        inside = true
        shownFocus = true
        val stops = linesOf(Kind.HEADING, Kind.ITEM, Kind.FOOT)
        val line = if (first) stops.firstOrNull() else stops.lastOrNull()
        pick(line, keepCursor = false)
        line?.let(::reveal)
        refresh()
    }

    /** Tab in a field goes on to the next field of the line, and Shift with it back. */
    private fun nextField(by: Int) {
        val hit = fieldHit ?: return
        val fields = hit.line.hits.filter { it.does == Does.CELL && cellAt(it.cell) is Cell.Text }
        val next = fields.getOrNull(fields.indexOf(hit) + by)
        closeField()
        if (next == null) return
        cursor = hit.line.hits.indexOf(next)
        val cell = cellAt(next.cell) as Cell.Text<T>
        openText(next, cell)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        val keys = recorder ?: return false
        keys.release(event.key())
        if (!keys.recording) finishRecording()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        field?.let {
            it.charTyped(event)
            return true
        }
        menu?.let {
            it.charTyped(event)
            return true
        }
        if (swallow) {
            swallow = false
            return true
        }
        return false
    }

    override fun enter(fromAbove: Boolean) {
        if (fromAbove) inside = false else enterAt(first = false)
    }

    override fun leave() {
        inside = false
        closeField()
        finishRecording()
        menu = null
        disarm()
    }

    override fun narrate(output: NarrationElementOutput): Boolean {
        (menu ?: field ?: recorder)?.let {
            it.updateNarration(output)
            return true
        }
        if (!inside) return false
        val line = pickedLine() ?: return false
        val hit = line.hits.getOrNull(cursor)
        val said = if (line.kind == Kind.HEADING || line.kind == Kind.ITEM) items.lineNarration(line.held, Keys::names) else null
        output.add(NarratedElementType.TITLE, Narration.join(said, hit?.let(::spoken)))
        if (status.isNotEmpty()) output.add(NarratedElementType.HINT, status.joinToString(" ") { it.string })
        return true
    }

    /** A control as the narrator says it: its name, its kind and its value. */
    private fun spoken(hit: Hit): String = when (hit.does) {
        Does.CELL -> when (val cell = cellAt(hit.cell)) {
            is Cell.Check -> Narration.join(cell.name, "switch", if (hit.on) "on" else "off")
            is Cell.Text -> Narration.join(cell.name, "text", itemOf(hit)?.let(cell.get)?.ifEmpty { "empty" })
            is Cell.Keys -> Narration.join(cell.name, "key bind", hit.label)
            is Cell.Pick<T, *> -> Narration.join(cell.name, "button", hit.label)
            is Cell.Count -> Narration.join(cell.name, "number", hit.label)
        }
        Does.GROUP -> Narration.join("Group", "switch", if (hit.on) "on" else "off")
        Does.NAME -> Narration.join(NAME, "text", (hit.line.entry as? Entry.Heading)?.group)
        Does.DETAILS -> Narration.join("Settings", "button", if (hit.on) "expanded" else "collapsed")
        else -> Narration.join(hit.caption, "button", if (hit.active) null else "off")
    }

    private companion object {
        const val LINE = 18
        const val MESSAGE = 14
        const val STATUS = 20
        const val STATUS_LINES = 2
        const val SETTINGS = 14
        const val GRIP_X = 1
        const val ARROW_X = 7
        const val ARROW_ROOM = 9
        const val HEADING_SWITCH = 14
        const val ITEM_GRIP = 12
        const val ITEM_SWITCH = 18
        const val EMPTY_X = ITEM_SWITCH
        const val COUNT = Kit.BUTTON_HEIGHT * 2 + 20
        const val MIN_GROW = 40
        const val NAME_FIELD = 140
        const val NAME = "Name"
        const val DETAILS_BELOW = 2
        const val DRAG_START = 2.0
        const val TIP = 170

        /** A cell's place among the details, past the places of the line's own cells. */
        const val DETAIL = 100
    }
}
