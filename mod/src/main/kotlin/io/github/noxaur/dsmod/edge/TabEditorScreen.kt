package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

private const val TITLE = "Menu Tabs"
private const val MENU_WIDTH = 176
private const val MENU_HEIGHT = 166
private const val FORM_WIDTH = 160
private const val LABEL_HEIGHT = 8
private const val DRAG_START = 3
private const val COMMAND_LENGTH = 256

/**
 * Sets up Menu Tabs on a menu the size of the inventory, with the places along its top and bottom edges. Drag a tab
 * to another place, where it trades places with any tab there, or press Escape during the drag to put it back.
 * Click an empty place to add a tab there. The form on the right changes the tab last clicked, and with no field
 * focused the arrow keys move that tab and Delete removes it. Closing saves through [onSave] and goes back to [parent].
 */
class TabEditorScreen(private val settings: DsModConfig.BetterUi, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    private var menuX = 0
    private var menuY = 0
    private var formX = 0
    private var formY = 0
    private var formHeight = 0
    private var readoutY = 0
    private val labels = IntArray(3)

    /** The index in [tabs] of the tab the form changes, or -1. */
    private var selected = -1
    private var grabbed = -1
    private var grabX = 0.0
    private var grabY = 0.0
    private var pressX = 0.0
    private var pressY = 0.0
    private var dragging = false
    private var mouseX = 0.0
    private var mouseY = 0.0

    private lateinit var command: KitTextField
    private lateinit var item: KitTextField
    private lateinit var name: KitTextField
    private lateinit var delete: KitButton
    private lateinit var addTab: KitButton
    private lateinit var addSearch: KitButton
    private lateinit var reset: KitButton

    private var tabs: List<MenuTab>
        get() = settings.tabs
        set(value) {
            settings.tabs = value
        }

    private val fieldWidth = FORM_WIDTH - Kit.INSET * 2

    override fun init() {
        val total = MENU_WIDTH + Kit.GAP * 4 + FORM_WIDTH
        menuX = (width - total) / 2
        menuY = (height - MENU_HEIGHT) / 2
        formX = menuX + MENU_WIDTH + Kit.GAP * 4
        val rowHeight = LABEL_HEIGHT + 2 + Kit.BUTTON_HEIGHT + Kit.GAP
        formHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT + Kit.GAP + rowHeight * 3 + (Kit.BUTTON_HEIGHT + Kit.GAP) * 2 + LABEL_HEIGHT
        formY = (height - formHeight) / 2
        val left = formX + Kit.INSET
        var y = formY + Kit.INSET
        val doneWidth = font.width("Done") + 16
        addRenderableWidget(KitButton(left + fieldWidth - doneWidth, y, "Done", ::onClose, doneWidth))
        y += Kit.BUTTON_HEIGHT + Kit.GAP + LABEL_HEIGHT + 2
        labels[0] = y - LABEL_HEIGHT - 2
        command = addRenderableWidget(KitTextField(left, y, fieldWidth, "Command", "", COMMAND_LENGTH) { typed -> change { it.copy(command = TabLayout.command(typed)) } }.hint("warp end"))
        y += rowHeight
        labels[1] = y - LABEL_HEIGHT - 2
        item = addRenderableWidget(KitTextField(left, y, fieldWidth, "Item", "", COMMAND_LENGTH) { typed ->
            val id = TabLayout.itemId(typed)
            val known = Identifier.tryParse(id)?.let(BuiltInRegistries.ITEM::containsKey) == true
            item.setTextColor(if (known || typed.isBlank()) Role.BODY.argb() else Role.BAD.argb())
            if (known) change { it.copy(item = id) }
        }.hint("chest"))
        y += rowHeight
        labels[2] = y - LABEL_HEIGHT - 2
        name = addRenderableWidget(KitTextField(left, y, fieldWidth, "Name", "", COMMAND_LENGTH) { typed -> change { it.copy(tooltip = typed.trim()) } }.hint("Shown when you point at it"))
        y += rowHeight
        val half = (fieldWidth - Kit.GAP) / 2
        addTab = addRenderableWidget(KitButton(left, y, "Add Tab", { add(MenuTab(command = "", item = "minecraft:paper")) }, half))
        addSearch = addRenderableWidget(KitButton(left + half + Kit.GAP, y, "Add Search", { add(MenuTab(item = "", tooltip = "Search", search = true)) }, half))
        y += Kit.BUTTON_HEIGHT + Kit.GAP
        delete = addRenderableWidget(KitButton(left, y, "Delete", ::remove, half))
        reset = addRenderableWidget(KitButton(left + half + Kit.GAP, y, "Reset", {
            tabs = TabLayout.DEFAULT
            select(-1)
        }, half))
        readoutY = y + Kit.BUTTON_HEIGHT + Kit.GAP
        select(selected.coerceAtMost(tabs.size - 1))
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX.toDouble()
        this.mouseY = mouseY.toDouble()
        val t = Kit.theme
        val hoveredTab = if (grabbed < 0) tabAt(this.mouseX, this.mouseY) else -1
        val place = place(this.mouseX, this.mouseY)

        // The tabs sit behind the menu, as they do on a real one, with an outline on each free place.
        for (edge in TabEdge.entries) {
            for (slot in 0 until TabLayout.PER_EDGE) {
                val index = TabLayout.at(tabs, edge, slot)
                val x = menuX + TabLayout.x(slot)
                if (index < 0 || index == grabbed && dragging) {
                    val top = if (edge == TabEdge.TOP) menuY - Kit.TAB_HEIGHT + 4 else menuY + MENU_HEIGHT
                    Kit.ring(graphics, x, top, Kit.TAB_WIDTH, Kit.TAB_HEIGHT - 4, if (place == edge to slot) t.hoverOutline else t.inkOff)
                    continue
                }
                drawTab(graphics, tabs[index], x, edgeY(edge), index == hoveredTab || index == selected)
            }
        }
        Kit.panel(graphics, menuX, menuY, MENU_WIDTH, MENU_HEIGHT)
        Kit.label(graphics, "Any menu", menuX + Kit.INSET + 1, menuY + 6, Kit.Ink.MUTED)
        for (row in 0 until 4) {
            for (column in 0 until 9) Kit.slot(graphics, menuX + 7 + column * 18, menuY + 83 + row * 18 + if (row == 3) 4 else 0)
        }
        if (selected >= 0 && !dragging) {
            val tab = tabs[selected]
            val top = if (tab.edge == TabEdge.TOP) menuY - Kit.TAB_HEIGHT + 2 else menuY + MENU_HEIGHT - 2
            Kit.ring(graphics, menuX + TabLayout.x(tab.slot) - 1, top - 1, Kit.TAB_WIDTH + 2, Kit.TAB_HEIGHT + 1, t.accent)
        }

        Kit.panel(graphics, formX, formY, FORM_WIDTH, formHeight)
        val left = formX + Kit.INSET
        Kit.label(graphics, TITLE, left, formY + Kit.INSET + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        for ((i, pair) in listOf(command to "Command", item to "Item", name to "Name").withIndex()) {
            Kit.label(graphics, pair.second, left, labels[i], if (pair.first.active) Kit.Ink.NORMAL else Kit.Ink.OFF)
        }
        Kit.label(graphics, readout(place), left, readoutY, Kit.Ink.MUTED)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (dragging && grabbed >= 0) {
            val tab = tabs[grabbed]
            drawTab(graphics, tab, (this.mouseX - grabX).toInt(), (this.mouseY - grabY).toInt() + if (tab.edge == TabEdge.TOP) Kit.TAB_HEIGHT - 2 else 2, true)
            graphics.requestCursor(CursorTypes.RESIZE_ALL)
        } else if (hoveredTab >= 0) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL)
        } else if (place != null) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
    }

    private fun drawTab(graphics: GuiGraphicsExtractor, tab: MenuTab, x: Int, edgeY: Int, hovered: Boolean) {
        val top = Kit.tab(graphics, x, edgeY, false, tab.edge, hovered)
        if (tab.search) {
            Kit.icon(graphics, Icon.SEARCH, x + (Kit.TAB_WIDTH - Icon.SEARCH.width) / 2, top + (16 - Icon.SEARCH.height) / 2, Kit.theme.ink, Kit.theme.inkShadow)
        } else {
            graphics.item(TabBar.icon(tab.item), x + (Kit.TAB_WIDTH - 16) / 2, top)
        }
    }

    /** The middle of [slot] along [edge], on screen. */
    fun placeAt(edge: TabEdge, slot: Int): Pair<Int, Int> {
        val top = if (edge == TabEdge.TOP) menuY - Kit.TAB_HEIGHT + 2 else menuY + MENU_HEIGHT - 2
        return menuX + TabLayout.x(slot) + Kit.TAB_WIDTH / 2 to top + Kit.TAB_HEIGHT / 2
    }

    private fun edgeY(edge: TabEdge) = if (edge == TabEdge.TOP) menuY else menuY + MENU_HEIGHT

    /** The index of the tab under the mouse, or -1. */
    private fun tabAt(x: Double, y: Double): Int {
        val (edge, slot) = place(x, y) ?: return -1
        return TabLayout.at(tabs, edge, slot)
    }

    /** The place under the mouse, along either edge, or null. */
    private fun place(x: Double, y: Double): Pair<TabEdge, Int>? {
        val edge = when (y.toInt()) {
            in menuY - Kit.TAB_HEIGHT + 2 until menuY + 2 -> TabEdge.TOP
            in menuY + MENU_HEIGHT - 2 until menuY + MENU_HEIGHT + Kit.TAB_HEIGHT - 2 -> TabEdge.BOTTOM
            else -> return null
        }
        val slot = TabLayout.slotAt(x.toInt() - menuX)
        return if (slot < 0) null else edge to slot
    }

    /** Where a dragged tab lands: the place along the edge nearer the mouse, or null past the ends of the edges. */
    private fun dropAt(x: Double, y: Double): Pair<TabEdge, Int>? {
        val slot = TabLayout.slotAt(x.toInt() - menuX)
        if (slot < 0) return null
        return (if (y < menuY + MENU_HEIGHT / 2) TabEdge.TOP else TabEdge.BOTTOM) to slot
    }

    private fun readout(place: Pair<TabEdge, Int>?): String {
        val tab = tabs.getOrNull(selected)
        return when {
            dragging -> "Drop it on a place."
            place != null && TabLayout.at(tabs, place.first, place.second) < 0 -> "Click to add a tab here."
            tab == null -> "Click a tab to change it."
            tab.search -> "Search. Drag it to move it."
            tab.command.isEmpty() -> "Type the command it sends."
            else -> "Sends /${tab.command}."
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val place = place(event.x(), event.y())
        if (place == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        focused = null
        val index = TabLayout.at(tabs, place.first, place.second)
        if (index < 0) {
            add(MenuTab(place.first, place.second, item = "minecraft:paper"), place)
            return true
        }
        select(index)
        grabbed = index
        grabX = event.x() - (menuX + TabLayout.x(place.second))
        grabY = event.y() - (if (place.first == TabEdge.TOP) menuY - Kit.TAB_HEIGHT + 2 else menuY + MENU_HEIGHT - 2)
        pressX = event.x()
        pressY = event.y()
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (grabbed < 0) return super.mouseDragged(event, dx, dy)
        if (!dragging && (kotlin.math.abs(event.x() - pressX) >= DRAG_START || kotlin.math.abs(event.y() - pressY) >= DRAG_START)) dragging = true
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (grabbed < 0) return super.mouseReleased(event)
        if (dragging) {
            dropAt(event.x(), event.y())?.let { (edge, slot) -> tabs = TabLayout.move(tabs, grabbed, edge, slot) }
        }
        grabbed = -1
        dragging = false
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (dragging && event.isEscape) {
            grabbed = -1
            dragging = false
            return true
        }
        val typing = focused is KitTextField
        if (typing && (event.isEscape || event.isConfirmation)) {
            focused = null
            return true
        }
        val tab = tabs.getOrNull(selected)
        if (!typing && tab != null) {
            val to = when {
                event.isLeft -> tab.edge to tab.slot - 1
                event.isRight -> tab.edge to tab.slot + 1
                event.isUp -> TabEdge.TOP to tab.slot
                event.isDown -> TabEdge.BOTTOM to tab.slot
                else -> null
            }
            if (to != null) {
                if (to.second in 0 until TabLayout.PER_EDGE) tabs = TabLayout.move(tabs, selected, to.first, to.second)
                return true
            }
            if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                remove()
                return true
            }
        }
        return super.keyPressed(event)
    }

    /** Points the form at the tab at [index], or at none for -1. */
    private fun select(index: Int) {
        selected = index
        val tab = tabs.getOrNull(index)
        val editable = tab != null && !tab.search
        for (field in listOf(command, item, name)) field.active = editable
        // The responders write to the selected tab, so the fields are filled while none is selected.
        selected = -1
        command.value = if (editable) tab.command else ""
        item.value = if (editable) tab.item.removePrefix("minecraft:") else ""
        name.value = if (editable) tab.tooltip else ""
        selected = index
        refresh()
    }

    private fun change(edit: (MenuTab) -> MenuTab) {
        if (selected < 0) return
        tabs = tabs.mapIndexed { i, tab -> if (i == selected) edit(tab) else tab }
    }

    private fun add(tab: MenuTab, at: Pair<TabEdge, Int>? = null) {
        val next = if (at != null) tabs + tab.copy(edge = at.first, slot = at.second) else TabLayout.add(tabs, tab) ?: return
        tabs = next
        select(next.size - 1)
        if (!tab.search) focused = command
    }

    private fun remove() {
        if (selected < 0) return
        tabs = tabs.filterIndexed { i, _ -> i != selected }
        select(-1)
    }

    private fun refresh() {
        delete.active = selected >= 0
        addTab.active = TabLayout.free(tabs) != null
        addSearch.active = addTab.active && tabs.none { it.search }
        reset.active = tabs != TabLayout.DEFAULT
    }

    override fun tick() = refresh()
}
