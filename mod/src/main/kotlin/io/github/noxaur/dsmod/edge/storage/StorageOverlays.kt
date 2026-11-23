package io.github.noxaur.dsmod.edge.storage

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.inventory.StorageMode
import io.github.noxaur.dsmod.core.feature.inventory.StorageOverview
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.overview.StoragePages
import io.github.noxaur.dsmod.core.ui.ItemSearch
import io.github.noxaur.dsmod.core.ui.Scroll
import io.github.noxaur.dsmod.core.ui.StorageLayout
import io.github.noxaur.dsmod.edge.MenuSkin
import io.github.noxaur.dsmod.edge.ScreenHooks
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.TabBar
import io.github.noxaur.dsmod.edge.TakenButtons
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.input.SlotPlaces
import io.github.noxaur.dsmod.edge.slotUnderMouse
import io.github.noxaur.dsmod.edge.ui.Kit
import java.util.BitSet
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** @keep Overview mode: the root Storage menu, a Storage page and the Ender Chest are drawn as every page at once, with
 * the player's inventory under them, as Firmament does. The server's menu stays the screen, and its real slots move into
 * the open page's place, so that page works as the menu does. The other pages are drawn from what the mod remembers, and
 * a click on one sends its command, unless an item is on the cursor. Each new menu from the server is laid out the same,
 * so the overview looks like one screen. */
object StorageOverlays {
    private lateinit var game: Game
    private lateinit var storage: StorageOverview

    private val scroll = Scroll()
    private var overlay: Overlay? = null
    private var switching = 0L
    private var grab = -1.0

    // @keep The slots this screen hid, kept over a new layout of the same screen, since the slots stay hidden until shown.
    private val covered = BitSet()

    /** @keep `open` is the open page's place in `shelves`, and -1 in the root menu. `items` is how many slots the menu
     * itself has, before the player's inventory. */
    private class Overlay(val screen: ContainerScreen, val title: String, val shelves: List<Shelf>, val open: Int, val layout: StorageLayout) {
        val items = screen.menu.rowCount * ContainerState.ROW_SLOTS
        val first = shelves.getOrNull(open)?.slots?.first ?: 0
        val dimmed = Array(shelves.size) { BitSet() }
        var searched: List<String>? = null
        var version = -1
        var found = false
    }

    fun bind(game: Game, storage: StorageOverview) {
        this.game = game
        this.storage = storage
        TabBar.alsoFound = { overlay?.found == true }
        TabBar.column = { screen -> overlay?.takeIf { it.screen === screen }?.layout?.stemX }
    }

    private fun applies(title: String) =
        storage.feature.runs && storage.mode.value == StorageMode.OVERVIEW && (title == StoragePages.ROOT || Shelf.of(title) != null)

    /** @keep Registered before the menu's other layers, so the tabs, the search bar and Go Back are placed on the overview
     * and not on the menu it replaced. */
    @InputApproved(by = "noxaur", reaches = ["Refusals.allowKeyPress", "Refusals.allowMouseDrag", "Refusals.allowMouseScroll"])
    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, opened ->
            val title = screen.title.string
            if (!applies(title)) {
                if (overlay?.screen === screen) overlay = null
                return@onEveryLayout
            }
            val shelves = storage.pages.shown(title)
            val open = Shelf.of(title)?.id
            val layout = StorageLayout(screen.width, screen.height, shelves.map { it.rows })
            val o = Overlay(screen, title, shelves, shelves.indexOfFirst { it.id == open }, layout)
            overlay = o
            scroll.content = layout.content
            scroll.view = layout.view
            if (opened) {
                covered.clear()
                switching = 0
                grab = -1.0
                if (o.open >= 0 && !layout.shows(o.open, scroll.offset)) scroll.to(layout.titleTop(o.open, 0) - layout.viewY)
            }
            screen.leftPos = layout.left
            screen.topPos = layout.top
            screen.imageWidth = layout.width
            screen.imageHeight = layout.height
            place(o)

            ScreenEvents.afterTick(screen).register { tick(o) }
            ScreenEvents.remove(screen).register { if (overlay === o) overlay = null }
            TakenButtons().register(screen, released = { grab = -1.0 }) { event -> click(o, event) }
            Refusals.allowMouseDrag(screen).register { _, event, _, _ ->
                if (grab < 0) return@register true
                scroll.drag(event.y() - layout.viewY - 1 - grab, track(layout))
                place(o)
                false
            }
            Refusals.allowMouseScroll(screen).register { _, x, y, _, amount ->
                if (!layout.overflows || !layout.inView(x, y) && !overBar(layout, x, y)) return@register true
                scroll.by(-(amount * StorageLayout.CELL).roundToInt())
                place(o)
                false
            }
            Refusals.allowKeyPress(screen).register { _, event ->
                if (screen.focused is EditBox) return@register true
                when (event.key()) {
                    GLFW.GLFW_KEY_PAGE_UP -> scroll.by(-layout.view)
                    GLFW.GLFW_KEY_PAGE_DOWN -> scroll.by(layout.view)
                    GLFW.GLFW_KEY_HOME -> scroll.to(0)
                    GLFW.GLFW_KEY_END -> scroll.to(scroll.max)
                    else -> return@register true
                }
                place(o)
                false
            }
        }
    }

    /** @keep The open page's slots sit in its cells while the whole cell is in view, and every other slot of the menu is
     * moved off the screen and hidden, so nothing out of view takes a click. */
    @InputApproved(by = "noxaur", reaches = ["SlotPlaces.move"])
    private fun place(o: Overlay) {
        val layout = o.layout
        val offset = scroll.offset
        val cells = if (o.open >= 0) layout.rows(o.open) * StorageLayout.ROW else 0
        for (slot in o.screen.menu.slots) {
            val i = slot.index
            if (i >= o.items) {
                val inventory = i - o.items
                move(o, slot, layout.inventoryCellX(inventory), layout.inventoryCellY(inventory))
                continue
            }
            val cell = i - o.first
            if (o.open >= 0 && cell in 0 until cells && layout.cellShows(o.open, cell, offset)) {
                move(o, slot, layout.cellX(o.open, cell), layout.cellY(o.open, cell, offset))
            } else {
                SlotPlaces.move(slot, OFF, OFF)
                if (!covered[i]) Slots.cover(i, true)
                covered.set(i)
            }
        }
    }

    @InputApproved(by = "noxaur", reaches = ["SlotPlaces.move"])
    private fun move(o: Overlay, slot: Slot, x: Int, y: Int) {
        SlotPlaces.move(slot, x + 1 - o.layout.left, y + 1 - o.layout.top)
        if (covered[slot.index]) Slots.cover(slot.index, false)
        covered.clear(slot.index)
    }

    /** @keep A page the root menu or a page's title brings in after the layout lays the menu out again. The search dims
     * the remembered items only when the words or the pages change. */
    private fun tick(o: Overlay) {
        val pages = storage.pages
        if (pages.version != o.version && pages.shown(o.title).size != o.shelves.size) {
            o.screen.resize(o.screen.width, o.screen.height)
            return
        }
        val words = TabBar.searchWords
        if (pages.version == o.version && words === o.searched) return
        if (pages.version != o.version) PageStacks.keep(storage.feature.player.containers.keys)
        o.version = pages.version
        o.searched = words
        o.found = false
        for ((p, shelf) in o.shelves.withIndex()) {
            val dimmed = o.dimmed[p]
            dimmed.clear()
            val page = pages.page(shelf.id)?.takeIf { p != o.open } ?: continue
            if (words.isEmpty()) continue
            for ((c, item) in PageStacks.of(shelf.id, page).owned.withIndex()) {
                if (item == null) continue
                if (ItemSearch.matches(words, item.name, item.lore)) o.found = true else dimmed.set(c)
            }
        }
    }

    @InputApproved(by = "noxaur", reaches = ["PlayerCommands.send"])
    private fun click(o: Overlay, event: MouseButtonEvent): Boolean {
        val layout = o.layout
        val x = event.x()
        val y = event.y()
        if (layout.overflows && overBar(layout, x, y)) {
            val track = track(layout)
            val thumb = scroll.thumb(track)
            val thumbTop = layout.viewY + 1 + scroll.thumbY(track)
            grab = if (y >= thumbTop && y < thumbTop + thumb) y - thumbTop else thumb / 2.0
            scroll.drag(y - layout.viewY - 1 - grab, track)
            place(o)
            return true
        }
        val page = layout.pageAt(x, y, scroll.offset)
        if (page < 0 || page == o.open) return false
        // @keep NEU keeps the open page while an item is on the cursor, so the item is never carried into a menu the player did not mean.
        val now = System.currentTimeMillis()
        if (o.screen.menu.carried.isEmpty && now >= switching) {
            Shelf.command(o.shelves[page].id)?.let {
                switching = now + SWITCH_MILLIS
                PlayerCommands.send(it)
            }
        }
        return true
    }

    private fun overBar(layout: StorageLayout, x: Double, y: Double) =
        x >= layout.scrollbarX && x < layout.scrollbarX + StorageLayout.SCROLLBAR && y >= layout.viewY && y < layout.viewY + layout.view

    private fun track(layout: StorageLayout) = layout.view - 2

    fun covers(screen: AbstractContainerScreen<*>) = overlay?.screen === screen

    /** @keep The page and cell under the mouse as `page * CELLS + cell`, or -1, so a frame allocates nothing */
    private fun hovered(o: Overlay, x: Int, y: Int): Int {
        val page = o.layout.pageAt(x.toDouble(), y.toDouble(), scroll.offset)
        if (page < 0) return -1
        val cell = o.layout.cellAt(page, x.toDouble(), y.toDouble(), scroll.offset)
        return if (cell < 0) -1 else page * CELLS + cell
    }

    fun background(screen: ContainerScreen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        val o = overlay?.takeIf { it.screen === screen } ?: return false
        game.metrics.time("storage.draw", Metrics.FRAME) { draw(o, graphics, mouseX, mouseY) }
        return true
    }

    private fun draw(o: Overlay, g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val layout = o.layout
        val offset = scroll.offset
        val font = Minecraft.getInstance().font
        val alpha = MenuSkin.glass(o.screen)
        val glass = alpha >= 0
        Kit.stemPanel(g, layout.left, layout.top, layout.width, layout.pagesHeight, layout.stemX, StorageLayout.STEM_WIDTH, layout.height - layout.pagesHeight, alpha)
        val hovered = if (o.screen.slotUnderMouse == null) hovered(o, mouseX, mouseY) else -1
        val slots = o.screen.menu.slots
        g.enableScissor(layout.viewX, layout.viewY, layout.viewX + layout.pagesWidth, layout.viewY + layout.view)
        for (p in 0 until layout.pages) {
            if (!layout.touches(p, offset)) continue
            val shelf = o.shelves[p]
            val x = layout.pageLeft(p)
            val gridY = layout.gridTop(p, offset)
            val cells = layout.rows(p) * StorageLayout.ROW
            Kit.label(g, shelf.title, x + 1, layout.titleTop(p, offset) + 1, Kit.Ink.NORMAL, glass)
            val page = storage.pages.page(shelf.id)
            val stacks = if (p == o.open) null else page?.let { PageStacks.of(shelf.id, it) }
            Kit.slotGrid(g, x, gridY, StorageLayout.ROW, layout.rows(p), glass)
            for (c in 0 until cells) {
                val cx = layout.cellX(p, c)
                val cy = layout.cellY(p, c, offset)
                if (p == o.open) {
                    // @keep A cell cut by the view's edge has no slot under it, so its item is drawn here.
                    if (layout.cellShows(p, c, offset)) continue
                    val stack = slots.getOrNull(o.first + c)?.item ?: continue
                    g.item(stack, cx + 1, cy + 1)
                    g.itemDecorations(font, stack, cx + 1, cy + 1)
                    continue
                }
                if (stacks == null) continue
                PageStacks.draw(g, font, stacks, c, cx + 1, cy + 1)
                if (o.dimmed[p][c]) Kit.slotDim(g, cx + 1, cy + 1)
                if (hovered == p * CELLS + c) Kit.slotHover(g, cx + 1, cy + 1)
            }
            if (p != o.open && page == null) {
                // @keep One wash over the grid, not one per slot. The wash is dark in both themes, so the label takes the light ink.
                Kit.dim(g, x, gridY, StorageLayout.GRID, layout.rows(p) * StorageLayout.CELL)
                Kit.label(g, LOAD, x + (StorageLayout.GRID - font.width(LOAD)) / 2, gridY + (cells / StorageLayout.ROW * StorageLayout.CELL - Kit.LABEL_HEIGHT) / 2, Kit.Ink.NORMAL, true)
            }
        }
        g.disableScissor()
        if (o.open >= 0 && layout.touches(o.open, offset)) {
            val x = layout.pageLeft(o.open)
            val top = layout.titleTop(o.open, offset)
            val bottom = layout.gridTop(o.open, offset) + layout.rows(o.open) * StorageLayout.CELL
            g.enableScissor(layout.viewX - RING, layout.viewY - RING, layout.viewX + layout.pagesWidth + RING, layout.viewY + layout.view + RING)
            Kit.ring(g, x - RING, top - RING, StorageLayout.GRID + 2 * RING, bottom - top + 2 * RING, Kit.theme.accent)
            g.disableScissor()
        }
        if (layout.overflows) {
            val track = track(layout)
            Kit.scrollbar(g, layout.scrollbarX, layout.viewY, layout.view, scroll.thumbY(track), scroll.thumb(track))
        }
        Kit.label(g, INVENTORY, layout.inventoryX + 1, layout.inventoryTitleY + 1, Kit.Ink.NORMAL, glass)
        Kit.slotGrid(g, layout.inventoryX, layout.inventoryCellY(0), StorageLayout.ROW, StorageLayout.HOTBAR / StorageLayout.ROW, glass)
        Kit.slotGrid(g, layout.inventoryX, layout.inventoryCellY(StorageLayout.HOTBAR), StorageLayout.ROW, 1, glass)
    }

    fun tooltip(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        val o = overlay?.takeIf { it.screen === screen } ?: return false
        if (screen.slotUnderMouse != null || !screen.menu.carried.isEmpty) return false
        val at = hovered(o, mouseX, mouseY)
        if (at < 0) return false
        val p = at / CELLS
        val c = at % CELLS
        val stack = if (p == o.open) {
            o.screen.menu.slots.getOrNull(o.first + c)?.item?.takeUnless { it.isEmpty }
        } else {
            o.shelves[p].id.let { id -> storage.pages.page(id)?.let { PageStacks.of(id, it).stacks.getOrNull(c) } }
        } ?: return false
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY)
        return true
    }

    private const val OFF = -10_000
    private const val CELLS = 64
    private const val RING = 2
    private const val SWITCH_MILLIS = 1_000L
    private const val LOAD = "Click to load items"
    private const val INVENTORY = "Inventory"
}
