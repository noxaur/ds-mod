package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.input.BindShare
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.BindEditScreen.Companion.describe
import io.github.noxaur.dsmod.edge.BindEditScreen.Companion.groupName
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.fit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

private const val TITLE = "Command Binds"
private const val WIDTH = 300
private const val GROUPS_WIDTH = 88
private const val ROW = 18
private const val ROW_STEP = ROW + 2
private const val GROUP_STEP = Kit.BUTTON_HEIGHT + 2
private const val LABEL_HEIGHT = 8
private const val MIN_ROWS = 4
private const val MAX_ROWS = 10

/**
 * Lists Command Binds, one row each: whether it is on, its name and its keys, and Edit, which opens everything
 * else. A row is dragged by its name to move it, or moved with Alt and the arrow keys, and Escape during a drag
 * puts it back. A double click edits it. Groups appear down the left once there is more than one, and the
 * chosen group is renamed in its field above the rows and deleted with a second press on its cross. Export
 * copies the group to the clipboard, and Import adds one from it. The rows scroll by whole rows: the wheel, the
 * scrollbar's thumb, Page Up and Page Down, and Home and End outside a field. The line at the bottom reads back
 * the row last touched, or what the last action did. Every change is in the settings at once, and closing saves.
 */
class CommandBindsScreen(private val settings: DsModConfig.General, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    private class Row(val bind: CommandBind, val on: KitCheckbox, val edit: KitButton) {
        val widgets: List<AbstractWidget> get() = listOf(on, edit)
    }

    private var group = ""
    private var first = 0
    private var firstGroup = 0
    private var current: CommandBind? = null
    private var groupArmed = false
    private var said: String? = null
    private var saidBad = false
    private var scrolling = false

    /** The row being dragged and the binds as they were when the drag began, for Escape to put back. */
    private var dragged: CommandBind? = null
    private var beforeDrag: List<CommandBind> = emptyList()

    private val rows = ArrayList<Row>()
    private val groupButtons = ArrayList<Pair<String, KitButton>>()
    private var groupField: KitTextField? = null
    private var deleteGroup: KitButton? = null

    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var contentY = 0
    private var listX = 0
    private var listY = 0
    private var listWidth = 0
    private var shown = MIN_ROWS
    private var groupRows = 0
    private var readoutY = 0
    private var readout = ""

    private val groups: List<String> get() = listOf("") + settings.bindGroups
    private val grouped get() = settings.bindGroups.isNotEmpty()
    private val inGroup: List<CommandBind> get() = settings.binds.filter { it.group == group }
    private val listHeight get() = shown * ROW_STEP - 2
    private val rowWidth get() = listWidth - Kit.SCROLLBAR_WIDTH - 2
    private val scrollX get() = listX + listWidth - Kit.SCROLLBAR_WIDTH

    override fun init() {
        val width = WIDTH + if (grouped) GROUPS_WIDTH + Kit.GAP else 0
        val header = if (grouped) Kit.BUTTON_HEIGHT + Kit.GAP else 0
        val fixed = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 2 + LABEL_HEIGHT + Kit.GAP * 3 + header
        shown = ((height - Kit.INSET * 2 - fixed + 2) / ROW_STEP).coerceIn(MIN_ROWS, MAX_ROWS)
        panelHeight = fixed + listHeight
        panelX = (this.width - width - Kit.INSET * 2) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        val right = left + width
        contentY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        listX = if (grouped) left + GROUPS_WIDTH + Kit.GAP else left
        listWidth = right - listX
        listY = contentY + header
        readoutY = listY + listHeight + Kit.GAP
        val bottom = readoutY + LABEL_HEIGHT + Kit.GAP
        groupRows = (listY + listHeight - contentY + 2) / GROUP_STEP

        var x = right
        for ((label, action) in listOf("Done" to ::onClose, "Export" to ::export, "Import" to ::import)) {
            val buttonWidth = font.width(label) + 16
            x -= buttonWidth
            addRenderableWidget(KitButton(x, top, label, action, buttonWidth))
            x -= Kit.GAP
        }
        x = right
        for ((label, action) in listOf("New Bind" to ::newBind, "New Group" to ::newGroup)) {
            val buttonWidth = font.width(label) + 16
            x -= buttonWidth
            addRenderableWidget(KitButton(x, bottom, label, action, buttonWidth))
            x -= Kit.GAP
        }
        groupField = null
        deleteGroup = null
        if (grouped) {
            val crossX = right - Kit.BUTTON_HEIGHT
            groupField = addRenderableWidget(KitTextField(listX, contentY, crossX - Kit.GAP - listX, "Group Name", groupName(group), BindShare.GROUP_LENGTH, ::rename)).also {
                it.setEditable(group.isNotEmpty())
            }
            deleteGroup = addRenderableWidget(KitButton(crossX, contentY, Icon.CROSS, "Delete Group", ::armGroup)).also { it.active = group.isNotEmpty() }
        }
        build()
    }

    /** Puts the group buttons and the rows back after the binds or the groups changed. */
    private fun build() {
        if (grouped != (groupField != null)) {
            rebuildWidgets()
            return
        }
        groupButtons.forEach { removeWidget(it.second) }
        groupButtons.clear()
        if (grouped) {
            for (name in groups) groupButtons += name to addRenderableWidget(KitButton(left, 0, fit(groupName(name), GROUPS_WIDTH - 8), { select(name) }, GROUPS_WIDTH))
        }
        rows.forEach { row -> row.widgets.forEach(::removeWidget) }
        rows.clear()
        val editWidth = font.width("Edit") + 12
        for (bind in inGroup) {
            rows += Row(
                bind,
                addRenderableWidget(KitCheckbox(listX + 4, 0, "", bind.enabled) { replace(bind, bind.copy(enabled = it)) }),
                addRenderableWidget(KitButton(listX + rowWidth - editWidth - 1, 0, "Edit", { edit(bind) }, editWidth)),
            )
        }
        scroll(0)
        refresh()
    }

    private fun layout() {
        firstGroup = firstGroup.coerceIn(0, (groupButtons.size - groupRows).coerceAtLeast(0))
        for ((i, pair) in groupButtons.withIndex()) {
            pair.second.y = contentY + (i - firstGroup) * GROUP_STEP
            pair.second.visible = i - firstGroup in 0 until groupRows
        }
        for ((i, row) in rows.withIndex()) {
            val y = listY + (i - first) * ROW_STEP
            row.on.y = y + (ROW - Kit.CHECKBOX_SIZE) / 2
            row.edit.y = y + 1
            row.widgets.forEach { it.visible = i - first in 0 until shown }
        }
    }

    private fun select(name: String) {
        group = name
        first = 0
        current = null
        groupArmed = false
        said = null
        rebuildWidgets()
    }

    private fun replace(old: CommandBind, new: CommandBind) {
        current = new
        said = null
        settings.binds = settings.binds.map { if (it === old) new else it }
        build()
    }

    private fun newBind() = edit(CommandBind(group = group), isNew = true)

    private fun edit(bind: CommandBind, isNew: Boolean = false) {
        said = null
        groupArmed = false
        minecraft.gui.setScreen(
            BindEditScreen(
                this, bind, isNew, groups, settings.binds.filter { it !== bind },
                onSave = { saved ->
                    settings.binds = if (isNew) settings.binds + saved else settings.binds.map { if (it === bind) saved else it }
                    land(saved)
                },
                onCopy = { copy ->
                    val off = copy.copy(enabled = false)
                    settings.binds = settings.binds.toMutableList().apply { add(indexOf(bind) + 1, off) }
                    land(off)
                    said = "Saved a copy. It starts off."
                },
                onDelete = {
                    settings.binds = settings.binds.filter { it !== bind }
                    current = null
                    said = "Deleted ${bind.title}."
                },
            ),
        )
    }

    /** Shows [bind] after the edit window saved it, in its own group. */
    private fun land(bind: CommandBind) {
        current = bind
        group = bind.group
        val at = inGroup.indexOf(bind)
        if (at < first || at >= first + shown) first = (at - shown + 1).coerceAtLeast(0)
    }

    /** Moves [bind] to [index] among the rows of its group. */
    private fun move(bind: CommandBind, index: Int) {
        val list = inGroup.toMutableList()
        val from = list.indexOf(bind)
        val to = index.coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.removeAt(from)
        list.add(to, bind)
        // The group's binds take the places its binds had in the whole list, in their new order.
        val order = list.iterator()
        settings.binds = settings.binds.map { if (it.group == group) order.next() else it }
        current = bind
        build()
        scrollTo(to)
    }

    private fun newGroup() {
        var n = groups.size
        while ("Group $n" in groups) n++
        settings.bindGroups += "Group $n"
        select("Group $n")
        groupField?.let {
            setFocused(it)
            it.moveCursorToEnd(false)
            it.setHighlightPos(0)
        }
    }

    /** Renames the chosen group as its field is typed in, once the name is one no other group has. */
    private fun rename(typed: String) {
        if (group.isEmpty() || deleteGroup == null) return
        val name = typed.trim()
        when {
            name == group -> said = null
            name.isEmpty() -> return say("Give the group a name.", bad = true)
            name == groupName("") || name in groups -> return say("Another group is called $name.", bad = true)
            else -> {
                settings.bindGroups = settings.bindGroups.map { if (it == group) name else it }
                settings.binds = settings.binds.map { if (it.group == group) it.copy(group = name) else it }
                group = name
                said = null
                build()
            }
        }
        refresh()
    }

    private fun armGroup() {
        if (!groupArmed) {
            groupArmed = true
            said = null
            refresh()
            return
        }
        val count = inGroup.size
        val name = group
        settings.binds = settings.binds.filter { it.group != name }
        settings.bindGroups = settings.bindGroups - name
        select("")
        say("Deleted $name${if (count > 0) " and its ${plural(count)}" else ""}.")
    }

    private fun export() {
        val binds = inGroup
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
        select(name)
        say("Imported ${plural(shared.binds.size)}. Each starts off.")
    }

    private fun say(text: String, bad: Boolean = false) {
        said = text
        saidBad = bad
        groupArmed = false
        refresh()
    }

    private fun plural(count: Int) = if (count == 1) "1 bind" else "$count binds"

    private fun refresh() {
        val bind = current?.takeIf { it in settings.binds }
        val said = said
        readout = fit(
            when {
                groupArmed -> "Press the cross again to delete ${groupName(group)}" + (inGroup.size.takeIf { it > 0 }?.let { " and its ${plural(it)}" } ?: "") + "."
                said != null -> said
                rows.isEmpty() -> "Add a bind with New Bind."
                bind == null -> "Drag a row to move it. Edit changes the rest."
                !bind.enabled -> "${bind.title} is off."
                bind.keys.isEmpty() -> "${bind.title} has no keys yet."
                else -> CommandBinds.clashes(bind, settings.binds).firstOrNull()?.let { "${Keys.names(bind.keys, bind.inOrder)} also fires ${it.title}." }
                    ?: "${Keys.names(bind.keys, bind.inOrder)} ${describe(bind)}."
            },
            panelWidth() - Kit.INSET * 2,
        )
    }

    private fun scroll(by: Int) {
        first = (first + by).coerceIn(0, (rows.size - shown).coerceAtLeast(0))
        layout()
    }

    private fun scrollTo(index: Int) {
        if (index < 0) return
        scroll(if (index < first) index - first else if (index >= first + shown) index - first - shown + 1 else 0)
    }

    /** The row under the mouse, where a press would drag or select it, or -1. */
    private fun rowAt(x: Double, y: Double): Int {
        if (x < listX || x >= listX + rowWidth || y < listY || y >= listY + listHeight) return -1
        val i = ((y - listY) / ROW_STEP).toInt()
        return (first + i).takeIf { (y - listY) % ROW_STEP < ROW && it < rows.size } ?: -1
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val focused = focused
        dragged?.let {
            if (event.isEscape) {
                settings.binds = beforeDrag
                dragged = null
                build()
            }
            return true
        }
        if (focused is KitTextField && (event.isEscape || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(null)
            if (group.isNotEmpty() && focused.value.trim() != group) focused.value = group
            said = null
            refresh()
            return true
        }
        val row = rows.firstOrNull { row -> row.widgets.any { it === focused } }
        if (row != null && event.hasAltDown() && (event.isUp || event.isDown)) {
            move(row.bind, rows.indexOf(row) + if (event.isUp) -1 else 1)
            rows.firstOrNull { it.bind === row.bind }?.let { setFocused(if (focused === row.on) it.on else it.edit) }
            return true
        }
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scroll(-shown)
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll(shown)
            GLFW.GLFW_KEY_HOME -> if (focused !is KitTextField) scroll(-rows.size) else return super.keyPressed(event)
            GLFW.GLFW_KEY_END -> if (focused !is KitTextField) scroll(rows.size) else return super.keyPressed(event)
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (rows.size > shown && event.button() == 0 && event.x >= scrollX && event.x < scrollX + Kit.SCROLLBAR_WIDTH &&
            event.y >= listY && event.y < listY + listHeight
        ) {
            scrolling = true
            drag(event.y)
            return true
        }
        val wasArmed = groupArmed
        val handled = super.mouseClicked(event, doubleClick)
        if (wasArmed && groupArmed && deleteGroup?.isMouseOver(event.x, event.y) != true) {
            groupArmed = false
            refresh()
        }
        if (handled) return true
        val at = rowAt(event.x, event.y)
        if (at < 0 || event.button() != 0) return false
        val bind = rows[at].bind
        if (doubleClick) {
            edit(bind)
            return true
        }
        current = bind
        said = null
        dragged = bind
        beforeDrag = settings.binds
        setFocused(null)
        refresh()
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
        val to = first + ((event.y.coerceIn(listY.toDouble(), listY + listHeight - 1.0) - listY) / ROW_STEP).toInt()
        move(bind, to)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        scrolling = false
        dragged = null
        return super.mouseReleased(event)
    }

    /** Puts the thumb's middle under the mouse. */
    private fun drag(mouseY: Double) {
        val thumb = thumbHeight()
        val at = ((mouseY - listY - 1 - thumb / 2.0) / (listHeight - 2 - thumb)).coerceIn(0.0, 1.0)
        first = (at * (rows.size - shown)).roundToInt()
        layout()
    }

    private fun thumbHeight() = ((listHeight - 2) * shown / rows.size.coerceAtLeast(shown)).coerceAtLeast(8)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        val by = if (scrollY > 0) -1 else 1
        if (grouped && mouseX < listX) {
            firstGroup += by
            layout()
        } else {
            scroll(by)
        }
        return true
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        val at = rows.indexOfFirst { row -> row.widgets.any { it === focused } }
        if (at >= 0) {
            current = rows[at].bind
            scrollTo(at)
            refresh()
        }
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, panelWidth(), panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        val nameX = listX + 4 + Kit.CHECKBOX_SIZE + Kit.GAP
        val clashing = settings.binds.filter { bind -> CommandBinds.clashes(bind, settings.binds).isNotEmpty() }
        for ((i, row) in rows.withIndex()) {
            if (i - first !in 0 until shown) continue
            val y = listY + (i - first) * ROW_STEP
            val bind = row.bind
            Kit.well(graphics, listX, y, rowWidth, ROW)
            if (bind === current) Kit.ring(graphics, listX - 1, y - 1, rowWidth + 2, ROW + 2, Kit.theme.accent)
            val keysRoom = (row.edit.x - Kit.GAP * 2 - nameX) / 2
            val keys = fit(Keys.names(bind.keys, bind.inOrder), keysRoom)
            val keysX = row.edit.x - Kit.GAP * 2 - font.width(keys)
            val name = fit(bind.title, keysX - Kit.GAP * 2 - nameX)
            graphics.text(font, text { if (bind.enabled) body(name) else muted(name) }.toComponent(), nameX, y + 5, -1, true)
            val game = bind.keys.size == 1 && Keys.gameKey(bind.keys[0]) != null
            val keysText = text {
                when {
                    bind.keys.isEmpty() || bind in clashing -> bad(keys)
                    game -> warning(keys)
                    !bind.enabled -> muted(keys)
                    else -> value(keys)
                }
            }
            graphics.text(font, keysText.toComponent(), keysX, y + 5, -1, true)
        }
        val over = rowAt(mouseX.toDouble(), mouseY.toDouble())
        if (dragged != null || over >= 0 && children().none { it.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }) {
            graphics.requestCursor(CursorTypes.RESIZE_NS)
        }
        if (rows.size > shown) {
            val thumb = thumbHeight()
            Kit.scrollbar(graphics, scrollX, listY, listHeight, (listHeight - 2 - thumb) * first / (rows.size - shown), thumb)
        }
        for ((name, button) in groupButtons) {
            if (name == group && button.visible) Kit.ring(graphics, button.x - 1, button.y - 1, button.width + 2, button.height + 2, Kit.theme.accent)
        }
        deleteGroup?.takeIf { groupArmed }?.let { Kit.ring(graphics, it.x - 1, it.y - 1, it.width + 2, it.height + 2, Role.BAD.argb()) }
        Kit.label(graphics, readout, left, readoutY, if (said != null && saidBad) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private fun panelWidth() = WIDTH + Kit.INSET * 2 + if (grouped) GROUPS_WIDTH + Kit.GAP else 0
}
