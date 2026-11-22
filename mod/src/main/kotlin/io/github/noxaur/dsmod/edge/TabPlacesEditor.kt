package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.ui.Box
import io.github.noxaur.dsmod.edge.ui.BoxEditor
import io.github.noxaur.dsmod.edge.ui.Editor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.edge.ui.fit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/**
 * Menu Tabs set up in the settings: a small menu with its tab places along the top and bottom edges, and beside it
 * the picked tab's command, item and name. A press on an empty place adds a tab there, and a press on a tab picks
 * it. A tab dragged to another place trades places with any tab there, and Escape during the drag puts it back. A
 * press on the menu gives it the keys: the arrows move the picked tab, Tab picks the next, Delete asks for a second
 * press, and Escape gives the keys back. Each change applies at once.
 */
class TabPlacesEditor(private val panel: Panel<List<MenuTab>>, private val host: Editor.Host) : BoxEditor {
    override val box = Box()


    // The menu, from the content's top, and the form beside or under it.
    private var menuY = 0
    private var formX = 0
    private var formY = 0
    private var formWidth = 0
    private var buttonsY = 0
    private var readoutY = 0
    private val buttons = EditorButtons(BUTTONS)

    /** The index in the tabs of the tab the form changes, or -1. */
    private var selected = -1
    private var grabbed = -1
    private var grabX = 0.0
    private var grabY = 0.0
    private var pressX = 0.0
    private var pressY = 0.0
    private var dragging = false
    private var mouseX = 0.0
    private var mouseY = 0.0

    /** Whether the keys are on the menu, which a press there or Enter on the row gives it. */
    private var entered = false
    private var armed = false
    private var readout = ""
    private var readoutText: Component = Component.empty()

    /** The place the mouse was over when the readout was last made, as [code] says, so it is made again only on a change. */
    private var readFor = NONE
    private var labelWidth = 0

    private val command = KitTextField(0, 0, 0, "Command", "", COMMAND_LENGTH) { typed -> change { it.copy(command = TabLayout.command(typed)) } }.hint("warp end")
    private val item: KitTextField = KitTextField(0, 0, 0, "Item", "", COMMAND_LENGTH) { typed ->
        val id = TabLayout.itemId(typed)
        val known = isItem(id)
        item.setTextColor(if (known || typed.isBlank()) Role.BODY.argb() else Role.BAD.argb())
        when {
            known -> change { it.copy(item = id) }
            typed.isBlank() -> change { it.copy(item = "") }
        }
    }.hint("chest")
    private val name = KitTextField(0, 0, 0, "Name", "", COMMAND_LENGTH) { typed -> change { it.copy(tooltip = typed.trim()) } }.hint("Optional")
    private val fields = listOf(command, item, name)
    private val icons = HashMap<String, ItemStack>()
    private var focused: KitTextField? = null

    private var tabs: List<MenuTab>
        get() = panel.value
        set(value) {
            panel.value = value
        }

    private val font get() = Kit.font

    override val capturing get() = entered || dragging || grabbed >= 0

    override fun layout(x: Int, y: Int, width: Int): Int {
        box.place(x, y, width)
        menuY = y + Kit.TAB_HEIGHT - 2
        val menuBottom = menuY + MENU_HEIGHT + Kit.TAB_HEIGHT - 2
        labelWidth = LABELS.maxOf(font::width) + Kit.GAP
        if (width - MENU_WIDTH - FORM_GAP >= FORM_MIN) {
            formX = x + MENU_WIDTH + FORM_GAP
            formY = y
            formWidth = minOf(width - MENU_WIDTH - FORM_GAP, FORM_MAX)
        } else {
            formX = x
            formY = menuBottom + Kit.GAP * 2
            formWidth = minOf(width, FORM_MAX)
        }
        buttonsY = formY + FIELD_STEP * 3
        // Two buttons to a row, each half the form, as the tab editor had them.
        val half = (formWidth - Kit.GAP) / 2
        for (i in BUTTONS.indices) {
            buttons.place(i, formX + (i % 2) * (half + Kit.GAP), half)
        }
        readoutY = buttonsY + FIELD_STEP * 2
        box.height = maxOf(menuBottom, readoutY + 9) - y + 2
        select(selected.coerceAtMost(tabs.size - 1))
        return box.height
    }

    override fun refresh() {
        if (selected >= tabs.size) select(-1) else if (focused == null) select(selected)
        say(place(mouseX, mouseY))
    }

    /** Words the readout for the mouse over [at], once per change. */
    private fun say(at: Int) {
        readFor = at
        readout = readout(at)
        readoutText = text { muted(fit(readout, box.width - (formX - box.x))) }.toComponent()
    }

    override fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int) {
        box.view(origin, clipTop, clipBottom)
        val left = formX + labelWidth
        for ((i, field) in fields.withIndex()) {
            val itemRoom = if (field === item) Kit.SLOT_SIZE + Kit.GAP else 0
            field.setWidth(formWidth - labelWidth - itemRoom - 8)
            field.place(left + itemRoom, box.origin + formY + i * FIELD_STEP)
        }
    }

    private fun menuTop() = box.origin + menuY

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        this.mouseX = mouseX.toDouble()
        this.mouseY = mouseY.toDouble()
        val t = Kit.theme
        val menuY = menuTop()
        val place = place(this.mouseX, this.mouseY)
        val hoveredTab = if (grabbed < 0 && place != NONE) TabLayout.at(tabs, edgeOf(place), slotOf(place)) else -1
        if (place != readFor && !dragging) say(place)
        // The tabs sit behind the menu, as they do on a real one, with an outline on each free place.
        for (edge in TabEdge.entries) {
            for (slot in 0 until TabLayout.PER_EDGE) {
                val index = TabLayout.at(tabs, edge, slot)
                val tx = box.x + TabLayout.x(slot)
                if (index < 0 || index == grabbed && dragging) {
                    val ty = tabY(edge) + 2
                    Kit.ring(g, tx, ty, Kit.TAB_WIDTH, Kit.TAB_HEIGHT - 4, if (place == code(edge, slot)) t.hoverOutline else t.inkOff)
                    continue
                }
                drawTab(g, tabs[index], tx, edgeY(edge), index == hoveredTab || index == selected)
            }
        }
        Kit.panel(g, box.x, menuY, MENU_WIDTH, MENU_HEIGHT)
        Kit.label(g, "Any menu", box.x + Kit.INSET + 1, menuY + 6, Kit.Ink.MUTED)
        for (column in 0 until 9) Kit.slot(g, box.x + 7 + column * Kit.SLOT_SIZE, menuY + 17)
        if (selected >= 0 && !dragging) {
            val tab = tabs[selected]
            val ty = tabY(tab.edge)
            Kit.ring(g, box.x + TabLayout.x(tab.slot) - 1, ty - 1, Kit.TAB_WIDTH + 2, Kit.TAB_HEIGHT + 1, t.accent)
        }
        if (entered && keys) Kit.ring(g, box.x - 2, menuY - 2, MENU_WIDTH + 4, MENU_HEIGHT + 4, t.hoverOutline)

        val tab = tabs.getOrNull(selected)
        // The form sits on the row's well, so its names are role text, which reads there in both themes.
        for ((i, label) in LABEL_TEXT.withIndex()) {
            g.text(font, label, formX, box.origin + formY + i * FIELD_STEP + (Kit.BUTTON_HEIGHT - 8) / 2, -1, true)
            fields[i].extractRenderState(g, mouseX, mouseY, 0f)
        }
        val slotX = formX + labelWidth
        val slotY = box.origin + formY + FIELD_STEP - 1
        Kit.slot(g, slotX, slotY)
        if (tab != null && !tab.search) icon(tab.item)?.let { g.item(it, slotX + 1, slotY + 1) }
        for (i in BUTTONS.indices) {
            val by = box.origin + buttonY(i)
            buttons.draw(g, i, by, this.mouseX, this.mouseY, buttonActive(i))
            if (i == DELETE && armed) Kit.ring(g, buttons.x(i) - 1, by - 1, buttons.width(i) + 2, Kit.BUTTON_HEIGHT + 2, Role.BAD.argb())
        }
        g.text(font, readoutText, formX, box.origin + readoutY, -1, true)

        if (dragging && grabbed >= 0) {
            val held = tabs[grabbed]
            drawTab(g, held, (this.mouseX - grabX).toInt(), (this.mouseY - grabY).toInt() + edgeY(held.edge) - tabY(held.edge), true)
            g.requestCursor(CursorTypes.RESIZE_ALL)
        } else if (hoveredTab >= 0) {
            g.requestCursor(CursorTypes.RESIZE_ALL)
        } else if (place != NONE) {
            g.requestCursor(CursorTypes.POINTING_HAND)
        }
        return null
    }

    /** The middle of place [slot] along [edge], on the screen, so a test can press it. */
    /** Where form field [which] is, 0 the command, 1 the item and 2 the name, so a test can type in it. */
    fun fieldSpot(which: Int): IntArray {
        val field = fields[which]
        return intArrayOf(field.x, field.y, field.width, field.height)
    }

    fun placeAt(edge: TabEdge, slot: Int): Pair<Int, Int> {
        val y = tabY(edge)
        return box.x + TabLayout.x(slot) + Kit.TAB_WIDTH / 2 to y + Kit.TAB_HEIGHT / 2
    }

    /** The middle of the field or button named [name] on the screen. */
    fun controlAt(name: String): Pair<Int, Int> {
        val field = fields.getOrNull(LABELS.indexOf(name))
        if (field != null) return field.x + field.width / 2 to field.y + 4
        val i = BUTTONS.indexOf(name)
        return buttons.middle(i) to box.origin + buttonY(i) + Kit.BUTTON_HEIGHT / 2
    }

    private fun buttonY(i: Int) = buttonsY + (i / 2) * FIELD_STEP

    private fun drawTab(g: GuiGraphicsExtractor, tab: MenuTab, tx: Int, edgeY: Int, hovered: Boolean) {
        val tabTop = Kit.tab(g, tx, edgeY, false, tab.edge, hovered)
        if (tab.search) {
            Kit.icon(g, Icon.SEARCH, tx + (Kit.TAB_WIDTH - Icon.SEARCH.width) / 2, tabTop + (16 - Icon.SEARCH.height) / 2, Kit.theme.ink, Kit.theme.inkShadow)
        } else {
            icon(tab.item)?.let { g.item(it, tx + (Kit.TAB_WIDTH - 16) / 2, tabTop) }
        }
    }

    /**
     * The item a tab shows, made once per item. Before the first world the game has not given items their parts,
     * so a tab shows no item on the title screen rather than failing.
     */
    private fun icon(id: String): ItemStack? {
        if (!Items.PAPER.builtInRegistryHolder().areComponentsBound()) return null
        return icons.getOrPut(id) { stackOf(id) }
    }

    private fun edgeY(edge: TabEdge) = if (edge == TabEdge.TOP) menuTop() else menuTop() + MENU_HEIGHT

    /** Where a tab on [edge] starts, tucked 2 pixels behind the menu. */
    private fun tabY(edge: TabEdge) = edgeY(edge) + if (edge == TabEdge.TOP) 2 - Kit.TAB_HEIGHT else -2

    /** A place as one number, so the frame finds the one under the mouse without making anything. */
    private fun code(edge: TabEdge, slot: Int) = edge.ordinal * TabLayout.PER_EDGE + slot

    private fun edgeOf(code: Int) = TabEdge.entries[code / TabLayout.PER_EDGE]

    private fun slotOf(code: Int) = code % TabLayout.PER_EDGE

    /** The place under the mouse, along either edge, or [NONE]. */
    private fun place(px: Double, py: Double): Int {
        if (py < box.clipTop || py >= box.clipBottom) return NONE
        val menuY = menuTop()
        val edge = when (py.toInt()) {
            in menuY - Kit.TAB_HEIGHT + 2 until menuY + 2 -> TabEdge.TOP
            in menuY + MENU_HEIGHT - 2 until menuY + MENU_HEIGHT + Kit.TAB_HEIGHT - 2 -> TabEdge.BOTTOM
            else -> return NONE
        }
        val slot = TabLayout.slotAt(px.toInt() - box.x)
        return if (slot < 0) NONE else code(edge, slot)
    }

    /** Where a dragged tab lands: the place along the edge nearer the mouse, or null past the ends of the edges. */
    private fun dropAt(px: Double, py: Double): Pair<TabEdge, Int>? {
        val slot = TabLayout.slotAt(px.toInt() - box.x)
        if (slot < 0) return null
        return (if (py < menuTop() + MENU_HEIGHT / 2) TabEdge.TOP else TabEdge.BOTTOM) to slot
    }

    private fun readout(place: Int): String {
        val tab = tabs.getOrNull(selected)
        return when {
            dragging -> "Drop it on a place."
            armed -> "Press Delete again."
            place != NONE && TabLayout.at(tabs, edgeOf(place), slotOf(place)) < 0 -> "Click to add a tab here."
            tab == null -> "Click a tab to change it."
            tab.search -> "Search. Drag it to move it."
            tab.command.isEmpty() -> "Type the command it sends."
            else -> "Sends /${tab.command}."
        }
    }

    private fun buttonActive(i: Int) = when (i) {
        ADD_TAB -> TabLayout.free(tabs) != null
        ADD_SEARCH -> TabLayout.free(tabs) != null && tabs.none { it.search }
        else -> selected >= 0
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val px = event.x()
        val py = event.y()
        // A press elsewhere, a field scrolled under the tab bar included, takes the keys back and still does its own job.
        if (!box.isOver(px, py)) {
            leave()
            return false
        }
        for (field in fields) {
            if (field.active && field.isMouseOver(px, py)) {
                focus(field)
                field.mouseClicked(event, doubleClick)
                return true
            }
        }
        focus(null)
        entered = true
        for (i in BUTTONS.indices) {
            if (buttons.at(i, box.origin + buttonY(i), px, py)) {
                if (buttonActive(i)) press(i)
                return true
            }
        }
        if (armed) armed = false
        val place = place(px, py)
        if (place == NONE || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true.also { refresh() }
        val edge = edgeOf(place)
        val slot = slotOf(place)
        val index = TabLayout.at(tabs, edge, slot)
        if (index < 0) {
            add(MenuTab(edge, slot, item = "minecraft:paper"), edge to slot)
            return true
        }
        select(index)
        grabbed = index
        grabX = px - (box.x + TabLayout.x(slot))
        grabY = py - tabY(edge)
        pressX = px
        pressY = py
        return true
    }

    private fun press(button: Int) {
        when (button) {
            ADD_TAB -> add(MenuTab(command = "", item = "minecraft:paper"))
            ADD_SEARCH -> add(MenuTab(item = "", tooltip = "Search", search = true))
            DELETE -> remove()
        }
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (grabbed < 0) return false
        mouseX = event.x()
        mouseY = event.y()
        if (!dragging && (Math.abs(event.x() - pressX) >= DRAG_START || Math.abs(event.y() - pressY) >= DRAG_START)) {
            dragging = true
            say(NONE)
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (grabbed < 0) return false
        if (dragging) dropAt(event.x(), event.y())?.let { (edge, slot) -> changeAll(TabLayout.move(tabs, grabbed, edge, slot)) }
        grabbed = -1
        dragging = false
        refresh()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean) = false

    override fun keyPressed(event: KeyEvent): Boolean {
        if (dragging) {
            if (event.isEscape) {
                grabbed = -1
                dragging = false
                refresh()
            }
            return true
        }
        val typing = focused
        if (typing != null) {
            when {
                event.isEscape || event.isConfirmation -> focus(null)
                // Tab and Shift + Tab move between the form's fields, as in the binds list.
                event.key() == GLFW.GLFW_KEY_TAB -> {
                    val by = if (Keys.shift(event)) -1 else 1
                    var at = fields.indexOf(typing)
                    do at = Math.floorMod(at + by, fields.size) while (!fields[at].active)
                    focus(fields[at])
                }
                else -> typing.keyPressed(event)
            }
            return true
        }
        if (!entered) {
            if (!(event.isSelection || event.isConfirmation)) return false
            entered = true
            if (selected < 0) select(tabs.indices.firstOrNull() ?: -1)
            refresh()
            return true
        }
        val tab = tabs.getOrNull(selected)
        val key = event.key()
        when {
            event.isEscape -> leave()
            key == GLFW.GLFW_KEY_TAB -> if (tabs.isNotEmpty()) select(Math.floorMod(selected + if (Keys.shift(event)) -1 else 1, tabs.size))
            tab != null && (event.isLeft || event.isRight || event.isUp || event.isDown) -> {
                val to = when {
                    event.isLeft -> tab.edge to tab.slot - 1
                    event.isRight -> tab.edge to tab.slot + 1
                    event.isUp -> TabEdge.TOP to tab.slot
                    else -> TabEdge.BOTTOM to tab.slot
                }
                if (to.second in 0 until TabLayout.PER_EDGE) changeAll(TabLayout.move(tabs, selected, to.first, to.second))
            }
            tab != null && (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) -> remove()
            tab != null && (event.isSelection || event.isConfirmation) && !tab.search -> focus(command)
            else -> return true
        }
        refresh()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val typing = focused ?: return false
        typing.charTyped(event)
        return true
    }

    override fun leave() {
        entered = false
        armed = false
        focus(null)
        if (dragging) {
            dragging = false
            grabbed = -1
        }
        refresh()
    }

    private fun focus(field: KitTextField?) {
        focused?.setFocused(false)
        focused = field
        field?.setFocused(true)
        if (field != null) entered = true
    }

    /** Points the form at the tab at [index], or at none for -1. */
    private fun select(index: Int) {
        if (index != selected) armed = false
        selected = index
        val tab = tabs.getOrNull(index)
        val form = tab?.takeIf { !it.search }
        for (field in fields) field.active = form != null
        // The fields write to the selected tab as they are filled, so they are filled while none is selected.
        selected = -1
        command.value = form?.command ?: ""
        item.value = form?.item?.removePrefix("minecraft:") ?: ""
        name.value = form?.tooltip ?: ""
        selected = index
        say(NONE)
    }

    private fun change(edit: (MenuTab) -> MenuTab) {
        if (selected < 0) return
        changeAll(tabs.mapIndexed { i, tab -> if (i == selected) edit(tab) else tab })
    }

    private fun changeAll(next: List<MenuTab>) {
        tabs = next
        armed = false
        host.changed()
    }

    private fun add(tab: MenuTab, at: Pair<TabEdge, Int>? = null) {
        val next = if (at != null) tabs + tab.copy(edge = at.first, slot = at.second) else TabLayout.add(tabs, tab) ?: return
        changeAll(next)
        select(next.size - 1)
        if (!tab.search) focus(command)
    }

    private fun remove() {
        if (selected < 0) return
        if (!armed) {
            armed = true
            refresh()
            return
        }
        armed = false
        changeAll(tabs.filterIndexed { i, _ -> i != selected })
        select(-1)
    }

    override fun narrate(output: NarrationElementOutput): Boolean {
        focused?.let {
            it.updateNarration(output)
            return true
        }
        if (!entered) return false
        val tab = tabs.getOrNull(selected)
        val where = tab?.let { "${if (it.edge == TabEdge.TOP) "Top" else "Bottom"} ${it.slot + 1}" }
        val what = tab?.let { if (it.search) "Search" else Narration.join(it.tooltip.ifBlank { null }, it.command.ifBlank { null }?.let { c -> "sends /$c" }) }
        output.add(NarratedElementType.TITLE, Narration.join(panel.name, "editor", where ?: "no tab picked", what, tab?.let { Narration.of(selected, tabs.size) }))
        output.add(NarratedElementType.HINT, "Arrows move the tab, Tab picks the next, Delete deletes it, and Escape leaves.")
        return true
    }

    private companion object {
        const val MENU_WIDTH = Kit.INVENTORY_WIDTH
        const val MENU_HEIGHT = 42
        const val FORM_GAP = 12
        const val FORM_MIN = 150
        const val FORM_MAX = 220
        const val FIELD_STEP = Kit.BUTTON_HEIGHT + Kit.GAP
        const val DRAG_START = 3
        const val COMMAND_LENGTH = 256
        val LABELS = listOf("Command", "Item", "Name")
        val LABEL_TEXT = LABELS.map { text { muted(it) }.toComponent() }
        val BUTTONS = listOf("Add Tab", "Add Search", "Delete")
        const val ADD_TAB = 0
        const val ADD_SEARCH = 1
        const val DELETE = 2
        const val NONE = -1
    }
}
