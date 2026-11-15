package io.github.noxaur.dsmod.edge

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
private const val WIDTH = 396
private const val GROUPS_WIDTH = 92
private const val CARD = 36
private const val CARD_STEP = CARD + 2
private const val GROUP_STEP = Kit.BUTTON_HEIGHT + 2
private const val LABEL_HEIGHT = 8
private const val MIN_CARDS = 3

/**
 * Lists Command Binds as SkyOcean lists its hotkeys: groups down the left, and the chosen group's binds as cards,
 * each with its name, keys and command, and buttons to move it up or down, edit, duplicate or delete it and turn
 * it on or off. A group is renamed in its field above the cards, shared through the clipboard with Export and
 * Import, and deleted with its binds. A delete asks for a second press. The cards scroll by whole cards: the
 * wheel, the scrollbar's thumb, Page Up and Page Down, and Home and End outside a field. The line at the bottom
 * reads back the card last touched, or what the last action did. Every change is in the settings at once, and
 * closing saves them.
 */
class CommandBindsScreen(private val settings: DsModConfig.General, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    private class Card(val bind: CommandBind, val up: KitButton, val down: KitButton, val edit: KitButton, val copy: KitButton, val delete: KitButton, val on: KitCheckbox) {
        val widgets: List<AbstractWidget> get() = listOf(up, down, edit, copy, delete, on)
    }

    private var group = ""
    private var first = 0
    private var firstGroup = 0
    private var current: CommandBind? = null

    /** The bind, or the name of the group, whose cross was pressed once. */
    private var armed: Any? = null
    private var said: String? = null
    private var saidBad = false
    private var dragging = false
    private val cards = ArrayList<Card>()
    private val groupButtons = ArrayList<Pair<String, KitButton>>()
    private lateinit var groupField: KitTextField
    private lateinit var deleteGroup: KitButton

    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var headerY = 0
    private var listX = 0
    private var listY = 0
    private var listWidth = 0
    private var rows = MIN_CARDS
    private var groupRows = 0
    private var readoutY = 0
    private var readout = ""

    private val groups: List<String> get() = listOf("") + settings.bindGroups
    private val inGroup: List<CommandBind> get() = settings.binds.filter { it.group == group }
    private val listHeight get() = rows * CARD_STEP - 2
    private val cardWidth get() = listWidth - Kit.SCROLLBAR_WIDTH - 2
    private val scrollX get() = listX + listWidth - Kit.SCROLLBAR_WIDTH

    override fun init() {
        val fixed = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 3 + Kit.GAP * 3
        rows = ((height - Kit.INSET * 2 - fixed + 2) / CARD_STEP).coerceAtLeast(MIN_CARDS)
        panelHeight = fixed + listHeight
        panelX = (width - WIDTH - Kit.INSET * 2) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        listX = left + GROUPS_WIDTH + Kit.GAP
        listWidth = WIDTH - GROUPS_WIDTH - Kit.GAP
        headerY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        listY = headerY + Kit.BUTTON_HEIGHT + Kit.GAP
        val bottom = listY + listHeight + Kit.GAP
        readoutY = bottom + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2
        groupRows = (bottom - Kit.GAP - headerY + 2) / GROUP_STEP

        val doneWidth = font.width("Done") + 16
        addRenderableWidget(KitButton(left + WIDTH - doneWidth, top, "Done", ::onClose, doneWidth))
        val importWidth = font.width("Import") + 16
        addRenderableWidget(KitButton(left + WIDTH - doneWidth - Kit.GAP - importWidth, top, "Import", ::import, importWidth))

        // The chosen group's own row: its name, Export and the cross that deletes it.
        val deleteX = listX + listWidth - Kit.BUTTON_HEIGHT
        val exportWidth = font.width("Export") + 16
        val exportX = deleteX - Kit.GAP - exportWidth
        groupField = addRenderableWidget(KitTextField(listX, headerY, exportX - Kit.GAP - listX, "Group Name", groupName(group), BindShare.GROUP_LENGTH, ::rename))
        addRenderableWidget(KitButton(exportX, headerY, "Export", ::export, exportWidth))
        deleteGroup = addRenderableWidget(KitButton(deleteX, headerY, Icon.CROSS, "Delete Group") { arm(group) { removeGroup() } })

        addRenderableWidget(KitButton(left, bottom, "New Group", ::newGroup, GROUPS_WIDTH))
        val newBindWidth = font.width("New Bind") + 16
        addRenderableWidget(KitButton(listX + listWidth - newBindWidth, bottom, "New Bind", ::newBind, newBindWidth))
        build()
    }

    /** Puts the group buttons and the cards back after the binds or the groups changed. */
    private fun build() {
        groupButtons.forEach { removeWidget(it.second) }
        groupButtons.clear()
        for (name in groups) {
            groupButtons += name to addRenderableWidget(KitButton(left, 0, fit(groupName(name), GROUPS_WIDTH - 8), { select(name) }, GROUPS_WIDTH))
        }
        cards.forEach { card -> card.widgets.forEach(::removeWidget) }
        cards.clear()
        val deleteX = listX + cardWidth - Kit.BUTTON_HEIGHT - 1
        val copyX = deleteX - 2 - Kit.BUTTON_HEIGHT
        val editWidth = font.width("Edit") + 12
        val editX = copyX - 2 - editWidth
        val onX = deleteX + Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE - Kit.GAP - font.width("On") - 2
        for (bind in inGroup) {
            cards += Card(
                bind,
                addRenderableWidget(KitButton(listX + 1, 0, Icon.UP, "Move Up") { move(bind, -1) }),
                addRenderableWidget(KitButton(listX + 1, 0, Icon.DOWN, "Move Down") { move(bind, 1) }),
                addRenderableWidget(KitButton(editX, 0, "Edit", { edit(bind, isNew = false) }, editWidth)),
                addRenderableWidget(KitButton(copyX, 0, Icon.COPY, "Duplicate") { duplicate(bind) }),
                addRenderableWidget(KitButton(deleteX, 0, Icon.CROSS, "Delete") { arm(bind) { delete(bind) } }),
                addRenderableWidget(KitCheckbox(onX, 0, "On", bind.enabled, onWell = true) { replace(bind, bind.copy(enabled = it)) }),
            )
        }
        groupField.setEditable(group.isNotEmpty())
        deleteGroup.active = group.isNotEmpty()
        scroll(0)
        refresh()
    }

    private fun layout() {
        firstGroup = firstGroup.coerceIn(0, (groupButtons.size - groupRows).coerceAtLeast(0))
        for ((i, pair) in groupButtons.withIndex()) {
            pair.second.y = headerY + (i - firstGroup) * GROUP_STEP
            pair.second.visible = i - firstGroup in 0 until groupRows
        }
        for ((i, card) in cards.withIndex()) {
            val y = listY + (i - first) * CARD_STEP
            card.up.y = y + 1
            card.down.y = y + CARD - Kit.BUTTON_HEIGHT - 1
            card.edit.y = y + 1
            card.copy.y = y + 1
            card.delete.y = y + 1
            card.on.y = y + CARD - Kit.CHECKBOX_SIZE - 4
            card.up.active = i > 0
            card.down.active = i < cards.lastIndex
            val shown = i - first in 0 until rows
            card.widgets.forEach { it.visible = shown }
        }
    }

    private fun select(name: String) {
        group = name
        first = 0
        current = null
        armed = null
        said = null
        groupField.value = groupName(name)
        build()
    }

    private fun save(binds: List<CommandBind>) {
        settings.binds = binds
        build()
    }

    private fun replace(old: CommandBind, new: CommandBind) {
        current = new
        armed = null
        said = null
        save(settings.binds.map { if (it === old) new else it })
    }

    private fun newBind() = edit(CommandBind(group = group), isNew = true)

    private fun edit(bind: CommandBind, isNew: Boolean) {
        armed = null
        said = null
        val others = settings.binds.filter { it !== bind }
        minecraft.gui.setScreen(
            BindEditScreen(this, bind, isNew, groups, others) { saved ->
                current = saved
                settings.binds = if (isNew) settings.binds + saved else settings.binds.map { if (it === bind) saved else it }
                if (saved.group != group) select(saved.group) else build()
                scrollTo(inGroup.indexOf(saved))
            },
        )
    }

    private fun duplicate(bind: CommandBind) {
        val copy = bind.copy(name = "${bind.title} (Copy)", enabled = false)
        current = copy
        say("Duplicated. The copy starts off.")
        save(settings.binds.toMutableList().apply { add(indexOf(bind) + 1, copy) })
        scrollTo(inGroup.indexOf(copy))
    }

    private fun delete(bind: CommandBind) {
        current = null
        say("Deleted ${bind.title}.")
        save(settings.binds.filter { it !== bind })
    }

    /** Swaps [bind] with the bind of its group above or below it. */
    private fun move(bind: CommandBind, by: Int) {
        val list = inGroup
        val other = list.getOrNull(list.indexOf(bind) + by) ?: return
        val all = settings.binds.toMutableList()
        val a = all.indexOf(bind)
        val b = all.indexOf(other)
        all[a] = other
        all[b] = bind
        current = bind
        armed = null
        said = null
        save(all)
        scrollTo(inGroup.indexOf(bind))
    }

    /** A first press on a cross arms it, and a second press on it does [action]. */
    private fun arm(target: Any, action: () -> Unit) {
        if (armed == target) {
            armed = null
            action()
            return
        }
        armed = target
        said = null
        if (target is CommandBind) current = target
        refresh()
    }

    private fun newGroup() {
        var n = groups.size
        while ("Group $n" in groups) n++
        settings.bindGroups += "Group $n"
        select("Group $n")
        setFocused(groupField)
        groupField.moveCursorToEnd(false)
        groupField.setHighlightPos(0)
    }

    /** Renames the chosen group as its field is typed in, once the name is one no other group has. */
    private fun rename(typed: String) {
        if (group.isEmpty() || !::deleteGroup.isInitialized) return
        val name = typed.trim()
        if (name == group) {
            said = null
            refresh()
            return
        }
        if (name.isEmpty() || name == groupName("") || name in groups) {
            say(if (name.isEmpty()) "Give the group a name." else "Another group is called $name.", bad = true)
            return
        }
        settings.bindGroups = settings.bindGroups.map { if (it == group) name else it }
        settings.binds = settings.binds.map { if (it.group == group) it.copy(group = name) else it }
        group = name
        said = null
        build()
    }

    private fun removeGroup() {
        val count = inGroup.size
        val name = group
        settings.binds = settings.binds.filter { it.group != name }
        settings.bindGroups = settings.bindGroups - name
        select("")
        say("Deleted $name${if (count > 0) " and its ${plural(count)}" else ""}.")
    }

    private fun export() {
        val binds = inGroup
        if (binds.isEmpty()) {
            say("${groupName(group)} has no binds to export.", bad = true)
            return
        }
        minecraft.keyboardHandler.clipboard = BindShare.encode(groupName(group), binds)
        say("Copied ${plural(binds.size)}. Paste them with Import.")
    }

    private fun import() {
        val shared = BindShare.decode(minecraft.keyboardHandler.clipboard)
        if (shared == null || shared.binds.isEmpty()) {
            say("No binds on the clipboard. Use Export first.", bad = true)
            return
        }
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
        armed = null
        refresh()
    }

    private fun plural(count: Int) = if (count == 1) "1 bind" else "$count binds"

    private fun refresh() {
        val bind = current
        val armed = armed
        val said = said
        readout = fit(
            when {
                armed is CommandBind -> "Press the cross again to delete it."
                armed is String -> "Press again to delete it" + (inGroup.size.takeIf { it > 0 }?.let { " and its ${plural(it)}" } ?: "") + "."
                said != null -> said
                cards.isEmpty() -> "Add a bind with New Bind."
                bind == null || bind !in settings.binds -> "${plural(inGroup.size)} in ${groupName(group)}."
                !bind.enabled -> "${bind.title} is off."
                bind.keys.isEmpty() -> "${bind.title} has no keys yet."
                else -> CommandBinds.clashes(bind, settings.binds).firstOrNull()?.let { "${Keys.names(bind.keys, bind.inOrder)} also fires ${it.title}." }
                    ?: "${Keys.names(bind.keys, bind.inOrder)} ${describe(bind)}."
            },
            listWidth - font.width("New Bind") - 16 - Kit.GAP,
        )
    }

    private fun scroll(by: Int) {
        first = (first + by).coerceIn(0, (cards.size - rows).coerceAtLeast(0))
        layout()
    }

    private fun scrollTo(index: Int) {
        if (index < 0) return
        scroll(if (index < first) index - first else if (index >= first + rows) index - first - rows + 1 else 0)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val focused = focused
        if (focused is KitTextField && (event.isEscape || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(null)
            if (group.isNotEmpty() && groupField.value.trim() != group) {
                groupField.value = group
                said = null
                refresh()
            }
            return true
        }
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scroll(-rows)
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll(rows)
            GLFW.GLFW_KEY_HOME -> if (focused !is KitTextField) scroll(-cards.size) else return super.keyPressed(event)
            GLFW.GLFW_KEY_END -> if (focused !is KitTextField) scroll(cards.size) else return super.keyPressed(event)
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (cards.size > rows && event.button() == 0 && event.x >= scrollX && event.x < scrollX + Kit.SCROLLBAR_WIDTH &&
            event.y >= listY && event.y < listY + listHeight
        ) {
            dragging = true
            drag(event.y)
            return true
        }
        val before = armed
        val handled = super.mouseClicked(event, doubleClick)
        // A press anywhere but the armed cross disarms it.
        if (before != null && armed == before) {
            armed = null
            refresh()
        }
        return handled
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) {
            drag(event.y)
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    /** Puts the thumb's middle under the mouse. */
    private fun drag(mouseY: Double) {
        val thumb = thumbHeight()
        val at = ((mouseY - listY - 1 - thumb / 2.0) / (listHeight - 2 - thumb)).coerceIn(0.0, 1.0)
        first = (at * (cards.size - rows)).roundToInt()
        layout()
    }

    private fun thumbHeight() = ((listHeight - 2) * rows / cards.size.coerceAtLeast(rows)).coerceAtLeast(8)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        val by = if (scrollY > 0) -1 else 1
        if (mouseX < listX) {
            firstGroup += by
            layout()
        } else {
            scroll(by)
        }
        return true
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        val at = cards.indexOfFirst { card -> card.widgets.any { it === focused } }
        if (at >= 0) {
            current = cards[at].bind
            scrollTo(at)
            refresh()
        }
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        val textX = listX + 1 + Kit.BUTTON_HEIGHT + Kit.GAP
        for ((i, card) in cards.withIndex()) {
            if (i - first !in 0 until rows) continue
            val y = listY + (i - first) * CARD_STEP
            val bind = card.bind
            Kit.well(graphics, listX, y, cardWidth, CARD)
            if (bind === current) Kit.ring(graphics, listX - 1, y - 1, cardWidth + 2, CARD + 2, Kit.theme.accent)
            val title = fit(bind.title, card.edit.x - Kit.GAP - textX)
            graphics.text(font, text { if (bind.enabled) body(title) else muted(title) }.toComponent(), textX, y + 5, -1, true)
            val clash = CommandBinds.clashes(bind, settings.binds).isNotEmpty()
            val game = bind.keys.size == 1 && Keys.gameKey(bind.keys[0]) != null
            val room = card.on.x - Kit.GAP - textX
            val keys = fit(Keys.names(bind.keys, bind.inOrder), room)
            val command = fit(" " + bind.command.trim(), room - font.width(keys))
            val line = text {
                when {
                    bind.keys.isEmpty() || clash -> bad(keys)
                    game -> warning(keys)
                    else -> value(keys)
                }
                muted(command)
            }
            graphics.text(font, line.toComponent(), textX, y + CARD - LABEL_HEIGHT - 6, -1, true)
            if (armed === bind) Kit.ring(graphics, card.delete.x - 1, card.delete.y - 1, Kit.BUTTON_HEIGHT + 2, Kit.BUTTON_HEIGHT + 2, Role.BAD.argb())
        }
        if (cards.size > rows) {
            val thumb = thumbHeight()
            Kit.scrollbar(graphics, scrollX, listY, listHeight, (listHeight - 2 - thumb) * first / (cards.size - rows), thumb)
        }
        for ((name, button) in groupButtons) {
            if (name == group && button.visible) Kit.ring(graphics, button.x - 1, button.y - 1, button.width + 2, button.height + 2, Kit.theme.accent)
        }
        if (armed is String) Kit.ring(graphics, deleteGroup.x - 1, deleteGroup.y - 1, Kit.BUTTON_HEIGHT + 2, Kit.BUTTON_HEIGHT + 2, Role.BAD.argb())
        Kit.label(graphics, readout, listX, readoutY, if (said != null && saidBad) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
