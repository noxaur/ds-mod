package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Favorites
import io.github.noxaur.dsmod.core.config.FavoritesView
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.SettingSection
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.NavRow
import io.github.noxaur.dsmod.core.ui.NavTree
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.Scroll
import io.github.noxaur.dsmod.core.ui.SettingHit
import io.github.noxaur.dsmod.core.ui.SettingsSearch
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.ui.Editor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitNavTree
import io.github.noxaur.dsmod.edge.ui.KitTabBar
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.edge.ui.fit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.ScreenNarrationCollector
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

private const val ROW = 20
private const val HEADING = 12
private const val ABOVE = 12
private const val FIRST_ABOVE = 2
private const val BELOW = 4
private const val TOP = 8
private const val SIDES = 12
private const val BOTTOM = SIDES
private const val MAX_WIDTH = 380
private const val GAP = 6
private const val EMPTY_TOP = 24
private const val TIP_DELAY = 350L

/** A section that a page of the pane scrolls to sits this far under the pane's top. */
private const val JUMP_MARGIN = 4

/** The pane starts under the tab bar. */
private const val BAR = Kit.TAB_BAR_HEIGHT

private const val MOUSE_BACK = 3
private const val MOUSE_FORWARD = 4

/**
 * ds-mod's own settings screen, filling the window: a sidebar of sections with a search field, Done, and a button
 * that collapses or expands every section, and beside it a pane under a bar of browser tabs. Each tab is one place
 * of [tabs], a section or a search, with its own scroll and history, and the pane shows the selected one. Each
 * named subsection has a heading, with the subsection's switch when the subsection is one feature, and its rows sit
 * in a well, each with the control of its kind from [SettingRows]. Typing searches every section from the selected
 * tab, and the pane lists what matched under "Section › Subsection" headings, with the rows that hold a match open.
 * Favorites, the first section, lists the rows the player starred under the same headings, in the tree's order.
 *
 * A change applies to [config] at once, and [save] writes it when the screen goes.
 */
class SettingsScreen(
    private val parent: Screen?,
    private val tabs: SettingsTabs = session(),
    private val config: DsModConfig = DsModClient.settings,
    private val save: () -> Unit = DsModClient::saveSettings,
    /** A row to open at, as a link elsewhere does that names the setting it edits. */
    private var reveal: String? = null,
    /** Words to search for on open, as `/dsmod <words>` does. */
    private var query: String? = null,
) : Screen(text { body("ds-mod") }.toComponent()) {
    private sealed class Item(val y: Int, val height: Int)
    private class Heading(
        y: Int, val prefix: String?, val separatorX: Int, val name: String, val marked: Kit.Marked?, val nameX: Int, val switch: Switch?, val lineX: Int,
    ) : Item(y, HEADING)
    private class Group(y: Int, height: Int) : Item(y, height)
    private class Empty(y: Int, val line: String, val x: Int, val ink: Kit.Ink) : Item(y, 8)

    private val sections = sections(config)
    private val every = sections.flatMap { it.walk() }
    private val icons = List(sections.size) { if (it == FAVORITES) Icon.STAR else null }
    private val tree = NavTree(sections.map { it.named.size })
    private val pane = Scroll()
    private val rows = SettingRows(opened, ::build, ::scrollTo, stars = SettingRows.Stars({ it in config.favorites }, ::star, { config.general.favoriteKey }))

    private lateinit var search: KitTextField
    private lateinit var nav: KitNavTree
    private lateinit var bar: KitTabBar
    private lateinit var fold: KitButton
    private var clear: KitButton? = null

    private var side = 0
    private var contentX = 0
    private var contentWidth = 0
    private var items: List<Item> = emptyList()
    private var tops = IntArray(0)
    private var spiedAt = -1
    private var thumbGrab = -1.0

    /** The query whose hits [SettingRows.found] opened. */
    private var foundFor: String? = null
    private var hovered: SettingRows.Tip? = null
    private var hoveredSince = 0L
    private var lastFrame = 0L

    /** Whether the pane has shown a tab yet, so there is a scroll to keep. */
    private var shown = false

    /** The plus focuses the search field, which the press's own focus on the bar would take back. */
    private var focusSearch = false

    /** A press of the Favorite Key is followed by its character, which would start a search. */
    private var eatChar = false
    private var mouseX = 0.0
    private var mouseY = 0.0

    private val barX get() = width - Kit.SCROLLBAR_WIDTH - 2
    private val track get() = height - BAR - 4

    override fun init() {
        keep()
        side = if (width < 460) 92 else if (width < 600) 100 else 116
        // The sidebar's outline is its right edge, so its content keeps the same gap from that as from the window's.
        val inner = side - 1 - Kit.GAP * 2
        val footY = height - Kit.GAP - Kit.BUTTON_HEIGHT
        val navY = Kit.GAP + Kit.BUTTON_HEIGHT + Kit.GAP
        search = addRenderableWidget(KitTextField(Kit.GAP, Kit.GAP, inner, "Search", tabs.current.place.query, 64, ::onSearch).hint("Search").icon(Icon.SEARCH))
        nav = addRenderableWidget(
            KitNavTree(
                Kit.GAP, navY, inner, footY - Kit.GAP - navY, tree,
                sections.map { it.name }, sections.map { it.icon.argb() }, sections.map { s -> s.named.mapNotNull { it.name } },
                ::select, ::jump, ::folded, ::openTab, icons,
            ),
        )
        // Vanilla narrates the first focused entry it did not narrate last, so two entries that both claim focus take
        // turns. Only the part that has the keys claims it: the rows, the search field, or else the sidebar.
        nav.hasKeys = { !rows.keys && !rows.capturing && (focused == null || focused === nav) }
        addWidget(rows.narration)
        bar = addRenderableWidget(
            KitTabBar(side, 0, width - side, tabs, sections.map { it.name }, sections.map { it.icon.argb() }, ::pick, ::close, { newTab().also { focusSearch = true } }, ::move, icons),
        )
        addRenderableWidget(KitButton(Kit.GAP, footY, "Done", ::onClose, inner - Kit.GAP - Kit.BUTTON_HEIGHT))
        fold = addRenderableWidget(KitButton(Kit.GAP + inner - Kit.BUTTON_HEIGHT, footY, Icon.DOWN, "Expand All", ::foldAll))
        contentX = side + SIDES
        contentWidth = (width - side - SIDES * 2).coerceAtMost(MAX_WIDTH)
        pane.view = height - BAR
        clear = null
        query?.let {
            keep()
            tabs.current.search(it)
        }
        query = null
        reveal?.let(::goTo)
        show()
        reveal?.let(rows::reveal)
        reveal = null
    }

    /** Takes the selected tab to the section that holds the row [id], with the rows above it opened. */
    private fun goTo(id: String) {
        val section = sections.indexOfFirst { s -> s.walk().any { it.id == id } }.takeIf { it >= 0 } ?: return
        fun path(rows: List<Setting>): List<String>? {
            for (row in rows) {
                if (row.id == id) return emptyList()
                path(row.children)?.let { return listOf(row.id) + it }
            }
            return null
        }
        sections[section].groups.firstNotNullOfOrNull { path(it.rows) }?.let(opened::addAll)
        keep()
        if (tabs.current.place.section != section || tabs.current.place.searching) tabs.current.go(section)
    }

    /** Keeps where the selected tab's pane is, before the tab changes or the screen goes. */
    private fun keep() {
        if (!shown) return
        tabs.current.scroll = pane.offset
        tabs.current.current = tree.current
    }

    /** Shows the selected tab: its place in the sidebar and the search field, and the pane where it was. */
    private fun show() {
        val tab = tabs.current
        val place = tab.place
        if (place.section != FAVORITES || place.searching) view.left()
        if (search.value != place.query) search.value = place.query
        tree.searching = place.searching
        // Selecting a section again would reopen it after the player closed it with its arrow.
        if (place.section < 0) tree.deselect() else if (place.section != tree.selected) tree.select(place.section)
        nav.refresh()
        build()
        if (tab.jump >= 0) {
            tab.scroll = tops.getOrElse(tab.jump) { 0 } - JUMP_MARGIN
            tab.current = tab.jump
            tab.jump = -1
        }
        pane.to(tab.scroll)
        tree.current = tab.current
        // The pane takes the mark back only once it has scrolled, so a subsection a jump marked stays marked, even
        // one whose heading can't reach the top near the end of a short section.
        spiedAt = pane.offset
        folded()
        bar.layout()
        shown = true
    }

    /** Typing searches from the selected tab, which becomes that search. */
    private fun onSearch(text: String) {
        if (text == tabs.current.place.query) return
        retab { tabs.current.search(text) }
    }

    /** A press on a section takes the selected tab there, and opens it again if its arrow had closed it. */
    private fun select(section: Int) {
        retab {
            tabs.current.go(section)
            tree.select(section)
        }
    }

    /** Opens [section] at its subsection [sub]: the pane jumps there from another section, and glides within one. */
    private fun jump(section: Int, sub: Int) {
        val tab = tabs.current
        if (tab.place.searching || section != tab.place.section) {
            retab {
                tab.go(section)
                tab.jump = sub
            }
            return
        }
        tree.jump(section, sub)
        nav.refresh()
        pane.glide(tops.getOrElse(sub) { 0 } - JUMP_MARGIN)
        spiedAt = pane.offset
        folded()
    }

    /** A Ctrl or middle press on a section, or on a subsection [sub], opens it in a tab after the selected one. */
    private fun openTab(section: Int, sub: Int) {
        tabs.open(Place(section), sub)
        bar.layout()
    }

    private fun pick(index: Int) {
        if (index == tabs.selected) return
        retab { tabs.select(index) }
    }

    private fun close(index: Int) {
        keep()
        if (tabs.close(index)) show()
    }

    /** Opens a blank tab, selected, with the keyboard in the search field. */
    private fun newTab() {
        retab { tabs.new() }
        setFocused(search)
    }

    private fun move(from: Int, to: Int) {
        tabs.move(from, to)
        bar.layout()
    }

    private fun history(back: Boolean) {
        keep()
        if (if (back) tabs.current.back() else tabs.current.forward()) show()
    }

    /** Runs a tab's change from the keyboard: keeps where the pane was, changes the tabs, and shows the selected one. */
    private inline fun retab(change: () -> Unit) {
        keep()
        change()
        show()
    }

    private fun foldAll() {
        if (tree.anyShown) tree.collapseAll() else tree.expandAll()
        nav.refresh()
        folded()
    }

    /** The fold button collapses while any section shows its subsections, and expands once none does. */
    private fun folded() {
        if (tree.anyShown) fold.show(Icon.UP, "Collapse All") else fold.show(Icon.DOWN, "Expand All")
        fold.active = !tree.searching
    }

    /** Lays the pane out for the selected tab: its section, what its search found, or a line when it is blank. */
    private fun build() {
        val items = ArrayList<Item>()
        val tops = ArrayList<Int>()
        fun centered(y: Int, text: String, ink: Kit.Ink) {
            val line = fit(text, contentWidth)
            items += Empty(y, line, contentX + (contentWidth - font.width(line)) / 2, ink)
        }
        val place = tabs.current.place
        var y = TOP
        var above = FIRST_ABOVE
        clear?.let(::removeWidget)
        clear = null
        rows.begin()
        rows.words = if (place.searching) SettingsSearch.words(place.query) else emptyList()
        if (place.searching) {
            val hits = SettingsSearch.hits(sections, place.query)
            nav.counts(SettingsSearch.counts(hits, sections.size))
            // A hit inside a row opens it, so the search shows where the match is. A row the player closes stays
            // closed until the query changes.
            if (place.query != foundFor) rows.found = hits.flatMapTo(HashSet()) { it.open }
            foundFor = place.query
            if (hits.isEmpty()) {
                y += EMPTY_TOP
                centered(y, "No setting matches \"${place.query.trim()}\".", Kit.Ink.NORMAL)
                y += 8 + GAP
                val label = "Clear Search"
                val button = KitButton(0, BAR + y, label, { search.value = "" })
                button.x = contentX + (contentWidth - button.width) / 2
                clear = addRenderableWidget(button)
                y += Kit.BUTTON_HEIGHT
            }
            y = list(items, hits, y, switches = true)
        } else if (place.section == FAVORITES) {
            foundFor = null
            rows.found = HashSet()
            nav.counts(null)
            val hits = Favorites.hits(sections, view.rows(every, config.favorites) { it.id }.map { it.id })
            if (hits.isEmpty()) {
                y += EMPTY_TOP
                val key = config.general.favoriteKey.takeIf { it >= 0 }?.let(Keys::name)
                for ((line, ink) in listOf(
                    "No favorites yet." to Kit.Ink.NORMAL,
                    (if (key == null) "Click a setting's star to keep it here." else "Click a setting's star, or press $key over it, to keep it here.") to Kit.Ink.MUTED,
                )) {
                    centered(y, line, ink)
                    y += 8 + GAP
                }
            }
            y = list(items, hits, y, switches = false)
        } else if (place.section < 0) {
            foundFor = null
            rows.found = HashSet()
            nav.counts(null)
            y += EMPTY_TOP
            centered(y, "Pick a section or search.", Kit.Ink.MUTED)
            y += 8
        } else {
            foundFor = null
            rows.found = HashSet()
            nav.counts(null)
            // No title: the sidebar and the tab name the section.
            for (group in sections[place.section].groups) {
                if (group.name != null) {
                    y += above
                    above = ABOVE
                    tops += y
                    y = headed(items, y, null, group.name, group.switch, group.rows)
                } else {
                    y += BELOW
                    above = ABOVE
                    items += group(y, group.rows)
                    y += items.last().height
                }
            }
        }
        rows.end()
        pane.content = y + BOTTOM
        this.items = items
        this.tops = tops.toIntArray()
        spiedAt = -1
    }

    /**
     * Lays [hits] out from [y] under "Section › Subsection" headings, the group's switch beside each when [switches],
     * and returns the content y under them.
     */
    private fun list(items: MutableList<Item>, hits: List<SettingHit>, top: Int, switches: Boolean): Int {
        var y = top
        var above = FIRST_ABOVE
        for (hit in hits) {
            y += above
            above = ABOVE
            val section = sections[hit.section].name
            val group = hit.group.name
            y = headed(items, y, if (group == null) null else section, group ?: section, if (switches) hit.group.switch else null, hit.rows)
        }
        return y
    }

    /** Stars or unstars the row [id]. One unstarred in Favorites stays there until the player leaves. */
    private fun star(id: String) {
        config.favorites = view.toggle(config.favorites, id, here = tabs.current.place.let { it.section == FAVORITES && !it.searching })
    }

    /** Whether the Favorite Key may act: not while the search has the keys, nor with Ctrl or Alt held, so typing and Ctrl+F work. */
    private fun favoriteFree(event: InputWithModifiers) = focused !== search && !Keys.control(event) && !Keys.alt(event)

    /** Adds a heading at [y] with [settings] in a well under it, and returns the y under them. */
    private fun headed(items: MutableList<Item>, y: Int, prefix: String?, name: String, switch: Switch?, settings: List<Setting>): Int {
        items += heading(y, prefix, name, switch)
        val well = group(y + HEADING + BELOW, settings)
        items += well
        return y + HEADING + BELOW + well.height
    }

    private fun heading(y: Int, prefix: String?, name: String, switch: Switch?): Heading {
        val separatorX = contentX + if (prefix == null) 0 else font.width(prefix)
        val nameX = if (prefix == null) contentX else separatorX + font.width(SEPARATOR)
        val end = nameX + font.width(name) + GAP
        if (switch != null) rows.heading(switch, end, y, contentX)
        val lineX = if (switch == null) end else end + Kit.SWITCH_WIDTH + SettingRows.HEADING_STAR + GAP
        // A heading's words find its rows, so a search paints them there too.
        return Heading(y, prefix, separatorX, name, Kit.Marked.of(name, rows.words), nameX, switch, lineX)
    }

    /** A well holding [settings], one row each with the control of its kind. */
    private fun group(y: Int, settings: List<Setting>): Group {
        val bottom = rows.add(settings, contentX + 1, y + 1, contentWidth - 2)
        return Group(y, bottom - y + 1)
    }

    /** Scrolls the pane so that the content from [top] to [bottom] shows, as the keyboard moves through the rows. */
    private fun scrollTo(top: Int, bottom: Int) {
        val view = pane.view
        if (top < pane.offset) pane.to(top) else if (bottom > pane.offset + view) pane.to(bottom - view)
        sync()
    }

    /** Where the row [id], or its ordered list's [entry], is on the screen, if the pane shows it, so a link or a test can find it. */
    fun spot(id: String, entry: Int = -1): SettingRows.Spot? {
        sync()
        return rows.spot(id, entry)
    }

    /** How far down the screen the middle of the sidebar's line for [section] is, if the sidebar shows it, so a test can find it. */
    fun line(section: String): Int? = nav.middle(NavRow(sections.indexOfFirst { it.name == section }))

    /** The ids of the rows the pane lists now, in order, so a test can read a page. */
    fun shown() = rows.ids()

    /** Whether the row [id] paints its changed marker. */
    fun marked(id: String) = rows.marked(id)

    /** The editor of the row [id], told where the pane is, so a test can find its controls. */
    fun editor(id: String): Editor? {
        sync()
        return rows.editor(id)
    }

    /** Scrolls the pane to the row [id]. */
    fun reveal(id: String) {
        rows.reveal(id)
    }

    /** Tells the rows where the pane is, before they draw or take input. */
    private fun sync() = rows.view(BAR - pane.offset, BAR, height, width, height)

    /** Marks the subsection whose heading is at the pane's top, once the pane has stopped moving. */
    private fun spy() {
        if (tree.searching || pane.gliding || pane.offset == spiedAt) return
        spiedAt = pane.offset
        tree.current = NavTree.current(tops, pane.offset, pane.max)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        eatChar = false
        // An open menu, a number's field or a key button being set takes every key, Escape included.
        if (rows.capturing) return rows.keyPressed(event)
        val favorite = config.general.favoriteKey
        if (key == favorite && !Keys.isMouse(favorite) && favoriteFree(event) && rows.starAt(mouseX, mouseY, keyboard = true)) return true.also { eatChar = true }
        // The tabs' keys are a browser's, and work in the search field too.
        if (Keys.control(event) && controlKey(event)) return true
        if (Keys.alt(event) && (event.isLeft || event.isRight)) return true.also { history(event.isLeft) }
        if (event.isEscape && bar.cancelDrag()) return true
        if (focused === search) {
            // Escape leaves the field before it closes the screen, and Enter keeps what was typed.
            if (event.isEscape || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return true.also { setFocused(null) }
            if (event.isUp || event.isDown) return nav.key(event)
            return super.keyPressed(event)
        }
        // Tab moves the keys between the sidebar and the rows.
        if (event.isCycleFocus && (focused == null || focused === nav)) {
            rows.keys = !rows.keys
            if (rows.keys) rows.showFocus()
            return true
        }
        if (rows.keys && rows.keyPressed(event)) return true
        if ((focused == null || focused === nav) && !rows.keys && nav.key(event)) return true
        when (key) {
            GLFW.GLFW_KEY_PAGE_UP -> pane.by(-(pane.view - ROW))
            GLFW.GLFW_KEY_PAGE_DOWN -> pane.by(pane.view - ROW)
            GLFW.GLFW_KEY_HOME -> pane.to(0)
            GLFW.GLFW_KEY_END -> pane.to(pane.max)
            else -> return super.keyPressed(event)
        }
        return true
    }

    override fun keyReleased(event: KeyEvent) = rows.keyReleased(event) || super.keyReleased(event)

    /** Ctrl+F goes to the search field, and the rest are a browser's keys for its tabs. Returns whether [event] was one. */
    private fun controlKey(event: KeyEvent): Boolean {
        val key = event.key()
        when {
            key == GLFW.GLFW_KEY_F -> {
                rows.keys = false
                setFocused(search)
                search.moveCursorToEnd(false)
                search.setHighlightPos(0)
            }
            key == GLFW.GLFW_KEY_T && Keys.shift(event) -> retab { tabs.reopen() }
            key == GLFW.GLFW_KEY_T -> newTab()
            key == GLFW.GLFW_KEY_W -> close(tabs.selected)
            key == GLFW.GLFW_KEY_TAB -> retab { tabs.cycle(if (Keys.shift(event)) -1 else 1) }
            key in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9 -> retab { tabs.number(key - GLFW.GLFW_KEY_0) }
            else -> return false
        }
        return true
    }

    /** Typing anywhere searches, unless it goes into a number's field or an open menu. */
    override fun charTyped(event: CharacterEvent): Boolean {
        if (eatChar) return true.also { eatChar = false }
        if ((rows.capturing || focused !== search) && rows.charTyped(event)) return true
        if (focused !== search && event.isAllowedChatCharacter && !Character.isWhitespace(event.codepoint())) {
            rows.keys = false
            setFocused(search)
            search.moveCursorToEnd(false)
        }
        return super.charTyped(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        sync()
        // The mouse's back and forward buttons walk the selected tab's history, wherever the mouse is, unless a key is being recorded.
        if (!rows.recordingKeys && (event.button() == MOUSE_BACK || event.button() == MOUSE_FORWARD)) return true.also { history(event.button() == MOUSE_BACK) }
        // A mouse button bound as the Favorite Key stars the row under the mouse.
        val favorite = config.general.favoriteKey
        val inPane = event.x >= side && event.y >= BAR
        if (!rows.capturing && Keys.isMouse(favorite) && event.button() == favorite && favoriteFree(event) && inPane && rows.starAt(event.x, event.y, keyboard = false)) return true
        if ((rows.capturing || event.x >= side && event.y >= BAR) && rows.mouseClicked(event, doubleClick)) {
            setFocused(null)
            return true
        }
        if (event.button() == 0 && pane.overflows && event.y >= BAR && event.x >= barX && event.x < barX + Kit.SCROLLBAR_WIDTH) {
            val thumbY = BAR + 2 + 1 + pane.thumbY(track - 2)
            val thumb = pane.thumb(track - 2)
            thumbGrab = if (event.y >= thumbY && event.y < thumbY + thumb) event.y - thumbY else thumb / 2.0
            dragThumb(event.y)
            return true
        }
        if (event.x < side) rows.keys = false
        val handled = super.mouseClicked(event, doubleClick)
        if (focusSearch) {
            focusSearch = false
            setFocused(search)
        }
        if (handled) return true
        if (event.x >= side) setFocused(null)
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (rows.mouseDragged(event, dx, dy)) return true
        if (thumbGrab < 0) return super.mouseDragged(event, dx, dy)
        dragThumb(event.y)
        return true
    }

    private fun dragThumb(y: Double) {
        pane.drag(y - BAR - 3 - thumbGrab, track - 2)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        thumbGrab = -1.0
        if (rows.mouseReleased(event)) return true
        val handled = super.mouseReleased(event)
        // A tab takes focus for its drag only, so the keys go back to the sidebar after it.
        if (focused === bar) setFocused(null)
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX < side || mouseY < BAR) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        sync()
        // Some systems turn the wheel with Shift held into a sideways scroll.
        val amount = if (scrollY != 0.0) scrollY else scrollX
        if (rows.mouseScrolled(mouseX, mouseY, amount, minecraft.hasShiftDown())) return true
        if (scrollY != 0.0) pane.by(if (scrollY > 0) -ROW else ROW)
        return true
    }

    /** What the narrator would say now, collected the way vanilla collects it, so a test can check it. */
    fun narration(): String = ScreenNarrationCollector().apply { update(::updateNarrationState) }.collectNarrationText(true)

    // The keys work the sidebar until the player picks a control, so nothing takes focus on open.
    override fun setInitialFocus() = setFocused(null)

    override fun onClose() = minecraft.gui.setScreen(parent)

    // The tabs outlive the screen, so reopening it brings back where each pane was. A change is written as the
    // screen goes, which is also when it opens an editor.
    override fun removed() {
        keep()
        view.left()
        rows.dispose()
        save()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // The Favorite Key acts on the row under the mouse, and a key event carries no mouse.
        this.mouseX = mouseX.toDouble()
        this.mouseY = mouseY.toDouble()
        val now = Util.getMillis()
        pane.step(now - lastFrame)
        lastFrame = now
        spy()
        sync()
        Kit.backdrop(graphics, 0, 0, width, height)
        Kit.side(graphics, 0, 0, side, height)
        val offset = pane.offset
        graphics.enableScissor(side, BAR, width, height)
        for (item in items) {
            val top = BAR + item.y - offset
            if (top >= height || top + item.height <= BAR) continue
            when (item) {
                is Heading -> {
                    val y = top + 2
                    // A heading whose switch is off reads as off, as its rows dim.
                    val ink = if (item.switch?.value == false) Kit.Ink.MUTED else Kit.Ink.NORMAL
                    if (item.prefix != null) {
                        Kit.label(graphics, item.prefix, contentX, y, ink)
                        Kit.label(graphics, SEPARATOR, item.separatorX, y, Kit.Ink.MUTED)
                    }
                    if (item.marked != null) Kit.label(graphics, item.marked, item.nameX, y, ink) else Kit.label(graphics, item.name, item.nameX, y, ink)
                    Kit.rule(graphics, item.lineX, top + HEADING / 2, contentX + contentWidth - item.lineX)
                }
                is Group -> Kit.well(graphics, contentX, top, contentWidth, item.height)
                is Empty -> Kit.label(graphics, item.line, item.x, top, item.ink)
            }
        }
        val inPane = mouseX >= side && mouseY >= BAR && thumbGrab < 0
        val tip = rows.draw(graphics, mouseX, mouseY, partialTick).takeIf { inPane }
        graphics.disableScissor()
        if (pane.overflows) Kit.scrollbar(graphics, barX, BAR + 2, track, pane.thumbY(track - 2), pane.thumb(track - 2))
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        rows.drawMenu(graphics, mouseX, mouseY, partialTick)
        // A description waits for a short hover, as the game's own tooltips do, so moving across rows shows none.
        if (tip !== hovered) {
            hovered = tip
            hoveredSince = now
        }
        if (tip != null && now - hoveredSince >= TIP_DELAY) graphics.setTooltipForNextFrame(font, tip.lines, mouseX, mouseY)
    }

    companion object {
        private const val SEPARATOR = " › "

        /** Favorites' place among the sections. */
        const val FAVORITES = 0

        /** The sections the screen lists: Favorites, which holds no groups of its own, and then the tree bound to [config]. */
        fun sections(config: DsModConfig) = listOf(SettingSection("Favorites", Role.ACTION, emptyList())) + SettingsTree(config).sections

        private var tabs: SettingsTabs? = null

        /**
         * The tabs, kept until the game closes, as a browser keeps its session. The first open has one, on Favorites, or
         * on the first section of the tree while nothing is starred, so it does not open on an empty page.
         */
        fun session(): SettingsTabs =
            tabs ?: SettingsTabs(Place(if (DsModClient.settings.favorites.isEmpty()) FAVORITES + 1 else FAVORITES)).also { tabs = it }

        /** The rows the player opened, kept with the tabs. */
        private val opened = HashSet<String>()

        /** Favorites as the player looks at it, kept with the tabs. */
        private val view = FavoritesView()
    }
}
