package io.github.noxaur.dsmod.core.ui

import kotlinx.serialization.Serializable

/** The edge of a menu that a tab hangs from. */
enum class TabEdge { TOP, BOTTOM }

/**
 * A tab the player put on the menus, at place [slot] of [edge] counted from the left. It sends [command] and shows
 * [item], or it is the search when [search] is true. [tooltip] names it, and a blank one shows the command instead.
 */
@Serializable
data class MenuTab(
    val edge: TabEdge = TabEdge.BOTTOM,
    val slot: Int = 0,
    val command: String = "",
    val item: String = "minecraft:chest",
    val tooltip: String = "",
    val search: Boolean = false,
)

/**
 * The rules for menu tabs, in GUI pixels from a menu's left edge: where each place on an edge sits, how the editor
 * moves, adds and removes tabs, and which way the search bar opens. Each edge has [PER_EDGE] places, and a tab
 * dropped on another trades places with it, as items in slots do.
 */
object TabLayout {
    const val PER_EDGE = 7
    const val TAB_WIDTH = 22
    const val SEARCH_WIDTH = 120
    private const val INSET = 4
    private const val STEP = 24

    val DEFAULT = listOf(
        MenuTab(TabEdge.BOTTOM, 0, "storage", "minecraft:chest", "Storage"),
        MenuTab(TabEdge.BOTTOM, 1, "ec", "minecraft:ender_chest", "Ender Chest"),
        MenuTab(TabEdge.BOTTOM, 2, "craft", "minecraft:crafting_table", "Crafting Table"),
        MenuTab(TabEdge.BOTTOM, 3, "forge", "minecraft:anvil", "Forge"),
        MenuTab(TabEdge.TOP, PER_EDGE - 1, item = "", tooltip = "Search", search = true),
    )

    /** Where place [slot] starts, from the menu's left edge. */
    fun x(slot: Int) = INSET + slot * STEP

    /** The place under [x], from the menu's left edge, or -1 when it is past either end. */
    fun slotAt(x: Int): Int {
        val slot = Math.floorDiv(x - INSET + (STEP - TAB_WIDTH) / 2, STEP)
        return if (slot in 0 until PER_EDGE) slot else -1
    }

    /** The index of the tab at [slot] of [edge], or -1. */
    fun at(tabs: List<MenuTab>, edge: TabEdge, slot: Int) = tabs.indexOfFirst { it.edge == edge && it.slot == slot }

    /** [tabs] with tab [index] moved to [slot] of [edge]. A tab already there takes [index]'s old place. */
    fun move(tabs: List<MenuTab>, index: Int, edge: TabEdge, slot: Int): List<MenuTab> {
        val moving = tabs[index]
        val other = at(tabs, edge, slot)
        return tabs.mapIndexed { i, tab ->
            when (i) {
                index -> tab.copy(edge = edge, slot = slot)
                other -> tab.copy(edge = moving.edge, slot = moving.slot)
                else -> tab
            }
        }
    }

    /** The first free place, along the bottom and then the top, or null when every place holds a tab. */
    fun free(tabs: List<MenuTab>): Pair<TabEdge, Int>? {
        for (edge in listOf(TabEdge.BOTTOM, TabEdge.TOP)) {
            for (slot in 0 until PER_EDGE) if (at(tabs, edge, slot) < 0) return edge to slot
        }
        return null
    }

    /** [tabs] with [tab] put in the first free place, or null when there is none. */
    fun add(tabs: List<MenuTab>, tab: MenuTab): List<MenuTab>? {
        val (edge, slot) = free(tabs) ?: return null
        return tabs + tab.copy(edge = edge, slot = slot)
    }

    /**
     * Where the open search bar of the tab at [slot] sits on a menu [menuWidth] wide, as its left and its width. It
     * opens from the tab toward the middle of the menu, over the tabs beside it, and stays within the menu's width.
     */
    fun searchBar(slot: Int, menuWidth: Int): Pair<Int, Int> {
        val width = minOf(SEARCH_WIDTH, menuWidth)
        val tab = x(slot)
        val left = if (tab + TAB_WIDTH / 2 < menuWidth / 2) minOf(tab, menuWidth - width) else maxOf(0, tab + TAB_WIDTH - width)
        return left to width
    }

    /** The index of the tab that sends [opener], the command that opened the menu shown now, or -1. */
    fun selected(tabs: List<MenuTab>, opener: String?) = if (opener == null) -1 else tabs.indexOfFirst { !it.search && it.command == opener }

    /** An item id as the player typed it, such as `Chest` or `minecraft:chest`, in the form the game names items by. */
    fun itemId(typed: String): String {
        val id = typed.trim().lowercase().replace(' ', '_')
        return if (id.isEmpty() || ':' in id) id else "minecraft:$id"
    }

    /** A command as the player typed it, without its slash. */
    fun command(typed: String) = typed.trim().removePrefix("/").trim()
}
