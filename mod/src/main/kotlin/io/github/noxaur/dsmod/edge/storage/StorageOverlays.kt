package io.github.noxaur.dsmod.edge.storage

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.menus.helpers.storageoverview.StorageMode
import io.github.noxaur.dsmod.core.feature.menus.helpers.storageoverview.StorageOverview
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.overview.StoragePages
import io.github.noxaur.dsmod.core.ui.ItemSearch
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Scroll
import io.github.noxaur.dsmod.core.ui.StorageLayout
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
import java.lang.ref.WeakReference
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

object StorageOverlays {
    private lateinit var game: Game
    private lateinit var storage: StorageOverview

    private val scroll = Scroll()
    private var overlay: Overlay? = null
    private var switching = 0L
    private var grab = -1.0

    private val covered = BitSet()

    /** @keep The screen is held weakly, so a closed one does not keep its world after the player leaves. */
    private class Original(screen: ContainerScreen, val width: Int, val height: Int, val x: IntArray, val y: IntArray) {
        val screen = WeakReference(screen)
    }

    private var original: Original? = null

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

    @InputApproved(by = "noxaur", reaches = ["Refusals.allowKeyPress", "Refusals.allowMouseDrag", "Refusals.allowMouseScroll"])
    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, opened ->
            val title = screen.title.string
            if (!applies(title)) {
                if (overlay?.screen === screen) overlay = null
                original?.takeIf { it.screen.get() === screen }?.let { restore(screen, it) }
                return@onEveryLayout
            }
            if (original?.screen?.get() !== screen) {
                val slots = screen.menu.slots
                original = Original(screen, screen.imageWidth, screen.imageHeight, IntArray(slots.size) { slots[it].x }, IntArray(slots.size) { slots[it].y })
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
    private fun restore(screen: ContainerScreen, was: Original) {
        screen.imageWidth = was.width
        screen.imageHeight = was.height
        screen.leftPos = (screen.width - was.width) / 2
        screen.topPos = (screen.height - was.height) / 2
        for (slot in screen.menu.slots) {
            SlotPlaces.move(slot, was.x[slot.index], was.y[slot.index])
            if (covered[slot.index]) Slots.cover(slot.index, false)
        }
        covered.clear()
        original = null
    }

    @InputApproved(by = "noxaur", reaches = ["SlotPlaces.move"])
    private fun move(o: Overlay, slot: Slot, x: Int, y: Int) {
        SlotPlaces.move(slot, x + 1 - o.layout.left, y + 1 - o.layout.top)
        if (covered[slot.index]) Slots.cover(slot.index, false)
        covered.clear(slot.index)
    }

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
        Kit.stemPanel(g, layout.left, layout.top, layout.width, layout.pagesHeight, layout.stemX, StorageLayout.STEM_WIDTH, layout.height - layout.pagesHeight)
        val hovered = if (o.screen.slotUnderMouse == null) hovered(o, mouseX, mouseY) else -1
        val slots = o.screen.menu.slots
        g.enableScissor(layout.viewX, layout.viewY, layout.viewX + layout.pagesWidth, layout.viewY + layout.view)
        for (p in 0 until layout.pages) {
            if (!layout.touches(p, offset)) continue
            val shelf = o.shelves[p]
            val x = layout.pageLeft(p)
            val gridY = layout.gridTop(p, offset)
            val cells = layout.rows(p) * StorageLayout.ROW
            Kit.label(g, shelf.title, x + 1, layout.titleTop(p, offset) + 1, Kit.Ink.NORMAL)
            val page = storage.pages.page(shelf.id)
            val stacks = if (p == o.open) null else page?.let { PageStacks.of(shelf.id, it) }
            Kit.slotGrid(g, x, gridY, StorageLayout.ROW, layout.rows(p))
            for (c in 0 until cells) {
                val cx = layout.cellX(p, c)
                val cy = layout.cellY(p, c, offset)
                if (p == o.open) {
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
                Kit.dim(g, x, gridY, StorageLayout.GRID, layout.rows(p) * StorageLayout.CELL)
                g.text(font, LOAD, x + (StorageLayout.GRID - font.width(LOAD)) / 2, gridY + (cells / StorageLayout.ROW * StorageLayout.CELL - Kit.LABEL_HEIGHT) / 2, Role.BODY.argb(), true)
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
        Kit.label(g, INVENTORY, layout.inventoryX + 1, layout.inventoryTitleY + 1, Kit.Ink.NORMAL)
        Kit.slotGrid(g, layout.inventoryX, layout.inventoryCellY(0), StorageLayout.ROW, StorageLayout.HOTBAR / StorageLayout.ROW)
        Kit.slotGrid(g, layout.inventoryX, layout.inventoryCellY(StorageLayout.HOTBAR), StorageLayout.ROW, 1)
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
