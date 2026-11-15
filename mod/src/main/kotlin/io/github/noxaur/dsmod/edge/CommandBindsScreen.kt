package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBind.Message
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitKeyButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.resources.language.I18n
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

private const val TITLE = "Command Binds"
private const val WIDTH = 300
private const val ROWS = 8
private const val ROW = Kit.BUTTON_HEIGHT + Kit.GAP
private const val KEY_WIDTH = 76
private const val LABEL_HEIGHT = 8

/**
 * Lists Command Binds' rows, each a command, its key and a button that removes it, with New Bind under them. The
 * list scrolls by whole rows: the wheel, the scrollbar's thumb, Page Up and Page Down, and Home and End outside a
 * field. A key shared with another row is ringed in `bad`, and one the game uses too in `warning`. The line under
 * the list reads back the row last touched. Every change is in the settings at once, and closing saves them.
 */
class CommandBindsScreen(private val settings: DsModConfig.General, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    private class Row(val field: KitTextField, val key: KitKeyButton, val remove: KitButton)

    private val rows = mutableListOf<Row>()
    private var first = 0
    private var current = 0
    private var dragging = false
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var listY = 0
    private var readoutY = 0
    private var readout = ""

    private val listHeight = ROWS * ROW - Kit.GAP
    private val scrollX get() = left + WIDTH - Kit.SCROLLBAR_WIDTH
    private val fieldWidth = WIDTH - Kit.SCROLLBAR_WIDTH - Kit.GAP * 3 - KEY_WIDTH - Kit.BUTTON_HEIGHT

    override fun init() {
        val panelWidth = WIDTH + Kit.INSET * 2
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 2 + listHeight + LABEL_HEIGHT + Kit.GAP * 5
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        listY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        readoutY = listY + listHeight + Kit.GAP * 2
        val doneWidth = font.width("Done") + 16
        addRenderableWidget(KitButton(left + WIDTH - doneWidth, top, "Done", ::onClose, doneWidth))
        addRenderableWidget(KitButton(left, readoutY + LABEL_HEIGHT + Kit.GAP * 2, "New Bind", ::add))
        rows.clear()
        settings.binds.forEach(::row)
        layout()
        refresh()
    }

    private fun row(bind: CommandBind): Row {
        lateinit var row: Row
        val field = KitTextField(left, 0, fieldWidth, "Command", bind.command, CommandBind.MAX_LENGTH) { changed(row) }
        field.setHint(text { muted("/warp end") }.toComponent())
        val key = KitKeyButton(left + fieldWidth + Kit.GAP, 0, KEY_WIDTH, "Key", bind.key) { changed(row) }
        val remove = KitButton(left + fieldWidth + KEY_WIDTH + Kit.GAP * 2, 0, Icon.CROSS, "Remove") { remove(row) }
        row = Row(addRenderableWidget(field), addRenderableWidget(key), addRenderableWidget(remove))
        rows += row
        return row
    }

    private fun add() {
        val row = row(CommandBind())
        current = rows.lastIndex
        save()
        scrollTo(rows.lastIndex)
        setFocused(row.field)
    }

    private fun remove(row: Row) {
        val at = rows.indexOf(row)
        rows.removeAt(at)
        removeWidget(row.field)
        removeWidget(row.key)
        removeWidget(row.remove)
        current = at.coerceAtMost(rows.lastIndex).coerceAtLeast(0)
        save()
        scroll(0)
    }

    private fun changed(row: Row) {
        current = rows.indexOf(row)
        save()
    }

    private fun save() {
        settings.binds = rows.map { CommandBind(it.field.value, it.key.key) }
        refresh()
    }

    private fun scroll(by: Int) {
        first = (first + by).coerceIn(0, (rows.size - ROWS).coerceAtLeast(0))
        layout()
    }

    private fun scrollTo(index: Int) = scroll(if (index < first) index - first else if (index >= first + ROWS) index - first - ROWS + 1 else 0)

    private fun layout() {
        for ((i, row) in rows.withIndex()) {
            val shown = i - first in 0 until ROWS
            val y = listY + (i - first) * ROW
            row.field.place(left, y)
            row.key.y = y
            row.remove.y = y
            row.field.visible = shown
            row.key.visible = shown
            row.remove.visible = shown
        }
    }

    /** The game's own key bind on [bound], if any. A debug key works only with F3 held, so only F3 itself counts. */
    private fun gameKey(bound: Int): KeyMapping? {
        if (bound < 0) return null
        val options = minecraft.options
        return options.keyMappings.firstOrNull {
            (it.category != KeyMapping.Category.DEBUG || it === options.keyDebugModifier) && Keys.matches(KeyMappingHelper.getBoundKeyOf(it), bound)
        }
    }

    private fun refresh() {
        val clashes = CommandBind.clashes(settings.binds)
        for (row in rows) {
            val bound = row.key.key
            row.key.clash = if (bound in clashes) Role.BAD else if (gameKey(bound) != null) Role.WARNING else null
        }
        val row = rows.getOrNull(current)
        val bind = settings.binds.getOrNull(current)
        readout = when {
            row == null || bind == null -> "Add a bind to send a command with one key."
            row.key.listening -> "Press a key or mouse button. Escape clears it."
            bind.message == null -> "Type a command, such as /warp end."
            bind.key < 0 -> "Pick a key for ${bind.command.trim()}."
            bind.key in clashes -> "${Keys.name(bind.key)} also sends ${settings.binds.first { it.key == bind.key && it !== bind }.command.trim()}."
            else -> gameKey(bind.key)?.let { "${Keys.name(bind.key)} is also the game's ${I18n.get(it.name)} key." }
                ?: when (val message = bind.message) {
                    is Message.Chat -> "${Keys.name(bind.key)} says ${message.text} in chat."
                    else -> "${Keys.name(bind.key)} sends ${bind.command.trim()}."
                }
        }
        readout = font.plainSubstrByWidth(readout, WIDTH).let { if (it.length < readout.length) font.plainSubstrByWidth(readout, WIDTH - font.width("...")) + "..." else it }
    }

    private fun listening() = rows.firstOrNull { it.key.listening }

    override fun keyPressed(event: KeyEvent): Boolean {
        listening()?.let {
            it.key.capture(event.key())
            refresh()
            return true
        }
        val focused = focused
        if (focused is KitTextField && (event.isEscape || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(null)
            return true
        }
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scroll(-ROWS)
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll(ROWS)
            GLFW.GLFW_KEY_HOME -> if (focused !is KitTextField) scroll(-rows.size) else return super.keyPressed(event)
            GLFW.GLFW_KEY_END -> if (focused !is KitTextField) scroll(rows.size) else return super.keyPressed(event)
            else -> return super.keyPressed(event).also { refresh() }
        }
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // A press on the listening button itself stops it, and a press elsewhere stops it and does its own job too.
        listening()?.let { row ->
            val bound = row.key.capture(event.button(), mouse = true)
            refresh()
            if (bound || row.key.isMouseOver(event.x, event.y)) return true
        }
        if (rows.size > ROWS && event.button() == 0 && event.x >= scrollX && event.x < scrollX + Kit.SCROLLBAR_WIDTH &&
            event.y >= listY && event.y < listY + listHeight
        ) {
            dragging = true
            drag(event.y)
            return true
        }
        val handled = super.mouseClicked(event, doubleClick)
        rows.indexOfFirst { it.key.listening || it.field.isFocused }.takeIf { it >= 0 }?.let { current = it }
        refresh()
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
        val track = listHeight - 2
        val thumb = thumbHeight()
        val at = ((mouseY - listY - 1 - thumb / 2.0) / (track - thumb)).coerceIn(0.0, 1.0)
        first = (at * (rows.size - ROWS)).roundToInt()
        layout()
    }

    private fun thumbHeight() = ((listHeight - 2) * ROWS / rows.size).coerceAtLeast(8)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        scroll(if (scrollY > 0) -1 else 1)
        return true
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        rows.indexOfFirst { it.field === focused || it.key === focused || it.remove === focused }.takeIf { it >= 0 }?.let {
            current = it
            scrollTo(it)
            refresh()
        }
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        // As in vanilla, a list that fits shows no scrollbar.
        if (rows.size > ROWS) {
            val thumb = thumbHeight()
            Kit.scrollbar(graphics, scrollX, listY, listHeight, (listHeight - 2 - thumb) * first / (rows.size - ROWS), thumb)
        }
        Kit.label(graphics, readout, left, readoutY, if (rows.isEmpty()) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
