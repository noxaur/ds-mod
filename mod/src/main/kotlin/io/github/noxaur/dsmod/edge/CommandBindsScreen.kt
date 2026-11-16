package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.BindShare
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.input.CommandBinds.Entry
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.EditorScreen
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import io.github.noxaur.dsmod.edge.ui.KitMenu
import io.github.noxaur.dsmod.edge.ui.KitStepper
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.fit
import io.github.noxaur.dsmod.edge.ui.wrap
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import java.util.IdentityHashMap
import kotlin.math.roundToInt

private const val TITLE = "Command Binds"
private const val LIST_WIDTH = 160
private const val DETAIL_WIDTH = 232
private const val SPLIT = 7
private const val WIDTH = LIST_WIDTH + SPLIT + DETAIL_WIDTH
private const val LABEL_WIDTH = 48
private const val LINE = 10
private const val READOUT_LINES = 2
private const val ROW = 22
private const val ROW_STEP = ROW + 2
private const val FIELD_STEP = Kit.BUTTON_HEIGHT + Kit.GAP
private const val CHECK_STEP = Kit.CHECKBOX_SIZE + Kit.GAP
private const val MAX_PRIORITY = 9

/** Where a row's arrow, switch and text start, from the row's left edge. */
private const val ARROW_X = 3
private const val BOX_X = 11
private const val TEXT_X = BOX_X + Kit.CHECKBOX_SIZE + Kit.GAP

/** Anywhere, then each area a bind can be limited to. */
private val AREAS: List<Area?> = listOf(null) + (Area.entries - Area.ANYWHERE)

/**
 * Command Binds on one screen: the binds in a container on the left under their groups' headings, and everything
 * about the one picked on the right, where each change takes effect at once.
 *
 * A heading has an arrow that folds its group away, a switch for the whole group, its name and how many binds it
 * has. A bind's row has its own switch, its name and command, and its keys with where it works. A row or heading
 * is dragged to move it, and a bind dragged under another heading joins that group. A right press opens a menu
 * of what can be done to the row. The keyboard works the list too: Up and Down pick, Left and Right fold and
 * unfold, Alt with Up or Down moves, and Delete deletes. Deletes ask for a second press. The list scrolls by whole
 * rows: the wheel, the scrollbar's thumb, Page Up and Page Down, and Home and End. The line under the details
 * says what stops the pick, or what the last action did, and is empty when all is well. Closing saves.
 */
class CommandBindsScreen(private val settings: DsModConfig.General, parent: Screen?, onSave: () -> Unit) :
    EditorScreen(TITLE, parent, onSave) {
    /** The bind or the group picked on the left, by its place in the binds or its name. */
    private var picked: Any? = null
    private var first = 0
    private var armed = false
    private var said: String? = null
    private var saidBad = false
    private var scrolling = false

    /** The bind or the group being dragged, and the lists as they were when it began, for Escape to put back. */
    private var dragged: Any? = null
    private var beforeDrag: Triple<List<CommandBind>, List<String>, Any?>? = null

    private var entries: List<Entry> = emptyList()
    private val bindBoxes = IdentityHashMap<CommandBind, KitCheckbox>()
    private val groupBoxes = HashMap<String, KitCheckbox>()
    private var keys: KitKeysButton? = null
    private var delete: KitButton? = null
    private var groupField: KitTextField? = null
    private var menu: KitMenu? = null

    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var contentY = 0
    private var listY = 0
    private var containerHeight = 0
    private var shown = 0
    private var detailX = 0
    private var readoutY = 0
    private var readout = emptyList<String>()
    private var labels = emptyList<Triple<String, Int, Int>>()

    private val groups: List<String> get() = listOf("") + settings.bindGroups
    private val bind: CommandBind? get() = (picked as? Int)?.let(settings.binds::getOrNull)
    private val group: String? get() = picked as? String
    private val listHeight get() = shown * ROW_STEP - 2
    private val rowsX get() = left + 2
    private val rowWidth get() = LIST_WIDTH - Kit.SCROLLBAR_WIDTH - 6
    private val scrollX get() = left + LIST_WIDTH - Kit.SCROLLBAR_WIDTH - 2

    /** The binds that can send: on, in a group that is on. */
    private val live: List<CommandBind> get() = settings.binds.filter { it.group !in settings.offGroups }

    init {
        // Opening shows the first bind, so its details are there without a click.
        picked = entries().filterIsInstance<Entry.Bind>().firstOrNull()?.let { entry -> settings.binds.indexOfFirst { it === entry.bind } }
    }

    private fun entries() = CommandBinds.entries(settings.binds, groups, settings.collapsedGroups)

    override fun init() {
        val detailHeight = FIELD_STEP * 6 + CHECK_STEP * 2 + READOUT_LINES * LINE + Kit.GAP * 2 + Kit.BUTTON_HEIGHT
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT + Kit.GAP + detailHeight
        panelX = (width - WIDTH - Kit.INSET * 2) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        contentY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        // The rows sit 2 in from the container's edge.
        listY = contentY + 2
        val bottom = panelY + panelHeight - Kit.INSET - Kit.BUTTON_HEIGHT
        containerHeight = bottom - Kit.GAP - contentY
        shown = (containerHeight - 4 + 2) / ROW_STEP
        detailX = left + LIST_WIDTH + SPLIT
        readoutY = contentY
        menu = null

        var x = left + WIDTH
        for ((label, action) in listOf("Done" to ::onClose, "Export" to ::export, "Import" to ::import)) {
            val buttonWidth = font.width(label) + 16
            x -= buttonWidth
            addRenderableWidget(KitButton(x, top, label, action, buttonWidth))
            x -= Kit.GAP
        }
        val half = (LIST_WIDTH - Kit.GAP) / 2
        addRenderableWidget(KitButton(left, bottom, "New Bind", ::newBind, half))
        addRenderableWidget(KitButton(left + half + Kit.GAP, bottom, "New Group", ::newGroup, half))

        entries = entries()
        bindBoxes.clear()
        groupBoxes.clear()
        for (entry in entries) {
            when (entry) {
                is Entry.Bind -> entry.bind.let { bind ->
                    bindBoxes[bind] = addRenderableWidget(KitCheckbox(rowsX + BOX_X, 0, "", bind.enabled, onWell = true) { on -> change(bind, bind.copy(enabled = on)) })
                }
                is Entry.Heading -> entry.group.let { group ->
                    groupBoxes[group] = addRenderableWidget(KitCheckbox(rowsX + BOX_X, 0, "", group !in settings.offGroups, onWell = true) { on -> switchGroup(group, on) })
                }
                is Entry.Empty -> {}
            }
        }
        keys = null
        delete = null
        groupField = null
        val labels = ArrayList<Triple<String, Int, Int>>()
        when {
            bind != null -> details(bind!!, labels, bottom)
            group != null -> groupDetails(group!!, labels, bottom)
        }
        this.labels = labels
        layout()
        refresh()
    }

    /** Everything about [bind], each change saved at once. */
    private fun details(bind: CommandBind, labels: MutableList<Triple<String, Int, Int>>, bottom: Int) {
        val index = picked as Int
        fun set(change: (CommandBind) -> CommandBind) {
            val now = settings.binds[index]
            change(now, change(now))
        }
        val controlX = detailX + LABEL_WIDTH
        val controlWidth = DETAIL_WIDTH - LABEL_WIDTH
        fun label(text: String, y: Int) {
            labels += Triple(text, detailX, y + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2)
        }

        var y = contentY
        label("Command", y)
        addRenderableWidget(KitTextField(controlX, y, controlWidth, "Command", bind.command, CommandBind.MAX_LENGTH) { typed -> set { it.copy(command = typed) } })
            .setHint(text { muted("/warp end") }.toComponent())
        y += FIELD_STEP
        label("Keys", y)
        keys = addRenderableWidget(KitKeysButton(controlX, y, controlWidth - Kit.BUTTON_HEIGHT - Kit.GAP, "Keys", bind.keys, CommandBind.MAX_KEYS) { recorded -> set { it.copy(keys = recorded) } })
            .also { it.inOrder = bind.inOrder }
        addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - Kit.BUTTON_HEIGHT, y, Icon.CROSS, "Clear Keys") { keys?.clear() })
        y += FIELD_STEP
        label("Name", y)
        addRenderableWidget(KitTextField(controlX, y, controlWidth, "Name", bind.name, CommandBind.MAX_LENGTH) { typed -> set { it.copy(name = typed) } })
            .setHint(text { muted("Optional") }.toComponent())
        y += FIELD_STEP
        label("Where", y)
        addRenderableWidget(KitCycleButton(controlX, y, controlWidth, "Where", Where.entries, bind.where, Where::title) { where -> set { it.copy(where = where) } })
        y += FIELD_STEP
        label("Area", y)
        addRenderableWidget(KitCycleButton(controlX, y, controlWidth, "Area", AREAS, bind.area, { it?.title ?: "Anywhere" }) { area -> set { it.copy(area = area) } })
        y += FIELD_STEP
        label("Priority", y)
        val stepper = addRenderableWidget(KitStepper(controlX, y, "Priority", 20) { step ->
            set { it.copy(priority = (it.priority + step).coerceIn(-MAX_PRIORITY, MAX_PRIORITY)) }
            children().filterIsInstance<KitStepper>().forEach { s -> s.value = settings.binds[index].priority.toString() }
        })
        stepper.value = bind.priority.toString()
        y += FIELD_STEP
        addRenderableWidget(KitCheckbox(controlX, y, "Other Keys Held Too", bind.extraKeys) { on -> set { it.copy(extraKeys = on) } })
        y += CHECK_STEP
        addRenderableWidget(KitCheckbox(controlX, y, "Keys in Order", bind.inOrder) { on ->
            set { it.copy(inOrder = on) }
            keys?.inOrder = on
        })
        readoutY = y + CHECK_STEP + Kit.GAP

        val deleteWidth = font.width("Delete") + 16
        delete = addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth, bottom, "Delete", ::deleteBind, deleteWidth))
        val copyWidth = font.width("Duplicate") + 16
        addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth - Kit.GAP - copyWidth, bottom, "Duplicate", ::duplicate, copyWidth))
        moveButtons(detailX + DETAIL_WIDTH - deleteWidth - copyWidth - Kit.GAP * 2, bottom)
    }

    /** Up and down buttons that end at [right], each off where it would move nothing. */
    private fun moveButtons(right: Int, y: Int) {
        for ((i, by) in listOf(1, -1).withIndex()) {
            val button = KitButton(right - (Kit.BUTTON_HEIGHT + Kit.GAP) * (i + 1), y, if (by < 0) Icon.UP else Icon.DOWN, if (by < 0) "Move Up" else "Move Down") { nudge(by) }
            addRenderableWidget(button).active = canNudge(by)
        }
    }

    /** The picked group: its name, which General keeps, and Delete. */
    private fun groupDetails(group: String, labels: MutableList<Triple<String, Int, Int>>, bottom: Int) {
        val controlX = detailX + LABEL_WIDTH
        labels += Triple("Group", detailX, contentY + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2)
        groupField = addRenderableWidget(KitTextField(controlX, contentY, DETAIL_WIDTH - LABEL_WIDTH, "Group Name", groupName(group), BindShare.GROUP_LENGTH, ::rename))
            .also { it.setEditable(group.isNotEmpty()) }
        readoutY = contentY + FIELD_STEP + Kit.GAP
        if (group.isNotEmpty()) {
            val deleteWidth = font.width("Delete") + 16
            delete = addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth, bottom, "Delete", ::deleteGroup, deleteWidth))
            moveButtons(detailX + DETAIL_WIDTH - deleteWidth - Kit.GAP, bottom)
        }
    }

    private fun layout() {
        first = first.coerceIn(0, (entries.size - shown).coerceAtLeast(0))
        for ((i, entry) in entries.withIndex()) {
            val box = when (entry) {
                is Entry.Bind -> bindBoxes[entry.bind]
                is Entry.Heading -> groupBoxes[entry.group]
                is Entry.Empty -> null
            } ?: continue
            box.y = listY + (i - first) * ROW_STEP + (ROW - Kit.CHECKBOX_SIZE) / 2
            box.visible = i - first in 0 until shown
        }
    }

    /** Picks a bind by its place in the binds, or a group by its name, and shows it on the right. */
    private fun pick(what: Any?) {
        picked = what
        armed = false
        said = null
        rebuildWidgets()
        entries.indexOfFirst { it.matches(what) }.takeIf { it >= 0 }?.let(::scrollTo)
    }

    private fun Entry.matches(what: Any?) = when (this) {
        is Entry.Bind -> what is Int && settings.binds.getOrNull(what) === bind
        is Entry.Heading -> what == group
        is Entry.Empty -> false
    }

    /** What a line of the list picks: a bind's place in the binds, or a group's name. */
    private fun Entry.target(): Any = when (this) {
        is Entry.Bind -> settings.binds.indexOfFirst { it === bind }
        is Entry.Heading -> group
        is Entry.Empty -> group
    }

    /** Puts [next] in place of [old] without rebuilding, so a field being typed in keeps its focus. */
    private fun change(old: CommandBind, next: CommandBind) {
        val index = settings.binds.indexOfFirst { it === old }.takeIf { it >= 0 } ?: return
        settings.binds = settings.binds.toMutableList().also { it[index] = next }
        entries = entries()
        bindBoxes.remove(old)?.let { bindBoxes[next] = it }
        armed = false
        said = null
        refresh()
    }

    private fun switchGroup(group: String, on: Boolean) {
        settings.offGroups = if (on) settings.offGroups - group else settings.offGroups + group
        groupBoxes[group]?.checked = on
        said = null
        refresh()
    }

    private fun collapse(group: String, folded: Boolean) {
        if (folded == group in settings.collapsedGroups) return
        settings.collapsedGroups = if (folded) settings.collapsedGroups + group else settings.collapsedGroups - group
        // A bind folded away cannot stay picked, so its group is picked instead.
        if (folded && bind?.group == group) picked = group
        rebuildWidgets()
        entries.indexOfFirst { it.matches(picked) }.takeIf { it >= 0 }?.let(::scrollTo)
    }

    private fun newBind() {
        val group = group ?: bind?.group ?: ""
        collapse(group, false)
        val fresh = CommandBind(group = group)
        // A new bind goes last in its group.
        settings.binds = CommandBinds.entries(settings.binds + fresh, groups).filterIsInstance<Entry.Bind>().map { it.bind }
        pick(settings.binds.indexOfFirst { it === fresh })
        children().filterIsInstance<KitTextField>().firstOrNull()?.let(::setFocused)
    }

    private fun duplicate() {
        val bind = bind ?: return
        val copy = bind.copy(enabled = false)
        settings.binds = settings.binds.toMutableList().apply { add(indexOf(bind) + 1, copy) }
        pick(settings.binds.indexOfFirst { it === copy })
        say("Duplicated. The copy starts off.")
    }

    private fun deleteBind() {
        val bind = bind ?: return
        if (!armed) return arm()
        settings.binds = settings.binds.filter { it !== bind }
        pick(bind.group)
        say("Deleted ${bind.title}.")
    }

    private fun setEnabled(bind: CommandBind, on: Boolean) {
        val next = bind.copy(enabled = on)
        change(bind, next)
        bindBoxes[next]?.checked = on
    }

    private fun newGroup() {
        var n = groups.size
        while ("Group $n" in groups) n++
        settings.bindGroups += "Group $n"
        pick("Group $n")
        groupField?.let {
            setFocused(it)
            it.moveCursorToEnd(false)
            it.setHighlightPos(0)
        }
    }

    /** Renames the picked group as its field is typed in, once the name is one no other group has. */
    private fun rename(typed: String) {
        val group = group?.takeIf { it.isNotEmpty() } ?: return
        val name = typed.trim()
        when {
            name == group -> said = null
            name.isEmpty() -> return say("Give the group a name.", bad = true)
            name == groupName("") || name in groups -> return say("Another group is called $name.", bad = true)
            else -> {
                fun List<String>.renamed() = map { if (it == group) name else it }
                settings.bindGroups = settings.bindGroups.renamed()
                settings.collapsedGroups = settings.collapsedGroups.renamed()
                settings.offGroups = settings.offGroups.renamed()
                settings.binds = settings.binds.map { if (it.group == group) it.copy(group = name) else it }
                picked = name
                said = null
                entries = entries()
                groupBoxes.remove(group)?.let { groupBoxes[name] = it }
            }
        }
        refresh()
    }

    private fun deleteGroup() {
        val group = group?.takeIf { it.isNotEmpty() } ?: return
        if (!armed) return arm()
        val count = settings.binds.count { it.group == group }
        settings.binds = settings.binds.filter { it.group != group }
        settings.bindGroups = settings.bindGroups - group
        settings.collapsedGroups = settings.collapsedGroups - group
        settings.offGroups = settings.offGroups - group
        pick("")
        say("Deleted $group${if (count > 0) " and its ${plural(count)}" else ""}.")
    }

    private fun arm() {
        armed = true
        said = null
        refresh()
    }

    /** Copies the group of whatever is picked, so the binds can be pasted into another game with Import. */
    private fun export() {
        val group = group ?: bind?.group ?: ""
        val binds = settings.binds.filter { it.group == group }
        if (binds.isEmpty()) return say("${groupName(group)} has no binds to export.", bad = true)
        minecraft.keyboardHandler.clipboard = BindShare.encode(groupName(group), binds)
        say("Copied ${plural(binds.size)} from ${groupName(group)}. Paste them with Import.")
    }

    private fun import() {
        val shared = BindShare.decode(minecraft.keyboardHandler.clipboard)
        if (shared == null || shared.binds.isEmpty()) return say("No binds on the clipboard. Use Export first.", bad = true)
        val base = shared.group.takeUnless { it.isEmpty() || it == groupName("") } ?: "Imported"
        var name = base
        var n = 2
        while (name in groups) name = "$base ${n++}"
        settings.bindGroups += name
        settings.binds += shared.binds.map { it.copy(group = name) }
        pick(name)
        // The new group is last, so the list scrolls to its end to show its binds, which the player should read.
        scrollTo(entries.lastIndex)
        say("Imported ${plural(shared.binds.size)}. Each starts off, so read them before you turn them on.")
    }

    private fun say(text: String, bad: Boolean = false) {
        said = text
        saidBad = bad
        armed = false
        refresh()
    }

    private fun plural(count: Int) = if (count == 1) "1 bind" else "$count binds"

    private fun refresh() {
        val bind = bind
        val group = group
        val said = said
        val keys = keys
        val clash = bind?.let { CommandBinds.clashes(it, live).firstOrNull() }?.takeIf { bind.group !in settings.offGroups }
        val game = bind?.keys?.singleOrNull()?.let(Keys::gameKey)
        keys?.clash = if (clash != null) Role.BAD else if (game != null) Role.WARNING else null
        val names = bind?.let { Keys.names(it.keys, it.inOrder) }
        readout = wrap(
            when {
                armed && bind != null -> "Press Delete again to delete this bind."
                armed && group != null -> "Press Delete again to delete it" + (settings.binds.count { it.group == group }.takeIf { it > 0 }?.let { " and its ${plural(it)}" } ?: "") + "."
                said != null -> said
                keys?.recording == true -> if (bind?.inOrder == true) "Press the keys in turn, then Escape." else "Hold the keys together, then let go."
                group != null -> if (group in settings.offGroups) "The group is off, so none of its binds send." else ""
                bind == null -> if (settings.binds.isEmpty()) "Add a bind with New Bind." else "Pick a bind to see it here."
                bind.message == null -> "Type a command, such as /warp end."
                bind.keys.isEmpty() -> "Press Keys, then the keys to use."
                bind.group in settings.offGroups -> "${groupName(bind.group)} is off, so this bind sends nothing."
                !bind.enabled -> "Off. Tick its box on the left to use it."
                clash != null -> "$names also fires ${clash.title}."
                game != null -> "$names is also the game's $game key."
                // A bind that works needs no words: its row and fields already say what it does.
                else -> ""
            },
            DETAIL_WIDTH,
            READOUT_LINES,
        )
    }

    private fun scroll(by: Int) {
        first += by
        layout()
    }

    private fun scrollTo(index: Int) = scroll(if (index < first) index - first else if (index >= first + shown) index - first - shown + 1 else 0)

    /** The line of the list under the mouse, or -1. */
    private fun entryAt(x: Double, y: Double): Int {
        if (x < rowsX || x >= rowsX + rowWidth || y < listY || y >= listY + listHeight) return -1
        val i = first + ((y - listY) / ROW_STEP).toInt()
        return i.takeIf { (y - listY) % ROW_STEP < ROW && it < entries.size } ?: -1
    }

    /** Moves [bind] to line [to] of the list. */
    private fun moveBind(bind: CommandBind, to: Int) {
        val (binds, moved) = CommandBinds.move(settings.binds, groups, settings.collapsedGroups, bind, to)
        if (binds == settings.binds && moved === bind) return
        settings.binds = binds
        if (dragged === bind) dragged = moved
        picked = binds.indexOfFirst { it === moved }
        rebuildWidgets()
        scrollTo(entries.indexOfFirst { it is Entry.Bind && it.bind === moved })
    }

    /** Moves [group] to where the group at line [to] is. General stays first. */
    private fun moveGroup(group: String, to: Int) {
        val over = entries.subList(0, (to + 1).coerceIn(1, entries.size)).last { it is Entry.Heading } as Entry.Heading
        if (over.group == group) return
        val order = settings.bindGroups
        settings.bindGroups = CommandBinds.moveGroup(order, group, if (over.group.isEmpty()) 0 else order.indexOf(over.group))
        rebuildWidgets()
        scrollTo(entries.indexOfFirst { it.matches(group) })
    }

    /**
     * Moves the picked bind a line, or the picked group past the next, as Alt with Up or Down does. A bind that
     * lands in a folded group unfolds it, so it stays in sight.
     */
    private fun nudge(by: Int) {
        val bind = bind
        val group = group
        when {
            bind != null -> {
                moveBind(bind, entries.indexOfFirst { it.matches(picked) } + by)
                this.bind?.group?.takeIf { it in settings.collapsedGroups }?.let { collapse(it, false) }
            }
            !group.isNullOrEmpty() -> {
                val order = settings.bindGroups
                settings.bindGroups = CommandBinds.moveGroup(order, group, order.indexOf(group) + by)
                rebuildWidgets()
                scrollTo(entries.indexOfFirst { it.matches(group) })
            }
        }
    }

    /** Whether [nudge] would move anything: General and the first line stay first. */
    private fun canNudge(by: Int): Boolean {
        val group = group
        return when {
            bind != null -> entries.indexOfFirst { it.matches(picked) }.let { if (by < 0) it > 1 else it < entries.lastIndex }
            !group.isNullOrEmpty() -> settings.bindGroups.indexOf(group) + by in settings.bindGroups.indices
            else -> false
        }
    }

    /** Picks the line [by] lines from the picked one, passing over empty lines. */
    private fun step(by: Int) {
        val lines = entries.withIndex().filter { it.value !is Entry.Empty }
        val at = lines.indexOfFirst { it.value.matches(picked) }
        lines.getOrNull((at + by).coerceIn(0, lines.lastIndex))?.let { pick(it.value.target()) }
    }

    private fun openMenu(entry: Entry, mouseX: Int, mouseY: Int) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        when (entry) {
            is Entry.Bind -> {
                val bind = entry.bind
                items += "Duplicate" to ::duplicate
                items += (if (bind.enabled) "Turn Off" else "Turn On") to { setEnabled(settings.binds[picked as Int], !bind.enabled) }
                moveItems(items)
                items += "Delete" to ::deleteBind
            }
            is Entry.Heading, is Entry.Empty -> {
                val group = entry.target() as String
                val folded = group in settings.collapsedGroups
                items += (if (folded) "Expand" else "Collapse") to { collapse(group, !folded) }
                val off = group in settings.offGroups
                items += (if (off) "Turn On" else "Turn Off") to { switchGroup(group, off) }
                if (group.isNotEmpty()) items += "Rename" to { groupField?.let { setFocused(it); it.moveCursorToEnd(false); it.setHighlightPos(0) } }
                moveItems(items)
                items += "Export" to ::export
                if (group.isNotEmpty()) items += "Delete" to ::deleteGroup
            }
        }
        closeMenu()
        menu = addRenderableWidget(KitMenu(mouseX, mouseY, width, height, items, ::closeMenu)).also(::setFocused)
    }

    /** Move Up and Move Down, where each would move the picked line. */
    private fun moveItems(items: MutableList<Pair<String, () -> Unit>>) {
        if (canNudge(-1)) items += "Move Up" to { nudge(-1) }
        if (canNudge(1)) items += "Move Down" to { nudge(1) }
    }

    private fun closeMenu() {
        menu?.let(::removeWidget)
        menu = null
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        menu?.let { return it.keyPressed(event) }
        val keys = keys
        if (keys != null && keys.recording) {
            keys.capture(event.key())
            refresh()
            return true
        }
        beforeDrag?.let { (binds, groupsBefore, pickedBefore) ->
            if (dragged != null && event.isEscape) {
                settings.binds = binds
                settings.bindGroups = groupsBefore
                picked = pickedBefore
                dragged = null
                beforeDrag = null
                rebuildWidgets()
            }
            return true
        }
        val focused = focused
        if (focused is KitTextField && (event.isEscape || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(null)
            group?.let { focused.value = groupName(it) }
            said = null
            refresh()
            return true
        }
        // The list's keys work while nothing on the right has the keyboard.
        val inList = focused == null || focused is KitCheckbox && (focused in bindBoxes.values || focused in groupBoxes.values)
        if (inList) {
            val bind = bind
            val group = group
            when {
                event.hasAltDown() && (event.isUp || event.isDown) -> return true.also { nudge(if (event.isUp) -1 else 1) }
                event.isUp -> return true.also { step(-1) }
                event.isDown -> return true.also { step(1) }
                event.isLeft -> return true.also {
                    when {
                        group != null -> collapse(group, true)
                        bind != null -> pick(bind.group)
                    }
                }
                event.isRight && group != null -> return true.also { collapse(group, false) }
                event.key() == GLFW.GLFW_KEY_DELETE -> return true.also { if (bind != null) deleteBind() else if (group != null) deleteGroup() }
            }
        }
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scroll(-shown)
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll(shown)
            GLFW.GLFW_KEY_HOME -> if (focused !is KitTextField) scroll(-entries.size) else return super.keyPressed(event)
            GLFW.GLFW_KEY_END -> if (focused !is KitTextField) scroll(entries.size) else return super.keyPressed(event)
            else -> return super.keyPressed(event).also { refresh() }
        }
        return true
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        val keys = keys
        if (keys != null && keys.recording) {
            keys.release(event.key())
            refresh()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        menu?.let { open ->
            if (open.isMouseOver(event.x, event.y)) return open.mouseClicked(event, doubleClick)
            // A press outside closes the menu and still does its own job.
            closeMenu()
        }
        val keys = keys
        if (keys != null && keys.recording) {
            val took = keys.capture(event.button(), mouse = true)
            refresh()
            if (took || keys.isMouseOver(event.x, event.y)) return true
        }
        if (entries.size > shown && event.button() == 0 && event.x >= scrollX && event.x < scrollX + Kit.SCROLLBAR_WIDTH &&
            event.y >= listY && event.y < listY + listHeight
        ) {
            scrolling = true
            drag(event.y)
            return true
        }
        if (armed && delete?.isMouseOver(event.x, event.y) != true) {
            armed = false
            refresh()
        }
        if (super.mouseClicked(event, doubleClick)) return true
        val at = entryAt(event.x, event.y)
        if (at < 0) return false
        val entry = entries[at]
        if (event.button() == 1) {
            if (!entry.matches(picked)) pick(entry.target())
            openMenu(entry, event.x.toInt(), event.y.toInt())
            return true
        }
        if (event.button() != 0) return false
        when (entry) {
            is Entry.Heading -> {
                // The arrow folds and unfolds, and a double click anywhere on the heading does too.
                if (event.x < rowsX + BOX_X - 1 || doubleClick) return true.also { collapse(entry.group, entry.group !in settings.collapsedGroups) }
                if (!entry.matches(picked)) pick(entry.group)
                if (entry.group.isNotEmpty()) startDrag(entry.group)
            }
            is Entry.Bind -> {
                if (!entry.matches(picked)) pick(entry.target())
                startDrag(entry.bind)
            }
            is Entry.Empty -> pick(entry.group)
        }
        return true
    }

    private fun startDrag(what: Any) {
        dragged = what
        beforeDrag = Triple(settings.binds, settings.bindGroups, picked)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (scrolling) {
            drag(event.y)
            return true
        }
        val what = dragged ?: return super.mouseDragged(event, dx, dy)
        // Past the top or bottom row the list scrolls under the row, so it can go anywhere.
        if (event.y < listY) scroll(-1) else if (event.y >= listY + listHeight) scroll(1)
        val line = first + ((event.y.coerceIn(listY.toDouble(), listY + listHeight - 1.0) - listY) / ROW_STEP).toInt()
        when (what) {
            is CommandBind -> if (line != entries.indexOfFirst { it is Entry.Bind && it.bind === what }) moveBind(what, line)
            is String -> moveGroup(what, line)
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val keys = keys
        if (keys != null && keys.recording) {
            keys.release(event.button())
            refresh()
        }
        scrolling = false
        dragged = null
        beforeDrag = null
        return super.mouseReleased(event)
    }

    /** Puts the thumb's middle under the mouse. */
    private fun drag(mouseY: Double) {
        val thumb = thumbHeight()
        val at = ((mouseY - listY - 1 - thumb / 2.0) / (listHeight - 2 - thumb)).coerceIn(0.0, 1.0)
        first = (at * (entries.size - shown)).roundToInt()
        layout()
    }

    private fun thumbHeight() = ((listHeight - 2) * shown / entries.size.coerceAtLeast(shown)).coerceAtLeast(8)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        if (menu == null && mouseX < detailX) scroll(if (scrollY > 0) -1 else 1) else return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        return true
    }

    // After a key, the game would focus the first button on each rebuild, and the list's keys would stop working.
    override fun setInitialFocus() = setFocused(null)

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        if (keys != null) refresh()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2)
        // The list sits in a sunk container, as the game's own lists do, with the scrollbar inside its edge.
        Kit.well(graphics, left, contentY, LIST_WIDTH, containerHeight)
        val clashing = live.filter { CommandBinds.clashes(it, live).isNotEmpty() }
        val over = if (menu == null) entryAt(mouseX.toDouble(), mouseY.toDouble()) else -1
        val textX = rowsX + TEXT_X
        val room = rowsX + rowWidth - 3 - textX
        for ((i, entry) in entries.withIndex()) {
            if (i - first !in 0 until shown) continue
            val y = listY + (i - first) * ROW_STEP
            when {
                entry.matches(picked) -> Kit.ring(graphics, rowsX, y, rowWidth, ROW, Kit.theme.accent)
                i == over && entry !is Entry.Empty -> Kit.ring(graphics, rowsX, y, rowWidth, ROW, Kit.theme.hoverOutline)
            }
            when (entry) {
                is Entry.Heading -> {
                    val folded = entry.group in settings.collapsedGroups
                    val off = entry.group in settings.offGroups
                    val arrow = if (folded) Icon.RIGHT else Icon.DOWN
                    Kit.icon(graphics, arrow, rowsX + ARROW_X + (5 - arrow.width) / 2, y + (ROW - arrow.height) / 2, Role.MUTED.argb())
                    val count = settings.binds.count { it.group == entry.group || entry.group.isEmpty() && it.group !in groups }.toString()
                    val name = fit(groupName(entry.group), room - font.width(count) - Kit.GAP)
                    val textY = y + (ROW - Kit.LABEL_HEIGHT) / 2
                    graphics.text(font, text { if (off) muted(name) else body(name) }.toComponent(), textX, textY, -1, true)
                    graphics.text(font, text { muted(count) }.toComponent(), rowsX + rowWidth - 3 - font.width(count), textY, -1, true)
                }
                is Entry.Bind -> {
                    val bind = entry.bind
                    val sends = bind.enabled && bind.group !in settings.offGroups
                    val named = bind.name.isNotBlank()
                    val title = fit(bind.title, room)
                    val command = if (named) fit(" " + bind.command.trim(), room - font.width(title)) else ""
                    graphics.text(font, text { if (sends) body(title) else muted(title); muted(command) }.toComponent(), textX, y + 3, -1, true)
                    val keys = Keys.names(bind.keys, bind.inOrder)
                    val tags = listOfNotNull(
                        when (bind.where) {
                            Where.WORLD -> null
                            Where.MENUS -> "menus"
                            Where.BOTH -> "+ menus"
                        },
                        bind.area?.title,
                    ).joinToString(" ") { " · $it" }
                    val keysShown = fit(keys, room)
                    val tagsShown = fit(tags, room - font.width(keysShown))
                    val line = text {
                        when {
                            bind.keys.isEmpty() -> bad("No keys")
                            !sends -> muted(keysShown)
                            bind in clashing -> bad(keysShown)
                            bind.keys.size == 1 && Keys.gameKey(bind.keys[0]) != null -> warning(keysShown)
                            else -> value(keysShown)
                        }
                        muted(tagsShown)
                    }
                    graphics.text(font, line.toComponent(), textX, y + 12, -1, true)
                }
                is Entry.Empty -> graphics.text(font, text { muted(fit("Drag binds here", room)) }.toComponent(), textX, y + (ROW - Kit.LABEL_HEIGHT) / 2, -1, true)
            }
        }
        val dragging = dragged != null
        if (dragging || over >= 0 && entries[over] !is Entry.Empty && children().none { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) } &&
            !(entries[over] is Entry.Heading && mouseX < rowsX + BOX_X - 1)
        ) {
            graphics.requestCursor(CursorTypes.RESIZE_NS)
        }
        if (entries.size > shown) {
            val thumb = thumbHeight()
            Kit.scrollbar(graphics, scrollX, listY, listHeight, (listHeight - 2 - thumb) * first / (entries.size - shown), thumb)
        }
        for ((label, x, y) in labels) Kit.label(graphics, label, x, y, Kit.Ink.MUTED)
        val ink = if (picked == null || said != null && saidBad) Kit.Ink.MUTED else Kit.Ink.NORMAL
        for ((i, line) in readout.withIndex()) Kit.label(graphics, line, detailX, readoutY + i * LINE, ink)
        delete?.takeIf { armed }?.let { Kit.ring(graphics, it.x - 1, it.y - 1, it.width + 2, it.height + 2, Role.BAD.argb()) }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    companion object {
        fun groupName(group: String) = group.ifEmpty { "General" }
    }
}
