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
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
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
private const val LABEL_HEIGHT = 8
private const val LINE = 10
private const val READOUT_LINES = 2
private const val ROW = 22
private const val ROW_STEP = ROW + 2
private const val FIELD_STEP = Kit.BUTTON_HEIGHT + Kit.GAP
private const val CHECK_STEP = Kit.CHECKBOX_SIZE + Kit.GAP
private const val MAX_PRIORITY = 9
/** Anywhere, then each area a bind can be limited to. */
private val AREAS: List<Area?> = listOf(null) + (Area.entries - Area.ANYWHERE)

/**
 * Command Binds on one screen: the binds down the left under their groups' headings, and everything about the
 * one picked on the right, where each change takes effect at once. A bind's row shows whether it is on, its name
 * and its keys. A row is dragged to move it, under another heading to put it in that group, or moved with Alt and
 * the arrow keys, and Escape during a drag puts it back. A heading picked on the left shows its group: its name,
 * Export and Delete. Deletes ask for a second press. The list scrolls by whole rows: the wheel, the scrollbar's
 * thumb, Page Up and Page Down, and Home and End outside a field. The line under the details reads back what
 * the bind does, what stops it, or what the last action did. Closing saves.
 */
class CommandBindsScreen(private val settings: DsModConfig.General, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    /** The bind or the group picked on the left, by its place in the binds or its name. */
    private var picked: Any? = null
    private var first = 0
    private var armed = false
    private var said: String? = null
    private var saidBad = false
    private var scrolling = false
    private var dragged: CommandBind? = null
    private var bindBeforeDrag: CommandBind? = null
    private var beforeDrag: List<CommandBind> = emptyList()

    private var entries: List<Entry> = emptyList()
    private val boxes = IdentityHashMap<CommandBind, KitCheckbox>()
    private var keys: KitKeysButton? = null
    private var delete: KitButton? = null

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

    init {
        // Opening shows the first bind, so its details are there without a click.
        picked = CommandBinds.entries(settings.binds, groups).firstNotNullOfOrNull { entry ->
            (entry as? Entry.Bind)?.let { settings.binds.indexOfFirst { it === entry.bind } }
        }
    }

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

        entries = CommandBinds.entries(settings.binds, groups)
        boxes.clear()
        for (entry in entries) {
            if (entry !is Entry.Bind) continue
            val bind = entry.bind
            boxes[bind] = addRenderableWidget(KitCheckbox(rowsX + 2, 0, "", bind.enabled, onWell = true) { on -> change(bind, bind.copy(enabled = on)) })
        }
        keys = null
        delete = null
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
            change(now).let { next -> change(now, next) }
        }
        val controlX = detailX + LABEL_WIDTH
        val controlWidth = DETAIL_WIDTH - LABEL_WIDTH
        val half = (controlWidth - Kit.GAP) / 2
        fun label(text: String, y: Int) {
            labels += Triple(text, detailX, y + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
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
        y += CHECK_STEP + Kit.GAP
        readoutY = y

        val deleteWidth = font.width("Delete") + 16
        delete = addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth, bottom, "Delete", ::deleteBind, deleteWidth))
        val copyWidth = font.width("Duplicate") + 16
        addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth - Kit.GAP - copyWidth, bottom, "Duplicate", ::duplicate, copyWidth))
    }

    /** The picked group: its name, which General keeps, and Delete. */
    private fun groupDetails(group: String, labels: MutableList<Triple<String, Int, Int>>, bottom: Int) {
        val controlX = detailX + LABEL_WIDTH
        labels += Triple("Group", detailX, contentY + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        addRenderableWidget(KitTextField(controlX, contentY, DETAIL_WIDTH - LABEL_WIDTH, "Group Name", groupName(group), BindShare.GROUP_LENGTH, ::rename))
            .setEditable(group.isNotEmpty())
        readoutY = contentY + FIELD_STEP + Kit.GAP
        if (group.isNotEmpty()) {
            val deleteWidth = font.width("Delete") + 16
            delete = addRenderableWidget(KitButton(detailX + DETAIL_WIDTH - deleteWidth, bottom, "Delete", ::deleteGroup, deleteWidth))
        }
    }

    private fun layout() {
        first = first.coerceIn(0, (entries.size - shown).coerceAtLeast(0))
        for ((i, entry) in entries.withIndex()) {
            if (entry !is Entry.Bind) continue
            val box = boxes[entry.bind] ?: continue
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
    }

    /** Puts [next] in place of [old] without rebuilding, so a field being typed in keeps its focus. */
    private fun change(old: CommandBind, next: CommandBind) {
        val index = settings.binds.indexOfFirst { it === old }.takeIf { it >= 0 } ?: return
        settings.binds = settings.binds.toMutableList().also { it[index] = next }
        entries = CommandBinds.entries(settings.binds, groups)
        boxes.remove(old)?.let { boxes[next] = it }
        armed = false
        said = null
        refresh()
    }

    private fun newBind() {
        val fresh = CommandBind(group = group ?: bind?.group ?: "")
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

    private fun newGroup() {
        var n = groups.size
        while ("Group $n" in groups) n++
        settings.bindGroups += "Group $n"
        pick("Group $n")
        children().filterIsInstance<KitTextField>().firstOrNull()?.let {
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
                settings.bindGroups = settings.bindGroups.map { if (it == group) name else it }
                settings.binds = settings.binds.map { if (it.group == group) it.copy(group = name) else it }
                picked = name
                said = null
                entries = CommandBinds.entries(settings.binds, groups)
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
        say("Copied ${plural(binds.size)}. Paste them with Import.")
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
        say("Imported ${plural(shared.binds.size)}. Each starts off.")
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
        val clash = bind?.let { CommandBinds.clashes(it, settings.binds).firstOrNull() }
        val game = bind?.keys?.singleOrNull()?.let(Keys::gameKey)
        keys?.clash = if (clash != null) Role.BAD else if (game != null) Role.WARNING else null
        val names = bind?.let { Keys.names(it.keys, it.inOrder) }
        readout = wrap(
            when {
                armed && bind != null -> "Press Delete again to delete this bind."
                armed && group != null -> "Press Delete again to delete it" + (settings.binds.count { it.group == group }.takeIf { it > 0 }?.let { " and its ${plural(it)}" } ?: "") + "."
                said != null -> said
                keys?.recording == true -> if (bind?.inOrder == true) "Press the keys in turn, then Escape." else "Hold the keys together, then let go."
                group != null -> "${plural(settings.binds.count { it.group == group })} in ${groupName(group)}. Drag binds under its heading."
                bind == null -> if (settings.binds.isEmpty()) "Add a bind with New Bind." else "Pick a bind to see it here."
                bind.message == null -> "Type a command, such as /warp end."
                bind.keys.isEmpty() -> "Press Keys, then the keys to use."
                clash != null -> "$names also fires ${clash.title}."
                game != null -> "$names is also the game's $game key."
                !bind.enabled -> "Off. Tick its box on the left to use it."
                else -> "$names ${describe(bind)}."
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

    /** Moves the dragged or picked bind to line [to] of the list. */
    private fun moveTo(bind: CommandBind, to: Int) {
        val (binds, moved) = CommandBinds.move(settings.binds, groups, bind, to)
        if (binds == settings.binds && moved === bind) return
        settings.binds = binds
        if (dragged === bind) dragged = moved
        picked = binds.indexOfFirst { it === moved }
        rebuildWidgets()
        scrollTo(entries.indexOfFirst { it is Entry.Bind && it.bind === moved })
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keys = keys
        if (keys != null && keys.recording) {
            keys.capture(event.key())
            refresh()
            return true
        }
        if (dragged != null) {
            if (event.isEscape) {
                settings.binds = beforeDrag
                picked = beforeDrag.indexOfFirst { it === bindBeforeDrag }
                dragged = null
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
        val bind = bind
        if (bind != null && event.hasAltDown() && (event.isUp || event.isDown)) {
            val at = entries.indexOfFirst { it is Entry.Bind && it.bind === bind }
            // Down past the next line, because the bind leaves its own line first.
            moveTo(bind, if (event.isUp) at - 1 else at + 1)
            return true
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
        if (at < 0 || event.button() != 0) return false
        when (val entry = entries[at]) {
            is Entry.Heading -> pick(entry.group)
            is Entry.Bind -> {
                if (bind !== entry.bind) pick(settings.binds.indexOfFirst { it === entry.bind })
                dragged = entry.bind
                bindBeforeDrag = entry.bind
                beforeDrag = settings.binds
            }
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (scrolling) {
            drag(event.y)
            return true
        }
        val bind = dragged ?: return super.mouseDragged(event, dx, dy)
        // Past the top or bottom row the list scrolls under the row, so it can go anywhere.
        if (event.y < listY) scroll(-1) else if (event.y >= listY + listHeight) scroll(1)
        val line = first + ((event.y.coerceIn(listY.toDouble(), listY + listHeight - 1.0) - listY) / ROW_STEP).toInt()
        val at = entries.indexOfFirst { it is Entry.Bind && it.bind === bind }
        if (line != at) moveTo(bind, line)
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
        if (mouseX < detailX) scroll(if (scrollY > 0) -1 else 1) else return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        return true
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        if (keys != null) refresh()
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        // The list sits in a sunk container, as the game's own lists do, with the scrollbar inside its edge.
        Kit.well(graphics, left, contentY, LIST_WIDTH, containerHeight)
        val clashing = settings.binds.filter { CommandBinds.clashes(it, settings.binds).isNotEmpty() }
        val textX = rowsX + 2 + Kit.CHECKBOX_SIZE + Kit.GAP
        for ((i, entry) in entries.withIndex()) {
            if (i - first !in 0 until shown) continue
            val y = listY + (i - first) * ROW_STEP
            if (entry.matches(picked)) Kit.ring(graphics, rowsX, y, rowWidth, ROW, Kit.theme.accent)
            when (entry) {
                is Entry.Heading -> {
                    val count = settings.binds.count { it.group == entry.group || entry.group.isEmpty() && it.group !in groups }.toString()
                    val name = fit(groupName(entry.group), rowWidth - 24)
                    graphics.text(font, text { body(name) }.toComponent(), rowsX + 3, y + ROW - LABEL_HEIGHT - 3, -1, true)
                    graphics.text(font, text { muted(count) }.toComponent(), rowsX + rowWidth - 3 - font.width(count), y + ROW - LABEL_HEIGHT - 3, -1, true)
                }
                is Entry.Bind -> {
                    val bind = entry.bind
                    val room = rowsX + rowWidth - 3 - textX
                    val name = fit(bind.title, room)
                    graphics.text(font, text { if (bind.enabled) body(name) else muted(name) }.toComponent(), textX, y + 3, -1, true)
                    val keys = fit(Keys.names(bind.keys, bind.inOrder), room)
                    val line = text {
                        when {
                            bind.keys.isEmpty() -> bad("No keys")
                            bind in clashing -> bad(keys)
                            bind.keys.size == 1 && Keys.gameKey(bind.keys[0]) != null -> warning(keys)
                            !bind.enabled -> muted(keys)
                            else -> value(keys)
                        }
                    }
                    graphics.text(font, line.toComponent(), textX, y + 12, -1, true)
                }
            }
        }
        val over = entryAt(mouseX.toDouble(), mouseY.toDouble())
        if (dragged != null || over >= 0 && entries[over] is Entry.Bind && children().none { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }) {
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

        /** What a bind does, after its keys: "sends /warp end in the world, in The End". */
        fun describe(bind: CommandBind): String {
            val what = when (val message = bind.message) {
                is CommandBind.Message.Chat -> "says ${message.text}"
                else -> "sends ${bind.command.trim()}"
            }
            val where = when (bind.where) {
                Where.WORLD -> "in the world"
                Where.MENUS -> "in menus"
                Where.BOTH -> "in the world and menus"
            }
            return "$what $where" + (bind.area?.let { ", in ${it.title}" } ?: "")
        }
    }
}
