package io.github.noxaur.dsmod.core.ui

import kotlinx.serialization.Serializable

enum class TabEdge { TOP, BOTTOM }

@Serializable
data class MenuTab(
    val edge: TabEdge = TabEdge.BOTTOM,
    val slot: Int = 0,
    val command: String = "",
    val item: String = "minecraft:chest",
    val tooltip: String = "",
    val search: Boolean = false,
)

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

    fun x(slot: Int) = INSET + slot * STEP

    fun slotAt(x: Int): Int {
        val slot = Math.floorDiv(x - INSET + (STEP - TAB_WIDTH) / 2, STEP)
        return if (slot in 0 until PER_EDGE) slot else -1
    }

    fun at(tabs: List<MenuTab>, edge: TabEdge, slot: Int) = tabs.indexOfFirst { it.edge == edge && it.slot == slot }

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

    fun free(tabs: List<MenuTab>): Pair<TabEdge, Int>? {
        for (edge in listOf(TabEdge.BOTTOM, TabEdge.TOP)) {
            for (slot in 0 until PER_EDGE) if (at(tabs, edge, slot) < 0) return edge to slot
        }
        return null
    }

    /** @keep The tabs with a search tab on the top edge moved to the bottom one, at its rightmost free slot, for a menu
     * whose top edge is far from the items it searches, such as the storage overview. It stays on top when the bottom is full. */
    fun searchBelow(tabs: List<MenuTab>): List<MenuTab> {
        val search = tabs.indexOfFirst { it.search && it.edge == TabEdge.TOP }
        if (search < 0) return tabs
        val slot = (PER_EDGE - 1 downTo 0).firstOrNull { at(tabs, TabEdge.BOTTOM, it) < 0 } ?: return tabs
        return tabs.mapIndexed { i, tab -> if (i == search) tab.copy(edge = TabEdge.BOTTOM, slot = slot) else tab }
    }

    fun add(tabs: List<MenuTab>, tab: MenuTab): List<MenuTab>? {
        val (edge, slot) = free(tabs) ?: return null
        return tabs + tab.copy(edge = edge, slot = slot)
    }

    fun searchBar(slot: Int, menuWidth: Int): Pair<Int, Int> {
        val width = minOf(SEARCH_WIDTH, menuWidth)
        val tab = x(slot)
        val left = if (tab + TAB_WIDTH / 2 < menuWidth / 2) minOf(tab, menuWidth - width) else maxOf(0, tab + TAB_WIDTH - width)
        return left to width
    }

    fun selected(tabs: List<MenuTab>, opener: String?) = if (opener == null) -1 else tabs.indexOfFirst { !it.search && it.command == opener }

    fun itemId(typed: String): String {
        val id = typed.trim().lowercase().replace(' ', '_')
        return if (id.isEmpty() || ':' in id) id else "minecraft:$id"
    }

    fun command(typed: String) = typed.trim().removePrefix("/").trim()
}
